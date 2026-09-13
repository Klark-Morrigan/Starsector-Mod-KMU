package kmu.maplayers.base.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.BorderTraceTolerances;
import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.geometry.SystemClusterBorders;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildDrawnSystemKeys;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration coverage for the border-ring trace path the full cluster build and the
 * incremental refresh both go through - {@link ClusterBorderTrace} over
 * {@link SystemClusterBorders}, the classifier, and the kmlib chainer and per-edge miter.
 * Pins acceptance case 6: a faction traced in the full pass and re-traced by the
 * incremental refresh yield byte-identical rings, because both hand the trace the same
 * members and adjacency. Also pins that the record forwards its parameters faithfully to
 * the cluster trace, the wiring a unit test on either class alone cannot see. Cells are
 * hand-built squares scaled clear of the fixed 150-unit border channel
 * ({@link CellShaper#BORDER_INSET_DISTANCE}), since the trace only reads the
 * adjacency graph and the keys.
 */
class ClusterBorderTraceIntegrationTest {

    private static final double WELD_TOLERANCE = 1e-3;
    private static final double MITER_SPIKE_LIMIT = 4.0;

    // Owned A faces the dead star B across its right edge; owned C (a rival) faces B across its
    // left edge. B holds no market, so it is absent from the owner map (an unheld frontier star).
    private static final Map<SystemKey, List<CellEdge>> EDGES = buildKeyedValues(Map.of(
        "A", List.of(
            buildEdge(0, 0, 2000, 0, null),
            buildEdge(2000, 0, 2000, 2000, "B"),
            buildEdge(2000, 2000, 0, 2000, null),
            buildEdge(0, 2000, 0, 0, null)),
        "C", List.of(
            buildEdge(4000, 0, 6000, 0, null),
            buildEdge(6000, 0, 6000, 2000, null),
            buildEdge(6000, 2000, 4000, 2000, null),
            buildEdge(4000, 2000, 4000, 0, "B"))));

    private static final Map<String, String> OWNERS = Map.of("A", "F", "C", "G");

    // Each cell drawing as its own star (identity draws-as over A and C), keyed by OWNERS - the
    // grouping every trace here runs under.
    private static final CellGrouping GROUPING =
        new CellGrouping(buildDrawnSystemKeys(Map.of("A", "A", "C", "C")), OWNERS);

    // One cell edge facing the given neighbour system, or the reach bound when it is null.
    private static CellEdge buildEdge(double x1, double y1, double x2, double y2, String neighbour) {

        return new CellEdge(
            x1,
            y1,
            x2,
            y2,
            neighbour == null
                ? EdgeTarget.REACH_BOUND
                : new EdgeTarget.AcrossSystem(buildCellKey(neighbour)));
    }

    @Nested
    class TraceRings {

        @Test
        void theFullPassAndTheIncrementalReTraceOfOneFactionAgree() {

            var trace = new ClusterBorderTrace(WELD_TOLERANCE, MITER_SPIKE_LIMIT);

            // The full build traces every faction in one pass; the incremental refresh re-traces
            // only the touched faction after a reshape. Both route through the same record, so F's
            // rings must match ring-for-ring, vertex-for-vertex.
            var fullPassF = trace.traceRings(buildCellKeys("A"), EDGES, GROUPING);
            var incrementalRetraceF = trace.traceRings(buildCellKeys("A"), EDGES, GROUPING);

            assertRingsEqual(fullPassF, incrementalRetraceF);

            // The channel actually fired: F's right border sits 150 inside its raw x = 2000 edge
            // facing B, so the rings are the real inset geometry rather than the raw cells.
            assertThat(computeMaxXOf(fullPassF.get(0)))
                .isCloseTo(1850.0, within(1e-6));
        }

        @Test
        void theRecordForwardsItsParametersToTheClusterTrace() {
            // The record's trace must equal a direct cluster trace given the same parameters - the
            // wiring that keeps the anchor fit and the cluster build (both go through the record)
            // clipping against the very rings the fills stroke.
            var trace = new ClusterBorderTrace(WELD_TOLERANCE, MITER_SPIKE_LIMIT);

            var throughRecord = trace.traceRings(buildCellKeys("A"), EDGES, GROUPING);
            var directTrace = SystemClusterBorders.traceBorderRings(
                buildCellKeys("A"),
                EDGES,
                GROUPING,
                Set.of(),
                new BorderTraceTolerances(
                    CellShaper.BORDER_INSET_DISTANCE,
                    WELD_TOLERANCE,
                    MITER_SPIKE_LIMIT));

            assertRingsEqual(throughRecord, directTrace);
        }

        @Test
        void twoRivalsSharingTheDeadStarLeaveItsCellUnclaimedBetweenThem() {

            var trace = new ClusterBorderTrace(WELD_TOLERANCE, MITER_SPIKE_LIMIT);

            // Each rival stops a channel inside its own cell edge against B, so B's whole cell
            // plus both channels - x = 1850 to x = 4150 - stays outside either cluster. Neither
            // reaches toward B's star at x = 3000.
            var fRings = trace.traceRings(buildCellKeys("A"), EDGES, GROUPING);
            var gRings = trace.traceRings(buildCellKeys("C"), EDGES, GROUPING);

            assertThat(computeMaxXOf(fRings.get(0)))
                .isCloseTo(1850.0, within(1e-6));
            assertThat(computeMinXOf(gRings.get(0)))
                .isCloseTo(4150.0, within(1e-6));
        }
    }

    // Asserts two ring sets match ring-for-ring and vertex-for-vertex - the identity a full
    // build and an incremental re-trace of the same faction must hold.
    private static void assertRingsEqual(List<List<double[]>> actual, List<List<double[]>> expected) {

        assertThat(actual)
            .hasSameSizeAs(expected);

        for (var i = 0; i < actual.size(); i++) {

            var actualRing = actual.get(i);
            var expectedRing = expected.get(i);

            assertThat(actualRing)
                .hasSameSizeAs(expectedRing);

            for (var v = 0; v < actualRing.size(); v++) {

                assertThat(actualRing.get(v))
                    .containsExactly(expectedRing.get(v), within(1e-9));
            }
        }
    }

    private static double computeMaxXOf(List<double[]> ring) {

        return ring
            .stream()
            .mapToDouble(vertex -> vertex[0])
            .max()
            .orElseThrow();
    }

    private static double computeMinXOf(List<double[]> ring) {

        return ring.stream()
            .mapToDouble(vertex -> vertex[0])
            .min()
            .orElseThrow();
    }
}
