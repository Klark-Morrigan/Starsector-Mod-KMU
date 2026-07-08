package kmu.maplayers.politicalmap.base.render;

import kmlib.math.geometry.PrincipalAxis;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the label slant preference: how a cluster's principal axis becomes the angle its
 * label leans at (capped short of vertical, faded to level for round clusters whose axis
 * is meaningless) and how a candidate line is docked for departing from that lean. The
 * anchor search's end-to-end geometry runs the slant at a zero cap, so this is where the
 * lean itself is exercised.
 */
class LabelSlantPreferenceTest {

    // A principal axis at a chosen line angle with chosen major/minor extents, so a test
    // can dial in an elongation (1 - minor/major) and a direction independently. The
    // centroid is irrelevant to the slant, so it sits at the origin.
    private static PrincipalAxis axisAt(double angleDegrees, double majorExtent,
            double minorExtent) {
        var angle = Math.toRadians(angleDegrees);
        return new PrincipalAxis(0.0, 0.0, Math.cos(angle), Math.sin(angle),
                majorExtent, minorExtent);
    }

    // A unit direction {x, y} at a line angle, the candidate the penalty scores.
    private static double[] directionAt(double angleDegrees) {
        var angle = Math.toRadians(angleDegrees);
        return new double[] {Math.cos(angle), Math.sin(angle)};
    }

    @Nested
    class ResolveFrom {

        private static final double MAX_SLANT = 22.0;

        @Test
        void resolveFromLeansAStronglyElongatedVerticalClusterToTheCap() {
            // A near-line vertical cloud (elongation ~1) wants its axis's 90 degrees, but
            // the cap holds the lean to the 22-degree ceiling rather than stand it upright.
            var slant = LabelSlantPreference.resolveFrom(axisAt(90.0, 1000.0, 0.0), MAX_SLANT);

            assertThat(slant.preferredAngle()).isCloseTo(Math.toRadians(22.0), within(1e-9));
        }

        @Test
        void resolveFromKeepsAShallowElongatedClusterBelowTheCap() {
            // A cloud strung along 10 degrees sits under the cap, so the lean is its own
            // angle scaled by the elongation (1 - 50/1000 = 0.95): 10 * 0.95 = 9.5 degrees.
            var slant = LabelSlantPreference.resolveFrom(axisAt(10.0, 1000.0, 50.0), MAX_SLANT);

            assertThat(slant.preferredAngle()).isCloseTo(Math.toRadians(9.5), within(1e-9));
        }

        @Test
        void resolveFromCollapsesARoundClusterToLevel() {
            // The axis reads vertical, but the cloud is round (equal extents, elongation 0),
            // so its direction is noise and the lean fades to level rather than follow it.
            var slant = LabelSlantPreference.resolveFrom(axisAt(90.0, 500.0, 500.0), MAX_SLANT);

            assertThat(slant.preferredAngle()).isCloseTo(0.0, within(1e-9));
        }

        @Test
        void resolveFromForcesLevelWhenTheCapIsZero() {
            // A zero cap forbids any lean, so even a strongly elongated tilted cloud sits
            // dead-level - the setting that reproduces the pre-slant horizontal labels.
            var slant = LabelSlantPreference.resolveFrom(axisAt(40.0, 1000.0, 0.0), 0.0);

            assertThat(slant.preferredAngle()).isCloseTo(0.0, within(1e-9));
        }
    }

    @Nested
    class ComputePenaltyMultiplier {

        private static final double STRENGTH = 0.5;
        private static final double EXPONENT = 2.0;

        @Test
        void computePenaltyMultiplierReturnsOneAtThePreferredAngle() {
            // A line sitting exactly on the lean pays nothing, whatever the strength.
            var slant = new LabelSlantPreference(Math.toRadians(15.0));

            assertThat(slant.computePenaltyMultiplier(directionAt(15.0), STRENGTH, EXPONENT))
                    .isCloseTo(1.0, within(1e-9));
        }

        @Test
        void computePenaltyMultiplierDocksAPerpendicularLineByTheFullStrength() {
            // A line square to the lean is a quarter turn away - the worst case - so it
            // loses the whole strength: 1 - 0.5 * 1 = 0.5.
            var slant = new LabelSlantPreference(Math.toRadians(15.0));

            assertThat(slant.computePenaltyMultiplier(directionAt(105.0), STRENGTH, EXPONENT))
                    .isCloseTo(0.5, within(1e-9));
        }

        @Test
        void computePenaltyMultiplierFallsOffMonotonicallyFromThePreferredAngle() {
            // The penalty grows with the deviation, so a line closer to the lean always
            // scores at least as high as one further from it.
            var slant = new LabelSlantPreference(0.0);

            var near = slant.computePenaltyMultiplier(directionAt(10.0), STRENGTH, EXPONENT);
            var mid = slant.computePenaltyMultiplier(directionAt(30.0), STRENGTH, EXPONENT);
            var far = slant.computePenaltyMultiplier(directionAt(60.0), STRENGTH, EXPONENT);

            assertThat(near).isGreaterThan(mid);
            assertThat(mid).isGreaterThan(far);
        }

        @Test
        void computePenaltyMultiplierTreatsALineAndItsOppositeAsOne() {
            // A direction and its 180-degree opposite are the same undirected line, so a
            // line at 30 degrees and one at 210 degrees pay the identical penalty.
            var slant = new LabelSlantPreference(0.0);

            var forward = slant.computePenaltyMultiplier(directionAt(30.0), STRENGTH, EXPONENT);
            var opposite = slant.computePenaltyMultiplier(directionAt(210.0), STRENGTH, EXPONENT);

            assertThat(forward).isCloseTo(opposite, within(1e-9));
        }

        @Test
        void computePenaltyMultiplierReturnsOneWhenTheStrengthIsZero() {
            // A zero strength turns the penalty off, so every line scores the full one no
            // matter how far it sits from the lean.
            var slant = new LabelSlantPreference(0.0);

            assertThat(slant.computePenaltyMultiplier(directionAt(90.0), 0.0, EXPONENT))
                    .isCloseTo(1.0, within(1e-9));
        }
    }

    @Nested
    class ToDirection {

        @Test
        void toDirectionReturnsTheUnitVectorOfThePreferredAngle() {
            var slant = new LabelSlantPreference(Math.toRadians(30.0));

            var direction = slant.toDirection();

            assertThat(direction[0]).isCloseTo(Math.cos(Math.toRadians(30.0)), within(1e-9));
            assertThat(direction[1]).isCloseTo(Math.sin(Math.toRadians(30.0)), within(1e-9));
        }

        @Test
        void toDirectionYieldsAUnitVector() {
            var slant = new LabelSlantPreference(Math.toRadians(37.0));

            var direction = slant.toDirection();

            assertThat(Math.hypot(direction[0], direction[1])).isCloseTo(1.0, within(1e-9));
        }
    }
}
