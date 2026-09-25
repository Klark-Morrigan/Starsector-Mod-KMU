package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.SystemHolderResolve;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;

/**
 * The political map's per-system holder read: the market contest, asked one system at a time.
 *
 * <p>What this layer fills {@link SystemHolderResolve} with. The weighting rule is sampled once
 * when the batch opens its pass, so every marked system in that batch is re-derived under the same
 * rule and the same grouping - which is what makes a single-system refresh land the winning bloc
 * the bulk pass would have.
 */
public final class DominanceSystemHolderResolve implements SystemHolderResolve {

    private final DominancePass pass;

    private DominanceSystemHolderResolve(DominancePass pass) {
        this.pass = pass;
    }

    /**
     * Opens one batch's dominance pass over a sector and the resolve above it.
     *
     * @param sector   the sector the batch re-derives against
     * @param grouping the grouping the standing build resolved its holders under
     * @return the batch's resolve
     */
    public static SystemHolderResolve openResolveOver(
            SectorAPI sector,
            HolderGrouping grouping) {

        return new DominanceSystemHolderResolve(
            DominancePass.readFromLunaSettings(sector, grouping));
    }

    @Override
    public HolderPass readHolderPass() {
        return pass.holding();
    }

    @Override
    public SystemOwner resolveHolderIn(StarSystemAPI system) {
        return SectorPolitics.resolveDominantHolder(system, pass);
    }
}
