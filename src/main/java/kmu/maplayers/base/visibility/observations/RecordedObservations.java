package kmu.maplayers.base.visibility.observations;

import java.util.Optional;

/**
 * What the register holds about each subject, asked by the subject's own id.
 *
 * <p>Handed out as a lookup over the stored entries rather than as a copy of them. A register is
 * opened once per pass and asked about many subjects, so a copy taken there would cost the whole
 * sector's observations to answer about one.
 *
 * <p>Says nothing about who did the observing, nor about whether anything is revealing the subject
 * right now. It answers only what was written down, which is one of the two halves
 * {@link ObservationRecency#resolveRecency} weighs.
 *
 * <p>A caller holding no register at all reads {@link #createEmptyRegister}, under which nothing
 * has ever been observed - the conservative answer, since a register that invented observations
 * would state facts nobody ever established.
 *
 * @param <T> what one observation says was observed
 */
@FunctionalInterface
public interface RecordedObservations<T> {

    /**
     * A register holding nothing, as an absent one reads.
     *
     * @param <T> what an observation would have said, had anything been recorded
     * @return the empty register; never null
     */
    static <T> RecordedObservations<T> createEmptyRegister() {
        return subjectId -> Optional.empty();
    }

    /**
     * What was last observed of one subject.
     *
     * @param subjectId the subject's own ID, as the family names it; an ID the register has never
     *                  held reads as never observed
     * @return what was observed, or empty where nothing was ever written down about it
     */
    Optional<T> readObservation(String subjectId);

    /**
     * The same reading, with a never-observed subject answered as nothing rather than as an empty
     * optional.
     *
     * <p>Offered because a family whose own port spells an absent observation as null would
     * otherwise unwrap this at each register it opens, one copy of the adaptation per family and
     * each free to answer differently.
     *
     * @param subjectId the subject's own ID, as the family names it
     * @return what was observed, or null where nothing was ever written down about it
     */
    default T readObservationOrNull(String subjectId) {

        return readObservation(subjectId)
            .orElse(null);
    }
}
