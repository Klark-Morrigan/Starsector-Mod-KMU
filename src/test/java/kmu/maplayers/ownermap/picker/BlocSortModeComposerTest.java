package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

import static kmu.maplayers.ownermap.picker.BlocSortFixtures.ROW_COLOUR;
import static kmu.maplayers.ownermap.picker.BlocSortFixtures.buildStandInBloc;
import static kmu.maplayers.ownermap.picker.BlocSortFixtures.listIdsInOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shape every owner-map sort vocabulary gets by declaring its numbers: where the mode's
 * own key sits, how a tie falls down the canonical chain and then to the name and the ID, which key
 * the player's direction reaches, and what a plain numeric value draws. The tail is pinned twice
 * over: as the end of that shape, and on its own, which is how a mode ranking by something no
 * vocabulary declares reaches it.
 *
 * <p>Run over a payload no view declares, so the cases say what the assembly does for any vocabulary
 * rather than what one layer's numbers happen to produce. The two real vocabularies keep their own
 * ordering and value cases, which is what shows they are laid out by this.
 */
final class BlocSortModeComposerTest {

    // The stand-in vocabulary's two numbers, named once so a case can promote either one and read the
    // same accessor back out of the chain.
    private static final ToIntFunction<HazardRating> SEVERITY = HazardRating::severity;
    private static final ToIntFunction<HazardRating> VOLATILITY = HazardRating::volatility;

    // What a mode that ranks by name names instead of a number. Spelled as the accessor type rather
    // than left a bare null at each call, so those cases read as the by-name ranking they set up.
    private static final ToIntFunction<HazardRating> NO_METRIC = null;

    // Severity first, volatility behind it - the order this stand-in vocabulary breaks ties down.
    private static final List<ToIntFunction<HazardRating>> CANONICAL_CHAIN =
        List.of(SEVERITY, VOLATILITY);

    // A caller's own order that separates nothing, so a case over the tail reads what the tail did
    // rather than what the order in front of it did.
    private static final Comparator<RankedBloc<HazardRating>> LEVEL_ORDER =
        (leftBloc, rightBloc) -> 0;

    @Nested
    class AssembleComparator {

        @Test
        void assembleComparatorRanksByThePrimaryMetricHighToLow() {

            var low = buildStandInBloc("low", "Low", 1, 0);
            var high = buildStandInBloc("high", "High", 9, 0);

            assertThat(listIdsSortedBy(SEVERITY, low, high))
                .containsExactly("high", "low");
        }

        @Test
        void assembleComparatorBreaksAPrimaryTieDownTheCanonicalChain() {

            // Level on severity, so the tie falls to volatility next in the chain: the higher one leads
            // even though the key being sorted is severity.
            var lowerVolatility = buildStandInBloc("a", "A", 5, 2);
            var higherVolatility = buildStandInBloc("b", "B", 5, 7);

            assertThat(listIdsSortedBy(SEVERITY, lowerVolatility, higherVolatility))
                .containsExactly("b", "a");
        }

        @Test
        void assembleComparatorPromotesThePrimaryMetricAheadOfTheCanonicalChain() {

            // Under volatility, the less severe but more volatile bloc leads: the primary key is
            // promoted ahead of severity, which sits first in the chain and would otherwise win.
            var severe = buildStandInBloc("severe", "Severe", 9, 1);
            var unstable = buildStandInBloc("unstable", "Unstable", 1, 8);

            assertThat(listIdsSortedBy(VOLATILITY, severe, unstable))
                .containsExactly("unstable", "severe");
        }

        @Test
        void assembleComparatorRanksByNameWhenNoMetricIsNamed() {

            var zeta = buildStandInBloc("z", "Zeta", 0, 0);
            var alpha = buildStandInBloc("a", "Alpha", 0, 0);

            assertThat(listIdsSortedBy(NO_METRIC, zeta, alpha))
                .containsExactly("a", "z");
        }

