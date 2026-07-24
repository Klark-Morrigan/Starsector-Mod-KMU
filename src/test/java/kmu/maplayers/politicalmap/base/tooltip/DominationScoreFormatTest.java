package kmu.maplayers.politicalmap.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link DominationScoreFormat}'s one presentation rule for a domination score: a whole number
 * grouped by thousands, with the grouping separator appearing only once a score is large enough to
 * warrant it, so small and large scores read consistently under the fixed root locale.
 */
final class DominationScoreFormatTest {

    @Nested
    class FormatScore {

        @Test
        void formatScoreRendersZeroAsASingleDigit() {
            assertThat(DominationScoreFormat.formatScore(0)).isEqualTo("0");
        }

        @Test
        void formatScoreRendersASubThousandScoreUngrouped() {
            // Below a thousand no separator is warranted, so the score renders bare.
            assertThat(DominationScoreFormat.formatScore(999)).isEqualTo("999");
        }

        @Test
        void formatScoreGroupsAtTheFirstThousand() {
            assertThat(DominationScoreFormat.formatScore(1000)).isEqualTo("1,000");
        }

        @Test
        void formatScoreGroupsEveryThreeDigitsOfALargeScore() {
            assertThat(DominationScoreFormat.formatScore(1234567)).isEqualTo("1,234,567");
        }
    }
}
