package kmu.maplayers.politicalmap.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link CellShaper}: a same-faction shared edge is left on the true cell
 * border (so two cells fuse into one cluster along it) while its ends are truncated
 * within the padded border; every other edge - a different faction, unowned space,
 * or a frontier - is pulled inward; an unowned cell has no interior seams; and two
 * neighbouring same-faction cells keep the very same shared line, so their fills
 * meet. With the frontier snapshot enabled, a factionless cell's edge facing an owned
 * neighbour recedes past the channel toward that owner's keep-out pocket, while owned
 * cells and the disabled snapshot inset by the plain channel unchanged.
 */
final class CellShaperTest {
    // Inward inset applied to national-border edges in the fixtures; small enough
    // that a side-10 cell survives it with room to spare.
    private static final double INSET = 2.0;

    // The frontier feature switched off: no sites, no radius. Every open frontier then
    // insets by the plain channel, so a cell shapes exactly as it did before the feature.
    private static final FrontierSettings FRONTIER_OFF =
            new FrontierSettings(Map.of(), 0.0, false);

    @Nested
    class ShapeCells {

        @Test
        void shapeCellsLeavesASameFactionSharedEdgeOnTheRawLineAsASeam() {
            // Cell "a" is the unit square; its right edge (x = 10) is shared with a
            // same-faction "b", the other three are frontiers. Only the shared edge
            // stays a seam (not a boundary), on the raw x = 10 line, so a
            // same-faction "b" filling up to x = 10 fuses with it.
            var shaped = CellShaper.shapeCells(
                    Map.of("a", squareCellSharedOnRight("b")),
                    Map.of("a", "hegemony", "b", "hegemony"), INSET, FRONTIER_OFF).get("a");

            assertThat(countSeamEdges(shaped)).isEqualTo(1);
            var seam = seamEdgesOf(shaped).get(0);
            assertThat(seam[0][0]).isCloseTo(10.0, within(1e-6));
            assertThat(seam[1][0]).isCloseTo(10.0, within(1e-6));
        }

        @Test
        void shapeCellsTruncatesASeamEndWithinThePaddedBorder() {
            // The kept seam no longer reaches the raw corners (y = 0 and y = 10): the
            // pulled-in top and bottom borders cut it back to the 2..8 inset band, so
            // the seam's ends stay within the padding rather than poking out to the
            // midline corner between cells.
            var shaped = CellShaper.shapeCells(
                    Map.of("a", squareCellSharedOnRight("b")),
                    Map.of("a", "hegemony", "b", "hegemony"), INSET, FRONTIER_OFF).get("a");

            var seam = seamEdgesOf(shaped).get(0);
            assertThat(seam[0][1]).isBetween(INSET - 1e-6, 8.0 + 1e-6);
            assertThat(seam[1][1]).isBetween(INSET - 1e-6, 8.0 + 1e-6);
        }

        @Test
        void shapeCellsInsetsASharedEdgeBetweenDifferentFactions() {
            // With a rival across the right edge, nothing merges: every edge is a
            // boundary and the fill pulls in to x <= 8, leaving the border channel.
            var shaped = CellShaper.shapeCells(
                    Map.of("a", squareCellSharedOnRight("b")),
                    Map.of("a", "hegemony", "b", "tritachyon"), INSET, FRONTIER_OFF).get("a");

            assertThat(shaped.edgeIsBoundary()).containsOnly(true);
            assertThat(shaped.fillPolygon()).allMatch(vertex -> vertex[0] <= 8.0 + 1e-6);
        }

        @Test
        void shapeCellsMakesEveryEdgeOfAnUnownedCellABoundary() {
            // "a" is absent from the owner map (unowned): it fuses with no one, so
            // every edge - even the one shared with an owned "b" - is a boundary.
            var shaped = CellShaper.shapeCells(
                    Map.of("a", squareCellSharedOnRight("b")),
                    Map.of("b", "hegemony"), INSET, FRONTIER_OFF).get("a");

            assertThat(shaped.edgeIsBoundary()).containsOnly(true);
        }

        @Test
        void shapeCellsMakesAFrontierEdgeABoundary() {
            // A lone owned cell touches no neighbour, so every edge is a frontier
            // into empty space - all boundaries, none merged.
            var shaped = CellShaper.shapeCells(
                    Map.of("a", squareCellSharedOnRight(null)),
                    Map.of("a", "hegemony"), INSET, FRONTIER_OFF).get("a");

            assertThat(shaped.edgeIsBoundary()).containsOnly(true);
        }

