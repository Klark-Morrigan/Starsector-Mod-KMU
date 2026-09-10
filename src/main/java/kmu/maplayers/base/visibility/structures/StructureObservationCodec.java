package kmu.maplayers.base.visibility.structures;

import kmu.maplayers.base.visibility.observations.ObservationCodec;
import kmu.util.KmuValues;

import java.util.Optional;

/**
 * What one structure observation says, written as the register holds it and read back out of it:
 * the moment ownership was last seen, the moment operation was last detected, the state the
 * structure was in, then who was holding it.
 *
 * <p><strong>The stored form is save state.</strong> Every entry in every existing save is spelt
 * this way, and nothing in a loaded game could tell an entry it can no longer read from a structure
 * nobody has ever found - so the separator, the field order and the flag letters are fixed once and
 * for good.
 *
 * <p>The two moments and the state letters are this family's fixed fields and the holder its
 * free-form one, a faction id a mod may spell with anything at all - so the holder runs to the end
 * of the entry and reads back verbatim however it is spelt.
 *
 * <p>An entry that cannot be parted into its four fields, or whose moments are not moments, reads
 * as existence alone. The register holding an entry at all is the record that somebody found the
 * structure, so that much survives an entry written by a build that spelt the rest differently, and
 * it is healed at the next observation of the same structure.
 *
 * <p>Package-private: what a structure observation means is this family's own, and the register
 * beside it is the only thing that ever spells one.
 */
final class StructureObservationCodec implements ObservationCodec<StructureObservation> {

    // What the state letters may say. Single characters so a state added later costs a letter
    // rather than a field, and stable once shipped for the reason the separator is.
    private static final char DISRUPTED_FLAG = 'd';
    private static final char NON_FUNCTIONAL_FLAG = 'n';

    // How many fields one entry has, and what parts them. Split to exactly this many, so a
    // separator inside the holder id is part of the id rather than a boundary.
    private static final int ENTRY_FIELD_COUNT = 4;
    private static final String FIELD_SEPARATOR = "|";
    private static final String FIELD_SEPARATOR_PATTERN = "\\|";

    // Where each field stands in an entry, fixed fields first.
    private static final int OWNERSHIP_MOMENT_FIELD = 0;
    private static final int OPERATION_MOMENT_FIELD = 1;
    private static final int STATE_FLAGS_FIELD = 2;
    private static final int HOLDER_FIELD = 3;

    @Override
    public StructureObservation decodeObservation(String storedObservation) {

        if (!KmuValues.hasText(storedObservation)) {
            // An entry stating nothing still says the structure was found, that being what a
            // register entry is. There is no weaker reading to fall to and no reason to lose it.
            return StructureObservation.createExistenceOnlyObservation();
        }
        var fields = storedObservation.split(FIELD_SEPARATOR_PATTERN, ENTRY_FIELD_COUNT);

        if (fields.length != ENTRY_FIELD_COUNT) {
            return StructureObservation.createExistenceOnlyObservation();
        }
        var ownershipSeenTimestamp = readStoredMoment(fields[OWNERSHIP_MOMENT_FIELD]);
        var operationDetectedTimestamp = readStoredMoment(fields[OPERATION_MOMENT_FIELD]);
        var storedFlags = fields[STATE_FLAGS_FIELD].trim();

        if (ownershipSeenTimestamp == null
                || operationDetectedTimestamp == null
                || !isStateFlagsField(storedFlags)) {

            return StructureObservation.createExistenceOnlyObservation();
        }
        return new StructureObservation(
            Optional.ofNullable(fields[HOLDER_FIELD]).filter(KmuValues::hasText),
            storedFlags.indexOf(DISRUPTED_FLAG) >= 0,
            storedFlags.indexOf(NON_FUNCTIONAL_FLAG) >= 0,
            ownershipSeenTimestamp,
            operationDetectedTimestamp);
    }

    @Override
    public String encodeObservation(StructureObservation observation) {

        return String.join(
            FIELD_SEPARATOR,
            writeStoredMoment(observation.ownershipSeenTimestamp()),
            writeStoredMoment(observation.operationDetectedTimestamp()),
            writeStateFlags(observation),
            observation.holderFactionId().orElse(""));
    }

    // Whether every letter in the state field is one this codec spells. A letter it does not know
    // is an entry written by something else, which is read as existence alone rather than guessed
    // at - a state field half understood would state that a structure was working when the entry
    // may well have said the opposite.
    private static boolean isStateFlagsField(String storedFlags) {

        for (var index = 0; index < storedFlags.length(); index++) {

            var storedFlag = storedFlags.charAt(index);

            if (storedFlag != DISRUPTED_FLAG && storedFlag != NON_FUNCTIONAL_FLAG) {
                return false;
            }
        }
        return true;
    }

    // One stored moment, empty where the field states none, and null where the field states
    // something that is not a moment at all - which is the whole entry unreadable rather than one
    // half of it missing, since a field that is not a number cannot be placed in the entry either.
    private static Optional<Long> readStoredMoment(String storedMoment) {

        if (!KmuValues.hasText(storedMoment)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(storedMoment.trim()));

        } catch (NumberFormatException notAMoment) {
            return null;
        }
    }

    // The state as its letters, in the one order they are ever written in, so two saves recording
    // the same state hold the same characters.
    private static String writeStateFlags(StructureObservation observation) {

        var storedFlags = new StringBuilder();

        if (observation.isDisrupted()) {
            storedFlags.append(DISRUPTED_FLAG);
        }
        if (observation.isNonFunctional()) {
            storedFlags.append(NON_FUNCTIONAL_FLAG);
        }
        return storedFlags.toString();
    }

    // A moment as the entry spells it, an unstated one leaving its field empty.
    private static String writeStoredMoment(Optional<Long> observedTimestamp) {

        return observedTimestamp
            .map(String::valueOf)
            .orElse("");
    }
}
