package kmu.maplayers.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ordered depths the hover box can be read at: that each level admits exactly the
 * subordination its tier of the account sits at - the cut every listing walk asks it about - and
 * that one key cycles through all four and back, so the deepest box can always be left.
 */
final class HoverTooltipDetailLevelTest {

    @Nested
    class GetMaximumSubordination {

        @Test
        void getMaximumSubordinationAdmitsOnlyTopLevelLinesAtTheFactionsLevel() {
            // Zero rather than "nothing": lines set in without being demoted - an alliance's member
            // factions - still carry subordination zero, which is how they survive this level.
            assertThat(HoverTooltipDetailLevel.FACTIONS.getMaximumSubordination())
                .isEqualTo(0);
        }

        @Test
        void getMaximumSubordinationAdmitsTheMarketTierAtTheCompositionLevel() {

            assertThat(HoverTooltipDetailLevel.SYSTEM_COMPOSITION.getMaximumSubordination())
                .isEqualTo(1);
        }

        @Test
        void getMaximumSubordinationAdmitsTheStatTierAtTheMarketStatsLevel() {

            assertThat(HoverTooltipDetailLevel.MARKET_STATS.getMaximumSubordination())
                .isEqualTo(2);
        }

        @Test
        void getMaximumSubordinationAdmitsThePatrolSplitAtThePatrolDetailsLevel() {

            assertThat(HoverTooltipDetailLevel.PATROL_DETAILS.getMaximumSubordination())
                .isEqualTo(3);
        }
    }

    @Nested
    class GetNextLevel {

        @Test
        void getNextLevelStepsFromFactionsToSystemComposition() {

            assertThat(HoverTooltipDetailLevel.FACTIONS.getNextLevel())
                .isEqualTo(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);
        }

        @Test
        void getNextLevelStepsFromSystemCompositionToMarketStats() {

            assertThat(HoverTooltipDetailLevel.SYSTEM_COMPOSITION.getNextLevel())
                .isEqualTo(HoverTooltipDetailLevel.MARKET_STATS);
        }

        @Test
        void getNextLevelStepsFromMarketStatsToPatrolDetails() {

            assertThat(HoverTooltipDetailLevel.MARKET_STATS.getNextLevel())
                .isEqualTo(HoverTooltipDetailLevel.PATROL_DETAILS);
        }

        @Test
        void getNextLevelWrapsFromPatrolDetailsBackToFactions() {
            // The wrap is what makes every press act: at the deepest level the next press collapses
            // rather than dead-ending, so the key that led in also leads out.
            assertThat(HoverTooltipDetailLevel.PATROL_DETAILS.getNextLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }
    }
}
