package kmu.maplayers.base.visibility.observations;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.util.KmuValues;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The register one family's observations are kept in: sector memory under a key of its own, read,
 * written, and brought back into step with a loaded save.
 *
 * <p>Sector memory rather than anything of vanilla's, because vanilla keeps no such fact. It
 * serialises into the save alongside everything else there, so an observation survives a reload the
 * way the visit that produced it does.
 *
 * <p><strong>Text in a map of text, never a value class of ours.</strong> A class put into a save
 * bakes its own name into every save holding one: moving or renaming it later fails the load rather
 * than the read, and there is nowhere in a loaded game to repair that from. So each entry is a
 * string built by the family's own {@link ObservationCodec}, which is also where the fixed-fields-
 * first convention that keeps a free-form id readable is stated.
 *
 * <p><strong>The key is save state.</strong> Whoever opens a store names it once and never again:
 * renaming a key silently drops every observation in every existing save, and nothing in a loaded
 * game can tell that from a player who has been nowhere.
 *
 * <p><strong>A sector with nothing to record leaves no register behind.</strong> What can be filed
 * is written out before the register is opened, so the vast majority of places - which hold nothing
 * worth recording at all - never put an empty map into a save between them.
 *
 * <p><strong>The lifecycle is held here because every family wants the same one.</strong> A load
 * sheds what the register no longer describes and then records what the player is standing among,
 * in that order. Both halves ask the family what only it knows - which of its subjects the sector
 * still holds, and what is being observed now - so the ordering is shared while neither question
 * is. A family wanting a different lifecycle takes the reconciliation back and keeps the
 * bytes, the two being independent.
 *
 * @param <T> what one observation says was observed
 */
public final class ObservationStore<T> {

    private final ObservationCodec<T> codec;
    private final String memoryKey;

    /**
     * Opens a register for one family's observations.
     *
     * <p>A half-built store names itself here rather than at the read that would trip over it: an
     * absent key would read the whole sector's memory under nothing, and a fault carried that far
     * reads as a save being empty rather than as a store being wrong.
     *
     * @param memoryKey the sector-memory key the register is held under - save state, and stable
     *                  once shipped
     * @param codec     how one of this family's observations is written and read back
     */
    public ObservationStore(String memoryKey, ObservationCodec<T> codec) {

        this.memoryKey = KmuValues.requireNonBlankText(memoryKey, "memoryKey");
        this.codec = Objects.requireNonNull(codec, "A register stores what a codec can write.");
    }

    /**
     * Opens the register for reading.
     *
     * @param sector the sector whose memory holds the register; null - or one holding no register
     *               yet - reads as nothing having been observed
     * @return what was last observed of each subject, by subject id; never null
     */
    public RecordedObservations<T> readObservations(SectorAPI sector) {

        var storedEntries = readStoredEntries(sector);

        if (storedEntries == null) {
            return RecordedObservations.createEmptyRegister();
        }
        return subjectId -> decodeStoredEntry(storedEntries.get(subjectId));
    }

    /**
     * Records what was observed of each of several subjects, opening the register on the first
     * write of a campaign.
     *
     * <p>Taken as a set rather than one subject at a time because an observation is normally made
     * of everything in a place at once, and a caller stamping each with the moment it read the
     * clock at would have two subjects seen together stated a tick apart.
     *
     * @param sector                  the sector whose memory holds the register; one with no memory
     *                                to write into is a no-op
     * @param observationsBySubjectId what was observed, by the subject's own id; nothing to
     *                                record - or nothing in it that can be filed - is a no-op, and
     *                                leaves no empty register behind
     */
    public void recordObservations(SectorAPI sector, Map<String, T> observationsBySubjectId) {

        var storedObservations = encodeRecordableObservations(observationsBySubjectId);

        if (storedObservations.isEmpty()) {
            return;
        }
        var storedEntries = openStoredEntries(sector);

        if (storedEntries != null) {
            storedEntries.putAll(storedObservations);
        }
    }

    /**
     * Drops every observation whose subject is no longer anywhere in the sector.
     *
     * <p>An observation outliving what it was about would go on answering for whatever next took
     * the id, which is a thing nobody ever observed.
     *
     * @param sector          the sector to reconcile the register against; null is a no-op
     * @param presentSubjects which of the family's subjects the sector still holds; asked only when
     *                        there is something to shed, so an unwritten register costs no sweep
     */
    public void dropObservationsOfAbsentSubjects(
            SectorAPI sector,
            PresentSubjectReader presentSubjects) {

        var storedEntries = readStoredEntries(sector);

        if (storedEntries == null || storedEntries.isEmpty()) {
            return;
        }
        storedEntries
            .keySet()
            .retainAll(presentSubjects.readPresentSubjectIds(sector));
    }

