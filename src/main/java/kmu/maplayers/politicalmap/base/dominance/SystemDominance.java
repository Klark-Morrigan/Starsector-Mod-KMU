package kmu.maplayers.politicalmap.base.dominance;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Picks the faction that dominates a star system from its market footprint.
 *
 * <p>Pure rule, no Starsector types: the caller hands in each faction's
 * {@link MarketFootprint} in one system, and this returns the id of the
 * faction that holds the most. Keeping it free of {@code MarketAPI} / economy
 * access lets the dominance rule be exercised directly on hand-built inputs,
 * independent of how the live markets are read (that is
 * {@link KnownMarketFootprints}'s job).
 *
 * <p>Dominance is decided by a four-level comparison of the footprints' weights
 * (each market's stability-scaled worth, not its raw size), each level breaking
 * a tie in the one above so the ordering is total and the winner deterministic:
 * <ol>
 *   <li>combined weight across all of the faction's markets;</li>
 *   <li>heaviest single market;</li>
 *   <li>planet-only weight, ranking planets above stations;</li>
 *   <li>a caller-supplied tie-break over the tied ids - by default the lowest
 *       id, so map iteration order never decides the winner; the live pass
 *       instead breaks the tie by system geometry.</li>
 * </ol>
 *
 * <p>Who is ranked at all is the caller's to say, through a candidacy predicate
 * over the ids ({@link BlocCandidacy}). Only candidates compete; where no
 * footprint is one, the whole map is ranked instead, so a holder barred from the
 * contest still holds what nobody contests. The bar is on the id and never on
 * the weight, so every score this rule compares is the one the weighting
 * produced.
 *
 * <p>Weights arrive as exact integers (the fixed-point grid the footprint read
 * rounds onto), so every comparison here is exact and the tie-break is only
 * reached on a genuine tie - no epsilon math inside the rule. Confining the
 * tie-break to a comparator over ids keeps this rule pure while letting the
 * live pass resolve a tie by which bloc holds the market nearest the system
 * centre (see {@link MarketProximityTieBreak}), a call it need make only when a
 * tie actually arises.
 */
public final class SystemDominance {

    private SystemDominance() {
    }

    /**
     * Resolves the dominant faction for one system, breaking an exact tie on
     * every weight level by lowest id.
     *
     * @param footprintByFactionId each faction's footprint in the system; an
     *                             empty map means no owned markets
     * @param candidacy            which ids may win the system; the rest are
     *                             ranked only where no candidate is present
     * @return the dominant faction's id, or {@code null} when the map is empty
     *         (an uninhabited system has no holder)
     */
    public static String resolveDominantFactionId(
            Map<String, MarketFootprint> footprintByFactionId,
            Predicate<String> candidacy) {
        return resolveDominantFactionId(
            footprintByFactionId,
            Comparator.naturalOrder(),
            candidacy);
    }

    /**
     * Resolves the dominant faction for one system, breaking an exact tie on
     * every weight level with the supplied comparator over the tied ids.
     *
     * <p>The three weight levels settle almost every system; the comparator is
     * consulted only when two blocs tie on all three, so a caller that resolves
     * ties by system geometry pays that cost only on a genuine tie. It must
     * impose a total order over the ids so the winner never depends on map
     * iteration order.
     *
     * @param footprintByFactionId each faction's footprint in the system; an
     *                             empty map means no owned markets
     * @param tieBreak             consulted only when two blocs tie on all three
     *                             weight levels; the id it orders first wins
     * @param candidacy            which ids may win the system; the rest are
     *                             ranked only where no candidate is present
     * @return the dominant faction's id, or {@code null} when the map is empty
     *         (an uninhabited system has no holder)
     */
    public static String resolveDominantFactionId(
            Map<String, MarketFootprint> footprintByFactionId,
            Comparator<String> tieBreak,
            Predicate<String> candidacy) {

        var dominantId = resolveLeaderAmong(footprintByFactionId, tieBreak, candidacy);

        // Nobody in the running means nobody to keep out: the contest reopens to every footprint,
        // so a system whose only presence is barred keeps exactly the holder it has always had.
        return dominantId != null
            ? dominantId
            : resolveLeaderAmong(footprintByFactionId, tieBreak, BlocCandidacy.NONE_BARRED);
    }

    // The top of one pass over the footprints, counting only the ids the candidacy admits. Answers
    // null where it admitted none, which is what the reopened second pass is taken on.
    private static String resolveLeaderAmong(
            Map<String, MarketFootprint> footprintByFactionId,
            Comparator<String> tieBreak,
            Predicate<String> candidacy) {

        String dominantId = null;
        MarketFootprint dominant = null;

        for (var entry : footprintByFactionId.entrySet()) {
            var factionId = entry.getKey();

            if (!candidacy.test(factionId)) {
                continue;
            }
            var footprint = entry.getValue();

            if (dominantId == null
                    || isMoreDominant(footprint, factionId, dominant, dominantId, tieBreak)) {
                dominantId = factionId;
                dominant = footprint;
            }
        }
        return dominantId;
    }

    // Whether the candidate outranks the current leader on the four-level chain:
    // combined weight, then heaviest single market, then planet weight, with the
    // supplied comparator as the final tie-break so the result never depends on
    // map iteration order.
    private static boolean isMoreDominant(
            MarketFootprint candidate,
            String candidateId,
            MarketFootprint leader,
            String leaderId,
            Comparator<String> tieBreak) {

        if (candidate.totalWeight() != leader.totalWeight()) {
            return candidate.totalWeight() > leader.totalWeight();
        }
        if (candidate.largestMarketWeight() != leader.largestMarketWeight()) {
            return candidate.largestMarketWeight() > leader.largestMarketWeight();
        }
        if (candidate.planetWeight() != leader.planetWeight()) {
            return candidate.planetWeight() > leader.planetWeight();
        }
        return tieBreak.compare(candidateId, leaderId) < 0;
    }
}
