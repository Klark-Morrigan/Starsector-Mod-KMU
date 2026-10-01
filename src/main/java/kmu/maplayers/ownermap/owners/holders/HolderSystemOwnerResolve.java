package kmu.maplayers.ownermap.owners.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.holding.OwnerMapInhabitation;
import kmu.maplayers.ownermap.owners.SpotlitBlocs;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwnerResolve;

/**
 * One batch's per-system answers for a layer painting holders: the layer's own holder resolve for
 * who holds a marked system, and the batch's pass for whether anybody lives there and whether the
 * spotlit bloc is among them.
 *
 * <p>The two halves are held together because the batch spends both, and both have to come off
 * one reading: the resolve was opened over this very pass, so a marked system's holder and its
 * habitation describe the sector at one moment.
 */
final class HolderSystemOwnerResolve implements SystemOwnerResolve {

    private final HolderPass pass;
    private final SystemHolderResolve holderResolve;
    private final String spotlitBlocId;

    /**
     * @param pass          the batch's reading of the sector, which the two habitation questions are
     *                      answered off
     * @param holderResolve the layer's holder resolve, opened over that same pass
     * @param spotlitBlocId the spotlit bloc the standing build was painted under, or null
     */
    HolderSystemOwnerResolve(
            HolderPass pass,
            SystemHolderResolve holderResolve,
            String spotlitBlocId) {

        this.pass = pass;
        this.holderResolve = holderResolve;
        this.spotlitBlocId = spotlitBlocId;
    }

    @Override
    public SystemOwner resolveOwnerOf(StarSystemAPI system) {
        return holderResolve.resolveHolderIn(system);
    }

    @Override
    public boolean isSystemInhabited(StarSystemAPI system) {
        // The same projection the whole-sector scan classified by, so a system re-derived here is
        // never taken off the map the rebuild put it on.
        return OwnerMapInhabitation.isSystemInhabited(pass, system);
    }

    @Override
    public boolean isSpotlitOwnerPresentIn(StarSystemAPI system) {
        return SpotlitBlocs.isBlocPresentIn(pass, spotlitBlocId, system);
    }
}
