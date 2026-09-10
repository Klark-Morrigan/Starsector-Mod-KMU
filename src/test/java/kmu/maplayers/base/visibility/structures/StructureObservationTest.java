package kmu.maplayers.base.visibility.structures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what an observation may hold: an unstated half reading as one nothing established, and no
 * room at all for a state that reports itself.
 */
final class StructureObservationTest {

    private static final String HOLDER_ID = "hegemony";
    private static final long OBSERVED_MOMENT = 4_200L;

    @Nested
    class Constructor {

        @Test
        void holdsWhatWasObservedOfTheStructure() {

            var observation = new StructureObservation(
                Optional.of(HOLDER_ID),
                true,
                false,
                Optional.of(OBSERVED_MOMENT),
                Optional.of(OBSERVED_MOMENT));

            assertThat(observation.holderFactionId())
                .contains(HOLDER_ID);
            assertThat(observation.isDisrupted())
                .isTrue();
            assertThat(observation.isNonFunctional())
                .isFalse();
            assertThat(observation.ownershipSeenTimestamp())
                .contains(OBSERVED_MOMENT);
            assertThat(observation.operationDetectedTimestamp())
                .contains(OBSERVED_MOMENT);
        }

        @Test
        void readsAHolderStatedAsNothingAsOneNobodyEstablished() {
            // The two spellings of "nobody has been close enough to say" - no optional at all, and
            // one holding a blank id - must not reach a reader as different answers, since one of
            // them would print an empty faction name beside a date.
            var observation = new StructureObservation(
                Optional.of("   "),
                false,
                false,
                Optional.empty(),
                Optional.of(OBSERVED_MOMENT));

            assertThat(observation.holderFactionId())
                .isEmpty();
        }

        @Test
        void readsAnUnstatedHalfAsOneNothingHasEstablished() {

            var observation = new StructureObservation(null, false, false, null, null);

            assertThat(observation.holderFactionId())
                .isEmpty();
            assertThat(observation.ownershipSeenTimestamp())
                .isEmpty();
            assertThat(observation.operationDetectedTimestamp())
                .isEmpty();
        }
    }

    @Nested
    class CreateExistenceOnlyObservation {

        @Test
        void statesThatTheStructureWasFoundAndNothingElse() {

            var observation = StructureObservation.createExistenceOnlyObservation();

            assertThat(observation.holderFactionId())
                .isEmpty();
            assertThat(observation.isDisrupted())
                .isFalse();
            assertThat(observation.isNonFunctional())
                .isFalse();
            assertThat(observation.ownershipSeenTimestamp())
                .isEmpty();
            assertThat(observation.operationDetectedTimestamp())
                .isEmpty();
        }
    }

    @Nested
    class RecordComponents {

        @Test
        void carryNothingAboutARunningHack() {
            // Asserted over the record's own shape rather than over a value, because the fault it
            // guards against is a field being added: a hack lapses on the campaign clock with
            // nothing fired, so a recorded one would go on being read out of the save for years
            // after the sniffer it describes had stopped reporting. It is read live instead.
            var componentNames = Arrays
                .stream(StructureObservation.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase(Locale.ROOT))
                .toList();

            assertThat(componentNames)
                .noneMatch(componentName -> componentName.contains("hack"));
        }
    }
}
