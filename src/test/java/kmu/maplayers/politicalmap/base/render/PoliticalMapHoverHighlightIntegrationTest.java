package kmu.maplayers.politicalmap.base.render;

import kmlib.math.geometry.CornerRounding;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.ShapedCell;
import kmu.maplayers.base.hover.HoverHighlight;
import kmu.maplayers.base.hover.HoverHighlightGeometry;
import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.base.theme.ThemeFixtures;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.render.hover.PoliticalMapHoverHighlightSource;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapCategory;
import kmu.maplayers.politicalmap.base.render.territories.MapStyling;
import kmu.maplayers.politicalmap.base.render.territories.PaintedCellBuilder;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;
import kmu.maplayers.politicalmap.base.render.territories.SystemOccupancy;
import kmu.maplayers.politicalmap.base.render.territories.TerritoryBuildInputs;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the one claim no unit test on either side of it can make: what a hover lights up over an
 * unpopulated cell is the ring that cell actually strokes.
 *
 * <p>Four parts have to agree for that to hold - the builder rounds a lone cell's corners, it
 * reports the rounded ring rather than the cell it was shaped from, the store keeps what it was
 * handed, and the highlight traces what the store answers with. Each is covered where it lives, and
 * a break anywhere along the chain reads the same way on screen: a wash and a trace standing a
 * rounding radius outside the outline beneath them, on the cells that cover most of the sector.
 *
 * <p>The suites either side of this one cannot catch that. The builder's own suite stops at the
 * record it returns, and the highlight's own suite is handed extents through a fake, so both would
 * pass with the two halves describing different shapes - which is exactly the state this chain was
 * in before the ring was reported at all.
 *
 * <p>An unpopulated cell is the case that matters because it is the one with nothing else to fall
 * back on: a cell inside a bloc is clamped to its cluster's frontier, which is rounded when it is
 * traced, so the clip hides a raw ring. A cell that fuses into nothing is clamped to nothing, and
 * the ring it reports is the whole of what lights up.
 */
final class PoliticalMapHoverHighlightIntegrationTest {

    // The cell the whole chain is exercised over. Held by nobody and settled by nobody, which is
    // what routes it down the builder's lone-cell path.
    private static final String UNPOPULATED_SYSTEM_ID = "never-settled-system";
    private static final SystemKey UNPOPULATED_CELL = buildCellKey(UNPOPULATED_SYSTEM_ID);

    // A corner shape that rounds the 10-unit cell below without being clamped: the step-back is
    // capped at half the shorter adjacent edge, so a radius under 5 arcs at its full size. The
    // chamfer threshold is off, so every corner arcs rather than being cut.
    private static final CornerRoundingStyle ROUNDING_ON = new CornerRoundingStyle(
        true,
        3.0,
        4,
        0.0,
        CornerRounding.ROUND_EVERY_CORNER);

    private static final CornerRoundingStyle ROUNDING_OFF =
        new CornerRoundingStyle(false, 0, 0, 0, CornerRounding.ROUND_EVERY_CORNER);

    @Nested
    class ResolveHighlightFor {

        @Test
        void lightsTheRoundedRingAnUnpopulatedCellStrokesRatherThanTheCellItWasShapedFrom() {

            var highlight = buildAndHoverUnpopulatedCellUnder(ROUNDING_ON);

            // The corner the raw Voronoi cell has at the origin is gone from what lights up,
            // because rounding stepped back from it along both edges before the cell was recorded.
            // Were the shaped cell recorded instead, the wash would trace straight through here.
            assertThat(hasPoint(highlight.washOutline(), 0, 0))
                .isFalse();

            // And it lights up at all, so the case above cannot pass by resolving nothing.
            assertThat(highlight.washOutline())
                .isNotEmpty();
            assertThat(highlight.washTriangles())
                .isNotEmpty();
        }

        @Test
        void lightsTheSharpRingAnUnpopulatedCellStrokesWhenRoundingIsOff() {
            // The other half of the gate, and what stops the case above from passing on any ring
            // that merely happens to miss the origin: under the same chain with rounding off, the
            // cell strokes the shape it was shaped from and that is what lights up.
            var highlight = buildAndHoverUnpopulatedCellUnder(ROUNDING_OFF);

            assertThat(hasPoint(highlight.washOutline(), 0, 0))
                .isTrue();
        }

