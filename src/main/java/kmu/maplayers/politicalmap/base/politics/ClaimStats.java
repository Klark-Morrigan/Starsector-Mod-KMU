package kmu.maplayers.politicalmap.base.politics;

/**
 * The two whole-sector numbers the claims picker sorts and displays a claiming bloc by, computed
 * once per grouped pass and carried beside the bloc's identity. The claims-layer counterpart to
 * {@link DominanceStats}: a separate record rather than two more fields on that one, so a layer's
 * sort can neither see nor be widened by another layer's metrics - the claims vocabulary has no
 * domination, presence, or dominance score to offer, and the held layers have no claim count.
 *
 * <p>The two are deliberately different measures of a claimant. Claims counts exactly the territory
 * this layer paints, so it is the layer's primary metric. Market size answers the separate question
 * "how big is this claimant" and is summed across the <em>whole sector</em> rather than across the
 * systems it claims: a claimed system is usually one the claimant does not hold, so a claim-scoped
 * size would read zero for nearly every faction and rank nothing.
 *
 * <p>Because the two are scoped differently, a bloc with claims and a market size of zero is normal
 * rather than a defect - a faction that claims territory but holds no colony anywhere still paints
 * here, so it is still worth spotlighting.
 *
 * <p>Plain data with no Starsector types, so the aggregation is exercised on hand-built inputs.
 *
 * @param claims     the number of star systems the bloc claims, under the active grouping
 * @param marketSize the bloc's summed raw colony size across every market it owns anywhere in the
 *                   sector, not only in the systems it claims
 */
public record ClaimStats(
    int claims,
    int marketSize) {

    /** A bloc claiming nothing and holding nothing; the identity a per-system fold begins from. */
    public static final ClaimStats EMPTY = new ClaimStats(0, 0);

    /**
     * Folds one system the bloc claims into these stats.
     *
     * @return a new stats value counting this claim
     */
    public ClaimStats addClaim() {
        return new ClaimStats(claims + 1, marketSize);
    }

    /**
     * Folds one system's worth of the bloc's colonies into these stats. Kept apart from
     * {@link #addClaim} because the two metrics are scoped differently - a system contributes a
     * claim, a market size, both, or neither - so neither fold may assume the other ran.
     *
     * @param systemMarketSize the summed raw colony size of the bloc's markets in one system
     * @return a new stats value including those colonies
     */
    public ClaimStats addMarketSize(int systemMarketSize) {
        return new ClaimStats(claims, marketSize + systemMarketSize);
    }
}
