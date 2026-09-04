package kmu.maplayers.base.visibility.observations;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins what the register underneath every family's observations does: what an unwritten one
 * answers, what a write leaves behind, and what a load sheds and then learns.
 *
 * <p>The stored map is a real one behind a mocked memory, rather than a stub per key, because every
 * case here is about how the register accumulates across calls - a stubbed read could not show a
 * second observation overwriting the first, nor a reconciliation removing an entry.
 *
 * <p>The observations are synthetic, and so is the codec that writes them. No family owns an axis
 * through the store yet, and a codec borrowed from one would pin that family's fields in a suite
 * about bytes and lifecycle. What the synthetic one does carry is the shape the store's convention
 * describes - a fixed field first, a free-form one running to the end - so the cases that turn on
 * that convention exercise it rather than describe it.
 */
final class ObservationStoreTest {

    private static final String ABSENT_SUBJECT_ID = "razed_base";
    private static final String OBSERVED_ELSEWHERE = "corvus";
    private static final long OBSERVED_MOMENT = 4_200L;
    private static final String OBSERVED_PLACE = "kumari_kandam";
    private static final String REGISTER_KEY = "$kmu_synthetic_observations";
    private static final String SUBJECT_ID = "sentinel_gantries";

    // A free-form field spelt with the codec's own separator in it. The one spelling that parts in
    // the wrong place unless the fixed field leads, which is why the convention exists at all.
    private static final String PLACE_HOLDING_THE_SEPARATOR = "outer@" + OBSERVED_PLACE;

    private MemoryAPI memoryMock;
    private SectorAPI sectorMock;
    private ObservationStore<SyntheticObservation> store;

    @BeforeEach
    void openStore() {

        memoryMock = mock(MemoryAPI.class);
        sectorMock = mock(SectorAPI.class);

        when(sectorMock.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);

        var codecFake = new SyntheticObservationCodecFake();

        store = new ObservationStore<>(REGISTER_KEY, codecFake);
    }

    @Nested
    class Constructor {

