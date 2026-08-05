package kmu.maplayers.politicalmap.base.dominance;

/**
 * The fielded-patrol part of one market's dominance weight, tier by tier: what each of the
 * three vanilla patrol tiers fielded and was worth, plus the one stability cut all three
 * took.
 *
 * <p>Only present when the patrol rule actually ran for the market - the player has the
 * factor on and a functional patrol HQ garrisons the colony - so its presence is itself the
 * answer to "did a garrison move this number".
 *
 * <p>The totals are summed from the tiers rather than stored beside them, so a reader adding
 * the tier lines up always lands on the total the same value reports.
 *
 * @param small                    the light-patrol tier
 * @param medium                   the medium-patrol tier
 * @param large                    the heavy-patrol tier
 * @param stabilityPenaltyFraction the share of the garrison's worth low stability removed,
 *                                 0..1; zero while the master stability weighting is off
 */
public record PatrolFactor(
    PatrolTierFactor small,
    PatrolTierFactor medium,
    PatrolTierFactor large,
    double stabilityPenaltyFraction) {

    /**
     * The garrison's share of the market's weight: what its three tiers folded in at
     * together, in size points, after the stability cut.
     *
     * @return the summed tier contributions
     */
    public double computeContribution() {
        return small.contribution() + medium.contribution() + large.contribution();
    }

    /**
     * How many patrols the colony fields across all three tiers, unweighted - the plain
     * headcount behind the weighted worth.
     *
     * @return the summed tier counts
     */
    public int computeTotalCount() {
        return small.count() + medium.count() + large.count();
    }
}
