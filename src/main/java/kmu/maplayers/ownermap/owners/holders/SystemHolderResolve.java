package kmu.maplayers.ownermap.owners.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;

/**
 * One reading of a sector, plus who holds any single system in it.
 *
 * <p>The per-system half of {@link HolderProvider}, and apart from it because the two answer
 * different costs. A provider resolves the whole sector at once, which is what a rebuild wants; an
 * incremental refresh re-derives only the handful of systems a colony event marked, and asking a
 * whole-sector resolve for each of them would pay for the sector once per marked system.
 *
 * <p>Both halves of the seam travel together because the incremental path spends both: who holds a
 * marked system is settled by whatever rule the layer paints by, while whether anybody lives there
 * and whether the spotlit bloc is among them are questions no such rule takes part in. Handed
 * apart, a caller could answer the two off different readings of one sector and redraw a cell
 * against a holder some other walk found.
 *
 * <p>Opened per batch rather than held, since it stands on one reading of the sector and a reading
 * outlives nothing: the batch that opened it is the batch entitled to it.
 */
public interface SystemHolderResolve {

    /**
     * The layer-generic reading of the sector this resolve answers over, which every question
     * beside the holder is asked of.
     *
     * @return the pass the holder answers were derived from
     */
    HolderPass readHolderPass();

    /**
     * Who paints one star system, under whatever rule the layer paints by.
     *
     * @param system the system to re-derive
     * @return the holder to record, or null where nobody holds it and the cell draws unowned
     */
    SystemOwner resolveHolderIn(StarSystemAPI system);
}
