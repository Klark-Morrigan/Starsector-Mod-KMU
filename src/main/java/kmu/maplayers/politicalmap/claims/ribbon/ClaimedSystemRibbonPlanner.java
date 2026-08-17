package kmu.maplayers.politicalmap.claims.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;

import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;

/**
 * The live read behind a claimed cell's band: the contest that settled the system's claim, which
 * says who painted the cell and where each bloc stands in it.
 *
 * <p>The claim mechanic publishes only its winner, so a band drawn from that alone would know
 * neither the painter's rivals nor their order. The whole contest is read instead - the same read
 * the box explaining a claim is written from - so the band and the box cannot disagree about who is
 * present or who leads.
 *
 * <p>The counting itself is the shared rule's, off the pass's own walk of the system, which is why
 * the reader is built over that walk rather than one of its own: the contest and the count then
 * read one reading of the system instead of two.
 */
public final class ClaimedSystemRibbonPlanner implements SystemRibbonPlanner {

    private final ClaimBreakdownReader breakdownReader;
    private final RibbonPlanInputs inputs;

    /**
     * @param breakdownReader where the contest behind a claim is read from
     * @param inputs          the pass the colonies are read from, where a bloc's shades come from,
     *                        and how far its runs go
     */
    public ClaimedSystemRibbonPlanner(
            ClaimBreakdownReader breakdownReader,
            RibbonPlanInputs inputs) {

        this.breakdownReader = breakdownReader;
        this.inputs = inputs;
    }

    /**
     * The planner a view resolves its claimed bands through, reading vanilla's own claim contest
     * over the bake's single walk of each system.
     *
     * @param inputs the bake's pass, palette source and laying rules
     * @return the planner ranking claimed cells by the vanilla contest
     */
    public static ClaimedSystemRibbonPlanner createForPass(RibbonPlanInputs inputs) {

        return new ClaimedSystemRibbonPlanner(
            new VanillaClaimBreakdownReader(inputs.pass().colonies()),
            inputs);
    }

    @Override
    public RibbonPlan planSystemRibbon(StarSystemAPI system) {

        var contest = breakdownReader.readBreakdown(system);

        // Nobody claims the system, so nothing painted the cell for a bloc and there is no painter
        // for the gate to be stated against - the band that would be drawn is one no fill asked for.
        if (contest.claimantFactionId() == null) {
            return RibbonPlan.NONE;
        }
        return ClaimCellRibbons.planClaimCellRibbon(
            inputs.grouping().resolveBlocId(contest.claimantFactionId()),
            system,
            contest,
            inputs);
    }
}
