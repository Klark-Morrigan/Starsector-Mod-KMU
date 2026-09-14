package kmu.maplayers.politicalmap.base.dominance.weighting;

import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;

/**
 * The fielded-patrol factor of a dominance pass: whether the patrols a colony fields
 * lift its dominance weight, the per-tier size points a small, medium, and large patrol
 * each add, and how far low stability erodes the bonus.
 *
 * <p>Grouped so the patrol half of the weighting travels as one value, keeping the
 * factor's toggle, tier weights, and penalty together where
 * {@link KnownMarketFootprints} applies them. The three tier weights pair with the
 * {@link kmlib.starsector.markets.PatrolCounts} the economy read returns.
 *
 * @param isWeighted          whether the patrols a colony fields add size points toward
 *                            its dominance, weighted by patrol size
 * @param smallWeight         the size points each small (light) patrol adds
 * @param mediumWeight        the size points each medium patrol adds
 * @param largeWeight         the size points each large (heavy) patrol adds
 * @param lowStabilityPenalty how much the patrol bonus is cut at zero stability, 0..1;
 *                            applied only while the rule's stability weighting is on
 */
public record PatrolWeighting(
    boolean isWeighted,
    double smallWeight,
    double mediumWeight,
    double largeWeight,
    double lowStabilityPenalty) {
}
