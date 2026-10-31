package kmu.maplayers.ownermap.render.hover;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.style.OwnerPaletteSlot;

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
 * <p>Also pins the identity contract the highlight's memoisation depends on: the same loops come
 * back for as long as the owner's cluster group stands, so a cursor resting on one cell costs one
 * trace rather than one per frame, and a replaced group is flattened afresh.
 */
final class OwnerMapHoverHighlightSourceTests {

    private static final String OWNER_ID = "hegemony";
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;
    private static final SystemOwner OWNER =
        new SystemOwner(OWNER_ID, new ColourPair(PRIMARY, SECONDARY));

    @Nested
    class ResolveCandidateFrontierLoopsOf {

        // Every loop the owner traced is a candidate; which of them encloses this cell is the
        // highlight's own geometric question, not one answered here.
        @Test
        void returnsTheOwnersOwnBorderLoops() {

            // ARRANGE
            var expectedLoops = List.of(
                buildSquareRun(0, 0, 100),
                buildSquareRun(500, 0, 100));

            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(expectedLoops)));

            // ACT
            var actualLoops = source.resolveCandidateFrontierLoopsOf(buildCellKey("A"));

            // ASSERT
            assertThat(actualLoops)
                .containsExactlyElementsOf(expectedLoops);
        }

        // Flattened across bodies and in cluster order: the cursor read cannot say which body it is
        // in, so every loop the owner strokes anywhere is a candidate.
        @Test
        void gathersEveryLoopOfEveryBodyOuterRingsAndEnclavesAlike() {

            // ARRANGE
            var homeOuter = buildSquareRun(0, 0, 100);
            var homeEnclave = buildSquareRun(40, 40, 20);
            var exclaveOuter = buildSquareRun(500, 0, 100);

            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                OwnerMapClusterFixtures.createClusterGroupWithLoops(List.of(
                    List.of(homeOuter, homeEnclave),
                    List.of(exclaveOuter)))));

            // ACT
            var actualLoops = source.resolveCandidateFrontierLoopsOf(buildCellKey("A"));

            // ASSERT
            assertThat(actualLoops)
                .containsExactly(homeOuter, homeEnclave, exclaveOuter);
        }

        // The cursor and the holding share one address, so hovering one of two systems that answer
        // to "A" haloes that system's own owner - where a lookup by ID alone would have handed back
        // the other's frontier.
        @Test
        void answersTheHoveredSystemOfAPairSharingAnId() {

            // ARRANGE
            var hovered = new SystemKey("A", "", "8b3");
            var twin = new SystemKey("A", "", "38d53");
            var expectedLoops = List.of(buildSquareRun(0, 0, 100));

            var source = readSourceOf(buildClustersHeldByKey(
                Map.of(hovered, OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(expectedLoops)));

            // ACT
            var actualHoveredLoops = source.resolveCandidateFrontierLoopsOf(hovered);
            var actualTwinLoops = source.resolveCandidateFrontierLoopsOf(twin);

            // ASSERT
            assertThat(actualHoveredLoops)
                .containsExactlyElementsOf(expectedLoops);
            assertThat(actualTwinLoops)
                .isEmpty();
        }

        // A decivilised or uninhabited cell fuses into no cluster group, so it has no frontier to
        // offer at all.
        @Test
        void returnsNothingForAnUnownedCell() {

            // ARRANGE
            var source = readSourceOf(buildClustersWith(
                Map.of(),
                Map.of("A", buildSquare(10, 10, 80)),
                null));

            // ACT
            var actualLoops = source.resolveCandidateFrontierLoopsOf(buildCellKey("A"));

            // ASSERT
            assertThat(actualLoops)
                .isEmpty();
        }

        // An owner whose group baked nothing is dropped from the draw lists, so its cell has no
        // frontier to offer even though somebody holds it.
        @Test
        void returnsNothingForAnOwnerWithNoClusterGroup() {

            // ARRANGE
            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                null));

            // ACT
            var actualLoops = source.resolveCandidateFrontierLoopsOf(buildCellKey("A"));

            // ASSERT
            assertThat(actualLoops)
                .isEmpty();
        }

        // The owner's border is "No color", so its cluster group carries no loops - an owned cell
        // that still has nothing to halo.
        @Test
        void returnsNothingWhenTheOwnerBakedNoBorder() {

            // ARRANGE
            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(List.of())));

            // ACT
            var actualLoops = source.resolveCandidateFrontierLoopsOf(buildCellKey("A"));

            // ASSERT
            assertThat(actualLoops)
                .isEmpty();
        }

        // Identity, not equality. The highlight memoises its resolved halo against the instance it
        // was handed, so a fresh list per ask would re-clip the wash every frame the cursor rests
        // on one cell - a change no assertion about the loops themselves would notice.
        @Test
        void returnsTheSameListWhileTheOwnersGroupStands() {

            // ARRANGE
            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(List.of(buildSquareRun(0, 0, 100)))));

            // ACT
            var actualFirstLoops = source.resolveCandidateFrontierLoopsOf(buildCellKey("A"));
            var actualSecondLoops = source.resolveCandidateFrontierLoopsOf(buildCellKey("A"));

            // ASSERT
            assertThat(actualSecondLoops)
                .isSameAs(actualFirstLoops);
        }

        // A previewed set asks after the holders of all its cells every frame. Asking after a
        // second owner must not evict the first owner's list, or every cell of a set held by
        // several owners would miss the highlight's memo on every frame.
        @Test
        void keepsEachOwnersListWhileTheAsksMoveBetweenOwners() {

            // ARRANGE
            var rival = new SystemOwner("tritachyon", new ColourPair(PRIMARY, SECONDARY));
            var clusters = fillClusters(
                OwnerMapClusterFixtures.createClustersOwnedBy(Map.of("A", OWNER, "B", rival)),
                Map.of(
                    "A", buildSquare(10, 10, 80),
                    "B", buildSquare(510, 10, 80)),
                buildClusterGroupWithLoops(List.of(buildSquareRun(0, 0, 100))));

            clusters
                .getStyledClusterGroupByOwnerId()
                .put(rival.ownerId(), buildClusterGroupWithLoops(List.of(buildSquareRun(500, 0, 100))));

            var source = readSourceOf(clusters);

            // ACT
            var actualFirstLoops = source.resolveCandidateFrontierLoopsOf(buildCellKey("A"));

            source.resolveCandidateFrontierLoopsOf(buildCellKey("B"));

            var actualReturningLoops = source.resolveCandidateFrontierLoopsOf(buildCellKey("A"));

            // ASSERT
            assertThat(actualReturningLoops)
                .isSameAs(actualFirstLoops);
        }

        // The other half of retaining an answer: an incremental refresh replaces an owner's group
        // wholesale when a neighbour flips, and a retained list that outlived it would halo a
        // frontier the map is no longer drawing.
        @Test
        void flattensAgainWhenARefreshReplacesTheOwnersGroup() {

            // ARRANGE
            var rebuiltLoop = buildSquareRun(900, 900, 100);
            var clusters = buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(List.of(buildSquareRun(0, 0, 100))));

            var source = readSourceOf(clusters);

            source.resolveCandidateFrontierLoopsOf(buildCellKey("A"));

            clusters
                .getStyledClusterGroupByOwnerId()
                .put(OWNER_ID, buildClusterGroupWithLoops(List.of(rebuiltLoop)));

            // ACT
            var actualLoops = source.resolveCandidateFrontierLoopsOf(buildCellKey("A"));

            // ASSERT
            assertThat(actualLoops)
                .containsExactly(rebuiltLoop);
        }
    }

    @Nested
    class ResolveHighlightColourOf {

        @Test
        void paintsAnOwnedCellInItsOwnersShade() {

            // ARRANGE
            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(List.of())));

            // ACT
            var actualColour = source.resolveHighlightColourOf(buildCellKey("A"), OwnerPaletteSlot.SECONDARY);

            // ASSERT
            assertThat(actualColour)
                .isEqualTo(SECONDARY);
        }

        // An unowned cell has no palette of its own, so the highlight says so in the same neutral
        // the cell's own outline draws in.
        @Test
        void paintsAnUnownedCellInTheNeutralShade() {

            // ARRANGE
            var source = readSourceOf(buildClustersWith(
                Map.of(),
                Map.of("A", buildSquare(10, 10, 80)),
                null));

            // ACT
            var actualColour = source.resolveHighlightColourOf(buildCellKey("A"), OwnerPaletteSlot.PRIMARY);

            // ASSERT
            assertThat(actualColour)
                .isEqualTo(OwnerMapClusterFixtures.NEUTRAL_COLOUR);
        }

        // The theme points the highlight at no shade at all, which the render pass reads as "skip
        // the whole thing".
        @Test
        void returnsNothingForANoColourChoice() {

            // ARRANGE
            var source = readSourceOf(buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(List.of())));

            // ACT
            var actualColour = source.resolveHighlightColourOf(buildCellKey("A"), null);

            // ASSERT
            assertThat(actualColour)
                .isNull();
        }
    }

    @Nested
    class GetFillPolygonByCellKey {

        // Passed through rather than rebuilt, which is what makes the halo trace the shape the
        // cursor was hit-tested against - the cursor read takes these same shapes off the same
        // clusters. Reading one cell out of them is the framework's own, pinned by
        // PaintedCellShapesTests.
        @Test
        void handsOverTheFramesOwnShapes() {

            // ARRANGE
            var clusters = buildClustersWith(
                Map.of("A", OWNER),
                Map.of("A", buildSquare(10, 10, 80)),
                buildClusterGroupWithLoops(List.of()));

            var expectedShapes = clusters.getFillPolygonByCellKey();

            // ACT
            var actualShapes = readSourceOf(clusters).getFillPolygonByCellKey();

            // ASSERT
            assertThat(actualShapes)
                .isSameAs(expectedShapes);
        }
    }

    private static OwnerMapHoverHighlightSource readSourceOf(
            OwnerMapClusters clusters) {

        return new OwnerMapHoverHighlightSource(clusters);
    }

    // Clusters carrying just what the source reads: who owns each system, each cell's shape,
    // and the owner's traced loops. A null cluster group stands for an owner with none - the state
    // an unowned cell's owner lookup lands in.
    private static OwnerMapClusters buildClustersWith(
            Map<String, SystemOwner> ownerBySystemId,
            Map<String, List<double[]>> fillPolygonBySystemId,
            StyledClusterGroup clusterGroup) {

        return fillClusters(
            OwnerMapClusterFixtures.createClustersOwnedBy(ownerBySystemId),
            fillPolygonBySystemId,
            clusterGroup);
    }

    // The same clusters with its owners stated by key, for the one case a name cannot pose:
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

    // Writes the cells and the owner's cluster group onto a built map, which is all the source reads
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
            clusters.getStyledClusterGroupByOwnerId().put(OWNER_ID, clusterGroup);
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
