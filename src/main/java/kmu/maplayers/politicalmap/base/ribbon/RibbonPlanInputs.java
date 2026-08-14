package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

/**
 * What every counting rule needs beyond the counts themselves: where a bloc's two shades come
 * from, how far a run goes, and what the cells nobody contests draw.
 *
 * <p>The set travels together through every rule and every planner, because no part of it is
 * usable without the rest - a run is a colour and a length, and whether a run is laid at all is
 * the gate over both - and because all of it is the pass's own one-time reads. Carried as one
 * value so a planner cannot be built from this pass's proportions and a stale palette source, and
 * so they are sampled in a single place rather than at each mechanic's own factory.
 *
 * @param palettes          where each present bloc's two shades are read from
 * @param lengths           how far a market's segment and an interjection run
 * @param uncontestedBands  what a cell no bloc but its own painter holds anything in draws:
 *                          whether it bands at all, and at what run length
 */
public record RibbonPlanInputs(
    BlocPaletteReader palettes,
    RibbonSegmentLengths lengths,
    UncontestedCellBands uncontestedBands) {

    /**
     * Samples the pair for one pass against the live sector.
     *
     * <p>The palette source is built here, once, and handed to every planner the pass creates -
     * so two mechanics counting the same map read a bloc's colours through one object rather than
     * each resolving its own from the same two inputs.
     *
     * @param sector           the sector each bloc's colour faction is read from
     * @param grouping         the grouping that names that colour faction, sampled once by the pass
     * @param lengths          how far a market's segment and an interjection run
     * @param uncontestedBands what the cells nobody contests draw, sampled once by the pass
     * @return the set every rule in the pass is stated over
     */
    public static RibbonPlanInputs createForSector(
            SectorAPI sector,
            HolderGrouping grouping,
            RibbonSegmentLengths lengths,
            UncontestedCellBands uncontestedBands) {

        return new RibbonPlanInputs(
            new SectorBlocPalettes(sector, grouping),
            lengths,
            uncontestedBands);
    }
}
