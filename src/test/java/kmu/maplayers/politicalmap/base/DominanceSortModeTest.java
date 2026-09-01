package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the sort mode's surfaces: the frozen persistence keys a save round-trips through, the
 * trailing value each mode draws on a row, and the comparator that lays a bloc list out. The
 * comparator tests are the meat - they pin that each mode promotes its own metric to the primary
 * key, that the shared canonical chain breaks ties the same way behind every mode, and that a
 * fully-level pair falls back to a stable by-id order. All exercised on hand-built blocs, since the
 * mode carries no Starsector types.
 */
final class DominanceSortModeTest {

    // The tone the picker offers a mode with no colour opinion of its own. Arbitrary and distinct from
    // any engine shade, since what the value cases read off it is only that the offered tone came back
    // on the run rather than one the mode chose.
    private static final Color ROW_COLOUR = Color.ORANGE;

    @Nested
    class PersistenceKey {

        @Test
        void persistenceKeyIsTheFrozenKeyForEachMode() {

            // Pinned as literals: renaming a key silently resets every save that stored that mode back
            // to the default, so a change must break this test before it ships.
            assertThat(DominanceSortMode.NAME.persistenceKey())
                .isEqualTo("name");
            assertThat(DominanceSortMode.DOMINATION.persistenceKey())
                .isEqualTo("domination");
            assertThat(DominanceSortMode.PRESENCE.persistenceKey())
                .isEqualTo("presence");
            assertThat(DominanceSortMode.SCORE.persistenceKey())
                .isEqualTo("score");
            assertThat(DominanceSortMode.MARKET_SIZE.persistenceKey())
                .isEqualTo("market_size");
        }
    }

    @Nested
    class ResolveTrailingRuns {

        @Test
        void resolveTrailingRunsIsTheModesMetricAsOneRowColouredRunForANumericMode() {

            var bloc = buildBloc(
                "hegemony",
                "Hegemony",
                new DominanceStats(5, 8, 40, 12));

            // One run apiece, in the tone the picker offered: none of these metrics carries a colour
            // of its own, so each value matches the name beside it.
            assertThat(DominanceSortMode.DOMINATION.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("5", ROW_COLOUR));
            assertThat(DominanceSortMode.PRESENCE.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("8", ROW_COLOUR));
            assertThat(DominanceSortMode.SCORE.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("40", ROW_COLOUR));
            assertThat(DominanceSortMode.MARKET_SIZE.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("12", ROW_COLOUR));
        }

        @Test
        void resolveTrailingRunsIsNoRunsUnderTheNameMode() {

            // The name mode ranks on the label, so there is no number to show and the row's value
            // column stays unfilled.
            assertThat(DominanceSortMode.NAME.resolveTrailingRuns(
                    buildBloc(
                        "hegemony",
                        "Hegemony",
                        new DominanceStats(5, 8, 40, 12)),
                    ROW_COLOUR))
                .isEmpty();
        }
    }

    @Nested
    class DefaultDirection {