        @Test
        void assembleComparatorBreaksANameTieDownTheWholeChain() {

            // Two blocs share a name, so a by-name ranking falls through to the numeric chain and the
            // more severe one leads.
            var milder = buildStandInBloc("milder", "Same", 1, 0);
            var harsher = buildStandInBloc("harsher", "Same", 8, 0);

            assertThat(listIdsSortedBy(NO_METRIC, milder, harsher))
                .containsExactly("harsher", "milder");
        }

        @Test
        void assembleComparatorBreaksAChainLevelPairByName() {

            // Level on every number, so a numeric ranking falls to the name, which reads A-to-Z behind
            // the chain whichever number led it.
            var zeta = buildStandInBloc("z", "Zeta", 3, 3);
            var alpha = buildStandInBloc("a", "Alpha", 3, 3);

            assertThat(listIdsSortedBy(SEVERITY, zeta, alpha))
                .containsExactly("a", "z");
        }

        @Test
        void assembleComparatorFlipsThePrimaryKeyWhenTheDirectionIsTheOpposite() {

            // Ascending reverses the primary metric, so the less severe bloc leads while the key is
            // still severity - only its direction changed.
            var low = buildStandInBloc("low", "Low", 1, 0);
            var high = buildStandInBloc("high", "High", 9, 0);

            assertThat(listIdsSortedBy(SEVERITY, SortDirection.ASCENDING, low, high))
                .containsExactly("low", "high");
        }

        @Test
        void assembleComparatorKeepsTheCanonicalChainWhenThePrimaryFlips() {

            // With the primary key ascending, a tie on it still breaks down the chain the same way:
            // level on severity, the higher volatility leads regardless of direction.
            var lowerVolatility = buildStandInBloc("a", "A", 5, 2);
            var higherVolatility = buildStandInBloc("b", "B", 5, 7);

            assertThat(listIdsSortedBy(
                    SEVERITY,
                    SortDirection.ASCENDING,
                    lowerVolatility,
                    higherVolatility))
                .containsExactly("b", "a");
        }

        @Test
        void assembleComparatorFlipsTheNameKeyWhenTheDirectionIsDescending() {

            // A by-name ranking's natural order is ascending, so descending reverses it to Z-to-A.
            var zeta = buildStandInBloc("z", "Zeta", 0, 0);
            var alpha = buildStandInBloc("a", "Alpha", 0, 0);

            assertThat(listIdsSortedBy(NO_METRIC, SortDirection.DESCENDING, zeta, alpha))
                .containsExactly("z", "a");
        }

        @Test
        void assembleComparatorFallsBackToAStableByIdOrderWhenEveryKeyIsLevel() {

            // Same name and identical numbers, so every visible key is level; the by-id key gives a
            // total order so the pair holds a fixed position rather than reshuffling frame to frame.
            var second = buildStandInBloc("bbb", "Same", 3, 3);
            var first = buildStandInBloc("aaa", "Same", 3, 3);

            assertThat(listIdsSortedBy(SEVERITY, second, first))
                .containsExactly("aaa", "bbb");
        }

        @Test
        void assembleComparatorSortsAnUnlabelledBlocWithTheBlanks() {

            // A bloc no name resolved for sorts as though its label were empty rather than throwing
            // when the ranking reaches the name.
            var named = buildStandInBloc("named", "Alpha", 0, 0);
            var unlabelled = buildStandInBloc("unlabelled", null, 0, 0);

            assertThat(listIdsSortedBy(NO_METRIC, named, unlabelled))
                .containsExactly("unlabelled", "named");
        }
    }

    @Nested
    class AppendSharedTail {

        @Test
        void appendSharedTailBreaksAPairTheCallersOwnOrderLeftLevelByName() {

            // The caller's order separates nothing, so what arranges the pair is the tail alone: the
            // label, A-to-Z.
            var zeta = buildStandInBloc("z", "Zeta", 9, 9);
            var alpha = buildStandInBloc("a", "Alpha", 1, 1);

            assertThat(listIdsWithSharedTailBehind(LEVEL_ORDER, zeta, alpha))
                .containsExactly("a", "z");
        }

