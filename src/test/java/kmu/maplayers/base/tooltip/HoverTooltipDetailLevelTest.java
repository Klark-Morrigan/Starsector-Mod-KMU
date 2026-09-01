package kmu.maplayers.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ordered depths the hover box can be read at: that each level admits exactly the
 * subordination its tier of the account sits at - the cut every listing walk asks it about - that
 * the two questions a composer asks before working a tier out answer over that same order, and that
 * one key cycles through all four and back, so the deepest box can always be left.
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
    class IsAdmittingAccounts {

        @Test
        void isAdmittingAccountsShowsNoAccountAtTheFactionsLevel() {
            // The level that names who holds the system and nothing under them, which is why a layer
            // may skip working an account out there at all.
            assertThat(HoverTooltipDetailLevel.FACTIONS.isAdmittingAccounts())
                .isFalse();
        }

        @Test
        void isAdmittingAccountsShowsAnAccountFromTheCompositionLevelDown() {
            // An account is one step under the box's voice from a listed line, so every level past
            // the shallowest shows one - read off that step rather than off a level named here, or
            // this answer and the cut could come to describe different boxes.
            assertThat(HoverTooltipDetailLevel.SYSTEM_COMPOSITION.isAdmittingAccounts())
                .isTrue();
            assertThat(HoverTooltipDetailLevel.MARKET_STATS.isAdmittingAccounts())
                .isTrue();
            assertThat(HoverTooltipDetailLevel.PATROL_DETAILS.isAdmittingAccounts())
                .isTrue();
        }
    }

    @Nested
    class IsReadingAtLeast {

        @Test
        void isReadingAtLeastReadsALevelAsDeepAsItself() {
            // The case both per-tier gates rest on: a tier is composed at the very level that names
            // it, so an answer that took "at least" as "deeper than" would drop every tier from the
            // level it exists to show.
            assertThat(HoverTooltipDetailLevel.MARKET_STATS
                    .isReadingAtLeast(HoverTooltipDetailLevel.MARKET_STATS))
                .isTrue();
        }

        @Test
        void isReadingAtLeastReadsALevelDeeperThanTheOneAskedAbout() {

            assertThat(HoverTooltipDetailLevel.PATROL_DETAILS
                    .isReadingAtLeast(HoverTooltipDetailLevel.SYSTEM_COMPOSITION))
                .isTrue();
        }

        @Test
        void isReadingAtLeastDoesNotReadALevelShallowerThanTheOneAskedAbout() {

            assertThat(HoverTooltipDetailLevel.SYSTEM_COMPOSITION
                    .isReadingAtLeast(HoverTooltipDetailLevel.PATROL_DETAILS))
                .isFalse();
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
