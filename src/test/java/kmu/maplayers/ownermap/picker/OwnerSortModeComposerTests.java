package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.function.ToIntFunction;

import static kmu.maplayers.ownermap.picker.OwnerSortFixtures.ROW_COLOUR;
import static kmu.maplayers.ownermap.picker.OwnerSortFixtures.buildStandInOwner;
import static kmu.maplayers.ownermap.picker.OwnerSortFixtures.listIdsInOrder;

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
final class OwnerSortModeComposerTests {

    // The stand-in vocabulary's two numbers, named once so a case can promote either one and read the
    // same accessor back out of the chain.
    private static final ToIntFunction<HazardRatingFake> SEVERITY = HazardRatingFake::severity;
    private static final ToIntFunction<HazardRatingFake> VOLATILITY = HazardRatingFake::volatility;

    // What a mode that ranks by name names instead of a number. Spelled as the accessor type rather
    // than left a bare null at each call, so those cases read as the by-name ranking they set up.
    private static final ToIntFunction<HazardRatingFake> NO_METRIC = null;

    // Severity first, volatility behind it - the order this stand-in vocabulary breaks ties down.
    private static final List<ToIntFunction<HazardRatingFake>> CANONICAL_CHAIN =
        List.of(SEVERITY, VOLATILITY);

    // A caller's own order that separates nothing, so a case over the tail reads what the tail did
    // rather than what the order in front of it did.
    private static final Comparator<RankedOwner<HazardRatingFake>> LEVEL_ORDER =
        (leftOwner, rightOwner) -> 0;

    @Nested
    class AssembleComparator {

        @Test
        void ranksByThePrimaryMetricHighToLow() {

            var low = buildStandInOwner("low", "Low", 1, 0);
            var high = buildStandInOwner("high", "High", 9, 0);

            assertThat(listIdsSortedBy(SEVERITY, low, high))
                .containsExactly("high", "low");
        }

        @Test
        void breaksAPrimaryTieDownTheCanonicalChain() {

            // Level on severity, so the tie falls to volatility next in the chain: the higher one leads
            // even though the key being sorted is severity.
            var lowerVolatility = buildStandInOwner("a", "A", 5, 2);
            var higherVolatility = buildStandInOwner("b", "B", 5, 7);

            assertThat(listIdsSortedBy(SEVERITY, lowerVolatility, higherVolatility))
                .containsExactly("b", "a");
        }

        @Test
        void promotesThePrimaryMetricAheadOfTheCanonicalChain() {

            // Under volatility, the less severe but more volatile owner leads: the primary key is
            // promoted ahead of severity, which sits first in the chain and would otherwise win.
            var severe = buildStandInOwner("severe", "Severe", 9, 1);
            var unstable = buildStandInOwner("unstable", "Unstable", 1, 8);

            assertThat(listIdsSortedBy(VOLATILITY, severe, unstable))
                .containsExactly("unstable", "severe");
        }

        @Test
        void ranksByNameWhenNoMetricIsNamed() {

            var zeta = buildStandInOwner("z", "Zeta", 0, 0);
            var alpha = buildStandInOwner("a", "Alpha", 0, 0);

            assertThat(listIdsSortedBy(NO_METRIC, zeta, alpha))
                .containsExactly("a", "z");
        }

        @Test
        void breaksANameTieDownTheWholeChain() {

            // Two owners share a name, so a by-name ranking falls through to the numeric chain and the
            // more severe one leads.
            var milder = buildStandInOwner("milder", "Same", 1, 0);
            var harsher = buildStandInOwner("harsher", "Same", 8, 0);

            assertThat(listIdsSortedBy(NO_METRIC, milder, harsher))
                .containsExactly("harsher", "milder");
        }

        @Test
        void breaksAChainLevelPairByName() {

            // Level on every number, so a numeric ranking falls to the name, which reads A-to-Z behind
            // the chain whichever number led it.
            var zeta = buildStandInOwner("z", "Zeta", 3, 3);
            var alpha = buildStandInOwner("a", "Alpha", 3, 3);

            assertThat(listIdsSortedBy(SEVERITY, zeta, alpha))
                .containsExactly("a", "z");
        }

        @Test
        void flipsThePrimaryKeyWhenTheDirectionIsTheOpposite() {

            // Ascending reverses the primary metric, so the less severe owner leads while the key is
            // still severity - only its direction changed.
            var low = buildStandInOwner("low", "Low", 1, 0);
            var high = buildStandInOwner("high", "High", 9, 0);

            assertThat(listIdsSortedBy(SEVERITY, SortDirection.ASCENDING, low, high))
                .containsExactly("low", "high");
        }

        @Test
        void keepsTheCanonicalChainWhenThePrimaryFlips() {

            // With the primary key ascending, a tie on it still breaks down the chain the same way:
            // level on severity, the higher volatility leads regardless of direction.
            var lowerVolatility = buildStandInOwner("a", "A", 5, 2);
            var higherVolatility = buildStandInOwner("b", "B", 5, 7);

            assertThat(listIdsSortedBy(
                    SEVERITY,
                    SortDirection.ASCENDING,
                    lowerVolatility,
                    higherVolatility))
                .containsExactly("b", "a");
        }

        @Test
        void flipsTheNameKeyWhenTheDirectionIsDescending() {

            // A by-name ranking's natural order is ascending, so descending reverses it to Z-to-A.
            var zeta = buildStandInOwner("z", "Zeta", 0, 0);
            var alpha = buildStandInOwner("a", "Alpha", 0, 0);

            assertThat(listIdsSortedBy(NO_METRIC, SortDirection.DESCENDING, zeta, alpha))
                .containsExactly("z", "a");
        }

