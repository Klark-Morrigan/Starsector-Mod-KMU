package kmu.maplayers.base.render.clusters;

import kmlib.opengl.GlLineQuality;
import kmlib.opengl.hatch.HatchJoinTally;
import kmlib.opengl.hatch.HatchPattern;
import kmlib.opengl.hatch.HatchRun;
import kmlib.profiling.recording.RecordingProfiler;

import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.theme.GlLineHatchStroke;
import kmu.maplayers.base.theme.HatchStyle;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a rebuild's hatch reports itself: the settings once as a line, then each body's cut as
 * a count and a name on the scope it was cut under. The split is the subject - a value that cannot
 * vary across a rebuild belongs on the heading, and a reading repeating it makes a reader compare
 * values to notice they never differ.
 */
final class HatchBuildDiagnosticsTest {

    private static final double SPACING = 400.0;
    private static final double ANGLE_RADIANS = 0.75;
    private static final double JOIN_TOLERANCE = 0.001;
    private static final double WIDTH_FRACTION = 0.4;

    @Nested
    class DescribeHatchSpecification {

        @Test
        void describeHatchSpecificationNamesEverySettingTheCutIsMadeUnder() {
            // A capture is attributed to the settings that produced it, so both halves of the
            // style are named: the layout that was baked and the stroke read per frame. The stroke
            // prints as itself rather than as fields picked out here, which is what keeps a kind
            // this class has never heard of legible.
            assertThat(HatchBuildDiagnostics.describeHatchSpecification(createHatchStyle()))
                .isEqualTo("Cluster hatch specification;"
                    + " pattern=HatchPattern[spacing=400.0, angleRadians=0.75,"
                    + " joinToleranceFraction=0.001]"
                    + " stroke=GlLineHatchStroke[quality=ALIASED, widthFraction=0.4]"
                    + " (tolerance and gaps as fractions of spacing)");
        }
    }

    @Nested
    class DescribeHatchJoins {

        @Test
        void describeHatchJoinsCarriesOnlyWhatVariesPerBody() {
            // Every reading here differs body to body, and none of them restates the tolerance
            // they were measured against - the heading already gave it, once. Nor the segment
            // count or the duration, which the call carries as a count and a span of its own.
            assertThat(HatchBuildDiagnostics.describeHatchJoins(createHatchRun()))
                .isEqualTo("exactJoins=7"
                    + " toleranceJoins=2"
                    + " overlappingJoins=1"
                    + " widestClosedGap=0.004"
                    + " narrowestOpenGap=0.06");
        }
    }

    @Nested
    class ReportHatchRun {

        @Test
        void reportHatchRunCountsStrokesRatherThanTheFloatsPackingThem() {
            // Eight floats pack two segments, so a row counting the array's own length reads 8.
            var profiler = new RecordingProfiler();
            var hatchRun = new HatchRun(
                new float[] {0f, 0f, 1f, 1f, 2f, 2f, 3f, 3f},
                HatchJoinTally.NO_JOINS);

            try (var cutScope = profiler.open(HatchBuildDiagnostics.CUT_HATCH_SECTION)) {
                HatchBuildDiagnostics.reportHatchRun(cutScope, hatchRun);
            }

            assertThat(readCutSegments(profiler))
                .isEqualTo(2);
        }
    }

    // What the one cut recorded above counted.
    private static long readCutSegments(RecordingProfiler profiler) {
        return profiler
            .snapshot()
            .get(0)
            .getRoots()
            .get(0)
            .findCount(MapBuildCounters.HATCH_SEGMENTS)
            .getTotals()
            .getTotal();
    }

    // One segment, with a tally distinctive enough that a reading threaded into the wrong slot is
    // caught by value.
    private static HatchRun createHatchRun() {
        return new HatchRun(
            new float[] {0f, 0f, 1f, 1f},
            new HatchJoinTally(7, 2, 1, 0.004, 0.06));
    }

    private static HatchStyle createHatchStyle() {
        return new HatchStyle(
            new HatchPattern(SPACING, ANGLE_RADIANS, JOIN_TOLERANCE),
            new GlLineHatchStroke(GlLineQuality.ALIASED, WIDTH_FRACTION));
    }
}
