package kmu.maplayers.politicalmap.base.dominance;

/**
 * One holder's market footprint within a single star system: how many markets it
 * holds there, plus the three weight quantities the dominance rule compares. Named
 * for what it aggregates - an holder's markets - and kept free of who the holder is,
 * so the same value serves any way markets are grouped into an holder.
 *
 * <p>Each of the three weights is a dominance weight - a market's size rating scaled
 * by its stability on the fixed-point grid {@link MarketWeights} defines - so
 * the rule ranks what each market is worth, not merely how big it is. The count
 * beside them is deliberately unweighted: it answers how many holdings there are
 * rather than what they are worth, which is what a readout of a system's makeup needs
 * and what no weight can be divided back into. It rides here rather than in a value
 * of its own because it folds along exactly the two paths the weights do, so a count
 * can never end up summed over a different set of markets than the weights beside it.
 *
 * <p>A plain value with no Starsector types so {@link SystemDominance} can be
 * exercised on hand-built inputs: {@link KnownMarketFootprints} reads the live
 * economy and folds each owned market into its holder's footprint, while the rule
 * only ever sees these totals.
 */
public record MarketFootprint(
    int marketCount,
    int totalWeight,
    int largestMarketWeight,
    int planetWeight) {

    // An holder with no counted markets; the identity for folding markets in.
    public static final MarketFootprint EMPTY = new MarketFootprint(0, 0, 0, 0);

    /**
     * Folds one owned market into this footprint.
     *
     * <p>The count rises by one however little the market weighs: a colony worth
     * nothing to the rule - collapsed stability, or a weightless holding - is still
     * a colony held there, and the count is what says so.
     *
     * @param marketWeight   the market's dominance weight, added to the combined
     *                       total and, when the market sits on a planet, to the
     *                       planet total
     * @param isPlanetMarket whether the market is on a planet rather than a
     *                       station, which the rule prefers at an otherwise exact
     *                       weight tie
     * @return a new footprint that includes this market
     */
    public MarketFootprint addMarket(int marketWeight, boolean isPlanetMarket) {
        return new MarketFootprint(
            marketCount + 1,
            totalWeight + marketWeight,
            Math.max(largestMarketWeight, marketWeight),
            isPlanetMarket ? planetWeight + marketWeight : planetWeight);
    }

    /**
     * Folds another holder's footprint into this one, combining two already-reduced
     * footprints as a bloc.
     *
     * <p>Merging is exactly folding the other footprint's markets in without
     * re-reading them: the market count, the combined weight and the planet weight all
     * sum, while the heaviest single market is the larger of the two - a bloc's biggest
     * market is the bigger of its members' biggest, not their sum. The counts sum
     * because the two holders' markets are disjoint sets of places: the read that
     * produced them banks one colony per place and owner, so no market can already be
     * counted on both sides. This is how a grouping collapses an alliance's member
     * factions into one footprint the dominance rule ranks as a unit; folding
     * {@link #EMPTY} in leaves a footprint unchanged, so a lone-faction bloc under the
     * identity grouping is untouched.
     *
     * @param other the other holder's footprint to combine into this one
     * @return a new footprint holding both holders' markets
     */
    public MarketFootprint merge(MarketFootprint other) {
        return new MarketFootprint(
            marketCount + other.marketCount(),
            totalWeight + other.totalWeight(),
            Math.max(largestMarketWeight, other.largestMarketWeight()),
            planetWeight + other.planetWeight());
    }
}
