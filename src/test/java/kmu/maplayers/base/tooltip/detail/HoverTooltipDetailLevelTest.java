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
 * one key cycles through them and back, so any box can always be left.
 *
 * <p>Where the cycle wraps is pinned against the box's own depth rather than against the last
 * constant, that being what stops a box whose account ends higher up offering a tier it can never
 * fill.
 *
 * <p>And what the hint at the foot of the box says: the phrase belongs to the level being arrived
 * at, so it is pinned per level against the words the game ships.
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
    class ResolveDeepestLevel {

        @Test
        void resolveDeepestLevelNamesTheLastTierTheCycleDeclares() {
            // The bound past which no box can hold anything, which is what lets a caller settle a
            // collapse without asking any box how far its own tree reaches.
            assertThat(HoverTooltipDetailLevel.resolveDeepestLevel())
                .isEqualTo(HoverTooltipDetailLevel.PATROL_DETAILS);
        }
    }

    @Nested
    class ResolveNextLevelWithin {

        @Test
        void resolveNextLevelWithinStepsFromFactionsToSystemComposition() {

            assertThat(HoverTooltipDetailLevel.FACTIONS
                    .resolveNextLevelWithin(HoverTooltipDetailLevel.PATROL_DETAILS))
                .isEqualTo(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);
        }

        @Test
        void resolveNextLevelWithinStepsFromSystemCompositionToMarketStats() {

            assertThat(HoverTooltipDetailLevel.SYSTEM_COMPOSITION
                    .resolveNextLevelWithin(HoverTooltipDetailLevel.PATROL_DETAILS))
                .isEqualTo(HoverTooltipDetailLevel.MARKET_STATS);
        }

        @Test
        void resolveNextLevelWithinStepsFromMarketStatsToPatrolDetails() {

            assertThat(HoverTooltipDetailLevel.MARKET_STATS
                    .resolveNextLevelWithin(HoverTooltipDetailLevel.PATROL_DETAILS))
                .isEqualTo(HoverTooltipDetailLevel.PATROL_DETAILS);
        }

        @Test
        void resolveNextLevelWithinWrapsFromTheDeepestLevelTheBoxHolds() {
            // The wrap is what makes every press act: once the box's own tree runs out the next press
            // collapses rather than dead-ending, so the key that led in also leads out.
            assertThat(HoverTooltipDetailLevel.PATROL_DETAILS
                    .resolveNextLevelWithin(HoverTooltipDetailLevel.PATROL_DETAILS))
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }

        @Test
        void resolveNextLevelWithinWrapsAtABoundShortOfTheDeepestLevelDeclared() {
            // The whole point of the bound: a box whose account ends at the market stats - a claim,
            // which no patrol enters - collapses from there rather than being offered a patrol tier
            // that would redraw what is already on screen.
            assertThat(HoverTooltipDetailLevel.MARKET_STATS
                    .resolveNextLevelWithin(HoverTooltipDetailLevel.MARKET_STATS))
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }

        @Test
        void resolveNextLevelWithinWrapsFromPastTheBoundRatherThanSittingThere() {
            // The level is one shared fact carried across layer switches, so a box can be reached at
            // a depth it holds nothing at. It collapses on the next press instead of stranding the
            // player at a level its own tree cannot act on.
            assertThat(HoverTooltipDetailLevel.PATROL_DETAILS
                    .resolveNextLevelWithin(HoverTooltipDetailLevel.MARKET_STATS))
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }

        @Test
        void resolveNextLevelWithinStaysPutForABoxHoldingNothingBelowTheShallowest() {
            // The one case where a press would change nothing: a box that lists nobody, read at the
            // level it opens on. There is no tier to open and nothing to collapse.
            assertThat(HoverTooltipDetailLevel.FACTIONS
                    .resolveNextLevelWithin(HoverTooltipDetailLevel.FACTIONS))
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }
    }

    @Nested
    class ResolveArrivalPhrase {

        @BeforeEach
        void installStrings() {
            StarsectorSettingsFake.installSettings();
        }

        @AfterEach
        void clearStrings() {
            StarsectorSettingsFake.clearSettings();
        }

        @Test
        void resolveArrivalPhraseNamesWhatArrivingAtTheLevelDoes() {
            // The hint states what the player would gain rather than which level they are at, so it
            // is read off the level being moved to - read off the one being left instead, a phrase
            // would name detail already on screen.
            assertThat(HoverTooltipDetailLevel.SYSTEM_COMPOSITION.resolveArrivalPhrase())
                .isEqualTo("expand system composition");
            assertThat(HoverTooltipDetailLevel.MARKET_STATS.resolveArrivalPhrase())
                .isEqualTo("expand market stats");
            assertThat(HoverTooltipDetailLevel.PATROL_DETAILS.resolveArrivalPhrase())
                .isEqualTo("expand patrol details");
        }

        @Test
        void resolveArrivalPhraseNamesTheCollapseAtTheShallowestLevelAlone() {
            // The cycle wraps, so the shallowest level is only ever arrived at by collapsing - which
            // is why it is the phrase that level carries, and why no other level names one.
            assertThat(HoverTooltipDetailLevel.FACTIONS.resolveArrivalPhrase())
                .isEqualTo("collapse to factions");
        }
    }
}
