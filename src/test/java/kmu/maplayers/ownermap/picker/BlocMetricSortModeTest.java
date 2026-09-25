package kmu.maplayers.ownermap.picker;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.ToIntFunction;

import static kmu.maplayers.ownermap.picker.BlocSortFixtures.ROW_COLOUR;
import static kmu.maplayers.ownermap.picker.BlocSortFixtures.buildStandInBloc;
import static kmu.maplayers.ownermap.picker.BlocSortFixtures.listIdsInModeOrder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what one declared mode is, as opposed to what the assembly behind it does: that the key it was
 * handed comes back out, and that the single metric it was handed is the one all three of its other
 * answers read - the drawn value, the natural direction, and which number the ranking leads with. Two
 * modes over the same chain, differing only in that metric, is what shows the three follow the mode
 * rather than the vocabulary.
 *
 * <p>How a ranking is then laid out - the chain behind the primary key, the flip, the name and by-id
 * tail - is {@link BlocSortModeComposer}'s and is pinned in its own suite. The two real vocabularies
 * keep their own cases, which is what shows they are declared through this.
 *
 * <p>No case reads the drawn label: the lookup goes through the live settings, which the test JVM has
 * none of, so an assertion there would pin the strings fallback rather than a label.
 *
 * <p>Run over a payload no view declares, so the cases say what a mode does for any vocabulary rather
 * than what one layer's numbers happen to produce.
 */
final class BlocMetricSortModeTest {

    // The stand-in vocabulary's chain: severity first, volatility behind it.
    private static final List<ToIntFunction<HazardRating>> CANONICAL_CHAIN =
        List.of(HazardRating::severity, HazardRating::volatility);

    // What a mode that ranks by name names instead of a number. Spelled as the accessor type rather
    // than left a bare null at the call, so the case reads as the by-name ranking it sets up.
    private static final ToIntFunction<HazardRating> NO_METRIC = null;

    // Two modes differing only in which number they promote, so a case can read an answer off each and
    // say the difference came from the metric rather than from anything the vocabulary supplied.
    private static final ListSortMode<RankedBloc<HazardRating>> BY_SEVERITY =
        new BlocMetricSortMode<>(
            "severity",
            "hazard_ctl_sort_severity",
            HazardRating::severity,
            CANONICAL_CHAIN);

    private static final ListSortMode<RankedBloc<HazardRating>> BY_VOLATILITY =
        new BlocMetricSortMode<>(
            "volatility",
            "hazard_ctl_sort_volatility",
            HazardRating::volatility,
            CANONICAL_CHAIN);

    private static final ListSortMode<RankedBloc<HazardRating>> BY_NAME =
        new BlocMetricSortMode<>(
            "name",
            "hazard_ctl_sort_name",
            NO_METRIC,
            CANONICAL_CHAIN);

    @Nested
    class PersistenceKey {

        @Test
        void persistenceKeyIsTheKeyTheModeWasDeclaredUnder() {

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
        void resolveTrailingRunsReadsTheModesOwnMetricOffTheSameBloc() {

            // One bloc, two modes: each draws the number it was declared over, so the value follows the
            // mode rather than the payload's first number or the chain's leading one.
            var bloc = buildStandInBloc("hazard", "Hazard", 7, 2);

            assertThat(BY_SEVERITY.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("7", ROW_COLOUR));
            assertThat(BY_VOLATILITY.resolveTrailingRuns(bloc, ROW_COLOUR))
                .containsExactly(new TextSpan("2", ROW_COLOUR));
        }

        @Test
        void resolveTrailingRunsIsNoRunsWhenTheModeNamesNoMetric() {

            // A by-name ranking has no number to show, so the row's value column stays unfilled.
            assertThat(BY_NAME.resolveTrailingRuns(buildStandInBloc("hazard", "Hazard", 7, 2), ROW_COLOUR))
                .isEmpty();
        }
    }

    @Nested
    class DefaultDirection {

        @Test
        void defaultDirectionFollowsWhetherTheModeNamesAMetric() {

            // A number leads with the bigger bloc; a name reads A-to-Z. Both follow from the metric the
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
        void comparatorLeadsWithTheModesOwnMetric() {

            // The same pair under two modes over the same chain: the severe bloc leads one ranking and
            // the volatile bloc leads the other, so the primary key is the mode's metric rather than
            // the chain's first entry.
            var severe = buildStandInBloc("severe", "Severe", 9, 1);
            var unstable = buildStandInBloc("unstable", "Unstable", 1, 8);

            assertThat(listIdsInModeOrder(BY_SEVERITY, severe, unstable))
                .containsExactly("severe", "unstable");
            assertThat(listIdsInModeOrder(BY_VOLATILITY, severe, unstable))
                .containsExactly("unstable", "severe");
        }

        @Test
        void comparatorLeadsWithTheLabelWhenTheModeNamesNoMetric() {

            // The more severe bloc would lead either numeric ranking, so a by-name ranking putting
            // Alpha first says the label led rather than a number.
            var zeta = buildStandInBloc("z", "Zeta", 9, 9);
            var alpha = buildStandInBloc("a", "Alpha", 1, 1);

            assertThat(listIdsInModeOrder(BY_NAME, zeta, alpha))
                .containsExactly("a", "z");
        }
    }

}
