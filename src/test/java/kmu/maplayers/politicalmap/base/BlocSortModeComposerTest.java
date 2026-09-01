package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shape every political-map sort vocabulary gets by declaring its numbers: where the mode's
 * own key sits, how a tie falls down the canonical chain and then to the name and the id, which key
 * the player's direction reaches, and what a plain numeric value draws.
 *
 * <p>Run over a payload no view declares, so the cases say what the assembly does for any vocabulary
 * rather than what one layer's numbers happen to produce. The two real vocabularies keep their own
 * ordering and value cases, which is what shows they are laid out by this.
 */
final class BlocSortModeComposerTest {

    // The tone the picker offers a mode with no colour opinion of its own. Arbitrary and distinct from
    // any engine shade, since what the value cases read off it is only that the offered tone came back
    // on the run rather than one the assembly chose.
    private static final Color ROW_COLOUR = Color.ORANGE;

    // The stand-in vocabulary's two numbers, held as fields for the reason a vocabulary holds its own:
    // the accessor a mode ranks by has to be the very one sitting in the chain for its slot to be
    // recognised there.
    private static final ToIntFunction<HazardRating> SEVERITY = HazardRating::severity;
    private static final ToIntFunction<HazardRating> VOLATILITY = HazardRating::volatility;

    // What a mode that ranks by name names instead of a number. Spelled as the accessor type rather
    // than left a bare null at each call, so those cases read as the by-name ranking they set up.
    private static final ToIntFunction<HazardRating> NO_METRIC = null;

    // Severity first, volatility behind it - the order this stand-in vocabulary breaks ties down.
    private static final List<ToIntFunction<HazardRating>> CANONICAL_CHAIN =
        List.of(SEVERITY, VOLATILITY);

    @Nested
    class AssembleComparator {

        @Test
        void assembleComparatorRanksByThePrimaryMetricHighToLow() {

            var low = buildBloc("low", "Low", 1, 0);
            var high = buildBloc("high", "High", 9, 0);

            assertThat(listIdsSortedBy(SEVERITY, low, high))
                .containsExactly("high", "low");
        }

        @Test
        void assembleComparatorBreaksAPrimaryTieDownTheCanonicalChain() {

            // Level on severity, so the tie falls to volatility next in the chain: the higher one leads
            // even though the key being sorted is severity.
            var lowerVolatility = buildBloc("a", "A", 5, 2);
            var higherVolatility = buildBloc("b", "B", 5, 7);

            assertThat(listIdsSortedBy(SEVERITY, lowerVolatility, higherVolatility))
                .containsExactly("b", "a");
        }

        @Test
        void assembleComparatorPromotesThePrimaryMetricAheadOfTheCanonicalChain() {

            // Under volatility, the less severe but more volatile bloc leads: the primary key is
            // promoted ahead of severity, which sits first in the chain and would otherwise win.
            var severe = buildBloc("severe", "Severe", 9, 1);
            var unstable = buildBloc("unstable", "Unstable", 1, 8);

            assertThat(listIdsSortedBy(VOLATILITY, severe, unstable))
                .containsExactly("unstable", "severe");
        }

        @Test
        void assembleComparatorRanksByNameWhenNoMetricIsNamed() {

            var zeta = buildBloc("z", "Zeta", 0, 0);
            var alpha = buildBloc("a", "Alpha", 0, 0);

            assertThat(listIdsSortedBy(NO_METRIC, zeta, alpha))
                .containsExactly("a", "z");
        }

        @Test
        void assembleComparatorBreaksANameTieDownTheWholeChain() {

            // Two blocs share a name, so a by-name ranking falls through to the numeric chain entire -
            // no slot of it was promoted away - and the more severe one leads.
            var milder = buildBloc("milder", "Same", 1, 0);
            var harsher = buildBloc("harsher", "Same", 8, 0);

            assertThat(listIdsSortedBy(NO_METRIC, milder, harsher))
                .containsExactly("harsher", "milder");
        }

