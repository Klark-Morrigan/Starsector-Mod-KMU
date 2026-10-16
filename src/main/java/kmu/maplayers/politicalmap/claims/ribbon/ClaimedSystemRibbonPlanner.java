package kmu.maplayers.politicalmap.claims.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.ClaimBreakdownReaderSource;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;

import kmu.maplayers.ownermap.owners.holders.base.RibbonPlanInputs;
import kmu.maplayers.ownermap.ribbon.RibbonPlan;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;
import kmu.maplayers.politicalmap.claims.PassClaimReaders;

import java.util.Optional;

/**
 * The live read behind a claimed cell's band: the contest that settled the system's claim, which
 * says who painted the cell and where each bloc stands in it.
 *
 * <p>The claim mechanic publishes only its winner, so a band drawn from that alone would know
 * neither the painter's rivals nor their order. The whole contest is read instead - the same read
 * the box explaining a claim is written from - so the band and the box cannot disagree about who is
 * present or who leads.
 *
 * <p>A system the contest settled on nobody is planned all the same, without a painter. Vanilla
 * leaves a settled system unclaimed for several reasons -
 * <a href="../README.md#inhabited-does-not-imply-claimed">the claim mechanic</a> says which - and this
 * layer's fill <em>is</em> the claim, so it says nothing about such a system at all. The band is what
 * does.
 *
 * <p>The counting itself is the shared rule's, off the pass's own walk of the system, which is why
 * the reader is built over that walk rather than one of its own: the contest and the count then
 * read one reading of the system instead of two.
 */
public final class ClaimedSystemRibbonPlanner
        implements SystemRibbonPlanner {

    private final ClaimBreakdownReader breakdownReader;
    private final RibbonPlanInputs inputs;

    /**
     * @param breakdownReader where the contest behind a claim is read from
     * @param inputs          everything one bake's bands are settled from, sampled once by the bake
     */
    public ClaimedSystemRibbonPlanner(
            ClaimBreakdownReader breakdownReader,
            RibbonPlanInputs inputs) {

        this.breakdownReader = breakdownReader;
        this.inputs = inputs;
    }

    /**
     * The planner a view resolves its claimed bands through, reading the claim contest over the
     * bake's single walk of each system.
     *
     * @param inputs            everything one bake's bands are settled from, sampled once by the bake
     * @param claimReaderSource the source the view reads every claim through, so a band ranks the
     *                          contest its cell was painted by
     * @return the planner ranking claimed cells by that contest
     */
    public static ClaimedSystemRibbonPlanner createForPass(
            RibbonPlanInputs inputs,
            ClaimBreakdownReaderSource claimReaderSource) {

        return new ClaimedSystemRibbonPlanner(
            PassClaimReaders.openBreakdownReaderOver(inputs.pass(), claimReaderSource),
            inputs);
    }

    @Override
    public RibbonPlan planSystemRibbon(StarSystemAPI system) {

        var contest = breakdownReader.readBreakdown(system);

        return ClaimCellRibbons.planClaimCellRibbon(
            resolvePaintingBlocId(contest),
            system,
            contest,
            inputs);
    }

    // The bloc whose fill the band sits inside, or none at all where no claim covers the system.
    //
    // Absence rather than a refusal, because a system nobody claims is still a system somebody may
    // be living in - a pirate haven, a player colony - and what the band reports is who is there.
    // The claim walk never saw the markets that put them there, so this layer's holding leaves
    // every such system unpainted and the band is the only thing that can say they are settled at
    // all.
    //
    // Handed on as an absent painter rather than as an ID no bloc carries, since the two readings
    // of contest differ on it: with a painter, one rival is a contest; with none, there is nobody
    // to be a rival of, so it takes two blocs.
    private Optional<String> resolvePaintingBlocId(SystemClaimBreakdown contest) {

        return Optional
            .ofNullable(contest.claimantFactionId())
            .map(inputs.grouping()::resolveBlocId);
    }
}
