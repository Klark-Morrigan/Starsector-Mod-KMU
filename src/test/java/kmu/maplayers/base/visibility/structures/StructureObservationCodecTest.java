package kmu.maplayers.base.visibility.structures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the stored form of a structure observation, spelt out literally: the moment ownership was
 * seen, the moment operation was detected, the fault letters, then the holder to the end.
 *
 * <p>Written against the text rather than through the register, because the text is save state.
 * Every entry in every existing save is spelt this way, and a change to the field order, the
 * separator or the fault letters would read as a player who had found nothing rather than as a
 * fault - so the one place it can be caught is a case asserting the characters themselves.
 */
final class StructureObservationCodecTest {

    private static final String HOLDER_ID = "hegemony";
    private static final long OPERATION_DETECTED = 4_300L;
    private static final long OWNERSHIP_SEEN = 4_200L;

    // A faction id spelt with the separator in it - the one spelling that parts in the wrong place
    // unless the moments and the faults lead and the holder runs to the end.
    private static final String HOLDER_ID_HOLDING_THE_SEPARATOR = "hegemony|remnant";

    private StructureObservationCodec codec;

    @BeforeEach
    void openCodec() {
        codec = new StructureObservationCodec();
    }

    @Nested
    class DecodeObservation {

        @Test
        void readsBackEveryFieldOfAFullyObservedStructure() {

            assertThat(codec.decodeObservation("4200|4300|dn|hegemony"))
                .isEqualTo(new StructureObservation(
                    Optional.of(HOLDER_ID),
                    Set.of(StructureFault.DISRUPTED, StructureFault.NON_FUNCTIONAL),
                    Optional.of(OWNERSHIP_SEEN),
                    Optional.of(OPERATION_DETECTED)));
        }

        @Test
        void readsEachFaultLetterAsTheFaultItStandsFor() {
            // Told apart one at a time as well as together, so the two letters cannot be swapped
            // for each other without a case going red.
            assertThat(codec.decodeObservation("4200|4300|d|hegemony").faults())
                .containsExactly(StructureFault.DISRUPTED);
            assertThat(codec.decodeObservation("4200|4300|n|hegemony").faults())
                .containsExactly(StructureFault.NON_FUNCTIONAL);
        }

        @Test
        void readsAStructureDetectedWorkingWithNoHolderEverEstablished() {
            // The ordinary shape of a structure nobody has been close to: somebody living in its
            // system reports that it is there and lit, and nothing about whose it is.
            assertThat(codec.decodeObservation("|4300||"))
                .isEqualTo(new StructureObservation(
                    Optional.empty(),
                    Set.of(),
                    Optional.empty(),
                    Optional.of(OPERATION_DETECTED)));
        }

        @Test
        void readsTheTwoMomentsApartWhereTheyWereEstablishedApart() {
            // The case one moment could not carry: a structure visited once and watched since, so
            // its holder is four cycles old while its state was confirmed this morning.
            assertThat(codec.decodeObservation("4200|4300||hegemony"))
                .isEqualTo(new StructureObservation(
                    Optional.of(HOLDER_ID),
                    Set.of(),
                    Optional.of(OWNERSHIP_SEEN),
                    Optional.of(OPERATION_DETECTED)));
        }

        @Test
        void readsAnEntryTimedByNothingAsObservedAtNoStatedMoment() {
            // What a sector with no clock to read records. The holder and the fault are a whole
            // observation with the dates missing rather than a broken one.
            assertThat(codec.decodeObservation("||d|hegemony"))
                .isEqualTo(new StructureObservation(
                    Optional.of(HOLDER_ID),
                    Set.of(StructureFault.DISRUPTED),
                    Optional.empty(),
                    Optional.empty()));
        }

