package kmu.maplayers.politicalmap.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the contract of {@link SystemClusterBorders#traceBorderRings}:
 *  - two same-key cells fuse into one border ring (their shared seam dropped),
 *  - a cell facing a different key keeps that shared edge as border,
 *  - a group with no geometry yields no rings,
 *  - an inset that swallows a cluster drops the ring rather than folding it over.
 *
 * <p>Cells here are hand-built squares rather than real Voronoi output, since the
 * border tracer only reads the adjacency graph and grouping keys - the geometry source is
 * irrelevant to which edges become border and how the boundary chains.
 */
final class SystemClusterBordersTest {

    private static final double BORDER_INSET = 2.0;
    private static final double WELD_TOLERANCE = 1e-3;
    private static final double MITER_SPIKE_LIMIT = 4.0;

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
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

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
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

            assertThat(rings).hasSize(1);
        }

        @Test
        void a_group_with_no_geometry_yields_no_rings() {
            var rings = SystemClusterBorders.traceBorderRings(List.of("missing"), Map.of(), Map.of(),
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

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
                    20.0, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

            assertThat(rings).isEmpty();
        }
    }
}
