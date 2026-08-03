package kmu.maplayers.base.theme;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ElementStyleAdjustment}'s recede union: an element that recedes for more than one
 * reason dims and desaturates once (strongest mute, either desaturate) rather than compounding, so
 * stacked recedes read one idempotent rule.
 */
class ElementStyleAdjustmentTest {

    @Nested
    class MergeRecede {

        @Test
        void mergeRecedeTakesTheStrongerMuteOfTheTwo() {
            // The smaller opacity multiplier is the stronger mute, so an element dims to whichever
            // recede dims it most rather than to the product of both.
            var muted = new ElementStyleAdjustment(0.3, false);
            var dimmer = new ElementStyleAdjustment(0.7, false);

            assertThat(muted.mergeRecede(dimmer))
                    .isEqualTo(new ElementStyleAdjustment(0.3, false));
        }

        @Test
        void mergeRecedeDesaturatesWhenEitherRecedeDesaturates() {
            // Desaturate is the OR of the two, so an element either recede desaturates ends up
            // desaturated even when the other leaves its palette alone.
            var desaturating = new ElementStyleAdjustment(1.0, true);
            var opaque = new ElementStyleAdjustment(1.0, false);

            assertThat(desaturating.mergeRecede(opaque).shouldDesaturate()).isTrue();
        }

        @Test
        void mergeRecedeLeavesADesaturateNeitherSideSets() {
            var opaque = new ElementStyleAdjustment(0.5, false);

            assertThat(opaque.mergeRecede(new ElementStyleAdjustment(0.8, false)).shouldDesaturate())
                    .isFalse();
        }

        @Test
        void mergeRecedeWithNoneReturnsTheOtherRecede() {
            // Folding the identity in leaves a recede unchanged, so an element nothing else adjusts
            // takes the one recede that does reach it whole.
            var recede = new ElementStyleAdjustment(0.3, true);

            assertThat(ElementStyleAdjustment.NONE.mergeRecede(recede))
                    .isEqualTo(new ElementStyleAdjustment(0.3, true));
        }

        @Test
        void mergeRecedeIsIdempotentWhenAppliedTwice() {
            // Folding the same recede in a second time changes nothing, which is what keeps an
            // element two reasons both recede from muting twice.
            var recede = new ElementStyleAdjustment(0.4, true);

            assertThat(recede.mergeRecede(recede)).isEqualTo(recede);
        }
    }
}
