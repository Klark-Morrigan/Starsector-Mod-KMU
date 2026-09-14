package kmu.maplayers.base.visibility.colonies;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a caller holding no register reads.
 *
 * <p>Worth a case because the answer is a decision rather than a default: a register that invented
 * sightings would show colonies nobody has met, so the unstated one has to answer as though
 * nothing had ever been seen.
 */
final class ColonySightingsTest {

    @Nested
    class None {

        @Test
        void reportsNothingSeenOfAnyColony() {

            assertThat(ColonySightings.NONE.readObservation("sentinel_gantries"))
                .isNull();
        }
    }
}
