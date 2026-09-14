package kmu.maplayers.base.geometry;

import kmlib.starsector.systems.SystemKey;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeFacing;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildIdentityGroupingByName;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;
import static kmu.maplayers.base.geometry.RingExtentFixture.readMaxXOf;
import static kmu.maplayers.base.geometry.RingExtentFixture.readMinXOf;

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
    // The trace tuning every case shares, the tolerances being the trace's own tuning rather
    // than anything a case is about; the one case that varies the channel builds its own.
    private static final BorderTraceTolerances TOLERANCES =
        new BorderTraceTolerances(BORDER_INSET, WELD_TOLERANCE, MITER_SPIKE_LIMIT);

    private static final Set<SystemKey> NO_COINCIDENT_NEIGHBOURS = Set.of();

    // The trace as every case here asks it: cells and edges named as the case states them, keyed
    // on the way in so the case reads as it did before the cells were keyed.
    private static List<List<double[]>> traceBorderRings(
            List<String> groupCellIds,
            Map<String, List<CellEdge>> edges,
            CellGrouping grouping,
            Set<SystemKey> coincidentNeighbourSystemKeys,
            BorderTraceTolerances tolerances) {

        return traceBorderRingsUnder(
            groupCellIds,
            edges,
            grouping,
            coincidentNeighbourSystemKeys,
            EdgeInsetRule.AT_EVERY_BORDER,
            tolerances);
    }

    // The same, under a stated inset rule, for the cases about where the ring lands rather than
    // about which edges bound it.
    private static List<List<double[]>> traceBorderRingsUnder(
            List<String> groupCellIds,
            Map<String, List<CellEdge>> edges,
            CellGrouping grouping,
            Set<SystemKey> coincidentNeighbourSystemKeys,
            EdgeInsetRule insetRule,
            BorderTraceTolerances tolerances) {

        return SystemClusterBorders.traceBorderRings(
            buildCellKeys(groupCellIds.toArray(String[]::new)),
            buildKeyedValues(edges),
            grouping,
            coincidentNeighbourSystemKeys,
            insetRule,
            tolerances);
    }

    @Nested
    class TraceBorderRings {
        @Test
        void twoSameKeyCellsFuseIntoOneRing() {
            // Cells A [0,0]..[10,10] and B [10,0]..[20,10] share the x = 10 edge.
            // Both held by F, so that seam is dropped and the six surviving border
            // edges chain into the single outline of the fused 20x10 rectangle.
            var edges = Map.of(
                    "A", List.of(
                            buildEdgeFacing(0, 0, 10, 0, null), buildEdgeFacing(10, 0, 10, 10, "B"),
                            buildEdgeFacing(10, 10, 0, 10, null), buildEdgeFacing(0, 10, 0, 0, null)),
                    "B", List.of(
                            buildEdgeFacing(10, 0, 20, 0, null), buildEdgeFacing(20, 0, 20, 10, null),
                            buildEdgeFacing(20, 10, 10, 10, null), buildEdgeFacing(10, 10, 10, 0, "A")));
            var owners = Map.of("A", "F", "B", "F");

            var rings = traceBorderRings(
                    List.of("A", "B"), edges, buildIdentityGroupingByName(edges.keySet(), owners), NO_COINCIDENT_NEIGHBOURS,
                    TOLERANCES);

            assertThat(rings).hasSize(1);
            assertThat(rings.get(0).size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        void anEdgeFacingADifferentKeyStaysABorder() {
            // The same two adjacent cells, but B is held by G. From F's group of
            // just A, the shared edge now faces a different owner, so it is a border
            // and A's whole square outlines one ring.
            var edges = Map.of(
                    "A", List.of(
                            buildEdgeFacing(0, 0, 10, 0, null), buildEdgeFacing(10, 0, 10, 10, "B"),
                            buildEdgeFacing(10, 10, 0, 10, null), buildEdgeFacing(0, 10, 0, 0, null)));
            var owners = Map.of("A", "F", "B", "G");

            var rings = traceBorderRings(
                    List.of("A"), edges, buildIdentityGroupingByName(edges.keySet(), owners), NO_COINCIDENT_NEIGHBOURS,
                    TOLERANCES);

            assertThat(rings).hasSize(1);
        }

        @Test
        void anEdgeFacingAnUnownedNeighbourStaysABorder() {
            // A [0,0]..[10,10] is held by F; its right edge faces a present but unowned
            // neighbour B (a dead star - a cell, no key). That grouped-vs-empty edge is an
            // open frontier, which still bounds the cluster, so A's whole square outlines
            // one ring. Were the frontier edge treated as a non-border and dropped, the
            // open chain of the remaining three edges could not close into a ring.
            var edges = Map.of(
                    "A", List.of(
                            buildEdgeFacing(0, 0, 10, 0, null), buildEdgeFacing(10, 0, 10, 10, "B"),
                            buildEdgeFacing(10, 10, 0, 10, null), buildEdgeFacing(0, 10, 0, 0, null)));
            var owners = Map.of("A", "F");

            var rings = traceBorderRings(
                    List.of("A"), edges, buildIdentityGroupingByName(edges.keySet(), owners), NO_COINCIDENT_NEIGHBOURS,
                    TOLERANCES);

            assertThat(rings).hasSize(1);
        }

        @Test
        void traceBorderRingsLandsTheRingOnTheRawOutlineUnderNowhere() {
            // The same lone F cell as the frontier case, traced with nothing inset: the ring
            // sits on the raw square (0..10) rather than pulled back to the 2..8 channel band.
            // The cluster's outline and its cells' fills are cut by one rule, so a cell drawn
            // on its true border is outlined on its true border.
            var edges = Map.of(
                    "A", List.of(
                            buildEdgeFacing(0, 0, 10, 0, null), buildEdgeFacing(10, 0, 10, 10, "B"),
                            buildEdgeFacing(10, 10, 0, 10, null), buildEdgeFacing(0, 10, 0, 0, null)));
            var owners = Map.of("A", "F");

            var rings = traceBorderRingsUnder(
                    List.of("A"),
                    edges,
                    buildIdentityGroupingByName(edges.keySet(), owners),
                    NO_COINCIDENT_NEIGHBOURS,
                    EdgeInsetRule.NOWHERE,
                    TOLERANCES);

            assertThat(rings)
                .hasSize(1);
            assertThat(readMinXOf(rings.get(0)))
                .isCloseTo(0.0, within(1e-6));
            assertThat(readMaxXOf(rings.get(0)))
                .isCloseTo(10.0, within(1e-6));
        }

        @Test
        void aGroupWithNoGeometryYieldsNoRings() {
            var rings = traceBorderRings(
                    List.of("missing"), Map.of(), buildIdentityGroupingByName(Set.of(), Map.of()),
                    NO_COINCIDENT_NEIGHBOURS,
                    TOLERANCES);

            assertThat(rings).isEmpty();
        }

        @Test
        void anInsetThatSwallowsTheClusterDropsTheRing() {
            // A lone 10x10 cell inset by 20 folds inside-out; the collapse guard
            // sees the winding flip and drops it rather than stroking a tangle.
            var edges = Map.of(
                    "A", List.of(
                            buildEdgeFacing(0, 0, 10, 0, null), buildEdgeFacing(10, 0, 10, 10, null),
                            buildEdgeFacing(10, 10, 0, 10, null), buildEdgeFacing(0, 10, 0, 0, null)));
            var owners = Map.of("A", "F");

            var rings = traceBorderRings(
                    List.of("A"), edges, buildIdentityGroupingByName(edges.keySet(), owners), NO_COINCIDENT_NEIGHBOURS,
                    new BorderTraceTolerances(20.0, WELD_TOLERANCE, MITER_SPIKE_LIMIT));

            assertThat(rings).isEmpty();
        }

        @Test
        void twoRegionsTracedAgainstEachOtherMeetExactlyOnTheirCoincidentEdge() {
            // Cells A [0,0]..[10,10] and B [10,0]..[20,10] share the x = 10 edge but carry
            // different keys, so each traces as its own cluster. Naming the other as coincident
            // leaves that shared edge un-inset from both sides: A's border reaches x = 10 and B's
            // starts at x = 10, so the two fills abut on the raw cell edge with no wedge between
            // them. Their outward edges still take the plain channel.
            var rings = traceCarvedNeighbours(
                Set.of(buildCellKey("B")),
                Set.of(buildCellKey("A")));

            assertThat(readMaxXOf(rings.first())).isCloseTo(10.0, within(1e-6));
            assertThat(readMinXOf(rings.second())).isCloseTo(10.0, within(1e-6));
        }

        @Test
        void twoRegionsTracedWithoutCoincidenceLeaveTheBorderChannelBetweenThem() {
            // The same two cells, neither naming the other: the shared edge is an ordinary
            // boundary, so both sides inset by the channel and the 2 + 2 gap between them is the
            // border channel two rival nations are meant to be separated by.
            var rings = traceCarvedNeighbours(NO_COINCIDENT_NEIGHBOURS, NO_COINCIDENT_NEIGHBOURS);

            assertThat(readMaxXOf(rings.first())).isCloseTo(8.0, within(1e-6));
            assertThat(readMinXOf(rings.second())).isCloseTo(12.0, within(1e-6));
        }

        // Traces the two differently-keyed neighbours A and B, each naming the given coincident
        // set, so a test varies only what each opts out of the channel.
        private static TracedPair traceCarvedNeighbours(
                Set<SystemKey> aCoincidentNeighbours, Set<SystemKey> bCoincidentNeighbours) {
            var edges = Map.of(
                    "A", List.of(
                            buildEdgeFacing(0, 0, 10, 0, null), buildEdgeFacing(10, 0, 10, 10, "B"),
                            buildEdgeFacing(10, 10, 0, 10, null), buildEdgeFacing(0, 10, 0, 0, null)),
                    "B", List.of(
                            buildEdgeFacing(10, 0, 20, 0, null), buildEdgeFacing(20, 0, 20, 10, null),
                            buildEdgeFacing(20, 10, 10, 10, null), buildEdgeFacing(10, 10, 10, 0, "A")));
            var owners = Map.of("A", "F#solid", "B", "F#hatched");
            return new TracedPair(
                    traceBorderRings(
                            List.of("A"), edges, buildIdentityGroupingByName(edges.keySet(), owners),
                            aCoincidentNeighbours,
                            TOLERANCES).get(0),
                    traceBorderRings(
                            List.of("B"), edges, buildIdentityGroupingByName(edges.keySet(), owners),
                            bCoincidentNeighbours,
                            TOLERANCES).get(0));
        }

        @Test
        void anOpenFrontierEdgeInsetsInwardByThePlainChannel() {
            // Grouped A [0,0]..[100,100] faces a dead star B across its right edge (x = 100).
            // That open frontier takes the same inward channel every other boundary does, so
            // the ring's right side lands at x = 98 and never reaches past the raw cell edge
            // toward B - a grouped cluster stops at the Voronoi midline like any other border.
            var edges = Map.of(
                    "A", List.of(
                            buildEdgeFacing(0, 0, 100, 0, null), buildEdgeFacing(100, 0, 100, 100, "B"),
                            buildEdgeFacing(100, 100, 0, 100, null), buildEdgeFacing(0, 100, 0, 0, null)));
            var owners = Map.of("A", "F");

            var rings = traceBorderRings(
                    List.of("A"), edges, buildIdentityGroupingByName(edges.keySet(), owners), NO_COINCIDENT_NEIGHBOURS,
                    TOLERANCES);

            assertThat(rings).hasSize(1);
            assertThat(readMaxXOf(rings.get(0))).isCloseTo(98.0, within(1e-6));
        }
    }

    // The single ring each of two neighbouring cells traced to, so a test reads the two sides of
    // their shared edge without unpacking two ring lists.
    private record TracedPair(List<double[]> first, List<double[]> second) {
    }

}
