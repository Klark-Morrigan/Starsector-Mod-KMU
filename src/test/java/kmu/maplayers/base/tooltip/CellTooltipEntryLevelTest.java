package kmu.maplayers.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one distinction the level exists to draw: both ways of standing under a line set it in a
 * step, and only the account of that line stands a step further under the box's voice. Asserted here
 * rather than only through a laid-out block, since it is the rule that keeps an allied holder's markets
 * reading as loudly as a lone holder's.
 */
final class CellTooltipEntryLevelTest {

    @Nested
    class GroupedUnder {

        @Test
        void groupedUnderSetsAPeerInWithoutDemotingIt() {
            // An alliance's member factions: inset beneath the line naming the alliance, yet the same kind of
            // statement it is - who holds the system, said at a finer granularity.
            var level = CellTooltipEntryLevel.LISTED_LEVEL.groupedUnder();

            assertThat(level.indentDepth())
                .isEqualTo(1);
            assertThat(level.subordinationLevel())
                .isEqualTo(0);
        }

        @Test
        void groupedUnderKeepsWhateverLevelItsParentWasAlreadyAt() {

            var level = CellTooltipEntryLevel.LISTED_LEVEL
                .subordinatedUnder()
                .groupedUnder();

            assertThat(level.indentDepth())
                .isEqualTo(2);
            assertThat(level.subordinationLevel())
                .isEqualTo(1);
        }
    }

    @Nested
    class SubordinatedUnder {

        @Test
        void subordinatedUnderSetsAnAccountInAndAStepUnderTheBoxsVoice() {

            var level = CellTooltipEntryLevel.LISTED_LEVEL.subordinatedUnder();

            assertThat(level.indentDepth())
                .isEqualTo(1);
            assertThat(level.subordinationLevel())
                .isEqualTo(1);
        }

        @Test
        void subordinatedUnderPutsAGroupedLinesAccountWhereAnUngroupedOnesLands() {
            // The consistency the whole type exists for: a market under a faction inside an alliance is
            // one step further in than a market under a lone faction, and exactly as loud - the alliance
            // added a level the account had nothing to do with.
            var underAlliedFaction = CellTooltipEntryLevel.LISTED_LEVEL
                .groupedUnder()
                .subordinatedUnder();
                
            var underLoneFaction = CellTooltipEntryLevel.LISTED_LEVEL.subordinatedUnder();

            assertThat(underAlliedFaction.subordinationLevel())
                .isEqualTo(1);
            assertThat(underLoneFaction.subordinationLevel())
                .isEqualTo(1);
            assertThat(underAlliedFaction.indentDepth())
                .isEqualTo(2);
            assertThat(underLoneFaction.indentDepth())
                .isEqualTo(1);
        }
    }

    @Nested
    class IsListedInItsOwnRight {

        @Test
        void isListedInItsOwnRightHoldsForABlocksOwnLine() {

            assertThat(CellTooltipEntryLevel.LISTED_LEVEL.isListedInItsOwnRight())
                .isTrue();
        }

        @Test
        void isListedInItsOwnRightFailsForAnythingSetInBeneathOne() {
            // A peer is not one of the block's own lines even though it speaks as loudly: how a line is
            // laid follows where it sits, and only how loudly it reads follows what it is.
            assertThat(CellTooltipEntryLevel.LISTED_LEVEL.groupedUnder().isListedInItsOwnRight())
                .isFalse();
            assertThat(CellTooltipEntryLevel.LISTED_LEVEL.subordinatedUnder().isListedInItsOwnRight())
                .isFalse();
        }
    }
}
