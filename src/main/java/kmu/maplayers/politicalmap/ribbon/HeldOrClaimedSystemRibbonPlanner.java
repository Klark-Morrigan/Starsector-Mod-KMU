package kmu.maplayers.politicalmap.ribbon;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReaderSource;

import kmu.maplayers.base.ribbon.RibbonPlan;
import kmu.maplayers.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.ownermap.owners.holders.base.RibbonPlanInputs;
import kmu.maplayers.politicalmap.claims.ribbon.ClaimedSystemRibbonPlanner;
import kmu.maplayers.politicalmap.dominance.ribbon.HeldSystemRibbonPlanner;

import org.apache.log4j.Logger;

/**
 * The planner behind a view whose cells are painted by held dominance and extended by claims:
 * each system counted by whichever of the two mechanics painted it.
 *
 * <p>The holder resolve those views run gives a claim the system only where the held resolve
 * left it unowned, so "does any bloc hold a weighed market here" is the very question that
 * decided which mechanic painted the cell. Asking it of the held read itself - which weighs the
 * system's colonies anyway - is what lets the band follow the fill without a set of system IDs
 * carried alongside to remember the split.
 *
 * <p>Composed rather than branched inside either planner, so each one stays a plain statement of
 * its own mechanic and this holds the one rule about which of them speaks for a cell. It sits
 * beside the two mechanics rather than inside either, as the holder sources folding claims onto
 * held dominance do: shelved under one mechanic, it would make that mechanic name the other.
 */
public final class HeldOrClaimedSystemRibbonPlanner
        implements SystemRibbonPlanner {

    // What the debug line calls each mechanic.
    private static final String CLAIMED_MECHANIC = "claimed";
    private static final String HELD_MECHANIC = "held";

    private static final Logger LOG = Global.getLogger(HeldOrClaimedSystemRibbonPlanner.class);

    private final HeldSystemRibbonSource heldSource;
    private final SystemRibbonPlanner claimedPlanner;

    /**
     * @param heldSource     the held mechanic's answer, asked first, since a held system is one
     *                       the claim extension never reached. Taken as the narrow source rather
     *                       than as the planner, because what this rule reads is the one thing a
     *                       planner cannot say: whether the mechanic speaks for the system at all
     * @param claimedPlanner the claim mechanic's planner, asked for the systems the held one
     *                       paints nothing in
     */
    HeldOrClaimedSystemRibbonPlanner(
            HeldSystemRibbonSource heldSource,
            SystemRibbonPlanner claimedPlanner) {

        this.heldSource = heldSource;
        this.claimedPlanner = claimedPlanner;
    }

    /**
     * The planner the faction and alliance views resolve their bands through.
     *
     * <p>Both halves are built from the one set of inputs, so the two mechanics count off a single
     * walk of each system and read a bloc's colours through a single palette source rather than
     * each opening its own.
     *
     * @param inputs            everything one bake's bands are settled from, sampled once by the bake
     * @param claimReaderSource the source the view reads every claim through, so a claimed cell's
     *                          band ranks the contest the cell was painted by
     * @return the planner counting each system by the mechanic that painted it
     */
    public static HeldOrClaimedSystemRibbonPlanner createForPass(
            RibbonPlanInputs inputs,
            ClaimBreakdownReaderSource claimReaderSource) {

        return new HeldOrClaimedSystemRibbonPlanner(
            HeldSystemRibbonPlanner.createForPass(inputs)::planHeldSystemRibbon,
            ClaimedSystemRibbonPlanner.createForPass(inputs, claimReaderSource));
    }

    @Override
    public RibbonPlan planSystemRibbon(StarSystemAPI system) {

        var heldPlan = heldSource.planHeldSystemRibbon(system);

        logAnsweringMechanic(system, heldPlan.isPresent());

        return heldPlan.orElseGet(() -> claimedPlanner.planSystemRibbon(system));
    }

    // Which mechanic counted the system. The band follows the fill only while this agrees with the
    // mechanic that painted the cell, and the map shows the band, not which of the two drew it.
    //
    // At debug, since the game's log4j predates the trace level.
    private static void logAnsweringMechanic(StarSystemAPI system, boolean isHeld) {

        var mechanic = isHeld
            ? HELD_MECHANIC
            : CLAIMED_MECHANIC;

        LOG.debug("System band planned; system=" + system.getId() + " mechanic=" + mechanic);
    }
}