        @Test
        void refusesAStoreWithNoKeyToKeepItsRegisterUnder() {
            // Named here rather than at the read that would trip over it: a register under no key
            // reads as a save holding nothing, which is exactly what a player who has been nowhere
            // looks like.
            assertThatThrownBy(() ->
                    new ObservationStore<>(null, new SyntheticObservationCodecFake()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesAStoreWhoseKeyResolvesToNothing() {

            assertThatThrownBy(() ->
                    new ObservationStore<>("   ", new SyntheticObservationCodecFake()))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void refusesAStoreWithNoCodecToWriteThrough() {

            assertThatThrownBy(() -> new ObservationStore<>(REGISTER_KEY, null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ReadObservations {

        @Test
        void readsNothingWhereTheRegisterHasNeverBeenWritten() {

            assertThat(readObservationOf(SUBJECT_ID))
                .isEmpty();
        }

        @Test
        void readsNothingWhereThereIsNoSectorToReadFrom() {

            assertThat(store.readObservations(null).readObservation(SUBJECT_ID))
                .isEmpty();
        }

        @Test
        void readsNothingWhereTheKeyHoldsSomethingThatIsNotARegister() {
            // Another party writing over the key must cost this family's observations and nothing
            // else: a read that threw here would take down every surface asking about them.
            storeRegister("not a register");

            assertThat(readObservationOf(SUBJECT_ID))
                .isEmpty();
        }

        @Test
        void readsNothingForASubjectTheRegisterHasNeverHeld() {

            openRegister().put(SUBJECT_ID, OBSERVED_MOMENT + "@" + OBSERVED_PLACE);

            assertThat(readObservationOf(ABSENT_SUBJECT_ID))
                .isEmpty();
        }

        @Test
        void readsBackWhatTheCodecWroteDown() {

            openRegister().put(SUBJECT_ID, "1720@" + OBSERVED_PLACE);

            assertThat(readObservationOf(SUBJECT_ID))
                .contains(new SyntheticObservation(1720L, OBSERVED_PLACE));
        }

        @Test
        void readsAFreeFormFieldWholeWhereItIsSpeltWithTheSeparator() {
            // The whole of the fixed-fields-first convention. The free-form field runs to the end
            // of the entry, so a separator inside it is part of what it says, not a boundary.
            openRegister().put(SUBJECT_ID, "1720@" + PLACE_HOLDING_THE_SEPARATOR);

            assertThat(readObservationOf(SUBJECT_ID))
                .contains(new SyntheticObservation(1720L, PLACE_HOLDING_THE_SEPARATOR));
        }

        @Test
        void readsAnEntryThatDoesNotPartAsTheCodecsOwnWeakerReading() {
            // The migration case, and the reason nothing here fails on an entry it did not write:
            // an entry saved before a field was kept is an observation with one half missing rather
            // than a broken one, and the store hands the whole of it over for the codec to say so.
            openRegister().put(SUBJECT_ID, PLACE_HOLDING_THE_SEPARATOR);

            assertThat(readObservationOf(SUBJECT_ID))
                .contains(new SyntheticObservation(null, PLACE_HOLDING_THE_SEPARATOR));
        }
    }

    @Nested
    class RecordObservations {

        @Test
        void recordsWhatWasObservedOfEverySubjectSeenTogether() {

            openRegister();

            store.recordObservations(
                sectorMock,
                Map.of(
                    SUBJECT_ID, new SyntheticObservation(OBSERVED_MOMENT, OBSERVED_PLACE),
                    ABSENT_SUBJECT_ID, new SyntheticObservation(OBSERVED_MOMENT, OBSERVED_PLACE)));

            assertThat(readObservationOf(SUBJECT_ID))
                .contains(new SyntheticObservation(OBSERVED_MOMENT, OBSERVED_PLACE));
            assertThat(readObservationOf(ABSENT_SUBJECT_ID))
                .contains(new SyntheticObservation(OBSERVED_MOMENT, OBSERVED_PLACE));
        }

        @Test
        void recordsAFreeFormFieldItCanReadBackWhole() {
            // The convention's other end. Written and read by the same codec, so a store that
            // mangled an entry on the way in or out would show up here rather than in a save.
            openRegister();

            store.recordObservations(
                sectorMock,
                Map.of(
                    SUBJECT_ID,
                    new SyntheticObservation(OBSERVED_MOMENT, PLACE_HOLDING_THE_SEPARATOR)));

            assertThat(readObservationOf(SUBJECT_ID))
                .contains(new SyntheticObservation(OBSERVED_MOMENT, PLACE_HOLDING_THE_SEPARATOR));
        }

        @Test
        void movesASubjectsObservationToWhateverWasLastSeenOfIt() {
            // Meeting a subject again names what is true now rather than adding to a list of
            // everything that has ever been true of it.
            openRegister().put(SUBJECT_ID, OBSERVED_ELSEWHERE);

            store.recordObservations(
                sectorMock,
                Map.of(SUBJECT_ID, new SyntheticObservation(OBSERVED_MOMENT, OBSERVED_PLACE)));

            assertThat(readObservationOf(SUBJECT_ID))
                .contains(new SyntheticObservation(OBSERVED_MOMENT, OBSERVED_PLACE));
        }

        @Test
        void leavesNoRegisterBehindWhereThereWasNothingToRecord() {
            // What keeps the save free of empty maps. Most places hold nothing worth recording at
            // all, and one register apiece for them would be the bulk of what the store ever wrote.
            store.recordObservations(sectorMock, Map.of());

            verify(memoryMock, never())
                .set(anyString(), any());
        }

        @Test
        void leavesNoRegisterBehindWhereThereIsNothingToRecordFrom() {
            // The same guard for a caller mid-walk holding a place it has no reading of yet.
            store.recordObservations(sectorMock, null);

            verifyNoInteractions(memoryMock);
        }

        @Test
        void recordsNothingWhereThereIsNoMemoryToWriteInto() {

            when(sectorMock.getMemoryWithoutUpdate())
                .thenReturn(null);

            store.recordObservations(
                sectorMock,
                Map.of(SUBJECT_ID, new SyntheticObservation(OBSERVED_MOMENT, OBSERVED_PLACE)));

            verifyNoInteractions(memoryMock);
        }

        @Test
        void recordsNothingAgainstASubjectTheGameNamesWithNothing() {
            // An entry no read could ever reach, since a subject is asked about by the very id it
            // would be filed under.
            var storedEntries = openRegister();
            var observationsBySubjectId = new HashMap<String, SyntheticObservation>();

            observationsBySubjectId.put(
                "  ",
                new SyntheticObservation(OBSERVED_MOMENT, OBSERVED_PLACE));

            store.recordObservations(sectorMock, observationsBySubjectId);

            assertThat(storedEntries)
                .isEmpty();
        }
    }

    @Nested
    class DropObservationsOfAbsentSubjects {

        @Test
        void dropsAnObservationOfASubjectTheSectorNoLongerHolds() {
            // An observation outliving what it was about would go on answering for whatever next
            // took the id, which is a thing nobody ever observed.
            var storedEntries = openRegister();

            storedEntries.put(SUBJECT_ID, OBSERVED_PLACE);
            storedEntries.put(ABSENT_SUBJECT_ID, OBSERVED_PLACE);

            store.dropObservationsOfAbsentSubjects(
                sectorMock,
                sector -> Set.of(SUBJECT_ID));

            assertThat(storedEntries)
                .containsOnlyKeys(SUBJECT_ID);
        }

        @Test
        void keepsAnObservationOfASubjectStandingSomewhereElse() {
            // Present but elsewhere is not absent. The observation stays and simply stops matching
            // where the subject is now, which is each family's own way of saying the news is old.
            var storedEntries = openRegister();

            storedEntries.put(SUBJECT_ID, OBSERVED_ELSEWHERE);

            store.dropObservationsOfAbsentSubjects(
                sectorMock,
                sector -> Set.of(SUBJECT_ID));

            assertThat(readObservationOf(SUBJECT_ID))
                .contains(new SyntheticObservation(null, OBSERVED_ELSEWHERE));
        }

        @Test
        void asksTheSectorForNothingWhereTheRegisterHasNeverBeenWritten() {
            // What the sweep is asked for rather than handed: the commonest load has nothing to
            // shed, and reading the whole sector to find that out is the entire cost of the
            // reconciliation paid for no result.
            var presentSubjectsMock = mock(ObservationStore.PresentSubjectReader.class);

            store.dropObservationsOfAbsentSubjects(sectorMock, presentSubjectsMock);

            verifyNoInteractions(presentSubjectsMock);
        }

        @Test
        void asksTheSectorForNothingWhereTheRegisterHoldsNothing() {

            openRegister();

            var presentSubjectsMock = mock(ObservationStore.PresentSubjectReader.class);

            store.dropObservationsOfAbsentSubjects(sectorMock, presentSubjectsMock);

            verifyNoInteractions(presentSubjectsMock);
        }
    }

    @Nested
    class ReconcileWithLoadedSave {

        @Test
        void shedsWhatHasGoneBeforeRecordingWhatIsBeingObservedNow() {
            // The ordering is the whole of what the store holds of the lifecycle. Recorded first,
            // what the player is standing among would be shed by the very sweep run to clear the
            // save - so the case records a subject the sweep is told nothing about.
            var storedEntries = openRegister();

            storedEntries.put(ABSENT_SUBJECT_ID, OBSERVED_PLACE);

            store.reconcileWithLoadedSave(
                sectorMock,
                sector -> Set.of(),
                sector -> store.recordObservations(
                    sector,
                    Map.of(SUBJECT_ID, new SyntheticObservation(OBSERVED_MOMENT, OBSERVED_PLACE))));

            assertThat(storedEntries)
                .containsOnlyKeys(SUBJECT_ID);
            assertThat(readObservationOf(SUBJECT_ID))
                .contains(new SyntheticObservation(OBSERVED_MOMENT, OBSERVED_PLACE));
        }

        @Test
        void observesNothingWhereThereIsNoSectorToReconcile() {

            var liveObservationsMock = mock(ObservationStore.LiveObservationRecorder.class);

            store.reconcileWithLoadedSave(null, sector -> Set.of(), liveObservationsMock);

            verifyNoInteractions(liveObservationsMock);
        }
    }

    // What the register says about one subject, read back through its own reader. The stored text
    // is the codec's business - a case asserting against it would be pinning an encoding this class
    // never chose, and would have to be rewritten the day a family changed one.
    private Optional<SyntheticObservation> readObservationOf(String subjectId) {
        return store
            .readObservations(sectorMock)
            .readObservation(subjectId);
    }

    // Opens the register the way a first observation would, so a case can seed it and then assert
    // against the very map the code under test writes into.
    private Map<String, String> openRegister() {

        var storedEntries = new HashMap<String, String>();

        storeRegister(storedEntries);

        return storedEntries;
    }

    private void storeRegister(Object storedValue) {

        when(memoryMock.contains(REGISTER_KEY))
            .thenReturn(true);
        when(memoryMock.get(REGISTER_KEY))
            .thenReturn(storedValue);
    }

    // One entry of the shape the store's convention describes: a fixed field, then a free-form one
    // running to the end. Neither means anything in particular - what an entry is about belongs to
    // whichever family owns the axis, and this suite is about the bytes underneath.
    private record SyntheticObservation(Long recordedMoment, String freeFormPlaceId) {
    }

    // A codec obeying that convention, weaker reading and all: an entry whose leading field is not
    // a moment is read as a free-form field alone rather than refused.
    private static final class SyntheticObservationCodecFake
            implements ObservationCodec<SyntheticObservation> {

        private static final String FIELD_SEPARATOR = "@";

        @Override
        public SyntheticObservation decodeObservation(String storedObservation) {

            var separatorIndex = storedObservation.indexOf(FIELD_SEPARATOR);

            if (separatorIndex < 0) {
                return new SyntheticObservation(null, storedObservation);
            }
            try {
                return new SyntheticObservation(
                    Long.parseLong(storedObservation.substring(0, separatorIndex)),
                    storedObservation.substring(separatorIndex + FIELD_SEPARATOR.length()));

            } catch (NumberFormatException notAMoment) {
                return new SyntheticObservation(null, storedObservation);
            }
        }

        @Override
        public String encodeObservation(SyntheticObservation observation) {

            if (observation.recordedMoment() == null) {
                return observation.freeFormPlaceId();
            }
            return observation.recordedMoment() + FIELD_SEPARATOR + observation.freeFormPlaceId();
        }
    }
}
