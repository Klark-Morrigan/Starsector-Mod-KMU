package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.SystemHolderResolve;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;

/**
 * The political map's per-system holder read: the market contest, asked one system at a time.
 *
 * <p>What this layer fills {@link SystemHolderResolve} with. The weighting rule is sampled once
 * when the batch's resolve is opened, so every marked system in that batch is re-derived under the
 * same rule - and over the batch's own pass, so under the same grouping and the same walk the
 * holder source answers the batch's other questions from. That is what makes a single-system
 * refresh land the winning bloc the bulk pass would have.
 */
public final class DominanceSystemHolderResolve implements SystemHolderResolve {

    private final DominancePass pass;

    private DominanceSystemHolderResolve(DominancePass pass) {
        this.pass = pass;
    }

    /**
     * Opens one batch's dominance pass over the batch's reading of the sector, and the resolve
     * above it.
     *
     * @param holding the batch's reading of the sector, under the grouping the standing build was
     *                folded by
     * @return the batch's resolve
     */
    public static SystemHolderResolve openResolveOver(HolderPass holding) {
        return new DominanceSystemHolderResolve(DominancePass.readRulesFromLunaSettings(holding));
    }

    @Override
    public SystemOwner resolveHolderIn(StarSystemAPI system) {
        return SectorPolitics.resolveDominantHolder(system, pass);
    }
}
