package kmu.maplayers.politicalmap.dominance.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.SystemDominance;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;

import java.util.Optional;

/**
 * The live read behind a held cell's band: one system's footprints under the pass that paints
 * the map, ranked and counted by {@link HeldCellRibbons}.
 *
 * <p>The whole of what this adds to the rule is the economy read and the winner it is ordered
 * around, both taken through the same {@link DominancePass} the fills are resolved under - so a
 * band counts the colonies its own cell's colour was decided from, under the same weighting rule
 * and the same dev reveal, rather than a second sample taken a moment later under a knob the
 * player has since moved.
 *
 * <p>Reading the footprints is the same per-system economy read the holder resolve makes, so a
 * band costs one more walk of a system's own market list - not of the sector's.
 */
public final class HeldSystemRibbonPlanner implements SystemRibbonPlanner, HeldSystemRibbonSource {

    private final SectorAPI sector;
    private final DominancePass pass;
    private final RibbonPlanInputs inputs;

    /**
     * @param sector   the sector whose economy each system's footprints are read from
     * @param pass     the weighting rule, dev reveal, and grouping this build resolves under,
     *                 sampled once so every band is counted under the settings the fills were
     * @param inputs   where a bloc's shades are read from, and how far its runs go
     */
    public HeldSystemRibbonPlanner(
            SectorAPI sector,
            DominancePass pass,
            RibbonPlanInputs inputs) {

        this.sector = sector;
        this.pass = pass;
        this.inputs = inputs;
    }

    /**
     * The planner a view resolves its held bands through, reading the player's live dominance
     * settings under the pass's grouping.
     *
     * @param sector   the sector whose economy is read
     * @param grouping the view's grouping, sampled once for the whole pass
     * @param inputs   where a bloc's shades are read from, and how far its runs go
     * @return the planner counting held cells under the live settings
     */
    public static HeldSystemRibbonPlanner createForSector(
            SectorAPI sector,
            HolderGrouping grouping,
            RibbonPlanInputs inputs) {

        return new HeldSystemRibbonPlanner(
            sector,
            DominancePass.readFromLunaSettings(sector, grouping),
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

        if (sector == null || system == null || sector.getEconomy() == null) {
            return Optional.empty();
        }
        var footprintByBlocId = pass.readBlocFootprints(system);
        if (footprintByBlocId.isEmpty()) {
            return Optional.empty();
        }

        // The winner resolved exactly as the fill's was, tie-break included, so the bloc the band
        // opens on is the bloc the cell is painted for even where the weights alone do not say so.
        var dominantBlocId = SystemDominance.resolveDominantFactionId(
            footprintByBlocId,
            pass.tieBreakFor(sector, system));

        return Optional.of(HeldCellRibbons.planHeldCellRibbon(
            dominantBlocId,
            footprintByBlocId,
            inputs));
    }
}
