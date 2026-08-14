package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

/**
 * The planner behind a view whose cells are painted by held dominance and extended by claims:
 * each system counted by whichever of the two mechanics painted it.
 *
 * <p>The holder resolve those views run gives a claim the system only where the held resolve
 * left it unowned, so "does any bloc hold a counted market here" is the very question that
 * decided which mechanic painted the cell. Asking it of the held read itself - which has to walk
 * the system's markets anyway - is what lets the band follow the fill without a second walk and
 * without a set of system ids carried alongside to remember the split.
 *
 * <p>Composed rather than branched inside either planner, so each one stays a plain statement of
 * its own mechanic and this holds the one rule about which of them speaks for a cell.
 */
public final class HeldOrClaimedSystemRibbonPlanner implements SystemRibbonPlanner {

    private final HeldSystemRibbonPlanner heldPlanner;
    private final SystemRibbonPlanner claimedPlanner;

    /**
     * @param heldPlanner    the held mechanic's planner, asked first, since a held system is one
     *                       the claim extension never reached
     * @param claimedPlanner the claim mechanic's planner, asked for the systems the held one
     *                       paints nothing in
     */
    public HeldOrClaimedSystemRibbonPlanner(
            HeldSystemRibbonPlanner heldPlanner,
            SystemRibbonPlanner claimedPlanner) {
                
        this.heldPlanner = heldPlanner;
        this.claimedPlanner = claimedPlanner;
    }

    /**
     * The planner the faction and alliance views resolve their bands through.
     *
     * @param sector   the sector whose economy and claims are read
     * @param grouping the view's grouping, sampled once for the whole pass
     * @param lengths  how far a market's segment and an interjection run
     * @return the planner counting each system by the mechanic that painted it
     */
    public static HeldOrClaimedSystemRibbonPlanner createForSector(
            SectorAPI sector,
            HolderGrouping grouping,
            RibbonSegmentLengths lengths) {

        return new HeldOrClaimedSystemRibbonPlanner(
            HeldSystemRibbonPlanner.createForSector(sector, grouping, lengths),
            ClaimedSystemRibbonPlanner.createForSector(sector, grouping, lengths));
    }

    @Override
    public RibbonPlan planSystemRibbon(StarSystemAPI system) {
        return heldPlanner
            .planHeldSystemRibbon(system)
            .orElseGet(() -> claimedPlanner.planSystemRibbon(system));
    }
}
