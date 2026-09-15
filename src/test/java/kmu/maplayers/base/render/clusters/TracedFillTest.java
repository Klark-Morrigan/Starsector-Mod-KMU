package kmu.maplayers.base.render.clusters;

import kmlib.math.geometry.RingRegion;
import kmlib.opengl.GlVertexRuns;
import kmlib.testfixtures.profiling.RecordedCapture;

import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.render.clusters.SplitFillBuilder.ClusterFill;
import kmu.maplayers.base.theme.ThemeFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a per-state fill records about each body's cut: how many strokes it came back as, and
 * how its joins closed.
 *
 * <p>Worth its own suite because the run is the only place the join readings exist. What the build
 * hands on is a draw record carrying segments alone, so a reading not taken as the body is cut is
 * not merely unreported - it is gone, and no later pass could reconstruct it.
 */
final class TracedFillTest {

    // A square large enough that a hatch at the spacing below crosses it several times, so a body
    // that hatches comes back with segments rather than with a single degenerate touch.
    private static final List<double[]> SQUARE = List.of(
        new double[] {0, 0},
        new double[] {1000, 0},
        new double[] {1000, 1000},
        new double[] {0, 1000});

    private static final double HATCH_SPACING = 200.0;
    private static final double HATCH_ANGLE_RADIANS = 0;
    private static final double HATCH_WIDTH_FRACTION = 0.5;

    private static final String CUT_HATCH_SECTION = "mapLayer.cutHatch";

    @Nested
    class BuildFillFor {

        @Test
        void countsTheStrokesItCutForThisBody() {
            var cutFill = new ClusterFill[1];

            var capture = RecordedCapture.recordWhile(() ->
                cutFill[0] = createPerFillState(List.of(SQUARE))
                    .buildFillFor(new RingRegion(SQUARE, List.of())));

            // Counted off the run that went on to be drawn, not off a second cut of the same fill -
            // so the number in the row describes what the frame actually shows.
            assertThat(readCutSegments(capture))
                .isEqualTo(cutFill[0].hatchSegments().length / GlVertexRuns.FLOATS_PER_SEGMENT);
        }

        @Test
        void namesTheCallWithHowItsJoinsClosed() {
            // The readings that settle whether the join tolerance is load-bearing, which exist
            // nowhere but on the run this call held.
            var capture = RecordedCapture.recordWhile(() ->
                createPerFillState(List.of(SQUARE))
                    .buildFillFor(new RingRegion(SQUARE, List.of())));

            assertThat(capture.findNode(CUT_HATCH_SECTION).getWorstCall().getTag())
                .contains("exactJoins=")
                .contains("widestClosedGap=");
        }

        @Test
        void recordsABodyThatHatchesNothingAsACutOfNoStrokes() {
            // A splitting owner whose members are all solid still cuts every body. The cut is
            // recorded, since it happened and cost something; what tells it from a body whose
            // hatch came out empty for a reason is the count, which is why it is a count.
            var cutFill = new ClusterFill[1];

            var capture = RecordedCapture.recordWhile(() ->
                cutFill[0] = createPerFillState(List.of())
                    .buildFillFor(new RingRegion(SQUARE, List.of())));

            assertThat(readCutSegments(capture))
                .isZero();
            assertThat(capture.findNode(CUT_HATCH_SECTION).getWorstCall().getTag())
                .isEmpty();
            assertThat(cutFill[0].hatchSegments())
                .isEmpty();
        }
    }

    // What the one cut this suite's subject made counted.
    private static long readCutSegments(RecordedCapture capture) {
        return capture
            .findNode(CUT_HATCH_SECTION)
            .findCount(MapBuildCounters.HATCH_SEGMENTS)
            .getTotals()
            .getTotal();
    }

    // A per-state fill hatching the given rings, with everything else it needs pinned: this suite's
    // subject is what the cut records, so the solid state is left empty and the hatch is laid out
    // to whatever crosses the square above.
    private static TracedFill.PerFillState createPerFillState(List<List<double[]>> hatchedRings) {

        return new TracedFill.PerFillState(
            List.of(),
            hatchedRings,
            ThemeFixtures.createHatchStyle(
                HATCH_SPACING,
                HATCH_ANGLE_RADIANS,
                HATCH_WIDTH_FRACTION));
    }
}
