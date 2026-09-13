package kmu.maplayers.base.visibility.structures;

import kmu.util.KmuValues;

import java.util.Optional;
import java.util.Set;

/**
 * What a structure was last observed to be: who was holding it, whether it was working, and when
 * each of those two things was last established.
 *
 * <p>What parts this from a colony's sighting is the payload. A sighting says a colony was seen
 * standing somewhere; this says what the structure looked like. A structure carries no fog of its
 * own, so a live reading of its holder would name a faction in a system nobody has ever
 * approached, and would report every handover in a system visited once.
 *
 * <p><strong>Two moments, because the two axes go stale independently.</strong> Who holds a
 * structure is established by somebody standing there; whether it is working is established by
 * anyone living in the same system, and for a relay by the network it answers on. A structure
 * visited once and then watched for cycles by the colony it orbits has current operation and
 * four-cycle-old ownership, and one moment could not state both.
 *
 * <p><strong>An absent holder is the never-established reading, not an unheld structure.</strong> A
 * structure genuinely held by nobody is held by the neutral faction, which is a faction like any
 * other and is recorded by its own id.
 *
 * <p><strong>A running hack is deliberately not a field here.</strong> The sniffer a hack installs
 * pushes into the player's own comm queue for as long as it lasts, so the state reports itself from
 * anywhere and is read live wherever it is stated. Recorded instead, it would be written into the
 * save and go on being read out for years after the sniffer it describes had lapsed - nothing fires
 * when a hack expires.
 *
 * @param holderFactionId            who was holding the structure when ownership was last
 *                                   established, or empty where nobody has ever established it
 * @param faults                     what was out of action about the structure when its operation
 *                                   was last established; empty for one found working
 * @param ownershipSeenTimestamp     when the holder was last observed directly, on the campaign
 *                                   clock's own scale, or empty where there was no clock to read
 * @param operationDetectedTimestamp when the structure was last established to be working or not,
 *                                   by whichever route reached it, or empty for the same reason
 */
public record StructureObservation(
    Optional<String> holderFactionId,
    Set<StructureFault> faults,
    Optional<Long> ownershipSeenTimestamp,
    Optional<Long> operationDetectedTimestamp) {

    /**
     * Reads an unstated half as an unestablished one, so an observation carrying less than the full
     * set cannot fail late on whichever half was left out, and copies the faults so what a reader
     * is shown cannot be changed under it afterwards.
     */
    public StructureObservation {
        holderFactionId = readStatedHolderId(holderFactionId);
        faults = faults == null ? Set.of() : Set.copyOf(faults);
        ownershipSeenTimestamp = readStatedMoment(ownershipSeenTimestamp);
        operationDetectedTimestamp = readStatedMoment(operationDetectedTimestamp);
    }

    /**
     * Builds the observation that says only that the structure was seen at all.
     *
     * <p>The weakest reading an entry can carry, and the one an entry that cannot be parted falls
     * back to: the register holding an entry for a structure is itself the record that somebody
     * found it, whatever else the entry has lost.
     *
     * @return an observation naming no holder, no fault and no moment
     */
    public static StructureObservation createExistenceOnlyObservation() {

        return new StructureObservation(
            Optional.empty(),
            Set.of(),
            Optional.empty(),
            Optional.empty());
    }

    // A holder stated as nothing at all is a holder nobody established, so the two spellings of
    // that - no optional, and one holding a blank ID - cannot reach a reader as different answers.
    private static Optional<String> readStatedHolderId(Optional<String> holderFactionId) {

        if (holderFactionId == null) {
            return Optional.empty();
        }
        return holderFactionId.filter(KmuValues::hasText);
    }

    // The same for a moment: an unstated one is one nothing was ever timed by, which is what a
    // sector with no clock to read records and what a reader states no date beside.
    private static Optional<Long> readStatedMoment(Optional<Long> observedTimestamp) {

        return observedTimestamp == null
            ? Optional.empty()
            : observedTimestamp;
    }
}
