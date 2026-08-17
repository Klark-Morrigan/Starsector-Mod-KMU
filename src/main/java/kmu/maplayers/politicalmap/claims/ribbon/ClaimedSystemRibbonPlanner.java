package kmu.maplayers.politicalmap.claims.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;

import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;

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
 * <p>A system the contest settled on nobody is planned all the same, without a painter. Vanilla's
 * walk skips a hidden market, a player-owned one, and one whose faction carries no territorial
 * flag, and reads only what the economy lists - so a pirate or Path base (both created hidden), a
 * player colony, a Remnant station or an unregistered one leaves its system unclaimed however
 * settled it is. This layer's fill is the claim, so it says nothing about those systems at all.
 * The band is what does.
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
    // Handed on as an absent painter rather than as an id no bloc carries, since the two arms of
    // the gate read it differently: with a painter, one rival is a contest; with none, there is
    // nobody to be a rival of, so it takes two blocs.
    private Optional<String> resolvePaintingBlocId(SystemClaimBreakdown contest) {

        return Optional
            .ofNullable(contest.claimantFactionId())
            .map(inputs.grouping()::resolveBlocId);
    }
}
