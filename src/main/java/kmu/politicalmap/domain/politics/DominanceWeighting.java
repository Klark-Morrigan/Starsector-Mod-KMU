package kmu.politicalmap.domain.politics;

import kmu.settings.KmuLunaSettings;

/**
 * The dominance-weighting rules in force for one resolution pass: the per-market
 * multipliers the footprint read applies before {@link SystemDominance} compares
 * factions.
 *
 * <p>Bundling the rules into one value lets a pass read the player's LunaLib
 * toggles once up front and thread a single argument down the footprint read,
 * rather than a growing list of loose flags as military-presence factors
 * accumulate. The LunaLib read is confined to {@link #readFromSettings()} - the
 * one seam that touches settings - so {@link KnownMarketFootprints} consumes plain
 * values and never reaches into settings itself.
 *
 * @param colonySizeWeight        the multiplier on each market's base size rating
 *                                - a visible colony's own {@code getSize()} or a
 *                                hidden base's fixed presence token - applied
 *                                before the station bonus is added and stability
 *                                scales the sum, so the player can dial how much
 *                                raw colony size sways a system's dominant faction
 * @param isStabilityWeighted     whether each market's size rating is scaled by
 *                                its stability before dominance is compared
 * @param isStationWeighted       whether a market with an attached defensive
 *                                station gains the station weight in size points
 *                                before stability scales the sum
 * @param stationWeight           the size points an attached defensive station
 *                                adds to a visible colony - a hidden base earns
 *                                this scaled by {@code stationHiddenMarketRate} -
 *                                so the player can dial how much a station is worth
 * @param stationHiddenMarketRate the fraction of the station weight a hidden
 *                                (concealed) base earns, so a fortified secret
 *                                base reads above a bare outpost without matching
 *                                an openly held stationed colony
 */
public record DominanceWeighting(double colonySizeWeight, boolean isStabilityWeighted,
        boolean isStationWeighted, double stationWeight, double stationHiddenMarketRate) {

    /**
     * Reads the player's current dominance-weighting toggles from LunaLib into one
     * pass-wide rule.
     *
     * <p>Called once per resolution pass at the entry points, so every system in the
     * pass resolves under the same rule even if the player applies a settings change
     * mid-walk. Isolating the settings read here keeps the footprint read that
     * consumes the result free of LunaLib access.
     *
     * @return the weighting the player's live settings describe
     */
    public static DominanceWeighting readFromSettings() {
        return new DominanceWeighting(
                KmuLunaSettings.getColonySizeWeight(),
                KmuLunaSettings.shouldWeighDominanceByStability(),
                KmuLunaSettings.shouldWeighDominanceByStation(),
                KmuLunaSettings.getStationWeight(),
                KmuLunaSettings.getStationHiddenMarketRate());
    }
}
