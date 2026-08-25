package kmu.maplayers.politicalmap.base.politics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-bloc stats fold: a present system adds to presence, score, and market size always, and
 * to domination only when the bloc wins that system, so a bloc's whole-sector metrics build up one
 * system at a time from the empty identity. Also pins what these metrics answer about a bloc's picker
 * row, which on these layers is settled by the weight alone.
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
    class IsPaintingNothing {

        @Test
        void isPaintingNothingIsTrueForABlocOfNoWeight() {
            // A bloc present through colonies the contest never weighed folds in at a score of
            // nought, so no cell is coloured for it anywhere and its row reads back.
            assertThat(new DominanceStats(0, 3, 0, 12).isPaintingNothing())
                .isTrue();
        }

        @Test
        void isPaintingNothingIsFalseForABlocCarryingAnyWeight() {
            // Any weight at all put the bloc into the contest the fills are the outcome of, so the
            // row has something to show.
            assertThat(new DominanceStats(0, 1, 1, 3).isPaintingNothing())
                .isFalse();
        }

        @Test
        void isPaintingNothingReadsTheWeightAloneAndNotTheDominationCount() {
            // The rule is the metric the layer paints by. A bloc that competes everywhere and wins
            // nowhere still reads at full strength, while a bloc holding sizeable but unweighed
            // colonies reads back however many systems it is present in.
            assertThat(new DominanceStats(0, 6, 9000, 30).isPaintingNothing())
                .isFalse();
            assertThat(new DominanceStats(0, 6, 0, 30).isPaintingNothing())
                .isTrue();
        }
    }
}