        @Test
        void readsAHolderIdHoldingTheSeparatorWhole() {
            // The fixed-fields-first convention from the reading side: a separator inside the
            // holder id is part of what it says, not a boundary.
            assertThat(codec.decodeObservation("4200|4300||hegemony|remnant"))
                .isEqualTo(new StructureObservation(
                    Optional.of(HOLDER_ID_HOLDING_THE_SEPARATOR),
                    Set.of(),
                    Optional.of(OWNERSHIP_SEEN),
                    Optional.of(OPERATION_DETECTED)));
        }

        @Test
        void readsAnEntryItCannotPartAsTheStructureHavingBeenFoundAndNothingMore() {

            assertThat(codec.decodeObservation("4200|4300"))
                .isEqualTo(StructureObservation.createExistenceOnlyObservation());
        }

        @Test
        void readsAnEntryWhoseMomentIsNotAMomentAsFoundAndNothingMore() {
            // Half an entry understood is worse than none of it: a fault read beside a moment that
            // is not one would state that a structure was working when the entry it came from may
            // well have said the opposite.
            assertThat(codec.decodeObservation("yesterday|4300|d|hegemony"))
                .isEqualTo(StructureObservation.createExistenceOnlyObservation());
        }

        @Test
        void readsAnEntryWhoseSecondMomentIsNotAMomentAsFoundAndNothingMore() {
            // Both moment fields are held to the same bar, so an entry half spelt in some other
            // build's words cannot arrive with one of its dates believed.
            assertThat(codec.decodeObservation("4200|yesterday|d|hegemony"))
                .isEqualTo(StructureObservation.createExistenceOnlyObservation());
        }

        @Test
        void readsAnEntryWhoseFaultLetterItDoesNotSpellAsFoundAndNothingMore() {

            assertThat(codec.decodeObservation("4200|4300|x|hegemony"))
                .isEqualTo(StructureObservation.createExistenceOnlyObservation());
        }

        @Test
        void readsAnEntryStatingNothingAsFoundAndNothingMore() {

            assertThat(codec.decodeObservation("  "))
                .isEqualTo(StructureObservation.createExistenceOnlyObservation());
        }
    }

    @Nested
    class EncodeObservation {

        @Test
        void writesTheMomentsAndTheFaultsAheadOfTheHolderTheyDescribe() {

            assertThat(codec.encodeObservation(new StructureObservation(
                    Optional.of(HOLDER_ID),
                    Set.of(StructureFault.DISRUPTED, StructureFault.NON_FUNCTIONAL),
                    Optional.of(OWNERSHIP_SEEN),
                    Optional.of(OPERATION_DETECTED))))
                .isEqualTo("4200|4300|dn|hegemony");
        }

        @Test
        void writesTheFaultLettersInOneOrderWhateverOrderTheyArrivedIn() {
            // Two saves recording the same faults must hold the same characters, or a register
            // written on one visit reads as changed on the next.
            assertThat(codec.encodeObservation(new StructureObservation(
                    Optional.of(HOLDER_ID),
                    Set.of(StructureFault.NON_FUNCTIONAL, StructureFault.DISRUPTED),
                    Optional.of(OWNERSHIP_SEEN),
                    Optional.of(OPERATION_DETECTED))))
                .isEqualTo("4200|4300|dn|hegemony");
        }

        @Test
        void writesAnEmptyFieldForEachHalfNothingHasEstablished() {

            assertThat(codec.encodeObservation(
                    StructureObservation.createExistenceOnlyObservation()))
                .isEqualTo("|||");
        }

        @Test
        void writesAHolderIdHoldingTheSeparatorWhereItReadsBackWhole() {
            // The convention's other end, written and read by the same codec: an id a mod spelt
            // with the separator survives the round trip rather than being mangled in a save.
            var observation = new StructureObservation(
                Optional.of(HOLDER_ID_HOLDING_THE_SEPARATOR),
                Set.of(StructureFault.NON_FUNCTIONAL),
                Optional.of(OWNERSHIP_SEEN),
                Optional.of(OPERATION_DETECTED));

            assertThat(codec.decodeObservation(codec.encodeObservation(observation)))
                .isEqualTo(observation);
        }
    }
}
