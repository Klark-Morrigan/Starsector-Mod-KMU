package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static kmu.maplayers.ownermap.picker.OwnerSortFixtures.ROW_COLOUR;
import static kmu.maplayers.ownermap.picker.OwnerSortFixtures.buildStandInOwner;
import static kmu.maplayers.ownermap.picker.OwnerSortFixtures.listIdsInModeOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what one declared mode is, as opposed to what the assembly behind it does: that the key it was
 * handed comes back out, and that the single metric it was handed is the one all three of its other
 * answers read - the drawn value, the natural direction, and which number the ranking leads with. Two
 * modes over the same chain, differing only in that metric, is what shows the three follow the mode
 * rather than the vocabulary.
 *
 * <p>How a ranking is then laid out - the chain behind the primary key, the flip, the name and by-ID
 * tail - is KMLib's {@code ListSortComparators}, reached through {@link OwnerSortModeComposer}, and is
 * pinned in KMLib's own suite. The two real vocabularies
 * keep their own cases, which is what shows they are declared through this.
 *
 * <p>No case reads the drawn label: the lookup goes through the live settings, which the test JVM has
 * none of, so an assertion there would pin the strings fallback rather than a label.
 *
 * <p>Run over a payload no view declares, so the cases say what a mode does for any vocabulary rather
 * than what one layer's numbers happen to produce.
 */
final class OwnerMetricSortModeTests {

    // The stand-in vocabulary's chain: severity first, volatility behind it.
    private static final List<ToIntFunction<HazardRatingFake>> CANONICAL_CHAIN =
        List.of(HazardRatingFake::severity, HazardRatingFake::volatility);

    // What a mode that ranks by name names instead of a number. Spelled as the accessor type rather
    // than left a bare null at the call, so the case reads as the by-name ranking it sets up.
    private static final ToIntFunction<HazardRatingFake> NO_METRIC = null;

    // Two modes differing only in which number they promote, so a case can read an answer off each and
    // say the difference came from the metric rather than from anything the vocabulary supplied.
    private static final ListSortMode<RankedOwner<HazardRatingFake>> BY_SEVERITY =
        new OwnerMetricSortMode<>(
            "severity",
            "hazard_ctl_sort_severity",
            HazardRatingFake::severity,
            CANONICAL_CHAIN);

    private static final ListSortMode<RankedOwner<HazardRatingFake>> BY_VOLATILITY =
        new OwnerMetricSortMode<>(
            "volatility",
            "hazard_ctl_sort_volatility",
            HazardRatingFake::volatility,
            CANONICAL_CHAIN);

    private static final ListSortMode<RankedOwner<HazardRatingFake>> BY_NAME =
        new OwnerMetricSortMode<>(
            "name",
            "hazard_ctl_sort_name",
            NO_METRIC,
            CANONICAL_CHAIN);

    @Nested
    class PersistenceKey {

        @Test
        void isTheKeyTheModeWasDeclaredUnder() {

            // Each mode carries its own key rather than one the vocabulary or the shape supplies, which
            // is what lets a save name the mode it stored.
            assertThat(BY_SEVERITY.persistenceKey())
                .isEqualTo("severity");
            assertThat(BY_VOLATILITY.persistenceKey())
                .isEqualTo("volatility");
            assertThat(BY_NAME.persistenceKey())
                .isEqualTo("name");
        }
    }

    @Nested
    class ResolveTrailingRuns {

        @Test
        void readsTheModesOwnMetricOffTheSameOwner() {

            // One owner, two modes: each draws the number it was declared over, so the value follows the
            // mode rather than the payload's first number or the chain's leading one.
            var owner = buildStandInOwner("hazard", "Hazard", 7, 2);

            assertThat(BY_SEVERITY.resolveTrailingRuns(owner, ROW_COLOUR))
                .containsExactly(new TextSpan("7", ROW_COLOUR));
            assertThat(BY_VOLATILITY.resolveTrailingRuns(owner, ROW_COLOUR))
                .containsExactly(new TextSpan("2", ROW_COLOUR));
        }

        @Test
        void isNoRunsWhenTheModeNamesNoMetric() {

            // A by-name ranking has no number to show, so the row's value column stays unfilled.
            assertThat(BY_NAME.resolveTrailingRuns(buildStandInOwner("hazard", "Hazard", 7, 2), ROW_COLOUR))
                .isEmpty();
        }
    }

    @Nested
    class DefaultDirection {

        @Test
        void followsWhetherTheModeNamesAMetric() {

            // A number leads with the bigger owner; a name reads A-to-Z. Both follow from the metric the
            // mode was declared over, not from anything else it was handed.
            assertThat(BY_SEVERITY.defaultDirection())
                .isEqualTo(SortDirection.DESCENDING);
            assertThat(BY_NAME.defaultDirection())
                .isEqualTo(SortDirection.ASCENDING);
        }
    }

    @Nested
    class Comparator {

        @Test
        void leadsWithTheModesOwnMetric() {

            // The same pair under two modes over the same chain: the severe owner leads one ranking and
            // the volatile owner leads the other, so the primary key is the mode's metric rather than
            // the chain's first entry.
            var severe = buildStandInOwner("severe", "Severe", 9, 1);
            var unstable = buildStandInOwner("unstable", "Unstable", 1, 8);

            assertThat(listIdsInModeOrder(BY_SEVERITY, severe, unstable))
                .containsExactly("severe", "unstable");
            assertThat(listIdsInModeOrder(BY_VOLATILITY, severe, unstable))
                .containsExactly("unstable", "severe");
        }

        @Test
        void leadsWithTheLabelWhenTheModeNamesNoMetric() {

            // The more severe owner would lead either numeric ranking, so a by-name ranking putting
            // Alpha first says the label led rather than a number.
            var zeta = buildStandInOwner("z", "Zeta", 9, 9);
            var alpha = buildStandInOwner("a", "Alpha", 1, 1);

            assertThat(listIdsInModeOrder(BY_NAME, zeta, alpha))
                .containsExactly("a", "z");
        }
    }

}
