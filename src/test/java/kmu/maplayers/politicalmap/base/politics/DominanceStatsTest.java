package kmu.maplayers.politicalmap.base.politics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-bloc stats fold: a present system adds to presence, score, and market size always, and
 * to domination only when the bloc wins that system, so a bloc's whole-sector metrics build up one
 * system at a time from the empty identity. Also pins what these metrics answer about a bloc's picker
 * row, which for the held layers is "nothing to say".
 */
final class DominanceStatsTest {

    @Nested
    class AddSystem {

        @Test
        void addSystemAddsPresenceScoreAndSizeButNotDominationForANonWinner() {
            // A bloc merely holding a market in a system it does not win takes a presence and its
            // weight and size, but no domination - the count that separates "holds" from "wins".
            assertThat(DominanceStats.EMPTY.addSystem(false, 5000, 5))
                .isEqualTo(new DominanceStats(0, 1, 5000, 5));
        }

        @Test
        void addSystemCountsDominationWhenTheBlocWinsTheSystem() {
            // Winning the system adds a domination on top of the presence, weight, and size.
            assertThat(DominanceStats.EMPTY.addSystem(true, 5000, 5))
                .isEqualTo(new DominanceStats(1, 1, 5000, 5));
        }

        @Test
        void addSystemAccumulatesAcrossSuccessiveSystems() {
            // Two systems fold in one after another: a dominated system and a merely-held one leave
            // one domination, two presences, and the summed weight and size.
            assertThat(DominanceStats.EMPTY.addSystem(true, 5000, 5).addSystem(false, 3000, 3))
                .isEqualTo(new DominanceStats(1, 2, 8000, 8));
        }
    }

    @Nested
    class IsDimmed {

        @Test
        void isDimmedIsFalseWhateverTheMetricsRead() {
            // Every bloc in these stats holds a market somewhere, so every row on the held layers has
            // something to show under the metrics they are painted by and none of them recedes. Read
            // at both ends - a strong bloc and the empty identity - since the guarantee is that these
            // layers have no receding state at all rather than that some threshold is not met.
            assertThat(new DominanceStats(4, 6, 9000, 30).isDimmed())
                .isFalse();
            assertThat(DominanceStats.EMPTY.isDimmed())
                .isFalse();
        }
    }
}
