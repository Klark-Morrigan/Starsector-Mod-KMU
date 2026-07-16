package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.geometry.CellEdge;
import kmu.maplayers.politicalmap.base.geometry.FrontierSettings;
import kmu.maplayers.politicalmap.base.geometry.SystemClusterBorders;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapStyle;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration coverage for the border-ring trace path the full territory build and the
 * incremental refresh both go through - {@link PoliticalBorderTrace} over
 * {@link SystemClusterBorders}, the classifier, the frontier setback, and the kmlib chainer
 * and per-edge miter, with the frontier snapshot enabled. Pins acceptance case 6: a faction
 * traced in the full pass and re-traced by the incremental refresh yield byte-identical
 * rings, because both hand the trace the same members, adjacency, and frontier - so an
 * open-frontier push that reaches a faction around a dead star lands the same whichever path
 * built it. Also pins that the record forwards its frontier faithfully, the wiring a unit
 * test on either class alone cannot see. Cells are hand-built squares scaled clear of the
 * fixed 150-unit border channel ({@link PoliticalMapStyle#BORDER_INSET_DISTANCE}), since the
 * trace only reads the adjacency graph, the keys, and the sites.
 */
class PoliticalBorderTraceIntegrationTest {

    private static final double WELD_TOLERANCE = 1e-3;
    private static final double MITER_SPIKE_LIMIT = 4.0;
    // A keep-out radius that, at the fixture's 2000-unit spacing, leaves a measurable pocket:
    // setback = spacing / 2 - r = 1000 - 400 = 600, so the push is channel + 600.
    private static final double KEEP_OUT_RADIUS = 400.0;

    // Owned A faces the dead star B across its right edge; owned C (a rival) faces B across its
    // left edge. B holds no market, so it is absent from the owner map (an unheld frontier star).
    private static final Map<String, List<CellEdge>> EDGES = Map.of(
            "A", List.of(
                    new CellEdge(0, 0, 2000, 0, null),
                    new CellEdge(2000, 0, 2000, 2000, "B"),
                    new CellEdge(2000, 2000, 0, 2000, null),
                    new CellEdge(0, 2000, 0, 0, null)),
            "C", List.of(
                    new CellEdge(4000, 0, 6000, 0, null),
                    new CellEdge(6000, 0, 6000, 2000, null),
                    new CellEdge(6000, 2000, 4000, 2000, null),
                    new CellEdge(4000, 2000, 4000, 0, "B")));
    private static final Map<String, String> OWNERS = Map.of("A", "F", "C", "G");
    private static final FrontierSettings FRONTIER_ON = new FrontierSettings(Map.of(
            "A", new double[] {1000, 1000},
            "B", new double[] {3000, 1000},
            "C", new double[] {5000, 1000}), KEEP_OUT_RADIUS, true);

    @Nested
    class TraceRings {
        @Test
        void the_full_pass_and_the_incremental_re_trace_of_one_faction_agree() {
            var trace = new PoliticalBorderTrace(WELD_TOLERANCE, MITER_SPIKE_LIMIT, FRONTIER_ON);

            // The full build traces every faction in one pass; the incremental refresh re-traces
            // only the touched faction after a reshape. Both route through the same record, so F's
            // rings must match ring-for-ring, vertex-for-vertex.
            var fullPassF = trace.traceRings(List.of("A"), EDGES, OWNERS);
            var incrementalRetraceF = trace.traceRings(List.of("A"), EDGES, OWNERS);

            assertRingsEqual(fullPassF, incrementalRetraceF);
            // The push actually fired: F's right border bulged past the raw x = 2000 edge toward
            // B's star, so this is the frontier-on path, not a coincidental all-inset match. The
            // channel plus setback (150 + 600 = 750) carries it to x = 2750.
            assertThat(maxXOf(fullPassF.get(0))).isCloseTo(2750.0, within(1e-6));
        }

        @Test
        void the_record_forwards_its_frontier_to_the_cluster_trace() {
            // The record's trace must equal a direct cluster trace given the same frontier - the
            // wiring that keeps the anchor fit and the territory build (both go through the record)
            // clipping against the very rings the fills stroke.
            var trace = new PoliticalBorderTrace(WELD_TOLERANCE, MITER_SPIKE_LIMIT, FRONTIER_ON);

            var throughRecord = trace.traceRings(List.of("A"), EDGES, OWNERS);
            var directTrace = SystemClusterBorders.traceBorderRings(List.of("A"), EDGES, OWNERS,
                    Set.of(),
                    PoliticalMapStyle.BORDER_INSET_DISTANCE, WELD_TOLERANCE, MITER_SPIKE_LIMIT,
                    FRONTIER_ON);

            assertRingsEqual(throughRecord, directTrace);
        }

        @Test
        void two_rivals_sharing_the_dead_star_leave_an_unclaimed_lens_between_them() {
            var trace = new PoliticalBorderTrace(WELD_TOLERANCE, MITER_SPIKE_LIMIT, FRONTIER_ON);

            // Each rival pushes its facing edge toward B and stops a channel short of the keep-out
            // line, so F's right border (x = 2750) never reaches G's left border (x = 3250): a
            // 500-wide lens with B's star at x = 3000 threads between the two territories.
            var fRings = trace.traceRings(List.of("A"), EDGES, OWNERS);
            var gRings = trace.traceRings(List.of("C"), EDGES, OWNERS);

            assertThat(maxXOf(fRings.get(0))).isCloseTo(2750.0, within(1e-6));
            assertThat(minXOf(gRings.get(0))).isCloseTo(3250.0, within(1e-6));
            assertThat(maxXOf(fRings.get(0))).isLessThan(minXOf(gRings.get(0)));
        }
    }

    // Asserts two ring sets match ring-for-ring and vertex-for-vertex - the identity a full
    // build and an incremental re-trace of the same faction must hold.
    private static void assertRingsEqual(List<List<double[]>> actual, List<List<double[]>> expected) {
        assertThat(actual).hasSameSizeAs(expected);
        for (var i = 0; i < actual.size(); i++) {
            var actualRing = actual.get(i);
            var expectedRing = expected.get(i);
            assertThat(actualRing).hasSameSizeAs(expectedRing);
            for (var v = 0; v < actualRing.size(); v++) {
                assertThat(actualRing.get(v)).containsExactly(expectedRing.get(v), within(1e-9));
            }
        }
    }

    private static double maxXOf(List<double[]> ring) {
        return ring.stream().mapToDouble(vertex -> vertex[0]).max().orElseThrow();
    }

    private static double minXOf(List<double[]> ring) {
        return ring.stream().mapToDouble(vertex -> vertex[0]).min().orElseThrow();
    }
}