        @Test
        void defaultDirectionIsDescendingForANumericMode() {

            // A numeric mode leads with the bigger bloc, so its natural order runs high-to-low.
            assertThat(DominanceSortMode.DOMINATION.defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
            assertThat(DominanceSortMode.MARKET_SIZE.defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
        }

        @Test
        void defaultDirectionIsAscendingForTheNameMode() {

            // The name mode reads A-to-Z, so its natural order runs ascending.
            assertThat(DominanceSortMode.NAME.defaultDirection())
                .isEqualTo(SortDirection.ASCENDING);
        }
    }

    @Nested
    class Comparator {

        @Test
        void comparatorRanksANumericModeByItsOwnMetricHighToLow() {

            var low = buildBloc(
                "low",
                "Low",
                new DominanceStats(1, 0, 0, 0));
            var high = buildBloc(
                "high",
                "High",
                new DominanceStats(9, 0, 0, 0));

            assertThat(listIdsSortedBy(DominanceSortMode.DOMINATION, low, high))
                .containsExactly("high", "low");
        }

        @Test
        void comparatorBreaksAMetricTieDownTheCanonicalChain() {

            // Level on domination, so the tie falls to presence next in the canonical chain: the higher
            // presence leads even though the mode being sorted is domination.
            var lowerPresence = buildBloc(
                "a",
                "A",
                new DominanceStats(5, 2, 0, 0));
            var higherPresence = buildBloc(
                "b",
                "B",
                new DominanceStats(5, 7, 0, 0));

            assertThat(listIdsSortedBy(DominanceSortMode.DOMINATION, lowerPresence, higherPresence))
                .containsExactly("b", "a");
        }

        @Test
        void comparatorPromotesEachModesMetricAheadOfTheCanonicalChain() {

            // Under score, the lower-dominating but higher-scoring bloc leads: score is promoted to the
            // primary key ahead of domination, which would otherwise win.
            var dominant = buildBloc(
                "dominant",
                "Dominant",
                new DominanceStats(9, 0, 10, 0));
            var scorer = buildBloc(
                "scorer",
                "Scorer",
                new DominanceStats(1, 0, 50, 0));

            assertThat(listIdsSortedBy(DominanceSortMode.SCORE, dominant, scorer))
                .containsExactly("scorer", "dominant");
        }

        @Test
        void comparatorRanksTheNameModeAlphabetically() {

            var zeta = buildBloc("z", "Zeta", DominanceStats.EMPTY);
            var alpha = buildBloc("a", "Alpha", DominanceStats.EMPTY);

            assertThat(listIdsSortedBy(DominanceSortMode.NAME, zeta, alpha))
                .containsExactly("a", "z");
        }

        @Test
        void comparatorFlipsANumericModesPrimaryKeyWhenTheDirectionIsAscending() {

            // Ascending reverses the primary metric, so the lower-dominating bloc leads while the
            // metric is still domination - only its direction changed.
            var low = buildBloc(
                "low",
                "Low",
                new DominanceStats(1, 0, 0, 0));
            var high = buildBloc(
                "high",
                "High",
                new DominanceStats(9, 0, 0, 0));

            assertThat(listIdsSortedBy(DominanceSortMode.DOMINATION, SortDirection.ASCENDING, low, high))
                .containsExactly("low", "high");
        }

        @Test
        void comparatorKeepsTheCanonicalTieBreakChainWhenThePrimaryFlips() {

            // Even with the primary metric ascending, a tie on it still breaks down the canonical chain
            // the same way: level on domination, the higher presence leads regardless of direction.
            var lowerPresence = buildBloc(
                "a",
                "A",
                new DominanceStats(5, 2, 0, 0));
            var higherPresence = buildBloc(
                "b",
                "B",
                new DominanceStats(5, 7, 0, 0));

            assertThat(listIdsSortedBy(
                    DominanceSortMode.DOMINATION,
                    SortDirection.ASCENDING,
                    lowerPresence,
                    higherPresence))
                .containsExactly("b", "a");
        }

        @Test
        void comparatorFlipsTheNameModeToDescendingWhenTheDirectionIsDescending() {

            // The name mode's default is ascending, so descending reverses it to Z-to-A.
            var zeta = buildBloc("z", "Zeta", DominanceStats.EMPTY);
            var alpha = buildBloc("a", "Alpha", DominanceStats.EMPTY);

            assertThat(listIdsSortedBy(DominanceSortMode.NAME, SortDirection.DESCENDING, zeta, alpha))
                .containsExactly("z", "a");
        }

        @Test
        void comparatorBreaksANameTieDownTheNumericChain() {

            // Two blocs share a name, so the name mode falls through to the numeric chain: the higher
            // dominating one leads.
            var weaker = buildBloc(
                "weaker",
                "Same",
                new DominanceStats(1, 0, 0, 0));
            var stronger = buildBloc(
                "stronger",
                "Same",
                new DominanceStats(8, 0, 0, 0));

            assertThat(listIdsSortedBy(DominanceSortMode.NAME, weaker, stronger))
                .containsExactly("stronger", "weaker");
        }

        @Test
        void comparatorFallsBackToAStableByIdOrderWhenEveryKeyIsLevel() {

            // Same name and identical stats, so every visible key is level; the by-id key gives a total
            // order so the pair holds a fixed position rather than reshuffling frame to frame.
            var second = buildBloc(
                "bbb",
                "Same",
                new DominanceStats(3, 3, 3, 3));
            var first = buildBloc(
                "aaa",
                "Same",
                new DominanceStats(3, 3, 3, 3));

            assertThat(listIdsSortedBy(DominanceSortMode.SCORE, second, first))
                .containsExactly("aaa", "bbb");
        }
    }

    // Sorts the blocs by the mode's comparator in the mode's own default direction and returns their
    // ids in the resulting order, so an assertion reads the default arrangement without spelling out
    // the direction. The direction-flip tests use the direction overload.
    @SafeVarargs
    private static List<String> listIdsSortedBy(
            DominanceSortMode mode,
            RankedBloc<DominanceStats>... blocs) {
        return listIdsSortedBy(mode, mode.defaultDirection(), blocs);
    }

    // Sorts the blocs by the mode's comparator in the given direction and returns their ids in order,
    // so an assertion reads the arrangement without the blocs' other fields getting in the way.
    @SafeVarargs
    private static List<String> listIdsSortedBy(
            DominanceSortMode mode,
            SortDirection direction,
            RankedBloc<DominanceStats>... blocs) {

        var sorted = new ArrayList<>(List.of(blocs));
        sorted.sort(mode.comparator(direction));

        var ids = new ArrayList<String>(sorted.size());
        for (var bloc : sorted) {
            ids.add(bloc.itemId());
        }
        return ids;
    }

    private static RankedBloc<DominanceStats> buildBloc(
            String id,
            String name,
            DominanceStats stats) {
        return new RankedBloc<>(new SelectableBloc(id, name, null), stats);
    }
}
