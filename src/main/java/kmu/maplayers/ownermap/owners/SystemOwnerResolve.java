package kmu.maplayers.ownermap.owners;

import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * One batch's reading of the sector, answering the three questions an incremental refresh asks of
 * a single marked system: who owns it, whether anything stands in it, and whether the spotlit
 * owner is among what does.
 *
 * <p>The per-system half of {@link OwnerSource}, and apart from it because the two answer
 * different costs. The source resolves the whole sector at once, which is what a rebuild wants; an
 * incremental refresh re-derives only the handful of systems a change marked, and asking the
 * whole-sector resolve for each of them would pay for the sector once per marked system.
 *
 * <p>All three questions travel on one resolve because the incremental path spends all three:
 * who owns a marked system is settled by whatever rule the layer paints by, while whether anybody
 * lives there and whether the spotlit owner is among them are questions the layer answers off the
 * same reading. Handed apart, a caller could answer them off different readings of one sector and
 * redraw a cell against an owner some other walk found.
 *
 * <p>Opened per batch rather than held, since it stands on one reading of the sector and a
 * reading outlives nothing: the batch that opened it is the batch entitled to it.
 */
public interface SystemOwnerResolve {

    /**
     * Who paints one star system, under whatever rule the layer paints by.
     *
     * @param system the system to re-derive; null for a system the sector no longer lists, which
     *               nobody owns
     * @return the owner to record, or null where nobody owns it and the cell draws unowned
     */
    SystemOwner resolveOwnerOf(StarSystemAPI system);

    /**
     * Whether anything stands in one star system, by the same reading the source's whole-sector
     * answer classifies inhabitation by - so a per-system re-derive cannot take a system off the
     * map that the rebuild put on it.
     *
     * @param system the system to read; null yields false
     * @return true when something stands in the system
     */
    boolean isSystemInhabited(StarSystemAPI system);

    /**
     * Whether the spotlit owner this resolve was opened under lives in one star system.
     *
     * <p>Asked only of systems the batch found nobody owns, since presence is what spares an
     * unowned cell the recede: a system somebody owns already draws in that owner's cluster, so
     * where the pick also lives there changes nothing.
     *
     * @param system the system to read; null yields false
     * @return true when the spotlit owner is present in the system; always false off spotlight
     */
    boolean isSpotlitOwnerPresentIn(StarSystemAPI system);
}
