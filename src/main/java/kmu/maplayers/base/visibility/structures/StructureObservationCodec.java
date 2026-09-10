package kmu.maplayers.base.visibility.structures;

import kmu.maplayers.base.visibility.observations.ObservationCodec;
import kmu.util.KmuValues;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What one structure observation says, written as the register holds it and read back out of it:
 * the moment ownership was last seen, the moment operation was last detected, the letters of
 * whatever was out of action, then who was holding it.
 *
 * <p><strong>The stored form is save state.</strong> Every entry in every existing save is spelt
 * this way, and nothing in a loaded game could tell an entry it can no longer read from a structure
 * nobody has ever found - so the separator, the field order and the fault letters are fixed once
 * and for good.
 *
 * <p>The two moments and the fault letters are this family's fixed fields and the holder its
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

    // What each fault is spelt as. Single characters so a fault added later costs a letter rather
    // than a field, and stable once shipped for the reason the separator is.
    private static final char DISRUPTED_LETTER = 'd';
    private static final char NON_FUNCTIONAL_LETTER = 'n';

    // How many fields one entry has, and what parts them. The pattern is derived from the
    // separator rather than spelt beside it, so the two cannot come to disagree - a split on one
    // character while entries are written with another reads every save as unparseable.
    private static final int ENTRY_FIELD_COUNT = 4;
    private static final String FIELD_SEPARATOR = "|";
    private static final Pattern FIELD_SEPARATOR_PATTERN =
        Pattern.compile(Pattern.quote(FIELD_SEPARATOR));

    // Where each field stands in an entry, fixed fields first.
    private static final int OWNERSHIP_MOMENT_FIELD = 0;
    private static final int OPERATION_MOMENT_FIELD = 1;
    private static final int FAULT_LETTERS_FIELD = 2;
    private static final int HOLDER_FIELD = 3;

    @Override
    public StructureObservation decodeObservation(String storedObservation) {

        if (!KmuValues.hasText(storedObservation)) {
            // An entry stating nothing still says the structure was found, that being what a
            // register entry is. There is no weaker reading to fall to and no reason to lose it.
            return StructureObservation.createExistenceOnlyObservation();
        }
        var fields = FIELD_SEPARATOR_PATTERN.split(storedObservation, ENTRY_FIELD_COUNT);

        if (fields.length != ENTRY_FIELD_COUNT) {
            return StructureObservation.createExistenceOnlyObservation();
        }
        var ownershipSeenTimestamp = readStoredMoment(fields[OWNERSHIP_MOMENT_FIELD]);
        var operationDetectedTimestamp = readStoredMoment(fields[OPERATION_MOMENT_FIELD]);
        var faults = readStoredFaults(fields[FAULT_LETTERS_FIELD].trim());

        if (ownershipSeenTimestamp == null || operationDetectedTimestamp == null
                || faults == null) {

            return StructureObservation.createExistenceOnlyObservation();
        }
        return new StructureObservation(
            Optional.ofNullable(fields[HOLDER_FIELD]).filter(KmuValues::hasText),
            faults,
            ownershipSeenTimestamp,
            operationDetectedTimestamp);
    }

    @Override
    public String encodeObservation(StructureObservation observation) {

        return String.join(
            FIELD_SEPARATOR,
            writeStoredMoment(observation.ownershipSeenTimestamp()),
            writeStoredMoment(observation.operationDetectedTimestamp()),
            writeStoredFaults(observation.faults()),
            observation.holderFactionId().orElse(""));
    }

    // The whole fault field, or null where a letter in it is one this codec does not spell.
    //
    // Half an entry understood is worse than none of it: a fault field read past a letter written
    // by something else would state that a structure was working when the entry it came from may
    // well have said the opposite.
    private static Set<StructureFault> readStoredFaults(String storedFaults) {

        var faults = EnumSet.noneOf(StructureFault.class);

        for (var index = 0; index < storedFaults.length(); index++) {

            var fault = resolveFaultBy(storedFaults.charAt(index));

            if (fault == null) {
                return null;
            }
            faults.add(fault);
        }
        return faults;
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

    // Which fault a letter stands for, or null where no fault is spelt that way.
    private static StructureFault resolveFaultBy(char storedLetter) {

        for (var fault : StructureFault.values()) {

            if (resolveStoredLetter(fault) == storedLetter) {
                return fault;
            }
        }
        return null;
    }

    // How one fault is spelt. A switch with no default rather than a letter carried on the fault
    // itself: the stored spelling is this codec's business, and a fault added to the enum stops
    // this compiling until somebody says what it is written as.
    private static char resolveStoredLetter(StructureFault fault) {

        return switch (fault) {
            case DISRUPTED -> DISRUPTED_LETTER;
            case NON_FUNCTIONAL -> NON_FUNCTIONAL_LETTER;
        };
    }

    // The faults as their letters, walked in the enum's own order so two saves recording the same
    // faults hold the same characters whatever order they were collected in.
    private static String writeStoredFaults(Set<StructureFault> faults) {

        var storedFaults = new StringBuilder();

        for (var fault : StructureFault.values()) {

            if (faults.contains(fault)) {
                storedFaults.append(resolveStoredLetter(fault));
            }
        }
        return storedFaults.toString();
    }

    // A moment as the entry spells it, an unstated one leaving its field empty.
    private static String writeStoredMoment(Optional<Long> observedTimestamp) {

        return observedTimestamp
            .map(String::valueOf)
            .orElse("");
    }
}
