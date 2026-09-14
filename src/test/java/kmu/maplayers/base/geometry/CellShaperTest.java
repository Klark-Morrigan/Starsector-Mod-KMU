package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeFacing;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildIdentityGrouping;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link CellShaper}: a same-owner shared edge is left on the true cell
 * border (so two cells fuse into one cluster along it) while its ends are truncated
 * within the padded border; every other edge - a different owner, unowned space,
 * or a frontier - is pulled inward by the one uniform channel; an unowned cell has no
 * interior seams; and two neighbouring same-owner cells keep the very same shared
 * line, so their fills meet.
 *
 * <p>And that the inset is the rule's to give: under {@link EdgeInsetRule#NOWHERE} every
 * edge stays on its true line whoever owns what across it, which is the partition the
 * channel is cut into, and under {@link EdgeInsetRule#EVERYWHERE} even a same-owner seam
 * pulls in.
 */
final class CellShaperTest {
    // Inward inset applied to cluster-border edges in the fixtures; small enough
    // that a side-10 cell survives it with room to spare.
    private static final double INSET = 2.0;

    @Nested
    class ShapeCells {

        @Test
        void shapeCellsLeavesASameKeySharedEdgeOnTheRawLineAsASeam() {
            // Cell "a" is the unit square; its right edge (x = 10) is shared with a
            // same-owner "b", the other three are frontiers. Only the shared edge
            // stays a seam (not a boundary), on the raw x = 10 line, so a
            // same-owner "b" filling up to x = 10 fuses with it.
            var shaped = buildShape(
                    Map.of("a", buildSquareCellSharedOnRight("b")),
                    Map.of("a", "hegemony", "b", "hegemony"), INSET).get("a");

            assertThat(countSeamEdges(shaped))
                .isEqualTo(1);

            var seam = listSeamEdgesOf(shaped).get(0);

            assertThat(seam[0][0])
                .isCloseTo(10.0, within(1e-6));
            assertThat(seam[1][0])
                .isCloseTo(10.0, within(1e-6));
        }

        @Test
        void shapeCellsTruncatesASeamEndWithinThePaddedBorder() {
            // The kept seam no longer reaches the raw corners (y = 0 and y = 10): the
            // pulled-in top and bottom borders cut it back to the 2..8 inset band, so
            // the seam's ends stay within the padding rather than poking out to the
            // midline corner between cells.
            var shaped = buildShape(
                    Map.of("a", buildSquareCellSharedOnRight("b")),
                    Map.of("a", "hegemony", "b", "hegemony"),
                    INSET)
                .get("a");

            var seam = listSeamEdgesOf(shaped).get(0);

            assertThat(seam[0][1])
                .isBetween(INSET - 1e-6, 8.0 + 1e-6);
            assertThat(seam[1][1])
                .isBetween(INSET - 1e-6, 8.0 + 1e-6);
        }

        @Test
        void shapeCellsInsetsASharedEdgeBetweenDifferentKeys() {
            // With a rival across the right edge, nothing merges: every edge is a
            // boundary and the fill pulls in to x <= 8, leaving the border channel.
            var shaped = buildShape(
                    Map.of("a", buildSquareCellSharedOnRight("b")),
                    Map.of("a", "hegemony", "b", "tritachyon"),
                    INSET)
                .get("a");

            assertThat(shaped.edgeIsBoundary())
                .containsOnly(true);
            assertThat(shaped.fillPolygon())
                .allMatch(vertex -> vertex[0] <= 8.0 + 1e-6);
        }

        @Test
        void shapeCellsMakesEveryEdgeOfAnUnownedCellABoundary() {
            // "a" is absent from the grouping-key map (unowned): it fuses with no one, so
            // every edge - even the one shared with a grouped "b" - is a boundary.
            var shaped = buildShape(
                    Map.of("a", buildSquareCellSharedOnRight("b")),
                    Map.of("b", "hegemony"),
                    INSET)
                .get("a");

            assertThat(shaped.edgeIsBoundary())
                .containsOnly(true);
        }

        @Test
        void shapeCellsMakesAFrontierEdgeABoundary() {
            // A lone owned cell touches no neighbour, so every edge is a frontier
            // into empty space - all boundaries, none merged.
            var shaped = buildShape(
                    Map.of("a", buildSquareCellSharedOnRight(null)),
                    Map.of("a", "hegemony"),
                    INSET)
                .get("a");

            assertThat(shaped.edgeIsBoundary())
                .containsOnly(true);
        }

        @Test
        void shapeCellsKeepsTwoSameKeyNeighboursMeetingOnTheSharedLine() {
            // Left square "a" and right square "b" share the x = 10 line and are both
            // Hegemony. Each keeps that edge as a seam on x = 10, so their fills meet
            // there with no channel between them.
            var shaped = buildShape(
                Map.of("a", buildSquareCellSharedOnRight("b"), "b", buildRightSquareCellSharedOnLeft("a")),
                Map.of("a", "hegemony", "b", "hegemony"),
                INSET);

            assertThat(listSeamEdgesOf(shaped.get("a")).get(0)[0][0])
                .isCloseTo(10.0, within(1e-6));
            assertThat(listSeamEdgesOf(shaped.get("b")).get(0)[0][0])
                .isCloseTo(10.0, within(1e-6));
        }

        @Test
        void shapeCellsInsetsAnOpenFrontierEdgeByThePlainChannelFromEitherSide() {
            // An open frontier - one side grouped, the other not - takes the same uniform
            // channel every other boundary does, from whichever side shapes it. Unowned
            // "a" facing grouped "b" stops at x = 98, and grouped "a" facing unowned "b"
            // stops there too; neither side reaches past the channel toward the other's star.
            var emptySide = buildShape(
                    Map.of("a", buildBigSquareCellSharedOnRight("b")),
                    Map.of("b", "hegemony"),
                    INSET)
                .get("a");

            var groupedSide = buildShape(
                    Map.of("a", buildBigSquareCellSharedOnRight("b")),
                    Map.of("a", "hegemony"),
                    INSET)
                .get("a");

            assertThat(computeMaxXOf(emptySide))
                .isCloseTo(98.0, within(1e-6));
            assertThat(computeMaxXOf(groupedSide))
                .isCloseTo(98.0, within(1e-6));

            // Still a cluster border, not fused as a seam: an open frontier joins no
            // cluster, so the edge stays a boundary inset off the raw line.
            assertThat(emptySide.edgeIsBoundary())
                .containsOnly(true);
        }

        @Test
        void shapeCellsLeavesEveryEdgeOnItsTrueLineUnderNowhere() {
            // The unit square shaped with nothing inset comes back as the unit square: the
            // fill spans the raw 0..10 rather than the 2..8 the channel would leave, and no
            // edge is flagged as lying on an offset line.
            var shaped = buildShapeUnder(
                    Map.of("a", buildSquareCellSharedOnRight("b")),
                    Map.of("a", "hegemony", "b", "tritachyon"),
                    EdgeInsetRule.NOWHERE,
                    INSET)
                .get("a");

            assertThat(computeMinXOf(shaped))
                .isCloseTo(0.0, within(1e-6));
            assertThat(computeMaxXOf(shaped))
                .isCloseTo(10.0, within(1e-6));
            assertThat(shaped.edgeIsBoundary())
                .containsOnly(false);
        }

        @Test
        void shapeCellsGivesTheSameShapeUnderNowhereWhoeverOwnsTheNeighbour() {
            // What an edge faces decides the inset and nothing else, so with the inset off
            // the two owner maps that disagree about every edge still shape one square. This
            // is the partition the channel is later cut into.
            var fused = buildShapeUnder(
                    Map.of("a", buildSquareCellSharedOnRight("b")),
                    Map.of("a", "hegemony", "b", "hegemony"),
                    EdgeInsetRule.NOWHERE,
                    INSET)
                .get("a");

            var rivals = buildShapeUnder(
                    Map.of("a", buildSquareCellSharedOnRight("b")),
                    Map.of("a", "hegemony", "b", "tritachyon"),
                    EdgeInsetRule.NOWHERE,
                    INSET)
                .get("a");

            assertThat(computeMaxXOf(fused))
                .isCloseTo(10.0, within(1e-6));
            assertThat(computeMaxXOf(rivals))
                .isCloseTo(10.0, within(1e-6));
        }

        @Test
        void shapeCellsPullsInEvenASameOwnerSeamUnderEverywhere() {
            // The one edge the shipped rule leaves on its line - a same-owner seam - pulls in
            // like the rest, so the fill stops at x = 8 and two same-owner neighbours that
            // would have fused are left a channel apart.
            var shaped = buildShapeUnder(
                    Map.of("a", buildSquareCellSharedOnRight("b")),
                    Map.of("a", "hegemony", "b", "hegemony"),
                    EdgeInsetRule.EVERYWHERE,
                    INSET)
                .get("a");

            assertThat(computeMinXOf(shaped))
                .isCloseTo(2.0, within(1e-6));
            assertThat(computeMaxXOf(shaped))
                .isCloseTo(8.0, within(1e-6));

            assertThat(shaped.edgeIsBoundary())
                .containsOnly(true);
        }
    }

    // Shapes the cells under the identity draws-as grouping, so a test names its edges and
    // keys exactly as before while the shaper reads a cell's key through its own star.
    //
    // The cells go in under the key the cut addresses them by and the shapes come back under the
    // names the case gave them, which is what leaves a case about shaping reading as it did before
    // the cells were keyed.
    private static Map<String, ShapedCell> buildShape(
            Map<String, List<CellEdge>> edges,
            Map<String, String> owners,
            double inset) {

        return buildShapeUnder(edges, owners, EdgeInsetRule.AT_EVERY_BORDER, inset);
    }

    // The same, under a stated inset rule, for the cases that are about the rule rather than
    // about what an edge faces.
    private static Map<String, ShapedCell> buildShapeUnder(
            Map<String, List<CellEdge>> edges,
            Map<String, String> owners,
            EdgeInsetRule insetRule,
            double inset) {

        var cellEdgesByCellKey = buildKeyedValues(edges);
        var shapedByCellKey = CellShaper.shapeCells(
            cellEdgesByCellKey,
            buildIdentityGrouping(cellEdgesByCellKey.keySet(), owners),
            insetRule,
            inset);

        var shapedBySystemId = new LinkedHashMap<String, ShapedCell>();

        for (var shaped : shapedByCellKey.entrySet()) {
            shapedBySystemId.put(shaped.getKey().systemId(), shaped.getValue());
        }
        return shapedBySystemId;
    }

    // The unit square (0,0)..(10,10) CCW, its right edge (x = 10) tagged with the
    // given neighbour and the other three left as frontiers (null neighbour).
    private static List<CellEdge> buildSquareCellSharedOnRight(String rightNeighbour) {

        return List.of(
            buildEdgeFacing(0, 0, 10, 0, null),
            buildEdgeFacing(10, 0, 10, 10, rightNeighbour),
            buildEdgeFacing(10, 10, 0, 10, null),
            buildEdgeFacing(0, 10, 0, 0, null));
    }

    // The side-100 square (0,0)..(100,100) CCW, its right edge (x = 100) tagged with the
    // given neighbour and the other three left as frontiers - large enough that the
    // channel-plus-setback frontier pull-in still leaves a drawable cell.
    private static List<CellEdge> buildBigSquareCellSharedOnRight(String rightNeighbour) {

        return List.of(
            buildEdgeFacing(0, 0, 100, 0, null),
            buildEdgeFacing(100, 0, 100, 100, rightNeighbour),
            buildEdgeFacing(100, 100, 0, 100, null),
            buildEdgeFacing(0, 100, 0, 0, null));
    }

    // The square (10,0)..(20,10) CCW, its left edge (x = 10) tagged with the given
    // neighbour - the mirror partner of a squareCellSharedOnRight cell.
    private static List<CellEdge> buildRightSquareCellSharedOnLeft(String leftNeighbour) {

        return List.of(
            buildEdgeFacing(10, 0, 20, 0, null),
            buildEdgeFacing(20, 0, 20, 10, null),
            buildEdgeFacing(20, 10, 10, 10, null),
            buildEdgeFacing(10, 10, 10, 0, leftNeighbour));
    }

    private static double computeMaxXOf(ShapedCell shaped) {

        return shaped
            .fillPolygon()
            .stream()
            .mapToDouble(vertex -> vertex[0])
            .max()
            .orElseThrow();
    }

    private static double computeMinXOf(ShapedCell shaped) {

        return shaped
            .fillPolygon()
            .stream()
            .mapToDouble(vertex -> vertex[0])
            .min()
            .orElseThrow();
    }

    private static long countSeamEdges(ShapedCell shaped) {

        var count = 0L;
        for (var isBoundary : shaped.edgeIsBoundary()) {

            if (!isBoundary) {
                count++;
            }
        }
        return count;
    }

    // The shaped cell's seam edges (those left un-inset), each as its two {x, y}
    // endpoints - what the render layer strokes as an interior line.
    private static List<double[][]> listSeamEdgesOf(ShapedCell shaped) {

        var seams = new ArrayList<double[][]>();
        var polygon = shaped.fillPolygon();
        var edgeIsBoundary = shaped.edgeIsBoundary();

        for (var i = 0; i < polygon.size(); i++) {

            if (!edgeIsBoundary[i]) {

                seams.add(new double[][] {
                    polygon.get(i),
                    polygon.get((i + 1) % polygon.size())});
            }
        }
        return seams;
    }
}
