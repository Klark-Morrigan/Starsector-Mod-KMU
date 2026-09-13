package kmu.maplayers.politicalmap.base.render.hover;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how the political map answers what the framework asks about a hovered cell: whose loops
 * are the candidates and what shade the highlight burns in, both derived from who holds the system,
 * plus the frame's painted shapes handed straight over.
 *
 * <p>Also pins the identity contract the highlight's memoisation depends on: the same instances
 * come back for as long as the build behind them stands, so a cursor resting on one cell costs
 * one trace rather than one per frame.
 */
final class PoliticalMapHoverHighlightSourceTest {

    private static final String FACTION_ID = "hegemony";
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;
    private static final DominantHolder OWNER =
        new DominantHolder(FACTION_ID, PRIMARY, SECONDARY);

    @Nested
    class ResolveCandidateFrontierLoopsOf {

        @Test
        void resolveCandidateFrontierLoopsOfReturnsTheHoldersOwnBorderLoops() {
            // Every loop the holder traced is a candidate; which of them encloses this cell is
            // the highlight's own geometric question, not one answered here.
            var loops = List.of(
                buildSquareRun(0, 0, 100),
                buildSquareRun(500, 0, 100));
            var source = readSourceOf(buildTerritoriesWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildTerritoryWithLoops(loops)));

            assertThat(source.resolveCandidateFrontierLoopsOf(buildCellKey("A")))
                .containsExactlyElementsOf(loops);
        }

        @Test
        void resolveCandidateFrontierLoopsOfNarrowsTheCellToTheIdTheHoldingIsKeyedBy() {
            // The join with the holding: the hovered cell arrives keyed and the holder map names
            // a system by id, so a cell whose system states an anchor arm still haloes its
            // holder's frontier rather than reading as factionless.
            var loops = List.of(buildSquareRun(0, 0, 100));
            var source = readSourceOf(buildTerritoriesWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildTerritoryWithLoops(loops)));

