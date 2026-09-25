package kmu.maplayers.politicalmap.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.ownermap.owners.SectorOwnershipFixtures;
import kmu.maplayers.politicalmap.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.StationWeighting;
import kmu.settings.HiddenMarketScalingChoice;

/**
 * The weighted reading of a sector this layer's suites pose their cases against.
 *
 * <p>Apart from the sector fixture it builds on, because the two belong to different tiers: a
 * sector holding factions and markets is what any owner-painted layer's case starts from, while
 * what those markets are <em>worth</em> is this mechanic's alone.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class DominancePassFixtures {

    private DominancePassFixtures() {
        // fixture of static wiring, no instances.
    }

    /**
     * The dominance rule the palette-resolving suites share: station and patrol weighting off, the
     * colony-size weight at its identity, and the colony penalty at a full collapse, so these tests
     * pin the stability rule alone. The footprint suite drives its own builder instead, since it
     * varies the station and patrol factors this constant holds off.
     *
     * @return the shared stability-only weighting rule
     */
    public static DominanceRules buildStabilityWeightedRules() {
        return new DominanceRules(true,
            new BaseSizeWeighting(1.0, HiddenMarketScalingChoice.FIXED, 1.0, 1.0),
            new StationWeighting(false, 1.0, 0.5, 0.5),
            new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));
    }

    /**
     * That same reading under the shared stability-only weighting rule - what a suite exercising a
     * resolve that weighs markets poses its cases against.
     *
     * @param sector the stubbed sector the pass reads
     * @return a dominance pass over that sector
     */
    public static DominancePass buildPassOver(SectorAPI sector) {
        return DominancePass.createOver(
            SectorOwnershipFixtures.buildHolderPassOver(sector),
            buildStabilityWeightedRules());
    }
}
