package kmu.maplayers.politicalmap.dominance.tooltip;

import kmu.maplayers.politicalmap.tooltip.ContestWording;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the heading the block of a system with no holder draws under, which is the one block whose
 * heading shares its words with another's: the contested block's, on an install where no system ever
 * changes hands.
 */
class StandingBlockTests {

    private static final String PRESENT_HEADING_KEY = "political_map_tooltip_section_present";

    @Nested
    class ResolveHeadingKey {

        // With no holder there is nothing to contest, so the wording that decides whether presence
        // beside a holder is a contest has nothing to decide here.
        @Test
        void headsThePresentBlockAsPresentWhereSystemsChangeHands() {

            // ACT
            var actualHeadingKey = StandingBlock.PRESENT.resolveHeadingKey(ContestWording.CONTESTED);

            // ASSERT
            assertThat(actualHeadingKey)
                .isEqualTo(PRESENT_HEADING_KEY);
        }

        @Test
        void headsThePresentBlockAsPresentWhereNoSystemChangesHands() {

            // ACT
            var actualHeadingKey = StandingBlock.PRESENT.resolveHeadingKey(ContestWording.PRESENT);

            // ASSERT
            assertThat(actualHeadingKey)
                .isEqualTo(PRESENT_HEADING_KEY);
        }
    }
}
