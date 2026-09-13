package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.SectorEntityToken;

import java.util.Collection;
import java.util.Set;

/**
 * The entities whose concealed colony is nevertheless common knowledge - a landmark the sector
 * points people at that happens to keep no comm directory, as against a base hiding from it.
 *
 * <p>Concealment is one flag on the market and it covers both, so nothing a colony read can see
 * parts them. It is not even always a statement about the place: vanilla sets it on a market it
 * builds for a station it means the player to visit and then declines to register with the economy,
 * so the flag is keeping a stand-in market off the books rather than keeping the station out of
 * sight. Neither does anything about where they stand part them: a base revealed by a rival's open
 * colony in the same system is still a base concealing itself, so a rule about who can see the
 * place would take the word away exactly where it is doing its work. What is left is identity, and
 * identity is what the game itself uses to recognise such a place.
 *
 * <p>Held as registered IDs rather than as a constant beside the reading, because a literal only
 * ever covers what shipped with the game: another mod's quest hub wearing the same shape wants the
 * same treatment, and the tag beside the IDs is how one opts in without this mod carrying a list of
 * other mods' content.
 *
 * <p>Empty until a composition root registers a set, which is the answer a registry with nothing
 * wired should give: every concealed colony is a secret until something says otherwise.
 *
 * <p>This says nothing about what the player may be shown. The colonies named here are concealed in
 * every sense a visibility rule cares about, and the one they are gated by was designed around one
 * of them - so the fact is kept apart from the gate rather than folded into a flag serving both.
 */
public final class OpenlyKnownColonyRegistry {

    /**
     * The tag an entity carries to be treated as openly known without its ID being registered.
     * Frozen once shipped: it is content, hung on entities that this mod does not build.
     */
    public static final String OPENLY_KNOWN_TAG = "kmu_openly_known_colony";

    // The registered IDs, keyed as the game keys an entity. Empty until a composition root supplies
    // the set at startup, before any sector map can open.
    private static Set<String> openlyKnownEntityIds = Set.of();

    private OpenlyKnownColonyRegistry() {
        // registry of static state, no instances.
    }

    /**
     * Records the entities whose concealment is public knowledge. Called once by the composition
     * root at startup: it is the only place a concrete entity is named, so the reading stays
     * ignorant of which of the sector's places are landmarks.
     *
     * @param entityIds the IDs to treat as openly known; null or empty registers nothing, leaving
     *                  every concealed colony a secret
     */
    public static void registerEntityIds(Collection<String> entityIds) {
        openlyKnownEntityIds = entityIds == null
            ? Set.of()
            : Set.copyOf(entityIds);
    }

    /**
     * Whether this entity's concealment is public knowledge.
     *
     * <p>The registered IDs and the tag are asked together rather than in a preferred order,
     * neither being a stronger statement than the other: one is what this mod knows of the sector
     * it shipped against, the other what another mod says of its own content.
     *
     * @param entity the entity a concealed colony stands on; a colony with none, and one whose
     *               entity the game never named, is a secret like any other
     * @return true when the entity is one the sector openly points at
     */
    public static boolean isOpenlyKnownEntity(SectorEntityToken entity) {

        if (entity == null) {
            return false;
        }
        var entityId = entity.getId();

        return (entityId != null && openlyKnownEntityIds.contains(entityId))
            || entity.hasTag(OPENLY_KNOWN_TAG);
    }
}