        @Test
        void shapeCellsKeepsTwoSameFactionNeighboursMeetingOnTheSharedLine() {
            // Left square "a" and right square "b" share the x = 10 line and are both
            // Hegemony. Each keeps that edge as a seam on x = 10, so their fills meet
            // there with no channel between them.
            var shaped = CellShaper.shapeCells(
                    Map.of("a", squareCellSharedOnRight("b"), "b", rightSquareCellSharedOnLeft("a")),
                    Map.of("a", "hegemony", "b", "hegemony"), INSET, FRONTIER_OFF);

            assertThat(seamEdgesOf(shaped.get("a")).get(0)[0][0]).isCloseTo(10.0, within(1e-6));
            assertThat(seamEdgesOf(shaped.get("b")).get(0)[0][0]).isCloseTo(10.0, within(1e-6));
        }

        @Test
        void shapeCellsPullsAFactionlessFrontierEdgePastTheChannelTowardTheOwnedStar() {
            // Factionless "a" (a side-100 square) faces owned "b" across its right edge;
            // the stars sit 100 apart, so the setback is dist/2 - r = 50 - 20 = 30. The
            // frontier edge insets by the channel plus that setback (2 + 30 = 32), pulling
            // it from x = 100 back to x = 68 - well past the plain channel toward b's star.
            var frontier = new FrontierSettings(
                    Map.of("a", new double[] {50, 50}, "b", new double[] {150, 50}), 20.0, true);
            var shaped = CellShaper.shapeCells(
                    Map.of("a", bigSquareCellSharedOnRight("b")),
                    Map.of("b", "hegemony"), INSET, frontier).get("a");

            assertThat(maxXOf(shaped)).isCloseTo(68.0, within(1e-6));
            // Still a national border, not fused as a seam: every edge of a factionless
            // cell is a boundary, and the pull-in keeps this one inset off the raw line.
            assertThat(shaped.edgeIsBoundary()).containsOnly(true);
        }

        @Test
        void shapeCellsLeavesAFactionlessFrontierEdgeOnTheChannelWhenDisabled() {
            // The same fixture with the toggle off: the open frontier insets by the plain
            // channel only (x = 100 -> 98), never reaching toward b's star.
            var shaped = CellShaper.shapeCells(
                    Map.of("a", bigSquareCellSharedOnRight("b")),
                    Map.of("b", "hegemony"), INSET, FRONTIER_OFF).get("a");

            assertThat(maxXOf(shaped)).isCloseTo(98.0, within(1e-6));
        }

        @Test
        void shapeCellsLeavesAFactionlessEdgeFacingAnotherEmptyCellOnTheChannel() {
            // "a" faces another factionless cell "c" (both ungrouped): that edge is a plain
            // boundary, not an open frontier, so it insets by the channel only even with the
            // frontier enabled and sites present - it stops at x = 98, not pulled toward c.
            var frontier = new FrontierSettings(
                    Map.of("a", new double[] {50, 50}, "c", new double[] {150, 50}), 20.0, true);
            var shaped = CellShaper.shapeCells(
                    Map.of("a", bigSquareCellSharedOnRight("c")),
                    Map.of(), INSET, frontier).get("a");

            assertThat(maxXOf(shaped)).isCloseTo(98.0, within(1e-6));
        }

        @Test
        void shapeCellsShrinksAnEnclosedDeadStarTowardTheKeepOutPocket() {
            // A factionless cell ringed by owned neighbours on all four sides, each 100
            // away: every edge is an open frontier, so all four pull in by 2 + 30 = 32,
            // shrinking the cell from [0,100]^2 to [32,68]^2 - the keep-out pocket around
            // its own star.
            var edges = List.of(
                    new CellEdge(0, 0, 100, 0, "s"),
                    new CellEdge(100, 0, 100, 100, "e"),
                    new CellEdge(100, 100, 0, 100, "n"),
                    new CellEdge(0, 100, 0, 0, "w"));
            var frontier = new FrontierSettings(Map.of(
                    "a", new double[] {50, 50},
                    "s", new double[] {50, -50},
                    "e", new double[] {150, 50},
                    "n", new double[] {50, 150},
                    "w", new double[] {-50, 50}), 20.0, true);
            var shaped = CellShaper.shapeCells(Map.of("a", edges),
                    Map.of("s", "hegemony", "e", "hegemony", "n", "hegemony", "w", "hegemony"),
                    INSET, frontier).get("a");

            assertThat(minXOf(shaped)).isCloseTo(32.0, within(1e-6));
            assertThat(maxXOf(shaped)).isCloseTo(68.0, within(1e-6));
            assertThat(minYOf(shaped)).isCloseTo(32.0, within(1e-6));
            assertThat(maxYOf(shaped)).isCloseTo(68.0, within(1e-6));
        }

