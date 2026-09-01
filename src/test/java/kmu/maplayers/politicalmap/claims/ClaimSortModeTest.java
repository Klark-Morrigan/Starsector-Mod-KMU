package kmu.maplayers.politicalmap.claims;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.maplayers.politicalmap.base.RankedBloc;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.politics.ClaimStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the claims vocabulary's surfaces: the frozen persistence keys a save round-trips through, the
 * trailing value each mode draws on a row, and the comparator that lays a claimant list out. The
 * comparator tests are the meat - they pin that each mode promotes its own metric to the primary key,
 * that the shared canonical chain breaks ties the same way behind every mode, and that a fully-level
 * pair falls back to a stable by-id order. All exercised on hand-built blocs, since the mode carries
 * no Starsector types.
 */
final class ClaimSortModeTest {

    // The tone the picker offers a mode with no colour opinion of its own. Arbitrary and distinct from
    // any engine shade, since what the value cases read off it is only that the offered tone came back
    // on the run rather than one the mode chose.
    private static final Color ROW_COLOUR = Color.ORANGE;

    @Nested
    class Modes {

        @Test
        void modesListsEveryModeInSelectorDisplayOrder() {

            // This list is the order the sort selector stacks its rows top to bottom, so it is a drawn
            // arrangement rather than an implementation detail: name first, then the two numeric
            // metrics. Pinned separately from the tie-break chain, which orders the same modes
            // differently and for a different reason.
            // Copied to the seam's own element type first: the bundle declares its modes as
            // "? extends ListSortMode", so a capture reaches the assertion and no enum constant can be
            // named against it directly.
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
        void comparatorRanksANumericModeByItsOwnMetricHighToLow() {

            var low = buildBloc("low", "Low", new ClaimStats(1, 0));
            var high = buildBloc("high", "High", new ClaimStats(9, 0));

            assertThat(listIdsSortedBy(ClaimSortMode.CLAIMS, low, high))
                .containsExactly("high", "low");
        }

        @Test
        void comparatorBreaksAMetricTieDownTheCanonicalChain() {

            // Level on claims, so the tie falls to market size next in the canonical chain: the bigger
            // claimant leads even though the mode being sorted is claims.
            var smaller = buildBloc("a", "A", new ClaimStats(5, 2));
            var bigger = buildBloc("b", "B", new ClaimStats(5, 7));

            assertThat(listIdsSortedBy(ClaimSortMode.CLAIMS, smaller, bigger))
                .containsExactly("b", "a");
        }

        @Test
        void comparatorPromotesEachModesMetricAheadOfTheCanonicalChain() {

            // Under market size, the fewer-claiming but bigger bloc leads: market size is promoted to
            // the primary key ahead of claims, which would otherwise win.
            var claimant = buildBloc("claimant", "Claimant", new ClaimStats(9, 10));
            var conglomerate = buildBloc("conglomerate", "Conglomerate", new ClaimStats(1, 50));

            assertThat(listIdsSortedBy(ClaimSortMode.MARKET_SIZE, claimant, conglomerate))
                .containsExactly("conglomerate", "claimant");
        }

        @Test
        void comparatorRanksTheNameModeAlphabetically() {

            var zeta = buildBloc("z", "Zeta", ClaimStats.EMPTY);
            var alpha = buildBloc("a", "Alpha", ClaimStats.EMPTY);

            assertThat(listIdsSortedBy(ClaimSortMode.NAME, zeta, alpha))
                .containsExactly("a", "z");
        }

        @Test
        void comparatorSinksTheClaimlessBlocsBelowEveryClaimantUnderTheDefaultMode() {
            // The list holds colony holders that claim nowhere, so where they land is what keeps it
            // readable: under the mode the view opens on they form a tail beneath every claimant, and
            // the list still opens on what the layer actually paints. A big holder claiming nothing is
            // what makes the point - it must not out-rank a small claimant on its market size.
            var claimant = buildBloc("claimant", "Claimant", new ClaimStats(1, 0));
            var holder = buildBloc("holder", "Holder", new ClaimStats(0, 90));

            assertThat(listIdsSortedBy(ClaimSortMode.CLAIMS, holder, claimant))
                .containsExactly("claimant", "holder");
        }

        @Test
        void comparatorInterleavesTheClaimlessBlocsUnderTheNameMode() {
            // Alphabetical means alphabetical: sorting by name mixes the claimless rows in among the
            // claimants rather than keeping the tail the claims mode groups them into.
            var claimant = buildBloc("b", "Beta", new ClaimStats(4, 0));
            var holder = buildBloc("a", "Alpha", new ClaimStats(0, 90));

            assertThat(listIdsSortedBy(ClaimSortMode.NAME, claimant, holder))
                .containsExactly("a", "b");
        }

        @Test
        void comparatorFlipsANumericModesPrimaryKeyWhenTheDirectionIsAscending() {

            // Ascending reverses the primary metric, so the fewer-claiming bloc leads while the metric
            // is still claims - only its direction changed.
            var low = buildBloc("low", "Low", new ClaimStats(1, 0));
            var high = buildBloc("high", "High", new ClaimStats(9, 0));

            assertThat(listIdsSortedBy(ClaimSortMode.CLAIMS, SortDirection.ASCENDING, low, high))
                .containsExactly("low", "high");
        }

        @Test
        void comparatorKeepsTheCanonicalTieBreakChainWhenThePrimaryFlips() {

            // Even with the primary metric ascending, a tie on it still breaks down the canonical chain
            // the same way: level on claims, the bigger market size leads regardless of direction.
            var smaller = buildBloc("a", "A", new ClaimStats(5, 2));
            var bigger = buildBloc("b", "B", new ClaimStats(5, 7));

            assertThat(listIdsSortedBy(
                    ClaimSortMode.CLAIMS,
                    SortDirection.ASCENDING,
                    smaller,
                    bigger))
                .containsExactly("b", "a");
        }

        @Test
        void comparatorFlipsTheNameModeToDescendingWhenTheDirectionIsDescending() {

            // The name mode's default is ascending, so descending reverses it to Z-to-A.
            var zeta = buildBloc("z", "Zeta", ClaimStats.EMPTY);
            var alpha = buildBloc("a", "Alpha", ClaimStats.EMPTY);

            assertThat(listIdsSortedBy(ClaimSortMode.NAME, SortDirection.DESCENDING, zeta, alpha))
                .containsExactly("z", "a");
        }

        @Test
        void comparatorBreaksANameTieDownTheNumericChain() {

            // Two blocs share a name, so the name mode falls through to the numeric chain: the one
            // claiming more leads.
            var weaker = buildBloc("weaker", "Same", new ClaimStats(1, 0));
            var stronger = buildBloc("stronger", "Same", new ClaimStats(8, 0));

            assertThat(listIdsSortedBy(ClaimSortMode.NAME, weaker, stronger))
                .containsExactly("stronger", "weaker");
        }

        @Test
        void comparatorFallsBackToAStableByIdOrderWhenEveryKeyIsLevel() {

            // Same name and identical stats, so every visible key is level; the by-id key gives a total
            // order so the pair holds a fixed position rather than reshuffling frame to frame.
            var second = buildBloc("bbb", "Same", new ClaimStats(3, 3));
            var first = buildBloc("aaa", "Same", new ClaimStats(3, 3));

            assertThat(listIdsSortedBy(ClaimSortMode.CLAIMS, second, first))
                .containsExactly("aaa", "bbb");
        }
    }

    // Sorts the blocs by the mode's comparator in the mode's own default direction and returns their
    // ids in the resulting order, so an assertion reads the default arrangement without spelling out
    // the direction. The direction-flip tests use the direction overload.
    @SafeVarargs
    private static List<String> listIdsSortedBy(
            ClaimSortMode mode,
            RankedBloc<ClaimStats>... blocs) {
        return listIdsSortedBy(mode, mode.defaultDirection(), blocs);
    }

    // Sorts the blocs by the mode's comparator in the given direction and returns their ids in order,
    // so an assertion reads the arrangement without the blocs' other fields getting in the way.
    @SafeVarargs
    private static List<String> listIdsSortedBy(
            ClaimSortMode mode,
            SortDirection direction,
            RankedBloc<ClaimStats>... blocs) {

        var sorted = new ArrayList<>(List.of(blocs));
        sorted.sort(mode.comparator(direction));

        var ids = new ArrayList<String>(sorted.size());
        for (var bloc : sorted) {
            ids.add(bloc.itemId());
        }
        return ids;
    }

    private static RankedBloc<ClaimStats> buildBloc(String id, String name, ClaimStats stats) {
        return new RankedBloc<>(new SelectableBloc(id, name, null), stats);
    }
}
