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
 * <p>The total is summed from the tiers rather than stored beside them, so a reader adding
 * the tier lines up always lands on the total the same value reports. Only the weighted
 * total is offered: a headcount across tiers that count for different amounts is a number
 * nothing can be concluded from, so what each tier fielded is read off the tier itself.
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
}