        @Test
        void shapeCellsFallsBackToTheChannelWhenAFrontierNeighbourHasNoSite() {
            // The frontier is enabled but the owned neighbour "b" has no site (e.g. a
            // stale adjacency edge): the setback cannot be measured, so the edge falls back
            // to the plain channel (x = 100 -> 98) rather than pulling in with garbage or
            // throwing.
            var frontier = new FrontierSettings(Map.of("a", new double[] {50, 50}), 20.0, true);
            var shaped = CellShaper.shapeCells(
                    Map.of("a", bigSquareCellSharedOnRight("b")),
                    Map.of("b", "hegemony"), INSET, frontier).get("a");

            assertThat(maxXOf(shaped)).isCloseTo(98.0, within(1e-6));
        }

        @Test
        void shapeCellsLeavesAnOwnedCellOnTheChannelWhenTheFrontierIsEnabled() {
            // Owned "a" faces an unowned neighbour "b" - an open frontier from the owned
            // side. The pull-in fires only for factionless cells, so an owned cell insets
            // by the plain channel (x = 100 -> 98), unchanged by the enabled frontier.
            var frontier = new FrontierSettings(
                    Map.of("a", new double[] {50, 50}, "b", new double[] {150, 50}), 20.0, true);
            var shaped = CellShaper.shapeCells(
                    Map.of("a", bigSquareCellSharedOnRight("b")),
                    Map.of("a", "hegemony"), INSET, frontier).get("a");

            assertThat(maxXOf(shaped)).isCloseTo(98.0, within(1e-6));
        }
    }

    // The unit square (0,0)..(10,10) CCW, its right edge (x = 10) tagged with the
    // given neighbour and the other three left as frontiers (null neighbour).
    private static List<CellEdge> squareCellSharedOnRight(String rightNeighbour) {
        return List.of(
                new CellEdge(0, 0, 10, 0, null),
                new CellEdge(10, 0, 10, 10, rightNeighbour),
                new CellEdge(10, 10, 0, 10, null),
                new CellEdge(0, 10, 0, 0, null));
    }

    // The side-100 square (0,0)..(100,100) CCW, its right edge (x = 100) tagged with the
    // given neighbour and the other three left as frontiers - large enough that the
    // channel-plus-setback frontier pull-in still leaves a drawable cell.
    private static List<CellEdge> bigSquareCellSharedOnRight(String rightNeighbour) {
        return List.of(
                new CellEdge(0, 0, 100, 0, null),
                new CellEdge(100, 0, 100, 100, rightNeighbour),
                new CellEdge(100, 100, 0, 100, null),
                new CellEdge(0, 100, 0, 0, null));
    }

    // The square (10,0)..(20,10) CCW, its left edge (x = 10) tagged with the given
    // neighbour - the mirror partner of a squareCellSharedOnRight cell.
    private static List<CellEdge> rightSquareCellSharedOnLeft(String leftNeighbour) {
        return List.of(
                new CellEdge(10, 0, 20, 0, null),
                new CellEdge(20, 0, 20, 10, null),
                new CellEdge(20, 10, 10, 10, null),
                new CellEdge(10, 10, 10, 0, leftNeighbour));
    }

    private static double maxXOf(ShapedCell shaped) {
        return shaped.fillPolygon().stream().mapToDouble(vertex -> vertex[0]).max().orElseThrow();
    }

    private static double minXOf(ShapedCell shaped) {
        return shaped.fillPolygon().stream().mapToDouble(vertex -> vertex[0]).min().orElseThrow();
    }

    private static double maxYOf(ShapedCell shaped) {
        return shaped.fillPolygon().stream().mapToDouble(vertex -> vertex[1]).max().orElseThrow();
    }

    private static double minYOf(ShapedCell shaped) {
        return shaped.fillPolygon().stream().mapToDouble(vertex -> vertex[1]).min().orElseThrow();
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
    private static List<double[][]> seamEdgesOf(ShapedCell shaped) {
        var seams = new ArrayList<double[][]>();
        var polygon = shaped.fillPolygon();
        var edgeIsBoundary = shaped.edgeIsBoundary();
        for (var i = 0; i < polygon.size(); i++) {
            if (!edgeIsBoundary[i]) {
                seams.add(new double[][] {
                        polygon.get(i), polygon.get((i + 1) % polygon.size())});
            }
        }
        return seams;
    }
}
