package kmu.politicalmap.domain;

/**
 * One faction's market footprint within a single star system, reduced to the
 * three quantities the dominance rule compares.
 *
 * <p>A plain value with no Starsector types so {@link SystemDominance} can be
 * exercised on hand-built inputs: {@link SectorPolitics} reads the live economy
 * and folds each owned market into its owner's footprint, while the rule only
 * ever sees these totals.
 */
public record FactionFootprint(int totalSize, int largestMarketSize, int planetSize) {

    // A faction with no counted markets; the identity for folding markets in.
    public static final FactionFootprint EMPTY = new FactionFootprint(0, 0, 0);

    /**
     * Folds one owned market into this footprint.
     *
     * @param marketSize     the market's size, added to the combined total and,
     *                       when the market sits on a planet, to the planet total
     * @param isPlanetMarket whether the market is on a planet rather than a
     *                       station, which the rule prefers at an otherwise exact
     *                       size tie
     * @return a new footprint that includes this market
     */
    public FactionFootprint addMarket(int marketSize, boolean isPlanetMarket) {
        return new FactionFootprint(
                totalSize + marketSize,
                Math.max(largestMarketSize, marketSize),
                isPlanetMarket ? planetSize + marketSize : planetSize);
    }
}
