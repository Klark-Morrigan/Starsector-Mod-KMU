package kmu.maplayers.politicalmap.base.dominance;

/**
 * One holder's market footprint within a single star system, reduced to the
 * three quantities the dominance rule compares. Named for what it aggregates -
 * an holder's markets - and kept free of who the holder is, so the same value
 * serves any way markets are grouped into an holder.
 *
 * <p>Every quantity is a dominance weight - a market's size rating scaled by
 * its stability on the fixed-point grid {@link KnownMarketFootprints} defines -
 * so the rule ranks what each market is worth, not merely how big it is.
 *
 * <p>A plain value with no Starsector types so {@link SystemDominance} can be
 * exercised on hand-built inputs: {@link KnownMarketFootprints} reads the live
 * economy and folds each owned market into its holder's footprint, while the rule
 * only ever sees these totals.
 */
public record MarketFootprint(
    int totalWeight,
    int largestMarketWeight,
    int planetWeight) {

    // An holder with no counted markets; the identity for folding markets in.
    public static final MarketFootprint EMPTY = new MarketFootprint(0, 0, 0);

    /**
     * Folds one owned market into this footprint.
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
            totalWeight + marketWeight,
            Math.max(largestMarketWeight, marketWeight),
            isPlanetMarket ? planetWeight + marketWeight : planetWeight);
    }

    /**
     * Folds another holder's footprint into this one, combining two already-reduced
     * footprints as a bloc.
     *
     * <p>Merging is exactly folding the other footprint's markets in without
     * re-reading them: the combined weight and planet weight sum, while the heaviest
     * single market is the larger of the two - a bloc's biggest market is the bigger
     * of its members' biggest, not their sum. This is how a grouping collapses an
     * alliance's member factions into one footprint the dominance rule ranks as a
     * unit; folding {@link #EMPTY} in leaves a footprint unchanged, so a lone-faction
     * bloc under the identity grouping is untouched.
     *
     * @param other the other holder's footprint to combine into this one
     * @return a new footprint holding both holders' markets
     */
    public MarketFootprint merge(MarketFootprint other) {
        return new MarketFootprint(
            totalWeight + other.totalWeight(),
            Math.max(largestMarketWeight, other.largestMarketWeight()),
            planetWeight + other.planetWeight());
    }
}
