package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what this vocabulary declares: the frozen persistence keys a save round-trips through, which
 * number each mode reads, the direction that follows from it, and the order ties break down the
 * canonical chain. The shape the ranking is then laid out in - the promoted key, the flip, the name
 * and by-id tail - is the shared assembly's and is pinned in its own suite, so a case here fails only
 * when this vocabulary's own declaration changes. All exercised on hand-built blocs, since the mode
 * carries no Starsector types.
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
        void comparatorRanksANumericModeByItsOwnMetric() {

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
        void comparatorBreaksTiesDownTheCanonicalChainInOrder() {

            // The order this vocabulary declares behind its numbers: domination, then presence, then
            // score, then market size. Each pair below is level on every metric ahead of the one it
            // differs on, so which bloc leads names the number the chain reaches next.
            assertThat(listIdsSortedBy(
                    DominanceSortMode.DOMINATION,
                    buildBloc("lower", "A", new DominanceStats(5, 2, 0, 0)),
                    buildBloc("higher", "B", new DominanceStats(5, 7, 0, 0))))
                .containsExactly("higher", "lower");

            assertThat(listIdsSortedBy(
                    DominanceSortMode.DOMINATION,
                    buildBloc("lower", "A", new DominanceStats(5, 5, 10, 0)),
                    buildBloc("higher", "B", new DominanceStats(5, 5, 40, 0))))
                .containsExactly("higher", "lower");

            assertThat(listIdsSortedBy(
                    DominanceSortMode.DOMINATION,
                    buildBloc("lower", "A", new DominanceStats(5, 5, 10, 3)),
                    buildBloc("higher", "B", new DominanceStats(5, 5, 10, 9))))
                .containsExactly("higher", "lower");
        }
    }

    // Sorts the blocs by the mode's comparator in the mode's own default direction and returns their
    // ids in the resulting order, so an assertion reads the arrangement without the blocs' other
    // fields getting in the way. The flipped direction belongs to the shared assembly and is pinned
    // where that lives.
    @SafeVarargs
    private static List<String> listIdsSortedBy(
            ListSortMode<RankedBloc<DominanceStats>> mode,
            RankedBloc<DominanceStats>... blocs) {

        var sorted = new ArrayList<>(List.of(blocs));
        sorted.sort(mode.comparator(mode.defaultDirection()));

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
