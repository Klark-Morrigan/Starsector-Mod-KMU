package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link EdgeInsetRule}: the shipped rule pulls in a border edge and leaves a fused one on
 * its true line, while the two forced rules answer the same for both - nothing anywhere, or the
 * full depth everywhere - which is what makes the true geometry and the channel over it
 * separable.
 */
final class EdgeInsetRuleTest {

    // A depth unlike any of the numbers the rule could otherwise return, so a rule handing back
    // the wrong one cannot pass by coincidence.
    private static final double DEPTH = 37.0;

    @Nested
    class ResolveInsetOf {

        @Test
        void resolveInsetOfPullsInABorderEdgeUnderTheShippedRule() {

            assertThat(EdgeInsetRule.AT_EVERY_BORDER.resolveInsetOf(true, DEPTH))
                .isCloseTo(37.0, within(1e-9));
        }

        @Test
        void resolveInsetOfLeavesAFusedEdgeOnItsLineUnderTheShippedRule() {

            assertThat(EdgeInsetRule.AT_EVERY_BORDER.resolveInsetOf(false, DEPTH))
                .isCloseTo(0.0, within(1e-9));
        }

        @Test
        void resolveInsetOfPullsInNothingUnderNowhere() {

            assertThat(EdgeInsetRule.NOWHERE.resolveInsetOf(true, DEPTH))
                .isCloseTo(0.0, within(1e-9));
            assertThat(EdgeInsetRule.NOWHERE.resolveInsetOf(false, DEPTH))
                .isCloseTo(0.0, within(1e-9));
        }

        @Test
        void resolveInsetOfPullsInEveryEdgeUnderEverywhere() {

            assertThat(EdgeInsetRule.EVERYWHERE.resolveInsetOf(true, DEPTH))
                .isCloseTo(37.0, within(1e-9));
            assertThat(EdgeInsetRule.EVERYWHERE.resolveInsetOf(false, DEPTH))
                .isCloseTo(37.0, within(1e-9));
        }
    }

    @Nested
    class IsFusingSharedEdges {

        @Test
        void isFusingSharedEdgesHoldsWhereASharedEdgeKeepsItsLine() {

            assertThat(EdgeInsetRule.AT_EVERY_BORDER.isFusingSharedEdges())
                .isTrue();
            assertThat(EdgeInsetRule.NOWHERE.isFusingSharedEdges())
                .isTrue();
        }

        @Test
        void isFusingSharedEdgesFailsWhereEvenASharedEdgePullsIn() {

            assertThat(EdgeInsetRule.EVERYWHERE.isFusingSharedEdges())
                .isFalse();
        }

        @Test
        void isFusingSharedEdgesAgreesWithWhatTheRuleGivesASharedEdge() {
            // The two are one statement: a shared edge left at zero is a shared edge the two
            // bodies meet along, and one pulled back by anything is a channel between them.
            for (var rule : EdgeInsetRule.values()) {

                assertThat(rule.isFusingSharedEdges())
                    .isEqualTo(rule.resolveInsetOf(false, DEPTH) == 0.0);
            }
        }
    }
}
