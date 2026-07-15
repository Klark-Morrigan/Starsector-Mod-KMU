package kmu.maplayers.politicalmap.base.politics.weighting;

import kmu.maplayers.politicalmap.base.politics.KnownMarketFootprints;
import kmu.settings.HiddenMarketScalingChoice;

/**
 * The base-size factor of a dominance pass: how a market's raw size rating enters
 * the dominance weight before the station and patrol bonuses are added, and how far
 * low stability erodes it.
 *
 * <p>Grouped so the base-size half of the weighting travels as one value rather than
 * four loose fields, keeping the factor's multiplier, hidden-market rating choice,
 * and penalty together where {@link KnownMarketFootprints} applies them.
 *
 * @param colonySizeWeight        the multiplier on each market's base size rating - a
 *                                visible colony's own size or a hidden market's chosen
 *                                rating - so the player can dial how much raw colony
 *                                size sways a system's dominant faction
 * @param hiddenMarketScaling     how a hidden market's base size rating is chosen:
 *                                {@code NORMAL} by its real size like any colony,
 *                                {@code FIXED} at {@code hiddenMarketFixedWeight}
 *                                regardless of size
 * @param hiddenMarketFixedWeight the fixed base size rating a hidden market folds in at
 *                                while its scaling is {@code FIXED}, in place of its real
 *                                size; unused while the scaling is {@code NORMAL}
 * @param lowStabilityPenalty     how much a colony's weighted base size is cut at zero
 *                                stability, 0..1 (1 removes it, 0 leaves it untouched);
 *                                applied only while the rule's stability weighting is on
 */
public record BaseSizeWeighting(
        double colonySizeWeight,
        HiddenMarketScalingChoice hiddenMarketScaling,
        double hiddenMarketFixedWeight,
        double lowStabilityPenalty) {
}