            assertThat(source.resolveCandidateFrontierLoopsOf(new SystemKey("A", "", "8b3")))
                .containsExactlyElementsOf(loops);
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsNothingForAFactionlessCell() {
            // A decivilised or uninhabited cell fuses into no territory, so it has no frontier
            // to offer at all.
            var source = readSourceOf(buildTerritoriesWith(
                Map.of(),
                Map.of("A", buildSquare(10, 10, 80)),
                null));

            assertThat(source.resolveCandidateFrontierLoopsOf(buildCellKey("A")))
                .isEmpty();
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsNothingWhenTheHolderBakedNoBorder() {
            // The holder's border is "No color", so its territory carries no loops - an owned cell
            // that still has nothing to halo.
            var source = readSourceOf(buildTerritoriesWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildTerritoryWithLoops(List.of())));

            assertThat(source.resolveCandidateFrontierLoopsOf(buildCellKey("A")))
                .isEmpty();
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsTheSameListWhileTheBuildStands() {
            // The highlight memoises on this instance, so handing back a fresh copy per call
            // would re-trace the loops every frame the cursor rests on one cell.
            var source = readSourceOf(buildTerritoriesWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildTerritoryWithLoops(List.of(buildSquareRun(0, 0, 100)))));

            assertThat(source.resolveCandidateFrontierLoopsOf(buildCellKey("A")))
                .isSameAs(source.resolveCandidateFrontierLoopsOf(buildCellKey("A")));
        }
    }

    @Nested
    class ResolveHighlightColourOf {

        @Test
        void resolveHighlightColourOfPaintsAnOwnedCellInItsHoldersShade() {
            var source = readSourceOf(buildTerritoriesWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildTerritoryWithLoops(List.of())));

            assertThat(source.resolveHighlightColourOf(buildCellKey("A"), FactionPaletteSlot.SECONDARY))
                .isEqualTo(SECONDARY);
        }

        @Test
        void resolveHighlightColourOfPaintsAFactionlessCellInTheNeutralShade() {
            // An unowned cell has no palette of its own, so the highlight says so in the same
            // neutral the cell's own outline draws in.
            var source = readSourceOf(buildTerritoriesWith(
                Map.of(),
                Map.of("A", buildSquare(10, 10, 80)),
                null));

            assertThat(source.resolveHighlightColourOf(buildCellKey("A"), FactionPaletteSlot.PRIMARY))
                .isEqualTo(PoliticalMapTerritoryFixtures.NEUTRAL_COLOUR);
        }

        @Test
        void resolveHighlightColourOfReturnsNothingForANoColourChoice() {
            // The theme points the highlight at no shade at all, which the render pass reads as
            // "skip the whole thing".
            var source = readSourceOf(buildTerritoriesWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildTerritoryWithLoops(List.of())));

            assertThat(source.resolveHighlightColourOf(buildCellKey("A"), null))
                .isNull();
        }
    }

    @Nested
    class GetFillPolygonByCellKey {

        @Test
        void getFillPolygonByCellKeyHandsOverTheFramesOwnShapes() {
            // Passed through rather than rebuilt, which is what makes the halo trace the shape
            // the cursor was hit-tested against - the cursor read takes these same shapes off
            // the same territories. Reading one cell out of them is the framework's own,
            // pinned by PaintedCellShapesTest.
            var paintedExtent = buildSquare(10, 10, 80);
            var territories = buildTerritoriesWith(
                Map.of("A", OWNER),
                Map.of("A", paintedExtent),
                buildTerritoryWithLoops(List.of()));

            assertThat(readSourceOf(territories).getFillPolygonByCellKey())
                .isSameAs(territories.getFillPolygonByCellKey());
        }
    }

    private static PoliticalMapHoverHighlightSource readSourceOf(
            PoliticalMapTerritories territories) {
        return new PoliticalMapHoverHighlightSource(territories);
    }

    // Territories carrying just what the source reads: who owns each system, each cell's shape,
    // and the holder's traced loops. A null territory stands for a faction with none - the state
    // a factionless cell's holder lookup lands in.
    private static PoliticalMapTerritories buildTerritoriesWith(
            Map<String, DominantHolder> ownerBySystemId,
            Map<String, List<double[]>> fillPolygonBySystemId,
            StyledClusterGroup clusterGroup) {

        var territories = PoliticalMapTerritoryFixtures
            .createTerritoriesOwnedBy(ownerBySystemId);

        for (var cell : fillPolygonBySystemId.entrySet()) {
            territories.putStyledCell(
                buildCellKey(cell.getKey()),
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                cell.getValue());
        }
        if (clusterGroup != null) {
            territories.getStyledClusterGroupByOwnerId().put(FACTION_ID, clusterGroup);
        }
        return territories;
    }

    // A territory whose loops are all the source reads; its fills and paints never come up here.
    private static StyledClusterGroup buildTerritoryWithLoops(List<float[]> borderLoops) {
        // One body carrying every loop: the source flattens across bodies either way, so the
        // shape of the holding is not what any case here turns on.
        return PoliticalMapTerritoryFixtures.createTerritoryWithLoops(List.of(borderLoops));
    }

    // An axis-aligned square, counter-clockwise, standing in for a cell's painted extent; the
    // source hands it back untouched, so only its identity matters.
    private static List<double[]> buildSquare(double minX, double minY, double side) {
        return List.of(
            new double[] {minX, minY},
            new double[] {minX + side, minY},
            new double[] {minX + side, minY + side},
            new double[] {minX, minY + side});
    }

    // The same square as the baked [x, y, x, y, ...] run a border loop is kept in.
    private static float[] buildSquareRun(float minX, float minY, float side) {
        return new float[] {
            minX, minY,
            minX + side, minY,
            minX + side, minY + side,
            minX, minY + side};
    }
}
