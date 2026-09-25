package kmu.maplayers.politicalmap.dominance.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.ribbon.RibbonPlan;
import kmu.maplayers.ownermap.ribbon.RibbonPlanInputs;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;
import kmu.maplayers.politicalmap.claims.ribbon.ClaimedSystemRibbonPlanner;

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
 * its own mechanic and this holds the one rule about which of them speaks for a cell. It sits on
 * the dominance side rather than between the two because that is the side that reaches for it: a
 * claim counts a cell here only where dominance paints nothing, which is a fact about how the
 * dominance-painted views extend themselves, not a mechanic of its own.
 */
public final class HeldOrClaimedSystemRibbonPlanner implements SystemRibbonPlanner {

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
    public HeldOrClaimedSystemRibbonPlanner(
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
     * @param inputs everything one bake's bands are settled from, sampled once by the bake
     * @return the planner counting each system by the mechanic that painted it
     */
    public static HeldOrClaimedSystemRibbonPlanner createForPass(RibbonPlanInputs inputs) {

        return new HeldOrClaimedSystemRibbonPlanner(
            HeldSystemRibbonPlanner.createForPass(inputs),
            ClaimedSystemRibbonPlanner.createForPass(inputs));
    }

    @Override
    public RibbonPlan planSystemRibbon(StarSystemAPI system) {
        return heldSource
            .planHeldSystemRibbon(system)
            .orElseGet(() -> claimedPlanner.planSystemRibbon(system));
    }
}
