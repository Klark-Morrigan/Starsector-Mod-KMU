package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static kmu.maplayers.ownermap.picker.OwnerSortFixtures.ROW_COLOUR;
import static kmu.maplayers.ownermap.picker.OwnerSortFixtures.buildStandInOwner;
import static kmu.maplayers.ownermap.picker.OwnerSortFixtures.listIdsInOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the owner map adds over KMLib's list comparators: a vocabulary's metrics are read off
 * the owner's stats record, a mode naming no metric still ranks by name once that reading is in
 * between, and a plain numeric value draws as one run in the row's tone. The comparator shape itself
 * is KMLib's and is pinned there.
 *
 * <p>Run over a payload no view declares, so the cases say what the reading does for any vocabulary
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

    @Nested
    class AssembleComparator {

        @Test
        void ranksByThePrimaryMetricReadOffTheStats() {

            var low = buildStandInOwner("low", "Low", 1, 0);
            var high = buildStandInOwner("high", "High", 9, 0);

            assertThat(listIdsSortedBy(SEVERITY, low, high))
                .containsExactly("high", "low");
        }

        @Test
        void breaksAPrimaryTieDownTheChainReadOffTheStats() {

            // Level on severity, so the tie falls to volatility next in the chain: the higher one leads
            // even though the key being sorted is severity.
            var lowerVolatility = buildStandInOwner("a", "A", 5, 2);
            var higherVolatility = buildStandInOwner("b", "B", 5, 7);

            assertThat(listIdsSortedBy(SEVERITY, lowerVolatility, higherVolatility))
                .containsExactly("b", "a");
        }

        @Test
        void ranksByNameWhenNoMetricIsNamed() {

            // The stats reading keeps a missing metric missing, so the mode still leads with the name
            // rather than with a metric that reads nothing.
            var zeta = buildStandInOwner("z", "Zeta", 9, 0);
            var alpha = buildStandInOwner("a", "Alpha", 1, 0);

            assertThat(listIdsSortedBy(NO_METRIC, zeta, alpha))
                .containsExactly("a", "z");
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

    // Assembles the comparator for a mode promoting this metric and reads the owners' IDs off it, in
    // the primary key's own natural direction, so an assertion reads the default arrangement without
    // spelling out the direction.
    @SafeVarargs
    private static List<String> listIdsSortedBy(
            ToIntFunction<HazardRatingFake> primaryMetric,
            RankedOwner<HazardRatingFake>... owners) {

        return listIdsInOrder(
            OwnerSortModeComposer.assembleComparator(
                primaryMetric,
                CANONICAL_CHAIN,
                OwnerSortModeComposer.resolveDefaultDirection(primaryMetric)),
            owners);
    }
}
