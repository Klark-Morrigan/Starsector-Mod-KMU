package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwnerResolver;

/**
 * Who holds any single system, and how its fill draws, under the rule a layer painting holders
 * paints by.
 *
 * <p>The whole of a layer's holding rule. A rebuild collects it over every system the pass walks
 * ({@link HolderResolution#collectOver}), and an incremental refresh asks it of only the handful of
 * systems a colony event marked. One rule serving both is what keeps a refreshed cell from parting
 * from the rebuild around it.
 *
 * <p>The holder and its fill state are the layer's to answer here. Whether anybody lives in a system
 * and whether the spotlit bloc is among them are questions no holding rule takes part in, so the
 * holder source answers them off the pass it opened this resolver over, and the layer states the
 * one thing that is its own.
 *
 * <p>Opened per rebuild or batch rather than held, since it stands on one reading of the sector and
 * a reading outlives nothing: the walk that opened it is the walk entitled to it.
 */
@FunctionalInterface
public interface SystemHolderResolver {

    /**
     * Who paints one star system, and how its fill draws, under whatever rule the layer paints by.
     *
     * @param system the system to resolve; null for a system the sector no longer lists, which
     *               nobody holds
     * @return the holder and fill state to record; {@link ResolvedSystemOwner#UNOWNED} where nobody
     *         holds it and the cell draws unowned
     */
    ResolvedSystemOwner resolveHolderIn(StarSystemAPI system);

    /**
     * The live rules this resolver sampled when it opened, which the holder source hands on as its
     * own {@link SystemOwnerResolver#describeSampledRules}.
     *
     * @return the sampled rules in readable form; {@link SystemOwnerResolver#NO_SAMPLED_RULES} for a
     *         resolver that sampled none
     */
    default String describeSampledRules() {
        return SystemOwnerResolver.NO_SAMPLED_RULES;
    }
}
