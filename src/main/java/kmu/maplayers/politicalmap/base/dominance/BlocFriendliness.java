package kmu.maplayers.politicalmap.base.dominance;

import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Whether one bloc is friendly with another: every faction of the one disposed above neutral toward
 * every faction of the other.
 *
 * <p>A disposition is a fact about two factions and a block of a hover box is a statement about two
 * blocs, and this is the bridge between them. Both memberships are read whole - a member holding no
 * colony where the question is being asked still counts - because a bloc-level fact answered over
 * only who happens to stand in one system is not the fact it claims to be: the same two alliances
 * would come out friendly over one system and contesting over the next on nothing but which subset
 * of each was present there, and a reader shown both readings has nothing to tell which was meant.
 *
 * <p>What that buys is one answer across the sector at one moment, and no more than that. A
 * disposition sliding past neutral still moves a bloc from one heading to another between hovers,
 * which is a box reporting a sector as it is rather than a rule to be fixed here.
 *
 * <p>Unanimity rather than a majority or a lead member, because either of those puts a heading over
 * factions the heading is false of - the fault a friendly block exists to fix, one level in. A bloc
 * this answers no for is not thereby hostile: it is a bloc no single statement is true of, and what
 * a caller does with that is its own.
 *
 * <p>Pure rule with no Starsector types: the memberships arrive as plain ids and the faction-level
 * disposition as a predicate over a pair of them, so the rule is exercised on hand-built inputs and
 * the live read binds where the two meet.
 */
public final class BlocFriendliness {

    private final BiPredicate<String, String> factionFriendliness;

    /**
     * Binds the faction-level answer the bloc-level one is composed from.
     *
     * @param factionFriendliness whether the first faction is disposed above neutral toward the
     *                            second
     */
    public BlocFriendliness(BiPredicate<String, String> factionFriendliness) {

        this.factionFriendliness = Objects.requireNonNull(
            factionFriendliness,
            "factionFriendliness");
    }

    /**
     * Whether two blocs are friendly - true only where every pair drawn across their memberships is.
     *
     * @param blocFactionIds      one bloc's whole membership, each faction asked as the first of a
     *                            pair
     * @param otherBlocFactionIds the other bloc's whole membership
     * @return true where every pair is above neutral
     */
    public boolean areBlocsFriendly(Set<String> blocFactionIds, Set<String> otherBlocFactionIds) {

        // Friendliness is a positive claim about two blocs, so a side made of nobody leaves nothing
        // to make it of. Answered false rather than as the vacuous truth walking no pairs at all
        // would otherwise report, which would file a bloc nothing is known about under a heading
        // asserting something about it.
        if (blocFactionIds.isEmpty() || otherBlocFactionIds.isEmpty()) {
            return false;
        }

        for (var factionId : blocFactionIds) {
            for (var otherFactionId : otherBlocFactionIds) {

                if (!factionFriendliness.test(factionId, otherFactionId)) {
                    return false;
                }
            }
        }
        return true;
    }
}
