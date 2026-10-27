package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwnerResolver;

/**
 * One batch's per-system answers for a layer painting holders: the layer's own holder resolver for
 * who holds a marked system, and the batch's pass for whether anybody lives there and whether the
 * spotlit bloc is among them.
 *
 * <p>The two halves are held together because the batch spends both, and both have to come off
 * one reading: the resolver was opened over this very pass, so a marked system's holder and its
 * habitation describe the sector at one moment.
 */
final class HolderSystemOwnerResolver implements SystemOwnerResolver {

    private final HolderPass pass;
    private final SystemHolderResolver holderResolver;
    private final String spotlitBlocId;

    /**
     * @param pass          the batch's reading of the sector, which the two habitation questions are
     *                      answered off
     * @param holderResolver the layer's holder resolver, opened over that same pass
     * @param spotlitBlocId the spotlit bloc the standing build was painted under, or null
     */
    HolderSystemOwnerResolver(
            HolderPass pass,
            SystemHolderResolver holderResolver,
            String spotlitBlocId) {

        this.pass = pass;
        this.holderResolver = holderResolver;
        this.spotlitBlocId = spotlitBlocId;
    }

    @Override
    public ResolvedSystemOwner resolveOwnerOf(StarSystemAPI system) {

        return holderResolver.resolveHolderIn(system);
    }

    @Override
    public boolean isSystemInhabited(StarSystemAPI system) {

        return HolderInhabitation.isSystemInhabited(pass, system);
    }

    @Override
    public boolean isSpotlitOwnerPresentIn(StarSystemAPI system) {

        return HolderInhabitation.isBlocPresentIn(pass, spotlitBlocId, system);
    }

    @Override
    public String describeSampledRules() {

        return holderResolver.describeSampledRules();
    }
}
