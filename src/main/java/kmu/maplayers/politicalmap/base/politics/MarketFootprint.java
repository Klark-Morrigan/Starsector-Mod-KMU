package kmu.maplayers.politicalmap.base.politics;

/**
 * One owner's market footprint within a single star system, reduced to the
 * three quantities the dominance rule compares. Named for what it aggregates -
 * an owner's markets - and kept free of who the owner is, so the same value
 * serves any way markets are grouped into an owner.
 *
 * <p>Every quantity is a dominance weight - a market's size rating scaled by
 * its stability on the fixed-point grid {@link KnownMarketFootprints} defines -
 * so the rule ranks what each market is worth, not merely how big it is.
 *
 * <p>A plain value with no Starsector types so {@link SystemDominance} can be
 * exercised on hand-built inputs: {@link SectorPolitics} reads the live economy
 * and folds each owned market into its owner's footprint, while the rule only
 * ever sees these totals.
 */
public record MarketFootprint(int totalWeight, int largestMarketWeight, int planetWeight) {

    // An owner with no counted markets; the identity for folding markets in.
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
}