        @Test
        void fallsBackToAStableByIdOrderWhenEveryKeyIsLevel() {

            // Same name and identical numbers, so every visible key is level; the by-id key gives a
            // total order so the pair holds a fixed position rather than reshuffling frame to frame.
            var second = buildStandInOwner("bbb", "Same", 3, 3);
            var first = buildStandInOwner("aaa", "Same", 3, 3);

            assertThat(listIdsSortedBy(SEVERITY, second, first))
                .containsExactly("aaa", "bbb");
        }

        @Test
        void sortsAnUnlabelledOwnerWithTheBlanks() {

            // An owner no name resolved for sorts as though its label were empty rather than throwing
            // when the ranking reaches the name.
            var named = buildStandInOwner("named", "Alpha", 0, 0);
            var unlabelled = buildStandInOwner("unlabelled", null, 0, 0);

            assertThat(listIdsSortedBy(NO_METRIC, named, unlabelled))
                .containsExactly("unlabelled", "named");
        }
    }

    @Nested
    class AppendSharedTail {

        @Test
        void breaksAPairTheCallersOwnOrderLeftLevelByName() {

            // The caller's order separates nothing, so what arranges the pair is the tail alone: the
            // label, A-to-Z.
            var zeta = buildStandInOwner("z", "Zeta", 9, 9);
            var alpha = buildStandInOwner("a", "Alpha", 1, 1);

            assertThat(listIdsWithSharedTailBehind(LEVEL_ORDER, zeta, alpha))
                .containsExactly("a", "z");
        }

        @Test
        void breaksAPairTheNameLeavesLevelById() {

            // Level under the caller's order and sharing a label, so only the by-id key behind the name
            // separates them - which is what keeps such a pair from reshuffling frame to frame.
            var second = buildStandInOwner("bbb", "Same", 0, 0);
            var first = buildStandInOwner("aaa", "Same", 0, 0);

            assertThat(listIdsWithSharedTailBehind(LEVEL_ORDER, second, first))
                .containsExactly("aaa", "bbb");
        }

        @Test
        void leavesTheCallersOwnOrderLeading() {

            // The caller's order decides the pair, so the tail never reaches the name - the owner that
            // would lead A-to-Z comes second.
            var zeta = buildStandInOwner("z", "Zeta", 9, 0);
            var alpha = buildStandInOwner("a", "Alpha", 1, 0);

            assertThat(listIdsWithSharedTailBehind(
                    Comparator.comparingInt(owner -> -owner.stats().severity()),
                    zeta,
                    alpha))
                .containsExactly("z", "a");
        }
    }

    @Nested
    class ResolveDefaultDirection {

        @Test
        void isDescendingForAMetric() {

            // A number leads with the bigger owner, so its natural order runs high-to-low.
            assertThat(OwnerSortModeComposer.resolveDefaultDirection(SEVERITY))
                .isEqualTo(SortDirection.DESCENDING);
        }

        @Test
        void isAscendingWhenNoMetricIsNamed() {

            // A name reads A-to-Z, so its natural order runs ascending.
            assertThat(OwnerSortModeComposer.resolveDefaultDirection(NO_METRIC))
                .isEqualTo(SortDirection.ASCENDING);
        }
    }

    @Nested
    class ResolveMetricRuns {

        @Test
        void isTheMetricAsOneRowColouredRun() {

            // One run, in the tone the picker offered: a plain number carries no colour of its own, so
            // the value matches the name beside it.
            assertThat(OwnerSortModeComposer.resolveMetricRuns(
                    SEVERITY,
                    buildStandInOwner("hazard", "Hazard", 7, 2),
                    ROW_COLOUR))
                .containsExactly(new TextSpan("7", ROW_COLOUR));
        }

        @Test
        void isNoRunsWhenNoMetricIsNamed() {

            // A by-name ranking has no number to show, so the row's value column stays unfilled.
            assertThat(OwnerSortModeComposer.resolveMetricRuns(
                    NO_METRIC,
                    buildStandInOwner("hazard", "Hazard", 7, 2),
                    ROW_COLOUR))
                .isEmpty();
        }
    }

    // Assembles the comparator for a mode promoting this metric and reads the owners' IDs off it, in the
    // primary key's own natural direction, so an assertion reads the default arrangement without
    // spelling out the direction. The flip cases use the direction overload.
    @SafeVarargs
    private static List<String> listIdsSortedBy(
            ToIntFunction<HazardRatingFake> primaryMetric,
            RankedOwner<HazardRatingFake>... owners) {

        return listIdsSortedBy(
            primaryMetric,
            OwnerSortModeComposer.resolveDefaultDirection(primaryMetric),
            owners);
    }

    // Puts the shared tail behind a caller's own order and reads the owners' IDs off the result, which
    // is how a mode outside the two vocabularies reaches the tail.
    @SafeVarargs
    private static List<String> listIdsWithSharedTailBehind(
            Comparator<RankedOwner<HazardRatingFake>> callersOrder,
            RankedOwner<HazardRatingFake>... owners) {

        return listIdsInOrder(OwnerSortModeComposer.appendSharedTail(callersOrder), owners);
    }

    // The same in a named direction, which is what the flip cases ask for.
    @SafeVarargs
    private static List<String> listIdsSortedBy(
            ToIntFunction<HazardRatingFake> primaryMetric,
            SortDirection direction,
            RankedOwner<HazardRatingFake>... owners) {

        return listIdsInOrder(
            OwnerSortModeComposer.assembleComparator(primaryMetric, CANONICAL_CHAIN, direction),
            owners);
    }
}
