package kmu.maplayers.politicalmap.base.dominance.weighting;

import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;
import kmu.maplayers.politicalmap.base.dominance.SystemDominance;
import kmu.settings.KmuLunaSettings;

/**
 * The dominance-weighting rules in force for one resolution pass: the master stability
 * switch and the three per-market weight factors - base size, an attached station, and
 * fielded patrols - the footprint read applies before {@link SystemDominance} compares
 * factions.
 *
 * <p>Bundling the rules into one value lets a pass read the player's LunaLib settings
 * once up front and thread a single argument down the footprint read, rather than a
 * growing list of loose flags. The LunaLib read is confined to
 * {@link #readFromLunaSettings()} - the one seam that touches settings - so
 * {@link KnownMarketFootprints} consumes plain values and never reaches into settings
 * itself.
 *
 * <p>Each of the three factors carries its own low-stability penalty.
 * {@link #isStabilityWeighted} is the master switch: when on, each factor's contribution
 * is cut toward zero stability by its penalty (1 removes the factor at zero stability, 0
 * leaves it untouched); with the master off, no factor is stability-scaled.
 *
 * @param isStabilityWeighted the master switch for stability scaling: when on, each factor
 *                            is cut toward zero stability by its own low-stability penalty;
 *                            when off, no factor is scaled by stability
 * @param baseSize            the base-size factor - the colony-size multiplier, the
 *                            hidden-market rating choice, and its low-stability penalty
 * @param station             the attached-station factor - its toggle, weight,
 *                            hidden-market rate, and low-stability penalty
 * @param patrols             the fielded-patrol factor - its toggle, per-tier weights, and
 *                            low-stability penalty
 */
public record DominanceRules(
        boolean isStabilityWeighted,
        BaseSizeWeighting baseSize,
        StationWeighting station,
        PatrolWeighting patrols) {

    /**
     * Reads the player's current dominance-weighting settings from LunaLib into one
     * pass-wide rule.
     *
     * <p>Called once per resolution pass at the entry points, so every system in the
     * pass resolves under the same rule even if the player applies a settings change
     * mid-walk. Isolating the settings read here keeps the footprint read that consumes
     * the result free of LunaLib access.
     *
     * @return the weighting the player's live settings describe
     */
    public static DominanceRules readFromLunaSettings() {
        return new DominanceRules(
                KmuLunaSettings.shouldWeighDominanceByStability(),
                new BaseSizeWeighting(
                        KmuLunaSettings.getColonySizeWeight(),
                        KmuLunaSettings.getHiddenMarketScaling(),
                        KmuLunaSettings.getHiddenMarketFixedWeight(),
                        KmuLunaSettings.getNormalLowStabilityPenalty()),
                new StationWeighting(
                        KmuLunaSettings.shouldWeighDominanceByStation(),
                        KmuLunaSettings.getStationWeight(),
                        KmuLunaSettings.getStationHiddenMarketRate(),
                        KmuLunaSettings.getStationLowStabilityPenalty()),
                new PatrolWeighting(
                        KmuLunaSettings.shouldWeighDominanceByPatrols(),
                        KmuLunaSettings.getPatrolSmallWeight(),
                        KmuLunaSettings.getPatrolMediumWeight(),
                        KmuLunaSettings.getPatrolLargeWeight(),
                        KmuLunaSettings.getPatrolLowStabilityPenalty()));
    }
}
