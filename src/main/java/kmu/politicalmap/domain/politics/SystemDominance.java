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
 * <p>Dominance is decided by a four-level comparison, each level breaking a tie
 * in the one above so the ordering is total and the winner deterministic:
 * <ol>
 *   <li>combined market size across all of the faction's markets;</li>
 *   <li>largest single market;</li>
 *   <li>planet-only size, ranking planets above stations;</li>
 *   <li>lowest faction id, so map iteration order never decides the winner.</li>
 * </ol>
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
    // combined size, then largest single market, then planet size, with the
    // lower faction id as the final tie-break so the result never depends on map
    // iteration order.
    private static boolean isMoreDominant(FactionFootprint candidate, String candidateId,
            FactionFootprint leader, String leaderId) {
        if (candidate.totalSize() != leader.totalSize()) {
            return candidate.totalSize() > leader.totalSize();
        }
        if (candidate.largestMarketSize() != leader.largestMarketSize()) {
            return candidate.largestMarketSize() > leader.largestMarketSize();
        }
        if (candidate.planetSize() != leader.planetSize()) {
            return candidate.planetSize() > leader.planetSize();
        }
        return candidateId.compareTo(leaderId) < 0;
    }
}
