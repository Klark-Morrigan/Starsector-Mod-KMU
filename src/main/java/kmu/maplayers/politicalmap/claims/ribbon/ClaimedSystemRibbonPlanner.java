package kmu.maplayers.politicalmap.claims.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;

/**
 * The live read behind a claimed cell's band: the contest that settled the system's claim,
 * counted by {@link ClaimCellRibbons}.
 *
 * <p>The claim mechanic publishes only its winner, so a band drawn from that alone would have to
 * go and count colonies on its own account. The whole contest is read instead - the same read the
 * box explaining a claim is written from - so the band and the box are two readings of one walk
 * rather than two opinions about what is in the system.
 *
 * <p>The band is ordered around the claimant, since that is the bloc the cell is painted for,
 * under a decree as much as under a scored win: a decreed bloc holding nothing there leads a band
 * made of the rivals present, which is exactly what the fill leaves unsaid.
 *
 * <p>Scoring walks every market in the system, so this is the costlier of the two mechanics to
 * count a band from. It is asked only of the cells a claim paints, and only when the map rebuilds.
 */
public final class ClaimedSystemRibbonPlanner implements SystemRibbonPlanner {

    private final ClaimBreakdownReader breakdownReader;
    private final HolderGrouping grouping;
    private final RibbonPlanInputs inputs;

    /**
     * @param breakdownReader where the contest behind a claim is read from
     * @param grouping        the view's grouping, folding each standing's faction into the bloc
     *                        the cell was painted in
     * @param inputs          where a bloc's shades are read from, and how far its runs go
     */
    public ClaimedSystemRibbonPlanner(
            ClaimBreakdownReader breakdownReader,
            HolderGrouping grouping,
            RibbonPlanInputs inputs) {

        this.breakdownReader = breakdownReader;
        this.grouping = grouping;
        this.inputs = inputs;
    }

    /**
     * The planner a view resolves its claimed bands through, reading vanilla's own claim contest.
     *
     * @param grouping the view's grouping, sampled once for the whole pass
     * @param inputs   where a bloc's shades are read from, and how far its runs go
     * @return the planner counting claimed cells from the vanilla contest
     */
    public static ClaimedSystemRibbonPlanner createForSector(
            HolderGrouping grouping,
            RibbonPlanInputs inputs) {

        return new ClaimedSystemRibbonPlanner(
            new VanillaClaimBreakdownReader(),
            grouping,
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
            grouping.resolveBlocId(contest.claimantFactionId()),
            contest,
            grouping,
            inputs);
    }
}
