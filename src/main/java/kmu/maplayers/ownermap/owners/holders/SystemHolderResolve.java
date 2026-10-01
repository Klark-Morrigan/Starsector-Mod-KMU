package kmu.maplayers.ownermap.owners.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.owners.SystemOwner;

/**
 * Who holds any single system, under the rule a layer painting holders paints by.
 *
 * <p>The per-system half of {@link HolderProvider}, and apart from it because the two answer
 * different costs. A provider resolves the whole sector at once, which is what a rebuild wants; an
 * incremental refresh re-derives only the handful of systems a colony event marked, and asking a
 * whole-sector resolve for each of them would pay for the sector once per marked system.
 *
 * <p>Only the holder is the layer's to answer here. Whether anybody lives in a marked system and
 * whether the spotlit bloc is among them are questions no holding rule takes part in, so the
 * holder source answers them off the pass it opened this resolve over, and the layer states the
 * one thing that is its own.
 *
 * <p>Opened per batch rather than held, since it stands on one reading of the sector and a reading
 * outlives nothing: the batch that opened it is the batch entitled to it.
 */
@FunctionalInterface
public interface SystemHolderResolve {

    /**
     * Who paints one star system, under whatever rule the layer paints by.
     *
     * @param system the system to re-derive
     * @return the holder to record, or null where nobody holds it and the cell draws unowned
     */
    SystemOwner resolveHolderIn(StarSystemAPI system);
}
