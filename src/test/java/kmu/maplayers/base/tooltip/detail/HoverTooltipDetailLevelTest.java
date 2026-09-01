package kmu.maplayers.base.tooltip.detail;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the ordered depths the hover box can be read at: that each level admits exactly the
 * subordination its tier of the account sits at - the cut every listing walk asks it about - that
 * the two questions a composer asks before working a tier out answer over that same order, and that
 * one key cycles through all four and back, so the deepest box can always be left.
 *
 * <p>And what the hint at the foot of the box says at each of them: the phrase names the step the
 * next press takes rather than the level being drawn, so it is pinned per level against the words
 * the game ships.
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

    @Nested
    class IsCollapsingOnNextPress {

        @Test
        void isCollapsingOnNextPressHoldsAtTheDeepestLevelAlone() {
            // The one place the key takes detail away rather than adding it, which is what both the
            // hint's wording and the press's own claim turn on.
            assertThat(HoverTooltipDetailLevel.PATROL_DETAILS.isCollapsingOnNextPress())
                .isTrue();
        }

        @Test
        void isCollapsingOnNextPressIsFalseAtEveryLevelShortOfTheDeepest() {

            assertThat(HoverTooltipDetailLevel.FACTIONS.isCollapsingOnNextPress())
                .isFalse();
            assertThat(HoverTooltipDetailLevel.SYSTEM_COMPOSITION.isCollapsingOnNextPress())
                .isFalse();
            assertThat(HoverTooltipDetailLevel.MARKET_STATS.isCollapsingOnNextPress())
                .isFalse();
        }
    }

    @Nested
    class ResolveNextActionPhrase {

        @BeforeEach
        void installStrings() {
            StarsectorSettingsFake.installSettings();
        }

        @AfterEach
        void clearStrings() {
            StarsectorSettingsFake.clearSettings();
        }

        @Test
        void resolveNextActionPhraseNamesTheStepThePressTakes() {
            // The hint states what the player would gain rather than which level they are at, so each
            // level is worded from the one it moves to - read off the level being drawn instead, every
            // phrase would name detail already on screen.
            assertThat(HoverTooltipDetailLevel.FACTIONS.resolveNextActionPhrase())
                .isEqualTo("expand system composition");
            assertThat(HoverTooltipDetailLevel.SYSTEM_COMPOSITION.resolveNextActionPhrase())
                .isEqualTo("expand market stats");
            assertThat(HoverTooltipDetailLevel.MARKET_STATS.resolveNextActionPhrase())
                .isEqualTo("expand patrol details");
        }

        @Test
        void resolveNextActionPhraseNamesTheCollapseAtTheDeepestLevelAlone() {
            // The cycle wraps, so the shallowest level is only ever arrived at by collapsing - which
            // is why it is the phrase that level carries, and why no other level names one.
            assertThat(HoverTooltipDetailLevel.PATROL_DETAILS.resolveNextActionPhrase())
                .isEqualTo("collapse to factions");
        }
    }
}
