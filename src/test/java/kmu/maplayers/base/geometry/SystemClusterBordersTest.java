package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the contract of {@link SystemClusterBorders#traceBorderRings}:
 *  - two same-owner cells fuse into one border ring (their shared seam dropped),
 *  - a cell facing a different owner keeps that shared edge as border,
 *  - a cell facing a present but unowned neighbour keeps that frontier edge as border,
 *  - a group with no geometry yields no rings,
 *  - an inset that swallows a cluster drops the ring rather than folding it over,
 *  - a cluster naming a neighbour coincident keeps that shared edge on the raw cell border.
 *
 * <p>Cells here are hand-built squares rather than real Voronoi output, since the
 * border tracer only reads the adjacency graph and owners - the geometry source is
 * irrelevant to which edges become border and how the boundary chains.
 */
final class SystemClusterBordersTest {

    private static final double BORDER_INSET = 2.0;
    private static final double WELD_TOLERANCE = 1e-3;
    private static final double MITER_SPIKE_LIMIT = 4.0;
    // No neighbour opted out of the channel: every boundary edge insets by it, the way a
    // trace of a whole cluster runs.
    private static final Set<String> NO_COINCIDENT_NEIGHBOURS = Set.of();

    // One CCW square cell edge, facing the neighbour system across it, or the reach bound
    // when that is null.
    private static CellEdge buildEdge(double x1, double y1, double x2, double y2, String neighbour) {
        return new CellEdge(x1, y1, x2, y2,
                neighbour == null
                        ? EdgeTarget.REACH_BOUND
                        : new EdgeTarget.AcrossSystem(neighbour));
    }

    // The grouping to trace under: each cell drawing as its own star (identity draws-as over
    // the cell set), keyed by the given owners.
    private static CellGrouping buildGrouping(
            Map<String, List<CellEdge>> edges, Map<String, String> owners) {
        var systemIdByCellId = new java.util.LinkedHashMap<String, String>();
        for (var cellId : edges.keySet()) {
            systemIdByCellId.put(cellId, cellId);
        }
        return new CellGrouping(systemIdByCellId, owners);
    }

    @Nested
    class TraceBorderRings {
        @Test
        void two_same_key_cells_fuse_into_one_ring() {
            // Cells A [0,0]..[10,10] and B [10,0]..[20,10] share the x = 10 edge.
            // Both held by F, so that seam is dropped and the six surviving border
            // edges chain into the single outline of the fused 20x10 rectangle.
            var edges = Map.of(
                    "A", List.of(
                            buildEdge(0, 0, 10, 0, null), buildEdge(10, 0, 10, 10, "B"),
                            buildEdge(10, 10, 0, 10, null), buildEdge(0, 10, 0, 0, null)),
                    "B", List.of(
                            buildEdge(10, 0, 20, 0, null), buildEdge(20, 0, 20, 10, null),
                            buildEdge(20, 10, 10, 10, null), buildEdge(10, 10, 10, 0, "A")));
            var owners = Map.of("A", "F", "B", "F");

            var rings = SystemClusterBorders.traceBorderRings(
                    List.of("A", "B"), edges, buildGrouping(edges, owners), NO_COINCIDENT_NEIGHBOURS,
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

            assertThat(rings).hasSize(1);
            assertThat(rings.get(0).size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void an_edge_facing_a_different_key_stays_a_border() {
            // The same two adjacent cells, but B is held by G. From F's group of
            // just A, the shared edge now faces a different owner, so it is a border
            // and A's whole square outlines one ring.
            var edges = Map.of(
                    "A", List.of(
                            buildEdge(0, 0, 10, 0, null), buildEdge(10, 0, 10, 10, "B"),
                            buildEdge(10, 10, 0, 10, null), buildEdge(0, 10, 0, 0, null)));
            var owners = Map.of("A", "F", "B", "G");

            var rings = SystemClusterBorders.traceBorderRings(
                    List.of("A"), edges, buildGrouping(edges, owners), NO_COINCIDENT_NEIGHBOURS,
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

            assertThat(rings).hasSize(1);
        }

        @Test
        void an_edge_facing_an_unowned_neighbour_stays_a_border() {
            // A [0,0]..[10,10] is held by F; its right edge faces a present but unowned
            // neighbour B (a dead star - a cell, no key). That grouped-vs-empty edge is an
            // open frontier, which still bounds the cluster, so A's whole square outlines
            // one ring. Were the frontier edge treated as a non-border and dropped, the
            // open chain of the remaining three edges could not close into a ring.
            var edges = Map.of(
                    "A", List.of(
                            buildEdge(0, 0, 10, 0, null), buildEdge(10, 0, 10, 10, "B"),
                            buildEdge(10, 10, 0, 10, null), buildEdge(0, 10, 0, 0, null)));
            var owners = Map.of("A", "F");

            var rings = SystemClusterBorders.traceBorderRings(
                    List.of("A"), edges, buildGrouping(edges, owners), NO_COINCIDENT_NEIGHBOURS,
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

            assertThat(rings).hasSize(1);
        }

        @Test
        void a_group_with_no_geometry_yields_no_rings() {
            var rings = SystemClusterBorders.traceBorderRings(
                    List.of("missing"), Map.of(), buildGrouping(Map.of(), Map.of()),
                    NO_COINCIDENT_NEIGHBOURS,
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

            assertThat(rings).isEmpty();
        }

        @Test
        void an_inset_that_swallows_the_cluster_drops_the_ring() {
            // A lone 10x10 cell inset by 20 folds inside-out; the collapse guard
            // sees the winding flip and drops it rather than stroking a tangle.
            var edges = Map.of(
                    "A", List.of(
                            buildEdge(0, 0, 10, 0, null), buildEdge(10, 0, 10, 10, null),
                            buildEdge(10, 10, 0, 10, null), buildEdge(0, 10, 0, 0, null)));
            var owners = Map.of("A", "F");

            var rings = SystemClusterBorders.traceBorderRings(
                    List.of("A"), edges, buildGrouping(edges, owners), NO_COINCIDENT_NEIGHBOURS,
                    20.0, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

            assertThat(rings).isEmpty();
        }

        @Test
        void two_regions_traced_against_each_other_meet_exactly_on_their_coincident_edge() {
            // Cells A [0,0]..[10,10] and B [10,0]..[20,10] share the x = 10 edge but carry
            // different keys, so each traces as its own cluster. Naming the other as coincident
            // leaves that shared edge un-inset from both sides: A's border reaches x = 10 and B's
            // starts at x = 10, so the two fills abut on the raw cell edge with no wedge between
            // them. Their outward edges still take the plain channel.
            var rings = traceCarvedNeighbours(Set.of("B"), Set.of("A"));

            assertThat(computeMaxXOf(rings.first())).isCloseTo(10.0, within(1e-6));
            assertThat(computeMinXOf(rings.second())).isCloseTo(10.0, within(1e-6));
        }

        @Test
        void two_regions_traced_without_coincidence_leave_the_border_channel_between_them() {
            // The same two cells, neither naming the other: the shared edge is an ordinary
            // boundary, so both sides inset by the channel and the 2 + 2 gap between them is the
            // border channel two rival nations are meant to be separated by.
            var rings = traceCarvedNeighbours(NO_COINCIDENT_NEIGHBOURS, NO_COINCIDENT_NEIGHBOURS);

            assertThat(computeMaxXOf(rings.first())).isCloseTo(8.0, within(1e-6));
            assertThat(computeMinXOf(rings.second())).isCloseTo(12.0, within(1e-6));
        }

        // Traces the two differently-keyed neighbours A and B, each naming the given coincident
        // set, so a test varies only what each opts out of the channel.
        private static TracedPair traceCarvedNeighbours(
                Set<String> aCoincidentNeighbours, Set<String> bCoincidentNeighbours) {
            var edges = Map.of(
                    "A", List.of(
                            buildEdge(0, 0, 10, 0, null), buildEdge(10, 0, 10, 10, "B"),
                            buildEdge(10, 10, 0, 10, null), buildEdge(0, 10, 0, 0, null)),
                    "B", List.of(
                            buildEdge(10, 0, 20, 0, null), buildEdge(20, 0, 20, 10, null),
                            buildEdge(20, 10, 10, 10, null), buildEdge(10, 10, 10, 0, "A")));
            var owners = Map.of("A", "F#solid", "B", "F#hatched");
            return new TracedPair(
                    SystemClusterBorders.traceBorderRings(List.of("A"), edges, buildGrouping(edges, owners),
                            aCoincidentNeighbours,
                            BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT).get(0),
                    SystemClusterBorders.traceBorderRings(List.of("B"), edges, buildGrouping(edges, owners),
                            bCoincidentNeighbours,
                            BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT).get(0));
        }

        @Test
        void an_open_frontier_edge_insets_inward_by_the_plain_channel() {
            // Grouped A [0,0]..[100,100] faces a dead star B across its right edge (x = 100).
            // That open frontier takes the same inward channel every other boundary does, so
            // the ring's right side lands at x = 98 and never reaches past the raw cell edge
            // toward B - a grouped cluster stops at the Voronoi midline like any other border.
            var edges = Map.of(
                    "A", List.of(
                            buildEdge(0, 0, 100, 0, null), buildEdge(100, 0, 100, 100, "B"),
                            buildEdge(100, 100, 0, 100, null), buildEdge(0, 100, 0, 0, null)));
            var owners = Map.of("A", "F");

            var rings = SystemClusterBorders.traceBorderRings(
                    List.of("A"), edges, buildGrouping(edges, owners), NO_COINCIDENT_NEIGHBOURS,
                    BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

            assertThat(rings).hasSize(1);
            assertThat(computeMaxXOf(rings.get(0))).isCloseTo(98.0, within(1e-6));
        }
    }

    // The single ring each of two neighbouring cells traced to, so a test reads the two sides of
    // their shared edge without unpacking two ring lists.
    private record TracedPair(List<double[]> first, List<double[]> second) {
    }

    private static double computeMaxXOf(List<double[]> ring) {
        return ring.stream().mapToDouble(vertex -> vertex[0]).max().orElseThrow();
    }

    private static double computeMinXOf(List<double[]> ring) {
        return ring.stream().mapToDouble(vertex -> vertex[0]).min().orElseThrow();
    }
}
