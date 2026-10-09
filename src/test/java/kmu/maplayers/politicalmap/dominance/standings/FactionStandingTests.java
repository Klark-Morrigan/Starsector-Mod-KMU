package kmu.maplayers.politicalmap.dominance.standings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which kind of standing counts as weighed: a weighed standing does, whatever its score, and a
 * presence-only standing never does.
 */
final class FactionStandingTests {

    @Nested
    class IsWeighed {

        @Test
        void answersTrueForAWeighedStandingEvenAtNought() {
            // A weighed nought is one the arithmetic arrived at, not the named nought of absence.
            var standing = new WeighedFactionStanding("hegemony", 0);

            assertThat(standing.isWeighed())
                .isTrue();
        }

        @Test
        void answersFalseForAPresenceOnlyStanding() {

            var standing = new PresenceOnlyFactionStanding("astral_armada");

            assertThat(standing.isWeighed())
                .isFalse();
        }
    }
}
