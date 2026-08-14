package kmu.maplayers.politicalmap.base.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

/**
 * What every counting rule needs beyond the counts themselves: where a bloc's two shades come
 * from, and how a band is laid.
 *
 * <p>The pair travels together through every rule and every planner, because neither half is
 * usable without the other - a run is a colour laid to a rule - and because both are the pass's
 * own one-time reads. Carried as one value so a planner cannot be built from this pass's rules
 * and a stale palette source, and so the two are sampled in a single place rather than at each
 * mechanic's own factory.
 *
 * @param palettes where each present bloc's two shades are read from
 * @param rules    how a band is laid: the run lengths, and what a cell nobody contests draws
 */
public record RibbonPlanInputs(
    BlocPaletteReader palettes,
    RibbonPlanRules rules) {

    /**
     * Samples the pair for one pass against the live sector.
     *
     * <p>The palette source is built here, once, and handed to every planner the pass creates -
     * so two mechanics counting the same map read a bloc's colours through one object rather than
     * each resolving its own from the same two inputs.
     *
     * @param sector   the sector each bloc's colour faction is read from
     * @param grouping the grouping that names that colour faction, sampled once by the pass
     * @param rules    how a band is laid, sampled once by the pass
     * @return the pair every rule in the pass is stated over
     */
    public static RibbonPlanInputs createForSector(
            SectorAPI sector,
            HolderGrouping grouping,
            RibbonPlanRules rules) {

        return new RibbonPlanInputs(
            new SectorBlocPalettes(sector, grouping),
            rules);
    }
}