        @Test
        void appendSharedTailBreaksAPairTheNameLeavesLevelById() {

            // Level under the caller's order and sharing a label, so only the by-id key behind the name
            // separates them - which is what keeps such a pair from reshuffling frame to frame.
            var second = buildStandInBloc("bbb", "Same", 0, 0);
            var first = buildStandInBloc("aaa", "Same", 0, 0);

            assertThat(listIdsWithSharedTailBehind(LEVEL_ORDER, second, first))
                .containsExactly("aaa", "bbb");
        }

        @Test
        void appendSharedTailLeavesTheCallersOwnOrderLeading() {

            // The caller's order decides the pair, so the tail never reaches the name - the bloc that
            // would lead A-to-Z comes second.
            var zeta = buildStandInBloc("z", "Zeta", 9, 0);
            var alpha = buildStandInBloc("a", "Alpha", 1, 0);

            assertThat(listIdsWithSharedTailBehind(
                    Comparator.comparingInt(bloc -> -bloc.stats().severity()),
                    zeta,
                    alpha))
                .containsExactly("z", "a");
        }
    }

    @Nested
    class ResolveDefaultDirection {

        @Test
        void resolveDefaultDirectionIsDescendingForAMetric() {

            // A number leads with the bigger bloc, so its natural order runs high-to-low.
            assertThat(BlocSortModeComposer.resolveDefaultDirection(SEVERITY))
                .isEqualTo(SortDirection.DESCENDING);
        }

        @Test
        void resolveDefaultDirectionIsAscendingWhenNoMetricIsNamed() {

            // A name reads A-to-Z, so its natural order runs ascending.
            assertThat(BlocSortModeComposer.resolveDefaultDirection(NO_METRIC))
                .isEqualTo(SortDirection.ASCENDING);
        }
    }

    @Nested
    class ResolveMetricRuns {

        @Test
        void resolveMetricRunsIsTheMetricAsOneRowColouredRun() {

            // One run, in the tone the picker offered: a plain number carries no colour of its own, so
            // the value matches the name beside it.
            assertThat(BlocSortModeComposer.resolveMetricRuns(
                    SEVERITY,
                    buildStandInBloc("hazard", "Hazard", 7, 2),
                    ROW_COLOUR))
                .containsExactly(new TextSpan("7", ROW_COLOUR));
        }

        @Test
        void resolveMetricRunsIsNoRunsWhenNoMetricIsNamed() {

            // A by-name ranking has no number to show, so the row's value column stays unfilled.
            assertThat(BlocSortModeComposer.resolveMetricRuns(
                    NO_METRIC,
                    buildStandInBloc("hazard", "Hazard", 7, 2),
                    ROW_COLOUR))
                .isEmpty();
        }
    }

    // Assembles the comparator for a mode promoting this metric and reads the blocs' IDs off it, in the
    // primary key's own natural direction, so an assertion reads the default arrangement without
    // spelling out the direction. The flip cases use the direction overload.
    @SafeVarargs
    private static List<String> listIdsSortedBy(
            ToIntFunction<HazardRating> primaryMetric,
            RankedBloc<HazardRating>... blocs) {

        return listIdsSortedBy(
            primaryMetric,
            BlocSortModeComposer.resolveDefaultDirection(primaryMetric),
            blocs);
    }

    // Puts the shared tail behind a caller's own order and reads the blocs' IDs off the result, which
    // is how a mode outside the two vocabularies reaches the tail.
    @SafeVarargs
    private static List<String> listIdsWithSharedTailBehind(
            Comparator<RankedBloc<HazardRating>> callersOrder,
            RankedBloc<HazardRating>... blocs) {

        return listIdsInOrder(BlocSortModeComposer.appendSharedTail(callersOrder), blocs);
    }

    // The same in a named direction, which is what the flip cases ask for.
    @SafeVarargs
    private static List<String> listIdsSortedBy(
            ToIntFunction<HazardRating> primaryMetric,
            SortDirection direction,
            RankedBloc<HazardRating>... blocs) {

        return listIdsInOrder(
            BlocSortModeComposer.assembleComparator(primaryMetric, CANONICAL_CHAIN, direction),
            blocs);
    }
}
