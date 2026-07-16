package kmu.maplayers.politicalmap.base.render.hover;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.hover.PoliticalMapHover;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.render.territories.FactionTerritory;
import kmu.maplayers.politicalmap.base.render.territories.FilterSnapshot;
import kmu.maplayers.politicalmap.base.render.territories.MapStyling;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.StyledCell;
import kmu.maplayers.politicalmap.base.render.territories.ViewGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the contract of {@link HoverHighlightGeometry#resolveHighlightFor}:
 *  - a hovered cell resolves the one border loop of its owner that encloses it, and never
 *    that owner's other pockets,
 *  - nested loops resolve the innermost - the cell's own cluster - rather than the distant
 *    one that also happens to contain it,
 *  - a factionless or unowned-loop cell still washes, with no halo,
 *  - nothing hovered, or a cell with no drawable shape, resolves nothing,
 *  - a resting cursor resolves once and reuses the answer until the geometry under it changes.
 *
 * <p>The fixtures are hand-built squares standing in for cells and traced border loops: the
 * search reads only the polygons and runs it is handed, so no shaping, styling, or GL is
 * involved in which loop it picks.
 */
final class HoverHighlightGeometryTest {
    private static final String FACTION_ID = "hegemony";
    private static final DominantOwner OWNER =
            new DominantOwner(FACTION_ID, Color.RED, Color.BLUE);

    @Nested
    class ResolveHighlightFor {
        @Test
        void a_hovered_cell_resolves_the_loop_that_encloses_it() {
            var loop = squareRun(0, 0, 100);
            var territories = territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(10, 10, 80)),
                    territoryWithLoops(List.of(loop)));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(territories, hoverOf("A"));

            assertThat(highlight.glowLoops()).containsExactly(loop);
        }

        @Test
        void a_faction_s_other_pocket_never_glows_for_a_cell_it_does_not_enclose() {
            // One faction, two disjoint clusters - the whole reason the loop is searched for
            // rather than taken as "the owner's border".
            var hoveredLoop = squareRun(0, 0, 100);
            var distantLoop = squareRun(500, 0, 100);
            var territories = territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(10, 10, 80)),
                    territoryWithLoops(List.of(distantLoop, hoveredLoop)));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(territories, hoverOf("A"));

            assertThat(highlight.glowLoops()).containsExactly(hoveredLoop);
        }

        @Test
        void nested_loops_resolve_the_innermost_one_around_the_cell() {
            // The faction's enclave, walled inside a rival that is itself walled inside the
            // faction's own cluster: three of its loops enclose the cell, and only the tightest
            // is the cluster the cell actually belongs to.
            var outerCluster = squareRun(0, 0, 1000);
            var enclaveInsideRival = squareRun(400, 400, 100);
            var territories = territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(410, 410, 80)),
                    territoryWithLoops(List.of(outerCluster, enclaveInsideRival)));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(territories, hoverOf("A"));

            assertThat(highlight.glowLoops()).containsExactly(enclaveInsideRival);
        }

        @Test
        void a_factionless_cell_washes_with_no_halo() {
            // A decivilised or uninhabited cell fuses into no territory, so there is no frontier
            // to bloom - but the cell itself is still what the cursor is on.
            var territories = territoriesWith(
                    Map.of(),
                    Map.of("A", square(10, 10, 80)),
                    null);

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(territories, hoverOf("A"));

            assertThat(highlight.glowLoops()).isEmpty();
            assertThat(highlight.washOutline()).isNotEmpty();
            assertThat(highlight.washTriangles()).isNotEmpty();
        }

        @Test
        void a_cell_no_loop_encloses_washes_with_no_halo() {
            // The faction's border is "No color", so it baked no loops at all; the cell still
            // reads under the cursor.
            var territories = territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(10, 10, 80)),
                    territoryWithLoops(List.of()));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(territories, hoverOf("A"));

            assertThat(highlight.glowLoops()).isEmpty();
            assertThat(highlight.washOutline()).isNotEmpty();
        }

        @Test
        void nothing_hovered_lights_nothing_up() {
            var territories = territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(10, 10, 80)),
                    territoryWithLoops(List.of(squareRun(0, 0, 100))));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(territories, PoliticalMapHover.NONE);

            assertThat(highlight.isEmpty()).isTrue();
        }

        @Test
        void a_hovered_cell_with_no_drawable_shape_lights_nothing_up() {
            var territories = territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", List.of()),
                    territoryWithLoops(List.of(squareRun(0, 0, 100))));

            var highlight = new HoverHighlightGeometry()
                    .resolveHighlightFor(territories, hoverOf("A"));

            assertThat(highlight.isEmpty()).isTrue();
        }

        @Test
        void a_resting_cursor_reuses_the_answer_it_already_resolved() {
            // The whole point of the memo: this runs every frame, and re-tracing the same loops
            // sixty times a second for an answer that cannot have changed is pure waste.
            var territories = territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(10, 10, 80)),
                    territoryWithLoops(List.of(squareRun(0, 0, 100))));
            var geometry = new HoverHighlightGeometry();

            var first = geometry.resolveHighlightFor(territories, hoverOf("A"));
            var second = geometry.resolveHighlightFor(territories, hoverOf("A"));

            assertThat(second).isSameAs(first);
        }

        @Test
        void a_rebuilt_cell_resolves_again_rather_than_tracing_a_shape_that_is_gone() {
            var territories = territoriesWith(
                    Map.of("A", OWNER),
                    Map.of("A", square(10, 10, 80)),
                    territoryWithLoops(List.of(squareRun(0, 0, 100))));
            var geometry = new HoverHighlightGeometry();
            var first = geometry.resolveHighlightFor(territories, hoverOf("A"));

            // An incremental re-shape replaces the cell's polygon and its faction's loops; the
            // retained answer describes geometry the map no longer paints.
            var reshapedLoop = squareRun(0, 0, 60);
            territories.putStyledCell("A", styledCell(), square(5, 5, 50));
            territories.getFactionTerritoryByFactionId()
                    .put(FACTION_ID, territoryWithLoops(List.of(reshapedLoop)));

            var second = geometry.resolveHighlightFor(territories, hoverOf("A"));

            assertThat(second).isNotSameAs(first);
            assertThat(second.glowLoops()).containsExactly(reshapedLoop);
        }
    }

    private static PoliticalMapHover hoverOf(String systemId) {
        return new PoliticalMapHover(systemId, List.of(systemId));
    }

    // Territories carrying just what the search reads: who owns each system, each cell's shape,
    // and the owner's traced loops. A null territory stands for a faction with none - the state
    // a factionless cell's owner lookup lands in.
    private static PoliticalMapTerritories territoriesWith(
            Map<String, DominantOwner> ownerBySystemId,
            Map<String, List<double[]>> fillPolygonBySystemId,
            FactionTerritory territory) {
        PoliticalMapView viewMock = mock(PoliticalMapView.class);
        var territories = new PoliticalMapTerritories(
                new LinkedHashMap<>(ownerBySystemId),
                new LinkedHashSet<>(),
                new MapStyling(null, Color.GRAY, new FactionPalette(Color.GRAY, Color.GRAY)),
                new ViewGrouping(viewMock, OwnershipGrouping.identity()),
                new FilterSnapshot(null, BlocStyleAdjustment.NONE, new LinkedHashSet<>()));
        for (var cell : fillPolygonBySystemId.entrySet()) {
            territories.putStyledCell(cell.getKey(), styledCell(), cell.getValue());
        }
        if (territory != null) {
            territories.getFactionTerritoryByFactionId().put(FACTION_ID, territory);
        }
        return territories;
    }

    // A territory whose loops are all the search reads; its fills and paints never come up here.
    private static FactionTerritory territoryWithLoops(List<float[]> borderLoops) {
        var noPaint = new UiElementPaint(null, 0);
        return new FactionTerritory(new float[0], new float[0], noPaint, borderLoops, noPaint, 0);
    }

    // A draw record standing in for the cell's own styling, which the search never reads - the
    // shape it is paired with is what matters.
    private static StyledCell styledCell() {
        var noPaint = new UiElementPaint(null, 0);
        return new StyledCell(new float[0], new float[0], new float[0], noPaint, noPaint,
                noPaint, 0, 0);
    }

    // An axis-aligned square, counter-clockwise, spanning [minX, minX + side] x
    // [minY, minY + side] - a stand-in cell shape or traced loop, which the search reads only
    // as an area that does or does not enclose a point.
    private static List<double[]> square(double minX, double minY, double side) {
        return List.of(
                new double[] {minX, minY},
                new double[] {minX + side, minY},
                new double[] {minX + side, minY + side},
                new double[] {minX, minY + side});
    }

    // The same square as the baked [x, y, x, y, ...] run a border loop is kept in.
    private static float[] squareRun(float minX, float minY, float side) {
        return new float[] {
            minX, minY,
            minX + side, minY,
            minX + side, minY + side,
            minX, minY + side};
    }
}
