package kmu.maplayers.politicalmap.base.dominance;

/**
 * One faction's markets in a single star system, paired: the dominance {@link MarketFootprint} the
 * rule ranks and the raw summed colony size the picker's "market size" metric reads. The two travel
 * together because both come from one walk of the system's markets under the same "counts as a
 * colony" filter, so reading them apart would walk - and filter - the economy twice. Raw market size
 * stays out of {@link MarketFootprint}, which is scoped to the dominance-weight quantities the rule
 * compares; this pairing is the one place the two otherwise-separate quantities are carried side by
 * side from the single read that produces them.
 *
 * @param footprint  the faction's dominance footprint in the system
 * @param marketSize the faction's summed raw colony size in the system
 */
public record FactionMarketContribution(
    MarketFootprint footprint,
    int marketSize) {

    /** A faction with no counted markets; the identity a market fold begins from. */
    public static final FactionMarketContribution EMPTY =
        new FactionMarketContribution(MarketFootprint.EMPTY, 0);

    /**
     * Folds another contribution into this one, combining two already-reduced contributions as a
     * bloc: the footprints merge as the dominance rule expects and the raw market sizes sum. This is
     * how the grouping collapses an alliance's member factions into the alliance's one contribution.
     *
     * @param other the other contribution to combine into this one
     * @return a new contribution holding both
     */
    public FactionMarketContribution merge(FactionMarketContribution other) {
        return new FactionMarketContribution(
            footprint.merge(other.footprint()),
            marketSize + other.marketSize());
    }

    /**
     * Folds one owned market into this contribution: its dominance weight into the footprint and its
     * raw colony size into the market-size total.
     *
     * @param marketWeight   the market's dominance weight, folded into the footprint's totals
     * @param isPlanetMarket whether the market sits on a planet rather than a station, which the
     *                       footprint tracks for the dominance rule's planet tie-break
     * @param marketSize     the market's raw colony size, added to the market-size total
     * @return a new contribution that includes this market
     */
    FactionMarketContribution addMarket(int marketWeight, boolean isPlanetMarket, int marketSize) {
        return new FactionMarketContribution(
            footprint.addMarket(marketWeight, isPlanetMarket),
            this.marketSize + marketSize);
    }
}