    /**
     * Brings the register into step with a loaded save: sheds the observations whose subjects have
     * gone, then records what is being observed where the save was left.
     *
     * <p>The second half is what a load owes the register. A save opened in a place produces no
     * arrival there, so without this the one place the player is looking at is the one place the
     * register has nothing to say about - and on the first load of a save written before any
     * observation was ever made, it is the only place it could learn anything at all.
     *
     * @param sector           the sector to reconcile; null is a no-op
     * @param presentSubjects  which of the family's subjects the sector still holds
     * @param liveObservations what is being observed where the save was left
     */
    public void reconcileWithLoadedSave(
            SectorAPI sector,
            PresentSubjectReader presentSubjects,
            LiveObservationRecorder liveObservations) {

        dropObservationsOfAbsentSubjects(sector, presentSubjects);

        if (sector != null) {
            liveObservations.recordObservationsSeenNow(sector);
        }
    }

    // What of a set can actually be filed, each written as the register holds it.
    //
    // Encoded before the register is opened rather than entry by entry into it, so that a set
    // nothing in which can be filed leaves no empty map in the save: the register is opened for
    // entries that will land in it, never for the attempt.
    private Map<String, String> encodeRecordableObservations(
            Map<String, T> observationsBySubjectId) {

        var storedObservations = new HashMap<String, String>();

        if (observationsBySubjectId == null) {
            return storedObservations;
        }
        for (var observation : observationsBySubjectId.entrySet()) {

            // A subject the game names with nothing cannot be asked about later, so recording it
            // would only put an entry in the save that no read could ever reach.
            if (KmuValues.hasText(observation.getKey()) && observation.getValue() != null) {

                storedObservations.put(
                    observation.getKey(),
                    codec.encodeObservation(observation.getValue()));
            }
        }
        return storedObservations;
    }

    // One stored entry read back through the family's own codec, which is the only thing that knows
    // what its fields mean - including how much of an entry it can still make sense of.
    private Optional<T> decodeStoredEntry(String storedObservation) {

        if (storedObservation == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(codec.decodeObservation(storedObservation));
    }

    // The stored register as it stands, or null when there is nothing to read - no sector, no
    // memory, or a save written before anything was ever recorded. A caller reading null treats it
    // as "nothing has been observed", which is the answer an absent record deserves.
    private Map<String, String> readStoredEntries(SectorAPI sector) {

        if (sector == null) {
            return null;
        }
        var memory = sector.getMemoryWithoutUpdate();

        if (memory == null || !memory.contains(memoryKey)) {
            return null;
        }
        return castStoredEntries(memory.get(memoryKey));
    }

    // The stored register, created and stored on first use. Writers go through here rather than
    // through the read above so that the first observation of a campaign has somewhere to land.
    private Map<String, String> openStoredEntries(SectorAPI sector) {

        var storedEntries = readStoredEntries(sector);

        if (storedEntries != null) {
            return storedEntries;
        }
        var memory = sector == null ? null : sector.getMemoryWithoutUpdate();

        if (memory == null) {
            return null;
        }
        var openedEntries = new HashMap<String, String>();

        memory.set(memoryKey, openedEntries);

        return openedEntries;
    }

    // Sector memory is untyped, so what comes back out of it is taken on trust - and refused
    // outright when it is not a map at all, since another party writing over the key would
    // otherwise take down every read of the register rather than merely emptying it.
    @SuppressWarnings("unchecked")
    private static Map<String, String> castStoredEntries(Object storedValue) {

        if (!(storedValue instanceof Map)) {
            return null;
        }
        return (Map<String, String>) storedValue;
    }

    /**
     * Which of a family's subjects the sector still holds.
     *
     * <p>Asked of the family rather than handed over by it, so a register with nothing in it costs
     * no sweep at all - which is the commonest load, and one where reading the whole sector to
     * discover there was nothing to shed is the reconciliation's entire cost paid for nothing.
     */
    @FunctionalInterface
    public interface PresentSubjectReader {

        /**
         * Every subject of this family the sector still holds.
         *
         * @param sector the sector being reconciled
         * @return the ids of every one of them, wherever it stands; never null
         */
        Set<String> readPresentSubjectIds(SectorAPI sector);
    }

    /**
     * What is being observed at this moment, recorded where a loaded save leaves the player.
     *
     * <p>Which events reveal a fact is entirely the family's own subject, so the store knows only
     * that a load is the moment to ask.
     */
    @FunctionalInterface
    public interface LiveObservationRecorder {

        /**
         * Records whatever is being observed where the save was left.
         *
         * @param sector the sector the save was loaded into
         */
        void recordObservationsSeenNow(SectorAPI sector);
    }
}
