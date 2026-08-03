package kmu.maplayers.base.render.clusters;

import kmlib.opengl.GlLineQuality;
import kmlib.opengl.hatch.HatchJoinTally;
import kmlib.opengl.hatch.HatchJoining;
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

    @Nested
    class DescribeHatchSpecification {

        @Test
        void describeHatchSpecificationCarriesTheToleranceOnlyForAJoiningThatReadsIt() {

            var merging = HatchBuildDiagnostics.describeHatchSpecification(
                createHatchStyle(HatchJoining.COALESCED));

            var unmerging = HatchBuildDiagnostics.describeHatchSpecification(
                createHatchStyle(HatchJoining.PER_TRIANGLE));

            assertThat(merging)
                .contains("tolerance=0.001");

            // A joining that merges nothing never reads the tolerance, so naming it would report a
            // setting that had no part in what was drawn.
            assertThat(unmerging)
                .doesNotContain("tolerance");
        }

        @Test
        void describeHatchSpecificationNamesBothHalvesOfTheStyle() {
            
            var specification = HatchBuildDiagnostics.describeHatchSpecification(
                createHatchStyle(HatchJoining.COALESCED));

            // The layout is baked and the stroke is read per frame, so a capture attributed to one
            // half alone cannot be reproduced. The stroke prints as itself, which is what keeps a
            // kind this class has never heard of legible.
            assertThat(specification)
                .contains("joining=COALESCED")
                .contains("spacing=400.0")
                .contains("angleRadians=0.75")
                .contains("stroke=GlLineHatchStroke[quality=ALIASED, widthPixels=2.0]");
        }
    }

    @Nested
    class DescribeHatchRun {

        @Test
        void describeHatchRunReportsTheSegmentCountInSegmentsRatherThanFloats() {
            // Eight floats pack two segments, so a row reporting the array's own length reads 8.
            var run = new HatchRun(
                new float[] {0f, 0f, 1f, 1f, 2f, 2f, 3f, 3f},
                HatchJoinTally.NO_JOINS);

            assertThat(HatchBuildDiagnostics.describeHatchRun(run))
                .isEqualTo("Cluster hatch built; segments=2");
        }
    }

    @Nested
    class DescribeJoinedHatchRun {

        @Test
        void describeJoinedHatchRunAddsTheJoinReadingsToTheSegmentCount() {
            // Every reading varies per body, so all of them belong on the row - and none of them
            // restates the tolerance they were measured against, which the heading already gave.
            assertThat(HatchBuildDiagnostics.describeJoinedHatchRun(createHatchRun()))
                .isEqualTo("Cluster hatch built; segments=1"
                    + " exactJoins=7"
                    + " toleranceJoins=2"
                    + " overlappingJoins=1"
                    + " widestClosedGap=0.004"
                    + " narrowestOpenGap=0.06");
        }
    }

    @Nested
    class SelectRunDescriberFor {

        @Test
        void selectRunDescriberForOmitsTheJoinReadingsUnderAJoiningThatMakesNoJoins() {
            // Its tally is structural zeroes rather than measurements, so a row carrying them
            // would read exactly like a merge that ran and closed nothing.
            var describeRun = HatchBuildDiagnostics.selectRunDescriberFor(
                createHatchStyle(HatchJoining.PER_TRIANGLE));

            assertThat(describeRun.apply(createHatchRun()))
                .isEqualTo("Cluster hatch built; segments=1");
        }

        @Test
        void selectRunDescriberForCarriesTheJoinReadingsUnderAJoiningThatMerges() {
            var describeRun = HatchBuildDiagnostics.selectRunDescriberFor(
                createHatchStyle(HatchJoining.COALESCED));

            assertThat(describeRun.apply(createHatchRun()))
                .contains("exactJoins=7")
                .contains("narrowestOpenGap=0.06");
        }
    }

    // One segment, with a tally distinctive enough that a row built from the wrong describer is
    // caught by the readings it does or does not carry rather than by its length.
    private static HatchRun createHatchRun() {
        return new HatchRun(
            new float[] {0f, 0f, 1f, 1f},
            new HatchJoinTally(7, 2, 1, 0.004, 0.06));
    }

    private static HatchStyle createHatchStyle(HatchJoining joining) {
        return new HatchStyle(
            SPACING,
            ANGLE_RADIANS,
            joining,
            JOIN_TOLERANCE,
            new GlLineHatchStroke(GlLineQuality.ALIASED, WIDTH_PIXELS));
    }
}
