package kmu.maplayers.ownermap.owners.holders.base;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;

/**
 * Who holds any single system, and how its fill draws, under the rule a layer painting holders
 * paints by.
 *
 * <p>The per-system half of {@link HolderProvider}, and apart from it because the two answer
 * different costs. A provider resolves the whole sector at once, which is what a rebuild wants; an
 * incremental refresh re-derives only the handful of systems a colony event marked, and asking a
 * whole-sector resolve for each of them would pay for the sector once per marked system.
 *
 * <p>The holder and its fill state are the layer's to answer here, as the provider answers both
 * for the whole sector. Whether anybody lives in a marked system and whether the spotlit bloc is
 * among them are questions no holding rule takes part in, so the holder source answers them off the
 * pass it opened this resolver over, and the layer states the one thing that is its own.
 *
 * <p>Opened per batch rather than held, since it stands on one reading of the sector and a reading
 * outlives nothing: the batch that opened it is the batch entitled to it.
 */
@FunctionalInterface
public interface SystemHolderResolver {

    /**
     * Who paints one star system, and how its fill draws, under whatever rule the layer paints by -
     * the answer its provider's whole-sector resolve would give the system under the same spotlight.
     *
     * @param system the system to re-derive; null for a system the sector no longer lists, which
     *               nobody holds
     * @return the holder and fill state to record; {@link ResolvedSystemOwner#UNOWNED} where nobody
     *         holds it and the cell draws unowned
     */
    ResolvedSystemOwner resolveHolderIn(StarSystemAPI system);
}
