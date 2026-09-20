package kmu.maplayers.base.visibility.colonies;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Who is standing in one place whose word about whatever else is there would reach the player.
 *
 * <p>The owners settling a place, read among the alliances standing while it is read. The two
 * travel as one value because neither answers the question alone: a set of owners with no
 * alliances beside it credits a partner with selling out its own side, and alliances with no
 * owners have nobody to silence.
 *
 * <p>Folded once per place and spent by every colony judged there, a place holding a handful of
 * owners at most.
 *
 * @param settlingOwnerIds the owners of the colonies that settle the place; null holds nobody
 * @param alliances        who stands with whom; null reads as {@link FactionAlliances#NONE}, which
 *                         credits every other faction present with speaking
 */
record PlaceWitnesses(
    Set<String> settlingOwnerIds,
    FactionAlliances alliances) {

    /** Nobody is standing here, so nothing present can be spoken for. */
    static final PlaceWitnesses NONE = new PlaceWitnesses(Set.of(), FactionAlliances.NONE);

    /**
     * Takes an unmodifiable copy, so a set folded once for a place cannot change under the colonies
     * being judged against it.
     *
     * <p>Copied into a hash set rather than through {@code Set.copyOf}, which refuses a null
     * element outright. A colony whose owner carries no ID names none, and a settler like that is
     * a faction standing here whatever the game failed to call it - dropping it would quietly
     * credit the place with one witness fewer, and throwing would take down the whole projection
     * over one malformed market.
     */
    PlaceWitnesses {

        settlingOwnerIds = settlingOwnerIds == null
            ? Set.of()
            : Collections.unmodifiableSet(new HashSet<>(settlingOwnerIds));

        alliances = alliances == null ? FactionAlliances.NONE : alliances;
    }

    /**
     * Whether anybody standing here would speak about a colony this faction holds.
     *
     * <p>Owner-aware rather than a bare "anybody lives here", because the people keeping a secret
     * are exactly the ones a faction-blind test credits with telling it. A pirate base in a system
     * the pirates openly hold would otherwise be announced to the player by the pirates, which is
     * the one case the route was never arguing for - a rival's colony in the same place does talk,
     * and that is what the route is for.
     *
     * <p>The exception is read at the size the world keeps it, an ally being no likelier to hand
     * over a partner's concealed base than the partner is: an alliance is a standing arrangement to
     * act as one, and selling out a partner is the thing it forbids. So a member of the same
     * alliance is passed over exactly as the owner itself is, and whatever was being spoken about
     * falls back to the only witness it should ever have been credited to - the player's own
     * sighting.
     *
     * <p>A colony held by nobody in particular names no owner, and nobody joins no alliance, so it
     * is spoken for by whoever is here - unless that whoever is the same nobody, which is the one
     * arrangement in which this comparison withholds without an owner to compare.
     *
     * @param ownerId the faction holding the colony in question; a colony no faction holds names
     *                none, which is compared like any other name
     * @return true when at least one settler here is neither that faction nor allied with it
     */
    boolean wouldSpeakAbout(String ownerId) {

        for (var settlingOwnerId : settlingOwnerIds) {

            if (!Objects.equals(settlingOwnerId, ownerId)
                    && !alliances.areFactionsAllied(settlingOwnerId, ownerId)) {

                return true;
            }
        }
        return false;
    }
}