        @Test
        void haloesNothingOverAnUnpopulatedCell() {
            // A cell that fuses into no cluster has no frontier to trace, so the halo half of the
            // highlight is empty and the wash is the whole of what it lights. Stated here because
            // it is why the wash has to carry the drawn ring: there is no clamp behind it to
            // correct a raw one.
            var highlight = buildAndHoverUnpopulatedCellUnder(ROUNDING_ON);

            assertThat(highlight.glowLoops())
                .isEmpty();
        }
    }

    // The whole chain over one unpopulated cell: shape it, build it through the real builder under
    // the given rounding, record it as a build does, and resolve what a cursor resting on it lights
    // up. Nothing is stubbed between the builder and the highlight, which is the point.
    private static HoverHighlight buildAndHoverUnpopulatedCellUnder(
            CornerRoundingStyle cornerRounding) {

        var territories = buildUnpopulatedTerritoriesUnder(cornerRounding);

        var painted = PaintedCellBuilder.buildPaintedCellForSystem(
            territories,
            UNPOPULATED_CELL,
            buildSquareCell());

        assertThat(painted)
            .as("the fixture must draw a cell for the chain to carry one")
            .isNotNull();

        territories.getPaintedCells().putPaintedCell(UNPOPULATED_CELL, painted);

        return new HoverHighlightGeometry().resolveHighlightFor(
            new PoliticalMapHoverHighlightSource(territories),
            new MapHover(UNPOPULATED_CELL, List.of(UNPOPULATED_CELL)));
    }

    // A build holding nobody and settling nowhere, so the cell routes to the uninhabited category,
    // under a theme whose only live knob is the corner rounding a case names.
    private static PoliticalMapTerritories buildUnpopulatedTerritoriesUnder(
            CornerRoundingStyle cornerRounding) {

        return new PoliticalMapTerritories(
            SystemOccupancy.createEmpty(),
            new TerritoryBuildInputs(
                new MapStyling(
                    buildThemeRoundingBy(cornerRounding),
                    PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE,
                    PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE,
                    PoliticalMapTerritoryFixtures.NEUTRAL_PALETTE),
                new ViewGrouping(mock(PoliticalMapView.class), HolderGrouping.identity()),
                ContentInputs.createEmpty(),
                Set.of(),
                Set.of()));
    }

    // Every category drawn by its outline alone, over a sector-wide tier carrying the given corner
    // rounding. Outline-only because that is how an uninhabited cell actually draws, and it is the
    // element whose ring this chain is about.
    private static RenderStyle buildThemeRoundingBy(CornerRoundingStyle cornerRounding) {

        var outlineOnly = new CategoryStyle(
            new ElementStyle(null, 1.0),
            new ElementStyle(FactionPaletteSlot.PRIMARY, 1.0),
            3.0,
            new ElementStyle(null, 1.0),
            1.0);

        var categories = new LinkedHashMap<MapStyleCategory, CategoryStyle>();
        for (var category : PoliticalMapCategory.values()) {
            categories.put(category, outlineOnly);
        }
        return new RenderStyle(
            ThemeFixtures.createGlobalStyleRoundingBy(cornerRounding),
            categories);
    }

    // A 10-unit square cell with a corner on the origin, so a case can name one vertex the rounding
    // is expected to remove. Every edge is national border, which is what a lone cell's are.
    private static ShapedCell buildSquareCell() {
        return new ShapedCell(
            List.of(
                new double[] {0, 0},
                new double[] {10, 0},
                new double[] {10, 10},
                new double[] {0, 10}),
            new boolean[] {true, true, true, true});
    }

    // Whether any of the traced loops passes through the given point. Each loop is a flat
    // [x, y, x, y, ...] run of vertices, and the clip can bite an extent into more than one loop,
    // so every one of them is asked.
    private static boolean hasPoint(List<float[]> loops, float x, float y) {
        for (var loop : loops) {
            for (var index = 0; index < loop.length; index += 2) {
                if (loop[index] == x && loop[index + 1] == y) {
                    return true;
                }
            }
        }
        return false;
    }
}
