package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for the one crossing test the constructions share, and the one choice they make
 * differently: what two runs meeting at a shared end amount to.
 */
class LineCrossingsTests {

    private static final double[] ORIGIN = {0, 0};
    private static final double[] EAST = {10, 0};
    private static final double[] NORTH = {0, 10};

    @Nested
    class IsCrossing {

        @Test
        void twoRunsCrossingInTheirMiddlesCrossUnderEitherReading() {

            var down = new double[] {5, 5};
            var up = new double[] {5, -5};

            var actualCrossing = LineCrossings.isCrossing(
                ORIGIN,
                EAST,
                down,
                up,
                LineCrossings.SharedAnchor.IS_CROSSING);

            var actualTouch = LineCrossings.isCrossing(
                ORIGIN,
                EAST,
                down,
                up,
                LineCrossings.SharedAnchor.IS_TOUCH);

            assertThat(actualCrossing)
                .isTrue();
            assertThat(actualTouch)
                .isTrue();
        }

        @Test
        void runsThatMissDoNotCross() {

            assertThat(LineCrossings.isCrossing(
                    ORIGIN,
                    EAST,
                    new double[] {0, 5},
                    new double[] {10, 5},
                    LineCrossings.SharedAnchor.IS_CROSSING))
                .isFalse();
        }

        @Test
        void runsSharingAnEndCrossWhenATouchCounts() {

            assertThat(LineCrossings.isCrossing(
                    ORIGIN,
                    EAST,
                    ORIGIN,
                    NORTH,
                    LineCrossings.SharedAnchor.IS_CROSSING))
                .isTrue();
        }

        @Test
        void runsSharingAnEndDoNotCrossWhenATouchIsExempt() {

            assertThat(LineCrossings.isCrossing(
                    ORIGIN,
                    EAST,
                    ORIGIN,
                    NORTH,
                    LineCrossings.SharedAnchor.IS_TOUCH))
                .isFalse();
        }

        @Test
        void endsWithinTheTouchingToleranceAreOneAnchor() {
            // Half a unit apart, inside the tolerance of one: the same anchor reached by two
            // different sums.
            var nearlyTheOrigin = new double[] {0, 0.5};

            assertThat(LineCrossings.isCrossing(
                    ORIGIN,
                    EAST,
                    nearlyTheOrigin,
                    NORTH,
                    LineCrossings.SharedAnchor.IS_TOUCH))
                .isFalse();
        }

        @Test
        void aRunTouchingAnotherMidwayIsNotAnAnchorTouch() {
            // A T-junction: one run ends on the other's middle, which is no end of the other,
            // so the exemption has nothing to say about it.
            assertThat(LineCrossings.isCrossing(
                    ORIGIN,
                    EAST,
                    new double[] {5, 0},
                    new double[] {5, 10},
                    LineCrossings.SharedAnchor.IS_TOUCH))
                .isTrue();
        }
    }
}