        @Test
        void assembleComparatorBreaksAChainLevelPairByName() {

            // Level on every number, so a numeric ranking falls to the name, which reads A-to-Z behind
            // the chain whichever number led it.
            var zeta = buildBloc("z", "Zeta", 3, 3);
            var alpha = buildBloc("a", "Alpha", 3, 3);

            assertThat(listIdsSortedBy(SEVERITY, zeta, alpha))
                .containsExactly("a", "z");
        }

        @Test
        void assembleComparatorFlipsThePrimaryKeyWhenTheDirectionIsTheOpposite() {

            // Ascending reverses the primary metric, so the less severe bloc leads while the key is
            // still severity - only its direction changed.
            var low = buildBloc("low", "Low", 1, 0);
            var high = buildBloc("high", "High", 9, 0);

            assertThat(listIdsSortedBy(SEVERITY, SortDirection.ASCENDING, low, high))
                .containsExactly("low", "high");
        }

        @Test
        void assembleComparatorKeepsTheCanonicalChainWhenThePrimaryFlips() {

            // With the primary key ascending, a tie on it still breaks down the chain the same way:
            // level on severity, the higher volatility leads regardless of direction.
            var lowerVolatility = buildBloc("a", "A", 5, 2);
            var higherVolatility = buildBloc("b", "B", 5, 7);

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
            var zeta = buildBloc("z", "Zeta", 0, 0);
            var alpha = buildBloc("a", "Alpha", 0, 0);

            assertThat(listIdsSortedBy(NO_METRIC, SortDirection.DESCENDING, zeta, alpha))
                .containsExactly("z", "a");
        }

        @Test
        void assembleComparatorFallsBackToAStableByIdOrderWhenEveryKeyIsLevel() {

            // Same name and identical numbers, so every visible key is level; the by-id key gives a
            // total order so the pair holds a fixed position rather than reshuffling frame to frame.
            var second = buildBloc("bbb", "Same", 3, 3);
            var first = buildBloc("aaa", "Same", 3, 3);

            assertThat(listIdsSortedBy(SEVERITY, second, first))
                .containsExactly("aaa", "bbb");
        }

        @Test
        void assembleComparatorSortsAnUnlabelledBlocWithTheBlanks() {

            // A bloc no name resolved for sorts as though its label were empty rather than throwing
            // when the ranking reaches the name.
            var named = buildBloc("named", "Alpha", 0, 0);
            var unlabelled = new RankedBloc<>(
                new SelectableBloc("unlabelled", null, null),
                new HazardRating(0, 0));

            assertThat(listIdsSortedBy(NO_METRIC, named, unlabelled))
                .containsExactly("unlabelled", "named");
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
                    buildBloc("hazard", "Hazard", 7, 2),
                    ROW_COLOUR))
                .containsExactly(new TextSpan("7", ROW_COLOUR));
        }

        @Test
        void resolveMetricRunsIsNoRunsWhenNoMetricIsNamed() {

            // A by-name ranking has no number to show, so the row's value column stays unfilled.
            assertThat(BlocSortModeComposer.resolveMetricRuns(
                    NO_METRIC,
                    buildBloc("hazard", "Hazard", 7, 2),
                    ROW_COLOUR))
                .isEmpty();
        }
    }

    // Sorts the blocs by the assembled comparator in the primary key's own natural direction and
    // returns their ids in the resulting order, so an assertion reads the default arrangement without
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

    // Sorts the blocs by the assembled comparator in the given direction and returns their ids in
    // order, so an assertion reads the arrangement without the blocs' other fields getting in the way.
    @SafeVarargs
    private static List<String> listIdsSortedBy(
            ToIntFunction<HazardRating> primaryMetric,
            SortDirection direction,
            RankedBloc<HazardRating>... blocs) {

        var sorted = new ArrayList<>(List.of(blocs));
        sorted.sort(BlocSortModeComposer.assembleComparator(
            primaryMetric,
            CANONICAL_CHAIN,
            direction));

        var ids = new ArrayList<String>(sorted.size());
        for (var bloc : sorted) {
            ids.add(bloc.itemId());
        }
        return ids;
    }

    private static RankedBloc<HazardRating> buildBloc(
            String id,
            String name,
            int severity,
            int volatility) {

        return new RankedBloc<>(
            new SelectableBloc(id, name, null),
            new HazardRating(severity, volatility));
    }
}
