package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.politicalmap.base.BlocSortFixtures.ROW_COLOUR;
import static kmu.maplayers.politicalmap.base.BlocSortFixtures.buildBloc;
import static kmu.maplayers.politicalmap.base.BlocSortFixtures.listIdsInModeOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what this vocabulary declares: the frozen persistence keys a save round-trips through, which
 * number each mode reads, the direction that follows from it, and the order ties break down the
 * canonical chain. What a declared mode answers at all, and the shape the ranking is then laid out in
 * - the promoted key, the flip, the name and by-id tail - are both shared and are pinned in their own
 * suites, so a case here fails only when this vocabulary's own declaration changes. All exercised on
 * hand-built blocs, since the mode carries no Starsector types.
 */
final class DominanceSortModesTest {

    @Nested
    class Modes {

        @Test
        void modesListsEveryModeInSelectorDisplayOrder() {

            // This list is the order the sort selector stacks its rows top to bottom, so it is a drawn
            // arrangement rather than an implementation detail: name first, then the numeric metrics.
            // Pinned separately from the tie-break chain, which orders the same modes differently and
            // for a different reason.
            // It is also what catches a mode declared and then not offered: the vocabulary is written
            // out by hand rather than read off the type, since the shared size mode belongs to it
            // without being declared here.
            // Copied to the seam's own element type first: the bundle declares its modes as
            // "? extends ListSortMode", so a capture reaches the assertion and no constant can be named
            // against it directly.
            var modes = List.<ListSortMode<RankedBloc<DominanceStats>>>copyOf(
                DominanceSortModes.MODES.modes());

            assertThat(modes)
                .containsExactly(
                    DominanceSortModes.NAME,
                    DominanceSortModes.DOMINATION,
                    DominanceSortModes.PRESENCE,
                    DominanceSortModes.SCORE,
                    DominanceSortModes.MARKET_SIZE);
        }

        @Test
        void modesFallsBackToDominationAsTheDefault() {

            // Domination is what these layers are painted by, so a fresh save and any unrecognised
            // stored key open on the ranking that matches what the map shows.
            assertThat(DominanceSortModes.MODES.defaultMode())
                .isEqualTo(DominanceSortModes.DOMINATION);
        }
    }

    @Nested
    class PersistenceKey {

        @Test
        void persistenceKeyIsTheFrozenKeyForEachMode() {

            // Pinned as literals: renaming a key silently resets every save that stored that mode back
            // to the default, so a change must break this test before it ships.
            assertThat(DominanceSortModes.NAME.persistenceKey())
                .isEqualTo("name");
            assertThat(DominanceSortModes.DOMINATION.persistenceKey())
                .isEqualTo("domination");
            assertThat(DominanceSortModes.PRESENCE.persistenceKey())
                .isEqualTo("presence");
            assertThat(DominanceSortModes.SCORE.persistenceKey())
                .isEqualTo("score");
            assertThat(DominanceSortModes.MARKET_SIZE.persistenceKey())
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
            assertThat(DominanceSortModes.DOMINATION.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("5", ROW_COLOUR));
            assertThat(DominanceSortModes.PRESENCE.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("8", ROW_COLOUR));
            assertThat(DominanceSortModes.SCORE.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("40", ROW_COLOUR));
            assertThat(DominanceSortModes.MARKET_SIZE.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("12", ROW_COLOUR));
        }

        @Test
        void resolveTrailingRunsIsNoRunsUnderTheNameMode() {

            // The name mode ranks on the label, so there is no number to show and the row's value
            // column stays unfilled.
            assertThat(DominanceSortModes.NAME.resolveTrailingRuns(
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
            assertThat(DominanceSortModes.DOMINATION.defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
            assertThat(DominanceSortModes.MARKET_SIZE.defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
        }

        @Test
        void defaultDirectionIsAscendingForTheNameMode() {

            // The name mode reads A-to-Z, so its natural order runs ascending.
            assertThat(DominanceSortModes.NAME.defaultDirection())
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

            assertThat(listIdsInModeOrder(DominanceSortModes.DOMINATION, low, high))
                .containsExactly("high", "low");
        }

        @Test
        void comparatorBreaksTiesDownTheCanonicalChainInOrder() {

            // The order this vocabulary declares behind its numbers: domination, then presence, then
            // score, then market size. Each pair below is level on every metric ahead of the one it
            // differs on, so which bloc leads names the number the chain reaches next.
            assertThat(listIdsInModeOrder(
                    DominanceSortModes.DOMINATION,
                    buildBloc("lower", "A", new DominanceStats(5, 2, 0, 0)),
                    buildBloc("higher", "B", new DominanceStats(5, 7, 0, 0))))
                .containsExactly("higher", "lower");

            assertThat(listIdsInModeOrder(
                    DominanceSortModes.DOMINATION,
                    buildBloc("lower", "A", new DominanceStats(5, 5, 10, 0)),
                    buildBloc("higher", "B", new DominanceStats(5, 5, 40, 0))))
                .containsExactly("higher", "lower");

            assertThat(listIdsInModeOrder(
                    DominanceSortModes.DOMINATION,
                    buildBloc("lower", "A", new DominanceStats(5, 5, 10, 3)),
                    buildBloc("higher", "B", new DominanceStats(5, 5, 10, 9))))
                .containsExactly("higher", "lower");
        }
    }

}
