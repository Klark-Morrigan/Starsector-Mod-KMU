package kmu.maplayers.politicalmap.base;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link BlocStyleAdjustment}'s recede union: a bloc that recedes for more than one reason
 * dims and desaturates once (strongest mute, either desaturate) rather than compounding, so the
 * filter-over-view and view-over-independent stacks read one idempotent rule.
 */
class BlocStyleAdjustmentTest {

    @Nested
    class MergeRecede {

        @Test
        void mergeRecedeTakesTheStrongerMuteOfTheTwo() {
            // The smaller opacity multiplier is the stronger mute, so a bloc dims to whichever recede
            // dims it most rather than to the product of both.
            var muted = new BlocStyleAdjustment(0.3, false);
            var dimmer = new BlocStyleAdjustment(0.7, false);

            assertThat(muted.mergeRecede(dimmer))
                    .isEqualTo(new BlocStyleAdjustment(0.3, false));
        }

        @Test
        void mergeRecedeDesaturatesWhenEitherRecedeDesaturates() {
            // Desaturate is the OR of the two, so a bloc either recede desaturates ends up
            // desaturated even when the other leaves its palette alone.
            var desaturating = new BlocStyleAdjustment(1.0, true);
            var opaque = new BlocStyleAdjustment(1.0, false);

            assertThat(desaturating.mergeRecede(opaque).desaturate()).isTrue();
        }

        @Test
        void mergeRecedeLeavesADesaturateNeitherSideSets() {
            var opaque = new BlocStyleAdjustment(0.5, false);

            assertThat(opaque.mergeRecede(new BlocStyleAdjustment(0.8, false)).desaturate()).isFalse();
        }

        @Test
        void mergeRecedeWithNoneReturnsTheOtherRecede() {
            // Folding the identity in leaves a recede unchanged, so a bloc the view does not adjust
            // takes the filter's shared recede whole.
            var recede = new BlocStyleAdjustment(0.3, true);

            assertThat(BlocStyleAdjustment.NONE.mergeRecede(recede))
                    .isEqualTo(new BlocStyleAdjustment(0.3, true));
        }

        @Test
        void mergeRecedeIsIdempotentWhenAppliedTwice() {
            // Folding the same recede in a second time changes nothing, which is what keeps a bloc
            // the view and the filter both recede from muting twice.
            var recede = new BlocStyleAdjustment(0.4, true);

            assertThat(recede.mergeRecede(recede)).isEqualTo(recede);
        }
    }
}
