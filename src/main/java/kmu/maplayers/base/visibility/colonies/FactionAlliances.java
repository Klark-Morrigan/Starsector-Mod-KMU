package kmu.maplayers.base.visibility.colonies;

import java.util.Map;

/**
 * Which factions stand together, as the alliance each allied faction belongs to.
 *
 * <p>Read by the revelation rule, which credits a place's inhabitants with speaking about whatever
 * else stands there. An alliance is a standing arrangement to act as one, and a member handing a
 * partner's concealed base to a third party is doing the one thing the arrangement forbids - so an
 * ally is no more a witness against its partner than the partner is against itself.
 *
 * <p>Plain data, and deliberately not a live handle back into whatever mod maintains the
 * arrangement: the rule over it is exercised on hand-built input, with no game around it.
 *
 * <p>Kept as a faction's alliance rather than as each alliance's members, because the only question
 * ever asked of it is whether two factions are on the same side - which is two lookups here, and a
 * scan of every membership the other way round.
 *
 * @param allianceIdByFactionId the alliance each allied faction belongs to, by faction id; a faction
 *                              the map does not name is in none
 */
public record FactionAlliances(
    Map<String, String> allianceIdByFactionId) {

    /**
     * Nobody stands with anybody. What an installation with no alliances - and one whose game has no
     * such arrangement at all - reads as, leaving the rule exactly as it answers on faction identity
     * alone.
     */
    public static final FactionAlliances NONE = new FactionAlliances(Map.of());

    /**
     * Takes an immutable copy, and reads an absent map as an empty one - so a set folded once for a
     * pass cannot change under the projections reading it, and an unstated one allies nobody.
     */
    public FactionAlliances {

        allianceIdByFactionId = allianceIdByFactionId == null
            ? Map.of()
            : Map.copyOf(allianceIdByFactionId);
    }

    /**
     * Whether these two factions stand together.
     *
     * <p>A faction in no alliance is allied with nobody, itself included: standing alone is not a
     * relationship, and whether a colony's own owner is settling its place is the caller's own test
     * rather than this one's.
     *
     * @param factionId      one faction's id; a colony no faction holds names none, which allies it
     *                       with nobody
     * @param otherFactionId the other faction's id, read the same way
     * @return true when both name the same alliance
     */
    public boolean areFactionsAllied(String factionId, String otherFactionId) {

        // Asked before the map is, an unowned colony reaching this with no id at all - which the
        // immutable map refuses outright rather than answering.
        if (factionId == null || otherFactionId == null) {
            return false;
        }
        var allianceId = allianceIdByFactionId.get(factionId);

        return allianceId != null
            && allianceId.equals(allianceIdByFactionId.get(otherFactionId));
    }
}
