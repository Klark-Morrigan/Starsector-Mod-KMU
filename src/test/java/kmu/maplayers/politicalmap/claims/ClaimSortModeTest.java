package kmu.maplayers.politicalmap.claims;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.politics.ClaimStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.politicalmap.base.BlocSortFixtures.ROW_COLOUR;
import static kmu.maplayers.politicalmap.base.BlocSortFixtures.buildBloc;
import static kmu.maplayers.politicalmap.base.BlocSortFixtures.listIdsInModeOrder;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what this vocabulary declares: the frozen persistence keys a save round-trips through, which
 * number each mode reads, the direction that follows from it, the order ties break down the canonical
 * chain, and where the blocs that claim nowhere land. The shape the ranking is then laid out in - the
 * promoted key, the flip, the name and by-id tail - is the shared assembly's and is pinned in its own
 * suite, so a case here fails only when this vocabulary's own declaration changes. All exercised on
 * hand-built blocs, since the mode carries no Starsector types.
 */
final class ClaimSortModeTest {

    @Nested
    class Modes {

        @Test
        void modesListsEveryModeInSelectorDisplayOrder() {

            // This list is the order the sort selector stacks its rows top to bottom, so it is a drawn
            // arrangement rather than an implementation detail: name first, then the two numeric
            // metrics. Pinned separately from the tie-break chain, which orders the same modes
            // differently and for a different reason.
            // It is also what catches a mode declared and then not offered: the vocabulary is written
            // out by hand rather than read off the type, since the shared size mode belongs to it
            // without being declared here.
            // Copied to the seam's own element type first: the bundle declares its modes as
            // "? extends ListSortMode", so a capture reaches the assertion and no constant can be named
            // against it directly.
            var modes = List.<ListSortMode<RankedBloc<ClaimStats>>>copyOf(ClaimSortMode.MODES.modes());

            assertThat(modes)
                .containsExactly(
                    ClaimSortMode.NAME,
                    ClaimSortMode.CLAIMS,
                    ClaimSortMode.MARKET_SIZE);
        }

        @Test
        void modesFallsBackToClaimsAsTheDefault() {

            // Claims is the metric this layer is actually painted by, so a fresh save and any
            // unrecognised stored key open on the ranking that matches what the map shows - rather
            // than on the market-size ranking, which describes a claimant but not its territory.
            assertThat(ClaimSortMode.MODES.defaultMode())
                .isEqualTo(ClaimSortMode.CLAIMS);
        }
    }

    @Nested
    class PersistenceKey {

        @Test
        void persistenceKeyIsTheFrozenKeyForEachMode() {

            // Pinned as literals: renaming a key silently resets every save that stored that mode back
            // to the default, so a change must break this test before it ships.
            assertThat(ClaimSortMode.NAME.persistenceKey())
                .isEqualTo("name");
            assertThat(ClaimSortMode.CLAIMS.persistenceKey())
                .isEqualTo("claims");
            assertThat(ClaimSortMode.MARKET_SIZE.persistenceKey())
                .isEqualTo("market_size");
        }
    }

    @Nested
    class ResolveTrailingRuns {

        @Test
        void resolveTrailingRunsIsTheModesMetricAsOneRowColouredRunForANumericMode() {

            var bloc = buildBloc("hegemony", "Hegemony", new ClaimStats(4, 26));

            // One run apiece, in the tone the picker offered: neither metric carries a colour of its
            // own, so each value matches the name beside it.
            assertThat(ClaimSortMode.CLAIMS.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("4", ROW_COLOUR));
            assertThat(ClaimSortMode.MARKET_SIZE.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("26", ROW_COLOUR));
        }

        @Test
        void resolveTrailingRunsIsNoRunsUnderTheNameMode() {

            // The name mode ranks on the label, so there is no number to show and the row's value
            // column stays unfilled.
            assertThat(ClaimSortMode.NAME.resolveTrailingRuns(
                    buildBloc("hegemony", "Hegemony", new ClaimStats(4, 26)),
                    ROW_COLOUR))
                .isEmpty();
        }
    }

    @Nested
    class DefaultDirection {

        @Test
        void defaultDirectionIsDescendingForANumericMode() {

            // A numeric mode leads with the bigger claimant, so its natural order runs high-to-low.
            assertThat(ClaimSortMode.CLAIMS.defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
            assertThat(ClaimSortMode.MARKET_SIZE.defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
        }

        @Test
        void defaultDirectionIsAscendingForTheNameMode() {

            // The name mode reads A-to-Z, so its natural order runs ascending.
            assertThat(ClaimSortMode.NAME.defaultDirection())
                .isEqualTo(SortDirection.ASCENDING);
        }
    }

    @Nested
    class Comparator {

        @Test
        void comparatorRanksANumericModeByItsOwnMetric() {

            var low = buildBloc("low", "Low", new ClaimStats(1, 0));
            var high = buildBloc("high", "High", new ClaimStats(9, 0));

            assertThat(listIdsInModeOrder(ClaimSortMode.CLAIMS, low, high))
                .containsExactly("high", "low");
        }

        @Test
        void comparatorBreaksTiesDownTheCanonicalChainInOrder() {

            // The order this vocabulary declares behind its numbers: claims, then market size. The pair
            // is level on claims, so which bloc leads names the number the chain reaches next.
            var smaller = buildBloc("a", "A", new ClaimStats(5, 2));
            var bigger = buildBloc("b", "B", new ClaimStats(5, 7));

            assertThat(listIdsInModeOrder(ClaimSortMode.CLAIMS, smaller, bigger))
                .containsExactly("b", "a");
        }

        @Test
        void comparatorSinksTheClaimlessBlocsBelowEveryClaimantUnderTheDefaultMode() {
            // The list holds colony holders that claim nowhere, so where they land is what keeps it
            // readable: under the mode the view opens on they form a tail beneath every claimant, and
            // the list still opens on what the layer actually paints. A big holder claiming nothing is
            // what makes the point - it must not out-rank a small claimant on its market size.
            var claimant = buildBloc("claimant", "Claimant", new ClaimStats(1, 0));
            var holder = buildBloc("holder", "Holder", new ClaimStats(0, 90));

            assertThat(listIdsInModeOrder(ClaimSortMode.CLAIMS, holder, claimant))
                .containsExactly("claimant", "holder");
        }

        @Test
        void comparatorInterleavesTheClaimlessBlocsUnderTheNameMode() {
            // Alphabetical means alphabetical: sorting by name mixes the claimless rows in among the
            // claimants rather than keeping the tail the claims mode groups them into.
            var claimant = buildBloc("b", "Beta", new ClaimStats(4, 0));
            var holder = buildBloc("a", "Alpha", new ClaimStats(0, 90));

            assertThat(listIdsInModeOrder(ClaimSortMode.NAME, claimant, holder))
                .containsExactly("a", "b");
        }
    }

}
