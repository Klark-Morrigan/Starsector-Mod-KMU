package kmu.maplayers.base.render.clusters;

import kmlib.opengl.GlLineQuality;
import kmlib.opengl.hatch.HatchJoinTally;
import kmlib.opengl.hatch.HatchRun;

import kmu.maplayers.base.theme.GlLineHatchStroke;
import kmu.maplayers.base.theme.HatchStyle;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a rebuild's hatch reports itself, in the two kinds of line a capture is read from: the
 * settings once, then one row per body. The split is the subject - a value that cannot vary across
 * a rebuild belongs on the heading, and a row repeating it makes a reader compare values to notice
 * they never differ.
 */
final class HatchBuildDiagnosticsTest {

    private static final double SPACING = 400.0;
    private static final double ANGLE_RADIANS = 0.75;
    private static final double JOIN_TOLERANCE = 0.001;
    private static final double WIDTH_PIXELS = 2.0;

    // Three microseconds: a span the millisecond format every other build line uses would round to
    // "0.00ms", so a row reporting the cut in milliseconds fails the expectations below.
    private static final long ELAPSED_NANOS = 3_000L;

    @Nested
    class DescribeHatchSpecification {

        @Test
        void describeHatchSpecificationNamesEverySettingTheCutIsMadeUnder() {
            // A capture is attributed to the settings that produced it, so both halves of the
            // style are named: the layout that was baked and the stroke read per frame. The stroke
            // prints as itself rather than as fields picked out here, which is what keeps a kind
            // this class has never heard of legible.
            assertThat(HatchBuildDiagnostics.describeHatchSpecification(createHatchStyle()))
                .isEqualTo("Cluster hatch specification; spacing=400.0"
                    + " angleRadians=0.75"
                    + " tolerance=0.001"
                    + " stroke=GlLineHatchStroke[quality=ALIASED, widthPixels=2.0]"
                    + " (tolerance and gaps as fractions of spacing)");
        }
    }

    @Nested
    class DescribeHatchRun {

        @Test
        void describeHatchRunCarriesOnlyWhatVariesPerBody() {
            // Every reading here differs body to body, and none of them restates the tolerance
            // they were measured against - the heading already gave it, once.
            assertThat(HatchBuildDiagnostics.describeHatchRun(createTimedHatchRun()))
                .isEqualTo("Cluster hatch built; segments=1 took=3.0us"
                    + " exactJoins=7"
                    + " toleranceJoins=2"
                    + " overlappingJoins=1"
                    + " widestClosedGap=0.004"
                    + " narrowestOpenGap=0.06");
        }

        @Test
        void describeHatchRunReportsTheSegmentCountInSegmentsRatherThanFloats() {
            // Eight floats pack two segments, so a row reporting the array's own length reads 8.
            var run = new TimedHatchRun(
                new HatchRun(
                    new float[] {0f, 0f, 1f, 1f, 2f, 2f, 3f, 3f},
                    HatchJoinTally.NO_JOINS),
                ELAPSED_NANOS);

            assertThat(HatchBuildDiagnostics.describeHatchRun(run))
                .startsWith("Cluster hatch built; segments=2 ");
        }
    }

    // One segment, with a tally distinctive enough that a reading threaded into the wrong slot is
    // caught by value. Timed at a span milliseconds would round away, so a row that reported the
    // cut in them fails here.
    private static TimedHatchRun createTimedHatchRun() {
        return new TimedHatchRun(
            new HatchRun(
                new float[] {0f, 0f, 1f, 1f},
                new HatchJoinTally(7, 2, 1, 0.004, 0.06)),
            ELAPSED_NANOS);
    }

    private static HatchStyle createHatchStyle() {
        return new HatchStyle(
            SPACING,
            ANGLE_RADIANS,
            JOIN_TOLERANCE,
            new GlLineHatchStroke(GlLineQuality.ALIASED, WIDTH_PIXELS));
    }
}
