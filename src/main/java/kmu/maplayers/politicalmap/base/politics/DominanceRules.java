package kmu.maplayers.politicalmap.base.politics;

import kmu.settings.HiddenMarketScalingChoice;
import kmu.settings.KmuLunaSettings;

/**
 * The dominance-weighting rules in force for one resolution pass: the per-market
 * multipliers and factor toggles the footprint read applies before
 * {@link SystemDominance} compares factions.
 *
 * <p>Bundling the rules into one value lets a pass read the player's LunaLib
 * settings once up front and thread a single argument down the footprint read,
 * rather than a growing list of loose flags as the military-presence factors
 * accumulate. The LunaLib read is confined to {@link #readFromLunaSettings()} - the
 * one seam that touches settings - so {@link KnownMarketFootprints} consumes plain
 * values and never reaches into settings itself.
 *
 * <p>Each of the three weight factors (colony size, an attached station, fielded
 * patrols) carries its own low-stability penalty: {@link #isStabilityWeighted} is
 * the master switch, and when it is on each factor's contribution is cut toward
 * zero stability by its penalty (1 removes the factor at zero stability, 0 leaves
 * it untouched). With the master off, no factor is stability-scaled.
 *
 * @param colonySizeWeight          the multiplier on each market's base size rating
 *                                  - a visible colony's own {@code getSize()} or a
 *                                  hidden market's chosen base size - applied before
 *                                  the station and patrol bonuses are added, so the
 *                                  player can dial how much raw colony size sways a
 *                                  system's dominant faction
 * @param hiddenMarketScaling       how a hidden market's base size rating is chosen:
 *                                  {@code NORMAL} by its real size like any colony,
 *                                  {@code FIXED} at {@code hiddenMarketFixedWeight}
 *                                  regardless of size
 * @param hiddenMarketFixedWeight   the fixed base size rating a hidden market folds
 *                                  in at while its scaling is {@code FIXED}, in place of
 *                                  its real size; unused while the scaling is {@code NORMAL}
 * @param isStabilityWeighted       the master switch for stability scaling: when on,
 *                                  each factor is cut toward zero stability by its own
 *                                  low-stability penalty; when off, no factor is scaled
 *                                  by stability
 * @param normalLowStabilityPenalty how much a colony's weighted base size is cut at
 *                                  zero stability, 0..1 (1 removes it, 0 leaves it
 *                                  untouched); applied only while stability weighting is on
 * @param isStationWeighted         whether a market with an attached defensive station
 *                                  gains the station weight in size points
 * @param stationWeight             the size points an attached defensive station adds to
 *                                  a visible colony - a hidden market earns this scaled
 *                                  by {@code stationHiddenMarketRate} - so the player can
 *                                  dial how much a station is worth
 * @param stationHiddenMarketRate   the fraction of the station weight a station on a
 *                                  hidden market earns, so a fortified secret base reads
 *                                  above a bare unstationed hidden market without
 *                                  matching an openly held stationed colony
 * @param stationLowStabilityPenalty how much the station bonus is cut at zero stability,
 *                                  0..1; applied only while stability weighting is on
 * @param isPatrolWeighted          whether the patrols a colony fields add size points
 *                                  toward its dominance, weighted by patrol size
 * @param patrolSmallWeight         the size points each small (light) patrol adds
 * @param patrolMediumWeight        the size points each medium patrol adds
 * @param patrolLargeWeight         the size points each large (heavy) patrol adds
 * @param patrolLowStabilityPenalty how much the patrol bonus is cut at zero stability,
 *                                  0..1; applied only while stability weighting is on
 */
public record DominanceRules(double colonySizeWeight,
        HiddenMarketScalingChoice hiddenMarketScaling, double hiddenMarketFixedWeight,
        boolean isStabilityWeighted, double normalLowStabilityPenalty,
        boolean isStationWeighted, double stationWeight, double stationHiddenMarketRate,
        double stationLowStabilityPenalty,
        boolean isPatrolWeighted, double patrolSmallWeight, double patrolMediumWeight,
        double patrolLargeWeight, double patrolLowStabilityPenalty) {

    /**
     * Reads the player's current dominance-weighting settings from LunaLib into one
     * pass-wide rule.
     *
     * <p>Called once per resolution pass at the entry points, so every system in the
     * pass resolves under the same rule even if the player applies a settings change
     * mid-walk. Isolating the settings read here keeps the footprint read that
     * consumes the result free of LunaLib access.
     *
     * @return the weighting the player's live settings describe
     */
    public static DominanceRules readFromLunaSettings() {
        return new DominanceRules(
                KmuLunaSettings.getColonySizeWeight(),
                KmuLunaSettings.getHiddenMarketScaling(),
                KmuLunaSettings.getHiddenMarketFixedWeight(),
                KmuLunaSettings.shouldWeighDominanceByStability(),
                KmuLunaSettings.getNormalLowStabilityPenalty(),
                KmuLunaSettings.shouldWeighDominanceByStation(),
                KmuLunaSettings.getStationWeight(),
                KmuLunaSettings.getStationHiddenMarketRate(),
                KmuLunaSettings.getStationLowStabilityPenalty(),
                KmuLunaSettings.shouldWeighDominanceByPatrols(),
                KmuLunaSettings.getPatrolSmallWeight(),
                KmuLunaSettings.getPatrolMediumWeight(),
                KmuLunaSettings.getPatrolLargeWeight(),
                KmuLunaSettings.getPatrolLowStabilityPenalty());
    }
}
