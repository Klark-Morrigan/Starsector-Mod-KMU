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
 * <p>How much of a membership is at odds is answered here too, at both tiers and off the same walk
 * over the same pairs as the yes-or-no. A caller filing a bloc under one heading and stating a number
 * worked out elsewhere could draw a count contradicting the heading it sits under - a bloc listed as
 * friendly saying none of it is.
 *
 * <p>Every answer reads its pairs from the faction named first, which is what keeps the two counts
 * and the yes-or-no one question. A disposition is a fact one faction holds about another, so a rule
 * asking the same pair from either end is a rule that can sort a faction one way and count it the
 * other wherever a supplied disposition is not returned alike both ways round.
 *
 * <p>Pure rule with no Starsector types: the memberships arrive as plain IDs and the faction-level
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

        // Friendliness is a positive claim about two blocs, so a bloc made of nobody leaves nothing
        // to make it of. Answered false rather than as the vacuous truth counting no members at all
        // would otherwise report, which would file a bloc nothing is known about under a heading
        // asserting something about it. The other side being empty needs no guard of its own: no
        // member is friendly with nobody, so every one of them is counted at odds below.
        return !blocFactionIds.isEmpty()
            && countMembersAtOddsWith(blocFactionIds, otherBlocFactionIds) == 0;
    }

    /**
     * How many of one bloc's members are not friendly with the whole of another - the number a row
     * stating how far its heading reaches is built from.
     *
     * <p>Counted per member rather than per pair, so a faction at odds with two of the other bloc
     * counts once: what is being asked is how much of this membership the heading is false of.
     *
     * @param blocFactionIds      the membership being counted
     * @param otherBlocFactionIds the bloc it is measured against, whole
     * @return how many members fall short of friendly
     */
    public int countMembersAtOddsWith(
            Set<String> blocFactionIds,
            Set<String> otherBlocFactionIds) {

        var atOddsCount = 0;

        for (var factionId : blocFactionIds) {

            if (!isFactionFriendlyWithAll(factionId, otherBlocFactionIds)) {
                atOddsCount++;
            }
        }
        return atOddsCount;
    }

    /**
     * How many of a bloc's members one faction is not friendly with - the number a row about that
     * faction rather than about a bloc is built from.
     *
     * <p>The other reading of the same walk: this counts the far side a faction falls short of, where
     * {@link #countMembersAtOddsWith} counts the near side falling short of a whole bloc. Both are
     * taken from the faction named first, so a faction sorted one way by
     * {@link #areBlocsFriendly} cannot be counted the other way here - which asking the pair from the
     * bloc's end would let happen wherever a disposition is not returned alike both ways round.
     *
     * @param factionId           the faction being counted for
     * @param otherBlocFactionIds the bloc it is measured against
     * @return how many of that bloc it falls short of friendly with
     */
    public int countFactionsAtOddsWith(String factionId, Set<String> otherBlocFactionIds) {

        var atOddsCount = 0;

        for (var otherFactionId : otherBlocFactionIds) {

            if (!factionFriendliness.test(factionId, otherFactionId)) {
                atOddsCount++;
            }
        }
        return atOddsCount;
    }

    // Whether one faction is above neutral with every one of a bloc's members - the reading every
    // public answer here is composed from, so no two of them can be worked out of pairs read in
    // opposite directions.
    //
    // A bloc made of nobody is nobody to be friendly with, on the same reasoning the whole rule is
    // a positive claim: there is no pair to make the claim of. Which is why this is not simply the
    // count above reaching nought - a faction is at odds with none of an empty bloc and friendly with
    // none of it either.
    private boolean isFactionFriendlyWithAll(String factionId, Set<String> otherBlocFactionIds) {

        return !otherBlocFactionIds.isEmpty()
            && countFactionsAtOddsWith(factionId, otherBlocFactionIds) == 0;
    }
}
