package kmu.maplayers.politicalmap.base.dominance;

import kmlib.starsector.entities.EntityMapIcon;

import java.util.Optional;

/**
 * The attached-station part of one market's dominance weight: the station that earned it,
 * what a station is worth before anything is taken off, and the two cuts taken off it.
 *
 * <p>Only present when the station rule actually ran for the market - the player has the
 * factor on at a non-zero weight and the market owns a station - so its presence is itself
 * the answer to "did a station move this number", and its absence needs no separate flag.
 *
 * <p>Identifying the station rather than merely reporting a bonus is what lets a reader tie the
 * number to something they can see on the map, which is why the name travels with the glyph
 * the map marks that station by: a system's stations are told apart on the map by their icon
 * as much as by their name.
 *
 * @param stationName                 the display name of the market's own orbital station
 * @param stationIcon                 the glyph the sector map marks that station with, or empty
 *                                    where it carries none. Recorded where the station entity was
 *                                    found rather than looked up again by whatever draws the name,
 *                                    so the glyph shown can only belong to the very station whose
 *                                    bonus is stated beside it
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
    Optional<EntityMapIcon> stationIcon,
    double weight,
    double hiddenMarketPenaltyFraction,
    double stabilityPenaltyFraction,
    double contribution) {
}
