package kmu.politicalmap.domain.politics;

import java.util.Map;

/**
 * Picks the faction that dominates a star system from its market footprint.
 *
 * <p>Pure rule, no Starsector types: the caller hands in each faction's
 * {@link FactionFootprint} in one system, and this returns the id of the
 * faction that holds the most. Keeping it free of {@code MarketAPI} / economy
 * access lets the dominance rule be exercised directly on hand-built inputs,
 * independent of how the live markets are read (that is
 * {@link SectorPolitics}'s job).
 *
 * <p>Dominance is decided by a four-level comparison of the footprints' weights
 * (each market's stability-scaled worth, not its raw size), each level breaking
 * a tie in the one above so the ordering is total and the winner deterministic:
 * <ol>
 *   <li>combined weight across all of the faction's markets;</li>
 *   <li>heaviest single market;</li>
 *   <li>planet-only weight, ranking planets above stations;</li>
 *   <li>lowest faction id, so map iteration order never decides the winner.</li>
 * </ol>
 *
 * <p>Weights arrive as exact integers (the fixed-point grid the footprint read
 * rounds onto), so every comparison here is exact and the id backstop is only
 * reached on a genuine tie - no epsilon math inside the rule.
 */
public final class SystemDominance {

    private SystemDominance() {
    }

    /**
     * Resolves the dominant faction for one system.
     *
     * @param footprintByFactionId each faction's footprint in the system; an
     *                             empty map means no owned markets
     * @return the dominant faction's id, or {@code null} when the map is empty
     *         (an uninhabited system has no owner)
     */
    public static String resolveDominantFactionId(
            Map<String, FactionFootprint> footprintByFactionId) {
        String dominantId = null;
        FactionFootprint dominant = null;
        for (var entry : footprintByFactionId.entrySet()) {
            var factionId = entry.getKey();
            var footprint = entry.getValue();
            if (dominantId == null || isMoreDominant(footprint, factionId, dominant, dominantId)) {
                dominantId = factionId;
                dominant = footprint;
            }
        }
        return dominantId;
    }

    // Whether the candidate outranks the current leader on the four-level chain:
    // combined weight, then heaviest single market, then planet weight, with the
    // lower faction id as the final tie-break so the result never depends on map
    // iteration order.
    private static boolean isMoreDominant(FactionFootprint candidate, String candidateId,
            FactionFootprint leader, String leaderId) {
        if (candidate.totalWeight() != leader.totalWeight()) {
            return candidate.totalWeight() > leader.totalWeight();
        }
        if (candidate.largestMarketWeight() != leader.largestMarketWeight()) {
            return candidate.largestMarketWeight() > leader.largestMarketWeight();
        }
        if (candidate.planetWeight() != leader.planetWeight()) {
            return candidate.planetWeight() > leader.planetWeight();
        }
        return candidateId.compareTo(leaderId) < 0;
    }
}
