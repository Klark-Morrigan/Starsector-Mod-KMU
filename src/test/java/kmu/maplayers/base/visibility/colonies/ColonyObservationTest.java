package kmu.maplayers.base.visibility.colonies;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Pins what one observation is: the place it names, the moment it may or may not carry, and what
 * an observation missing either half does.
 *
 * <p>The undated half is the case worth pinning rather than the plain one. Every value recorded
 * before observations were timed reads back through here, so a constructor that refused one would
 * fail a load rather than a read.
 */
final class ColonyObservationTest {

    private static final long OBSERVED_AT = 4_200L;
    private static final String SYSTEM_ID = "kumari_kandam";

    @Nested
    class CreateObservationAt {

        @Test
        void reportsThePlaceAndTheMomentItWasMade() {

            var observation = ColonyObservation.createObservationAt(SYSTEM_ID, OBSERVED_AT);

            assertThat(observation.locationId())
                .isEqualTo(SYSTEM_ID);
            assertThat(observation.observedTimestamp())
                .contains(OBSERVED_AT);
        }
    }

    @Nested
    class CreateUndatedObservation {

        @Test
        void reportsThePlaceAndNoMomentAtAll() {

            var observation = ColonyObservation.createUndatedObservation(SYSTEM_ID);

            assertThat(observation.locationId())
                .isEqualTo(SYSTEM_ID);
            assertThat(observation.observedTimestamp())
                .isEmpty();
        }
    }

    @Nested
    class Constructor {

        @Test
        void readsAnUnstatedMomentAsNoMoment() {
            // A hand-built value is one hop from a load, and an observation is meant to survive
            // being made before anybody wrote down when - so the absent half is absorbed rather
            // than left to surface at whatever reads it.
            assertThat(new ColonyObservation(SYSTEM_ID, null).observedTimestamp())
                .isEmpty();
        }

        @Test
        void refusesAnObservationOfNowhere() {
            // The place is the half every visibility rule spends. An observation without one
            // answers no question the register is ever asked.
            assertThatNullPointerException()
                .isThrownBy(() -> new ColonyObservation(null, Optional.of(OBSERVED_AT)));
        }
    }
}
