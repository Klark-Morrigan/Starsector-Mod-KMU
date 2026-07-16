package kmu.maplayers.politicalmap.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the contract of {@link SystemClusterBorders#traceBorderRings}:
 *  - two same-key cells fuse into one border ring (their shared seam dropped),
 *  - a cell facing a different key keeps that shared edge as border,
 *  - a cell facing a present but unowned neighbour keeps that frontier edge as border,
 *  - a group with no geometry yields no rings,
 *  - an inset that swallows a cluster drops the ring rather than folding it over,
 *  - with the frontier enabled, an owned cell's open-frontier edge bulges outward toward
 *    the dead star and stops at the star's keep-out line, and two rivals sharing one dead
 *    neighbour each stop short of it without overlapping.
 *
 * <p>Cells here are hand-built squares rather than real Voronoi output, since the
 * border tracer only reads the adjacency graph and grouping keys - the geometry source is
 * irrelevant to which edges become border and how the boundary chains.
 */
final class SystemClusterBordersTest {

    private static final double BORDER_INSET = 2.0;
    private static final double WELD_TOLERANCE = 1e-3;
    private static final double MITER_SPIKE_LIMIT = 4.0;
    // The frontier feature off: no sites, no radius. Every open frontier then insets by the
    // plain channel, so a cluster traces exactly as it did before the feature.
    private static final FrontierSettings FRONTIER_OFF =
            new FrontierSettings(Map.of(), 0.0, false);

    // One CCW square cell edge, tagged with the neighbour across it (null for a
    // frontier into empty space).
    private static CellEdge edge(double x1, double y1, double x2, double y2, String neighbour) {
        return new CellEdge(x1, y1, x2, y2, neighbour);
    }

    @Nested
    class TraceBorderRings {
        @Test
        void two_same_faction_cells_fuse_into_one_ring() {
            // Cells A [0,0]..[10,10] and B [10,0]..[20,10] share the x = 10 edge.
            // Both held by F, so that seam is dropped and the six surviving border
            // edges chain into the single outline of the fused 20x10 rectangle.
            var edges = Map.of(
                    "A", List.of(
                            edge(0, 0, 10, 0, null), edge(10, 0, 10, 10, "B"),
                            edge(10, 10, 0, 10, null), edge(0, 10, 0, 0, null)),
                    "B", List.of(
                            edge(10, 0, 20, 0, null), edge(20, 0, 20, 10, null),
                            edge(20, 10, 10, 10, null), edge(10, 10, 10, 0, "A")));
            var owners = Map.of("A", "F", "B", "F");

            var rings = SystemClusterBorders.traceBorderRings(List.of("A", "B"), edges, owners,
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT, FRONTIER_OFF);

            assertThat(rings).hasSize(1);
            assertThat(rings.get(0).size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void an_edge_facing_a_different_faction_stays_a_border() {
            // The same two adjacent cells, but B is held by G. From F's group of
            // just A, the shared edge now faces a different owner, so it is a border
            // and A's whole square outlines one ring.
            var edges = Map.of(
                    "A", List.of(
                            edge(0, 0, 10, 0, null), edge(10, 0, 10, 10, "B"),
                            edge(10, 10, 0, 10, null), edge(0, 10, 0, 0, null)));
            var owners = Map.of("A", "F", "B", "G");

            var rings = SystemClusterBorders.traceBorderRings(List.of("A"), edges, owners,
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT, FRONTIER_OFF);

            assertThat(rings).hasSize(1);
        }

        @Test
        void an_edge_facing_an_unowned_neighbour_stays_a_border() {
            // A [0,0]..[10,10] is held by F; its right edge faces a present but unowned
            // neighbour B (a dead star - a cell, no owner). That owned-vs-empty edge is an
            // open frontier, which still bounds the cluster, so A's whole square outlines
            // one ring. Were the frontier edge treated as a non-border and dropped, the
            // open chain of the remaining three edges could not close into a ring.
            var edges = Map.of(
                    "A", List.of(
                            edge(0, 0, 10, 0, null), edge(10, 0, 10, 10, "B"),
                            edge(10, 10, 0, 10, null), edge(0, 10, 0, 0, null)));
            var owners = Map.of("A", "F");

            var rings = SystemClusterBorders.traceBorderRings(List.of("A"), edges, owners,
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT, FRONTIER_OFF);

            assertThat(rings).hasSize(1);
        }

        @Test
        void a_group_with_no_geometry_yields_no_rings() {
            var rings = SystemClusterBorders.traceBorderRings(List.of("missing"), Map.of(), Map.of(),
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT, FRONTIER_OFF);

            assertThat(rings).isEmpty();
        }

        @Test
        void an_inset_that_swallows_the_cluster_drops_the_ring() {
            // A lone 10x10 cell inset by 20 folds inside-out; the collapse guard
            // sees the winding flip and drops it rather than stroking a tangle.
            var edges = Map.of(
                    "A", List.of(
                            edge(0, 0, 10, 0, null), edge(10, 0, 10, 10, null),
                            edge(10, 10, 0, 10, null), edge(0, 10, 0, 0, null)));
            var owners = Map.of("A", "F");

            var rings = SystemClusterBorders.traceBorderRings(List.of("A"), edges, owners,
                    20.0, WELD_TOLERANCE, MITER_SPIKE_LIMIT, FRONTIER_OFF);

            assertThat(rings).isEmpty();
        }

        @Test
        void an_open_frontier_edge_bulges_out_to_the_star_keep_out_line_when_enabled() {
            // Owned A [0,0]..[100,100] faces a dead star B across its right edge (x = 100).
            // The stars sit 100 apart, so the setback is dist/2 - r = 50 - 20 = 30. With the
            // frontier on, that edge pushes outward toward B by the channel plus the setback
            // (2 + 30 = 32), so the right border reaches x = 132 - r - channel = 18 short of
            // B's star at x = 150, the keep-out line - while the other three insets stay
            // inward by the plain channel.
            var edges = Map.of(
                    "A", List.of(
                            edge(0, 0, 100, 0, null), edge(100, 0, 100, 100, "B"),
                            edge(100, 100, 0, 100, null), edge(0, 100, 0, 0, null)));
            var owners = Map.of("A", "F");
            var frontier = new FrontierSettings(
                    Map.of("A", new double[] {50, 50}, "B", new double[] {150, 50}), 20.0, true);

            var rings = SystemClusterBorders.traceBorderRings(List.of("A"), edges, owners,
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT, frontier);

            assertThat(rings).hasSize(1);
            assertThat(maxXOf(rings.get(0))).isCloseTo(132.0, within(1e-6));
        }

        @Test
        void two_rivals_sharing_a_dead_neighbour_each_stop_at_the_keep_out_without_overlapping() {
            // A [0,0]..[100,100] (F) and C [200,0]..[300,100] (G) both border the dead star
            // B in the middle, 100 from each. Tracing each faction alone, both push their
            // facing edge outward toward B by 2 + 30 = 32: F's right border reaches x = 132,
            // G's left border reaches x = 168, so a 36-wide unclaimed lens with B's star at
            // x = 150 threads between them - the two never overlap.
            var edges = Map.of(
                    "A", List.of(
                            edge(0, 0, 100, 0, null), edge(100, 0, 100, 100, "B"),
                            edge(100, 100, 0, 100, null), edge(0, 100, 0, 0, null)),
                    "C", List.of(
                            edge(200, 0, 300, 0, null), edge(300, 0, 300, 100, null),
                            edge(300, 100, 200, 100, null), edge(200, 100, 200, 0, "B")));
            var owners = Map.of("A", "F", "C", "G");
            var frontier = new FrontierSettings(Map.of(
                    "A", new double[] {50, 50}, "B", new double[] {150, 50},
                    "C", new double[] {250, 50}), 20.0, true);

            var fRings = SystemClusterBorders.traceBorderRings(List.of("A"), edges, owners,
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT, frontier);
            var gRings = SystemClusterBorders.traceBorderRings(List.of("C"), edges, owners,
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT, frontier);

            assertThat(maxXOf(fRings.get(0))).isCloseTo(132.0, within(1e-6));
            assertThat(minXOf(gRings.get(0))).isCloseTo(168.0, within(1e-6));
            assertThat(maxXOf(fRings.get(0))).isLessThan(minXOf(gRings.get(0)));
        }
    }

    private static double maxXOf(List<double[]> ring) {
        return ring.stream().mapToDouble(vertex -> vertex[0]).max().orElseThrow();
    }

    private static double minXOf(List<double[]> ring) {
        return ring.stream().mapToDouble(vertex -> vertex[0]).min().orElseThrow();
    }
}
