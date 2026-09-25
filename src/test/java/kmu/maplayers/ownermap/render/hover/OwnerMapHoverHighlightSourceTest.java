package kmu.maplayers.ownermap.render.hover;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.style.FactionPaletteSlot;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how an owner map answers what the framework asks about a hovered cell: whose loops
 * are the candidates and what shade the highlight burns in, both derived from who holds the system,
 * plus the frame's painted shapes handed straight over.
 *
 * <p>Also pins the identity contract the highlight's memoisation depends on: the same instances
 * come back for as long as the build behind them stands, so a cursor resting on one cell costs
 * one trace rather than one per frame.
 */
final class OwnerMapHoverHighlightSourceTest {

    private static final String FACTION_ID = "hegemony";
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;
    private static final SystemOwner OWNER =
        new SystemOwner(FACTION_ID, PRIMARY, SECONDARY);

    @Nested
    class ResolveCandidateFrontierLoopsOf {

        @Test
        void resolveCandidateFrontierLoopsOfReturnsTheHoldersOwnBorderLoops() {
            // Every loop the holder traced is a candidate; which of them encloses this cell is
            // the highlight's own geometric question, not one answered here.
            var loops = List.of(
                buildSquareRun(0, 0, 100),
                buildSquareRun(500, 0, 100));
            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(loops)));

            assertThat(source.resolveCandidateFrontierLoopsOf(buildCellKey("A")))
                .containsExactlyElementsOf(loops);
        }

        @Test
        void resolveCandidateFrontierLoopsOfAnswersTheHoveredSystemOfAPairSharingAnId() {
            // The cursor and the holding share one address, so hovering one of two systems that
            // answer to "A" haloes that system's own holder - where a lookup by ID alone would
            // have handed back the other's frontier.
            var hovered = new SystemKey("A", "", "8b3");
            var loops = List.of(buildSquareRun(0, 0, 100));
            var source = readSourceOf(buildClustersHeldByKey(
                Map.of(hovered, OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(loops)));

            assertThat(source.resolveCandidateFrontierLoopsOf(hovered))
                .containsExactlyElementsOf(loops);

            assertThat(source.resolveCandidateFrontierLoopsOf(new SystemKey("A", "", "38d53")))
                .isEmpty();
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsNothingForAFactionlessCell() {
            // A decivilised or uninhabited cell fuses into no cluster group, so it has no frontier
            // to offer at all.
            var source = readSourceOf(buildClustersWith(
                Map.of(),
                Map.of("A", buildSquare(10, 10, 80)),
                null));

            assertThat(source.resolveCandidateFrontierLoopsOf(buildCellKey("A")))
                .isEmpty();
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsNothingWhenTheHolderBakedNoBorder() {
            // The holder's border is "No color", so its cluster group carries no loops - an owned cell
            // that still has nothing to halo.
            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(List.of())));

            assertThat(source.resolveCandidateFrontierLoopsOf(buildCellKey("A")))
                .isEmpty();
        }

        @Test
        void resolveCandidateFrontierLoopsOfReturnsTheSameListWhileTheBuildStands() {
            // The highlight memoises on this instance, so handing back a fresh copy per call
            // would re-trace the loops every frame the cursor rests on one cell.
            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(List.of(buildSquareRun(0, 0, 100)))));

            assertThat(source.resolveCandidateFrontierLoopsOf(buildCellKey("A")))
                .isSameAs(source.resolveCandidateFrontierLoopsOf(buildCellKey("A")));
        }
    }

    @Nested
    class ResolveHighlightColourOf {

        @Test
        void resolveHighlightColourOfPaintsAnOwnedCellInItsHoldersShade() {
            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(List.of())));

            assertThat(source.resolveHighlightColourOf(buildCellKey("A"), FactionPaletteSlot.SECONDARY))
                .isEqualTo(SECONDARY);
        }

        @Test
        void resolveHighlightColourOfPaintsAFactionlessCellInTheNeutralShade() {
            // An unowned cell has no palette of its own, so the highlight says so in the same
            // neutral the cell's own outline draws in.
            var source = readSourceOf(buildClustersWith(
                Map.of(),
                Map.of("A", buildSquare(10, 10, 80)),
                null));

            assertThat(source.resolveHighlightColourOf(buildCellKey("A"), FactionPaletteSlot.PRIMARY))
                .isEqualTo(OwnerMapClusterFixtures.NEUTRAL_COLOUR);
        }

        @Test
        void resolveHighlightColourOfReturnsNothingForANoColourChoice() {
            // The theme points the highlight at no shade at all, which the render pass reads as
            // "skip the whole thing".
            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(List.of())));

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
            // the same clusters. Reading one cell out of them is the framework's own,
            // pinned by PaintedCellShapesTest.
            var paintedExtent = buildSquare(10, 10, 80);
            var clusters = buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", paintedExtent),
                buildClusterGroupWithLoops(List.of()));

            assertThat(readSourceOf(clusters).getFillPolygonByCellKey())
                .isSameAs(clusters.getFillPolygonByCellKey());
        }
    }

    private static OwnerMapHoverHighlightSource readSourceOf(
            OwnerMapClusters clusters) {
        return new OwnerMapHoverHighlightSource(clusters);
    }

    // Clusters carrying just what the source reads: who owns each system, each cell's shape,
    // and the holder's traced loops. A null cluster group stands for a faction with none - the state
    // a factionless cell's holder lookup lands in.
    private static OwnerMapClusters buildClustersWith(
            Map<String, SystemOwner> ownerBySystemId,
            Map<String, List<double[]>> fillPolygonBySystemId,
            StyledClusterGroup clusterGroup) {

        return fillClusters(
            OwnerMapClusterFixtures.createClustersOwnedBy(ownerBySystemId),
            fillPolygonBySystemId,
            clusterGroup);
    }

    // The same clusters with its holders stated by key, for the one case a name cannot pose:
    // two systems sharing a vanilla ID, only one of which the cursor is over.
    private static OwnerMapClusters buildClustersHeldByKey(
            Map<SystemKey, SystemOwner> ownerBySystemKey,
            Map<String, List<double[]>> fillPolygonBySystemId,
            StyledClusterGroup clusterGroup) {

        return fillClusters(
            OwnerMapClusterFixtures.createClustersOwnedByKeys(ownerBySystemKey),
            fillPolygonBySystemId,
            clusterGroup);
    }

    // Writes the cells and the holder's cluster group onto a built map, which is all the source reads
    // beyond the holding the two builders above differ in how they state.
    private static OwnerMapClusters fillClusters(
            OwnerMapClusters clusters,
            Map<String, List<double[]>> fillPolygonBySystemId,
            StyledClusterGroup clusterGroup) {

        for (var cell : fillPolygonBySystemId.entrySet()) {
            clusters.getPaintedCells().putPaintedCell(
                buildCellKey(cell.getKey()),
                OwnerMapClusterFixtures.createPlaceholderPaintedCellOn(cell.getValue()));
        }
        if (clusterGroup != null) {
            clusters.getStyledClusterGroupByOwnerId().put(FACTION_ID, clusterGroup);
        }
        return clusters;
    }

    // A cluster group whose loops are all the source reads; its fills and paints never come up here.
    private static StyledClusterGroup buildClusterGroupWithLoops(List<float[]> borderLoops) {
        // One body carrying every loop: the source flattens across bodies either way, so the
        // shape of the holding is not what any case here turns on.
        return OwnerMapClusterFixtures.createClusterGroupWithLoops(List.of(borderLoops));
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
