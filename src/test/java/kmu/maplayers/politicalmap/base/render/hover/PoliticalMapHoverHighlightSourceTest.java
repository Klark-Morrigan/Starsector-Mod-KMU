package kmu.maplayers.politicalmap.base.render.hover;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how the political map answers what the framework asks about a hovered cell: whose loops
 * are the candidates and what shade the ground burns in, both derived from who holds the system,
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
                squareRun(0, 0, 100),
                squareRun(500, 0, 100));
            var source = sourceOf(territoriesWith(
                Map.of("A", OWNER),
                Map.of("A", square(10, 10, 80)),
                territoryWithLoops(loops)));

            assertThat(source.resolveCandidateFrontierLoopsOf("A"))
                .containsExactlyElementsOf(loops);
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsNothingForFactionlessGround() {
            // A decivilised or uninhabited cell fuses into no territory, so it has no frontier
            // to offer at all.
            var source = sourceOf(territoriesWith(
                Map.of(),
                Map.of("A", square(10, 10, 80)),
                null));

            assertThat(source.resolveCandidateFrontierLoopsOf("A"))
                .isEmpty();
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsNothingWhenTheHolderBakedNoBorder() {
            // The holder's border is "No color", so its territory carries no loops - owned ground
            // that still has nothing to halo.
            var source = sourceOf(territoriesWith(
                Map.of("A", OWNER),
                Map.of("A", square(10, 10, 80)),
                territoryWithLoops(List.of())));

            assertThat(source.resolveCandidateFrontierLoopsOf("A"))
                .isEmpty();
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsTheSameListWhileTheBuildStands() {
            // The highlight memoises on this instance, so handing back a fresh copy per call
            // would re-trace the loops every frame the cursor rests on one cell.
            var source = sourceOf(territoriesWith(
                Map.of("A", OWNER),
                Map.of("A", square(10, 10, 80)),
                territoryWithLoops(List.of(squareRun(0, 0, 100)))));

            assertThat(source.resolveCandidateFrontierLoopsOf("A"))
                .isSameAs(source.resolveCandidateFrontierLoopsOf("A"));
        }
    }

    @Nested
    class ResolveHighlightColourOf {

        @Test
        void resolveHighlightColourOfPaintsOwnedGroundInItsHoldersShade() {
            var source = sourceOf(territoriesWith(
                Map.of("A", OWNER),
                Map.of("A", square(10, 10, 80)),
                territoryWithLoops(List.of())));

            assertThat(source.resolveHighlightColourOf("A", FactionPaletteSlot.SECONDARY))
                .isEqualTo(SECONDARY);
        }

        @Test
        void resolveHighlightColourOfPaintsFactionlessGroundInTheNeutralShade() {
            // Unowned ground has no palette of its own, so the highlight says so in the same
            // neutral the cell's own outline draws in.
            var source = sourceOf(territoriesWith(
                Map.of(),
                Map.of("A", square(10, 10, 80)),
                null));

            assertThat(source.resolveHighlightColourOf("A", FactionPaletteSlot.PRIMARY))
                .isEqualTo(PoliticalMapTerritoryFixtures.NEUTRAL_COLOUR);
        }

        @Test
        void resolveHighlightColourOfReturnsNothingForANoColourChoice() {
            // The theme points the highlight at no shade at all, which the render pass reads as
            // "skip the whole thing".
            var source = sourceOf(territoriesWith(
                Map.of("A", OWNER),
                Map.of("A", square(10, 10, 80)),
                territoryWithLoops(List.of())));

            assertThat(source.resolveHighlightColourOf("A", null))
                .isNull();
        }
    }

    @Nested
    class GetFillPolygonByCellId {

        @Test
        void getFillPolygonByCellIdHandsOverTheFramesOwnShapes() {
            // Passed through rather than rebuilt, which is what makes the halo trace the shape
            // the cursor was hit-tested against - the cursor read takes these same shapes off
            // the same territories. Reading one cell out of them is the framework's own,
            // pinned by PaintedCellShapesTest.
            var paintedExtent = square(10, 10, 80);
            var territories = territoriesWith(
                Map.of("A", OWNER),
                Map.of("A", paintedExtent),
                territoryWithLoops(List.of()));

            assertThat(sourceOf(territories).getFillPolygonByCellId())
                .isSameAs(territories.getFillPolygonByCellId());
        }
    }

    private static PoliticalMapHoverHighlightSource sourceOf(
            PoliticalMapTerritories territories) {
        return new PoliticalMapHoverHighlightSource(territories);
    }

    // Territories carrying just what the source reads: who owns each system, each cell's shape,
    // and the holder's traced loops. A null territory stands for a faction with none - the state
    // a factionless cell's holder lookup lands in.
    private static PoliticalMapTerritories territoriesWith(
            Map<String, DominantHolder> ownerBySystemId,
            Map<String, List<double[]>> fillPolygonBySystemId,
            StyledClusterGroup clusterGroup) {

        var territories = PoliticalMapTerritoryFixtures
            .createTerritoriesOwnedBy(ownerBySystemId);

        for (var cell : fillPolygonBySystemId.entrySet()) {
            territories.putStyledCell(
                cell.getKey(),
                PoliticalMapTerritoryFixtures.createPlaceholderStyledCell(),
                cell.getValue());
        }
        if (clusterGroup != null) {
            territories.getStyledClusterGroupByOwnerId().put(FACTION_ID, clusterGroup);
        }
        return territories;
    }

    // A territory whose loops are all the source reads; its fills and paints never come up here.
    private static StyledClusterGroup territoryWithLoops(List<float[]> borderLoops) {
        // One body carrying every loop: the source flattens across bodies either way, so the
        // shape of the holding is not what any case here turns on.
        return PoliticalMapTerritoryFixtures.createTerritoryWithLoops(List.of(borderLoops));
    }

    // An axis-aligned square, counter-clockwise, standing in for a cell's painted extent; the
    // source hands it back untouched, so only its identity matters.
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
