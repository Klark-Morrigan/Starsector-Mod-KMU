package kmu.maplayers.base.visibility.installations;

import java.util.Map;

/**
 * What the shipped table states, entity type by entity type: the hand-set half of how the map
 * treats a market-less place.
 *
 * <p>A value read once and asked many times. Admission and kind are put to the table for every
 * installation of every system a pass walks, and a file re-read per question would pay for the
 * whole sector's parsing over and over while giving each question its own chance to answer
 * differently.
 *
 * <p>Keyed by entity type rather than by entity, because a row is a statement about a kind of
 * place and not about one of them. Two stations of the same type are the same case, and a table
 * that could tell them apart would be a rule wearing a table's clothes.
 *
 * <p>Immutable, and knows nothing of where it came from. Whether the file was there, whether it
 * parsed, and what a malformed row costs are all the reader's questions; by the time a table
 * exists those are settled, and a caller holding one can only be told what it states.
 *
 * @param overridesByEntityTypeId what the file states about each type it mentions, by entity type
 *                                id
 */
public record InstallationOverrideTable(
    Map<String, InstallationOverride> overridesByEntityTypeId) {

    /** A table stating nothing - what a sector with no readable file behind it answers through. */
    public static final InstallationOverrideTable NONE = new InstallationOverrideTable(Map.of());

    /** Takes an immutable copy, and reads an absent map as a table stating nothing. */
    public InstallationOverrideTable {
        overridesByEntityTypeId = overridesByEntityTypeId == null
            ? Map.of()
            : Map.copyOf(overridesByEntityTypeId);
    }

    /**
     * What the file states about one entity type.
     *
     * @param entityTypeId the custom entity type's id; a type the file never mentions - and an
     *                     entity naming no type at all - reads as {@link InstallationOverride#NONE},
     *                     which leaves every question to the facts
     * @return what the table states about that type
     */
    public InstallationOverride readOverrideOf(String entityTypeId) {

        if (entityTypeId == null) {
            return InstallationOverride.NONE;
        }
        return overridesByEntityTypeId.getOrDefault(entityTypeId, InstallationOverride.NONE);
    }
}
