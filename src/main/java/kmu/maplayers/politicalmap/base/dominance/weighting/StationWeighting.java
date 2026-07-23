package kmu.maplayers.politicalmap.base.dominance.weighting;

import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;

/**
 * The attached-station factor of a dominance pass: whether a defensive station lifts
 * its market's dominance weight, by how much, the reduced rate a hidden market's
 * station earns, and how far low stability erodes the bonus.
 *
 * <p>Grouped so the station half of the weighting travels as one value, keeping the
 * factor's toggle, weight, hidden-market rate, and penalty together where
 * {@link KnownMarketFootprints} applies them.
 *
 * @param isWeighted          whether a market with an attached defensive station gains
 *                            the station weight in size points
 * @param weight              the size points an attached defensive station adds to a
 *                            visible colony - a hidden market earns this scaled by
 *                            {@code hiddenMarketRate} - so the player can dial how much a
 *                            station is worth
 * @param hiddenMarketRate    the fraction of the station weight a station on a hidden
 *                            market earns, so a fortified secret base reads above a bare
 *                            unstationed hidden market without matching an openly held
 *                            stationed colony
 * @param lowStabilityPenalty how much the station bonus is cut at zero stability, 0..1;
 *                            applied only while the rule's stability weighting is on
 */
public record StationWeighting(
        boolean isWeighted,
        double weight,
        double hiddenMarketRate,
        double lowStabilityPenalty) {
}
