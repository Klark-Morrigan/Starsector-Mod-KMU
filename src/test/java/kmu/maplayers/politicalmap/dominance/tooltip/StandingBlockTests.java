package kmu.maplayers.politicalmap.dominance.tooltip;

import kmu.maplayers.politicalmap.tooltip.ContestWording;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the heading each block draws under, and which of them the install's wording reaches: only the
 * block standing against a holder, whose heading says either that the system is contested or merely
 * that its blocs are present.
 *
 * <p>The block of a system with no holder is pinned beside it, being the one other block whose heading
 * shares those words - and the one that must not follow the wording, there being no holder to contest.
 */
class StandingBlockTests {

    private static final String DOMINATED_HEADING_KEY = "political_map_tooltip_section_dominated";
    private static final String ALLIED_HEADING_KEY = "political_map_tooltip_section_allied_with_system_holder";
    private static final String FRIENDLY_HEADING_KEY = "political_map_tooltip_section_friendly_with_system_holder";
    private static final String CONTESTED_HEADING_KEY = "political_map_tooltip_section_contested";
    private static final String PRESENT_HEADING_KEY = "political_map_tooltip_section_present";
    private static final String NON_POLITICAL_HEADING_KEY = "political_map_tooltip_section_non_political";

    @Nested
    class ResolveHeadingKey {

        // Who holds the system is the same fact whether or not systems ever change hands.
        @ParameterizedTest
        @EnumSource(ContestWording.class)
        void headsTheHolderBlockAlikeUnderEitherWording(ContestWording contestWording) {

            // ACT
            var actualHeadingKey = StandingBlock.HOLDER.resolveHeadingKey(contestWording);

            // ASSERT
            assertThat(actualHeadingKey)
                .isEqualTo(DOMINATED_HEADING_KEY);
        }

        // An alliance with the holder is a relation, true of a sector nothing ever moves.
        @ParameterizedTest
        @EnumSource(ContestWording.class)
        void headsTheAlliedBlockAlikeUnderEitherWording(ContestWording contestWording) {

            // ACT
            var actualHeadingKey = StandingBlock.ALLIED.resolveHeadingKey(contestWording);

            // ASSERT
            assertThat(actualHeadingKey)
                .isEqualTo(ALLIED_HEADING_KEY);
        }

        // Goodwill toward the holder is a relation too, as true of a sector nothing ever moves.
        @ParameterizedTest
        @EnumSource(ContestWording.class)
        void headsTheFriendlyBlockAlikeUnderEitherWording(ContestWording contestWording) {

            // ACT
            var actualHeadingKey = StandingBlock.FRIENDLY.resolveHeadingKey(contestWording);

            // ASSERT
            assertThat(actualHeadingKey)
                .isEqualTo(FRIENDLY_HEADING_KEY);
        }

        // Where systems change hands, the blocs beside a holder are competing for the system.
        @Test
        void headsTheContestedBlockAsContestedWhereSystemsChangeHands() {

            // ACT
            var actualHeadingKey = StandingBlock.CONTESTED.resolveHeadingKey(ContestWording.CONTESTED);

            // ASSERT
            assertThat(actualHeadingKey)
                .isEqualTo(CONTESTED_HEADING_KEY);
        }

        // Where nothing ever changes hands, the same blocs are neighbours rather than challengers.
        @Test
        void headsTheContestedBlockAsPresentWhereNoSystemChangesHands() {

            // ACT
            var actualHeadingKey = StandingBlock.CONTESTED.resolveHeadingKey(ContestWording.PRESENT);

            // ASSERT
            assertThat(actualHeadingKey)
                .isEqualTo(PRESENT_HEADING_KEY);
        }

        // With no holder there is nothing to contest, so the wording that decides whether presence
        // beside a holder is a contest has nothing to decide here.
        @ParameterizedTest
        @EnumSource(ContestWording.class)
        void headsThePresentBlockAsPresentUnderEitherWording(ContestWording contestWording) {

            // ACT
            var actualHeadingKey = StandingBlock.PRESENT.resolveHeadingKey(contestWording);

            // ASSERT
            assertThat(actualHeadingKey)
                .isEqualTo(PRESENT_HEADING_KEY);
        }

        // Candidacy is about taking part in the contest at all, not about how it is worded.
        @ParameterizedTest
        @EnumSource(ContestWording.class)
        void headsTheNonPoliticalBlockAlikeUnderEitherWording(ContestWording contestWording) {

            // ACT
            var actualHeadingKey = StandingBlock.NON_POLITICAL.resolveHeadingKey(contestWording);

            // ASSERT
            assertThat(actualHeadingKey)
                .isEqualTo(NON_POLITICAL_HEADING_KEY);
        }
    }
}
