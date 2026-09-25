package kmu.maplayers.base.visibility.observations;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins that an axis cannot be built half-formed.
 *
 * <p>Worth its own cases because nothing else would catch them. Neither guard is a branch in the
 * compiled class, so a coverage report reads a fully exercised constructor whether or not either
 * has ever been tried - and the fault a missing guard produces surfaces at the far end of a row's
 * composition, where it reads as the surface being broken rather than as an axis being wrong.
 */
final class ObservationAxisTest {

    private static final String LEAD_IN_KEY = "owner_map_tooltip_last_seen";

    @Nested
    class Constructor {

        @Test
        void holdsTheWordsAndTheStateItWasBuiltFrom() {

            var axis = new ObservationAxis(LEAD_IN_KEY, ObservationRecency.OBSERVED_NOW);

            assertThat(axis.leadInKey())
                .isEqualTo(LEAD_IN_KEY);
            assertThat(axis.recency())
                .isEqualTo(ObservationRecency.OBSERVED_NOW);
        }

        @Test
        void refusesAnAxisWithNoWordsToIntroduceItsDate() {

            assertThatThrownBy(() -> new ObservationAxis(null, ObservationRecency.OBSERVED_NOW))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesAnAxisWhoseWordsResolveToNothing() {
            // A blank ID is the failure a null one would be caught for and a present one would not:
            // it composes a remark with nothing in front of the date, which reads as a fault in the
            // surface rather than as the missing string it is.
            assertThatThrownBy(() -> new ObservationAxis("   ", ObservationRecency.OBSERVED_NOW))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesAnAxisInNoneOfTheThreeStates() {

            assertThatThrownBy(() -> new ObservationAxis(LEAD_IN_KEY, null))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void holdsARecalledStateCarryingNoMoment() {
            // The one state that looks half-built and is not: news somebody wrote down without
            // saying when is a whole axis, and a guard that rejected it would fail a load.
            var axis = new ObservationAxis(
                LEAD_IN_KEY,
                new ObservationRecency.RecalledObservation(Optional.empty()));

            assertThat(axis.recency())
                .isEqualTo(new ObservationRecency.RecalledObservation(Optional.empty()));
        }
    }
}
