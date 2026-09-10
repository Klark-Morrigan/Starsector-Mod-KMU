package kmu.maplayers.base.visibility.structures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a caller holding no register reads.
 *
 * <p>Worth a case because the answer is a decision rather than a default: a register that invented
 * observations would name holders nobody has ever established, so the unstated one has to answer
 * as though nothing had ever been seen.
 */
final class StructureObservationsTest {

    @Nested
    class None {

        @Test
        void reports_nothing_observed_of_any_structure() {

            assertThat(StructureObservations.NONE.readObservation("kumari_kandam_relay"))
                .isNull();
        }
    }
}
