package kmu.maplayers.politicalmap.base.politics;

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
record FactionMarketContribution(MarketFootprint footprint, int marketSize) {

    /** A faction with no counted markets; the identity a market fold begins from. */
    static final FactionMarketContribution EMPTY =
            new FactionMarketContribution(MarketFootprint.EMPTY, 0);

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
