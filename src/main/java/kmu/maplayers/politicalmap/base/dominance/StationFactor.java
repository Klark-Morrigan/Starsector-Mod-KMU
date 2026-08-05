package kmu.maplayers.politicalmap.base.dominance;

/**
 * The attached-station part of one market's dominance weight: the station that earned it,
 * what a station is worth before anything is taken off, and the two cuts taken off it.
 *
 * <p>Only present when the station rule actually ran for the market - the player has the
 * factor on at a non-zero weight and the market owns a station - so its presence is itself
 * the answer to "did a station move this number", and its absence needs no separate flag.
 *
 * <p>Naming the station rather than merely reporting a bonus is what lets a reader tie the
 * number to something they can see on the map.
 *
 * @param stationName                 the display name of the market's own orbital station
 * @param weight                      the size points a station is worth before either cut
 * @param hiddenMarketPenaltyFraction the share of that weight a hidden market forfeits,
 *                                    0..1; zero for a market held in the open
 * @param stabilityPenaltyFraction    the share of what remained that low stability removed,
 *                                    0..1; zero while the master stability weighting is off
 * @param contribution                this part's share of the market's weight in size
 *                                    points, after both cuts
 */
public record StationFactor(
    String stationName,
    double weight,
    double hiddenMarketPenaltyFraction,
    double stabilityPenaltyFraction,
    double contribution) {
}
