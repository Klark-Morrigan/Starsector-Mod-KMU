package kmu.maplayers.politicalmap.dominance.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.ribbon.RibbonPlan;
import kmu.maplayers.ownermap.ribbon.RibbonPlanInputs;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.SystemDominance;

import java.util.Optional;

/**
 * The live read behind a held cell's band: one system's footprints under the pass that paints
 * the map, ranked by {@link HeldCellRibbons}.
 *
 * <p>The whole of what this adds to the rule is the weight read and the winner the band is ordered
 * around, both taken through the same {@link DominancePass} the fills are resolved under - so a
 * band opens on the bloc its own cell's colour was decided for, under the same weighting rule and
 * the same colony rule, rather than a second sample taken a moment later under a knob the player has
 * since moved.
 *
 * <p>The footprints come off the pass's own walk of the system, which the counting beneath them
 * shares, so ranking a band costs no walk of its own.
 */
public final class HeldSystemRibbonPlanner implements SystemRibbonPlanner, HeldSystemRibbonSource {

    private final DominancePass pass;
    private final RibbonPlanInputs inputs;

    /**
     * @param pass   the sector walk, weighting rule, colony rule, and grouping this build resolves
     *               under, sampled once so every band is ranked under the settings the fills were
     * @param inputs everything one bake's bands are settled from, sampled once by the bake
     */
    public HeldSystemRibbonPlanner(DominancePass pass, RibbonPlanInputs inputs) {
        this.pass = pass;
        this.inputs = inputs;
    }

    /**
     * The planner a view resolves its held bands through: the bake's own reading of the sector
     * under the player's live weighting rule, which is the one knob that reading does not carry.
     *
     * <p>Built over the bake's pass rather than opening one, so the ranking here and the counting
     * beneath it share the walk with the claim half beside them.
     *
     * @param inputs everything one bake's bands are settled from, sampled once by the bake
     * @return the planner ranking held cells under the live weighting rule
     */
    public static HeldSystemRibbonPlanner createForPass(RibbonPlanInputs inputs) {

        return new HeldSystemRibbonPlanner(
            DominancePass.readRulesFromLunaSettings(inputs.pass()),
            inputs);
    }

    @Override
    public RibbonPlan planSystemRibbon(StarSystemAPI system) {
        return planHeldSystemRibbon(system).orElse(RibbonPlan.NONE);
    }

    /**
     * Plans a system's band, and says whether the held mechanic paints the system at all.
     *
     * <p>Both answers come off one economy walk, which is what keeps the distinction the
     * composition turns on from costing a second one.
     *
     * @param system the system to count
     * @return the system's plan, or empty where no bloc holds a counted market there
     */
    @Override
    public Optional<RibbonPlan> planHeldSystemRibbon(StarSystemAPI system) {

        if (system == null || !pass.canReadEconomy()) {
            return Optional.empty();
        }
        var footprintByBlocId = pass.readBlocFootprints(system);
        if (footprintByBlocId.isEmpty()) {
            return Optional.empty();
        }

        // The winner resolved exactly as the fill's was, tie-break and candidacy included, so the
        // bloc the band opens on is the bloc the cell is painted for even where the weights alone
        // do not say so.
        var dominantBlocId = SystemDominance.resolveDominantFactionId(
            footprintByBlocId,
            pass.resolveRankingRulesFor(system));

        return Optional.of(HeldCellRibbons.planHeldCellRibbon(
            dominantBlocId,
            system,
            footprintByBlocId,
            inputs));
    }
}
