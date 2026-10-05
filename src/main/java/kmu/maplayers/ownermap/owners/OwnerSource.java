package kmu.maplayers.ownermap.owners;

import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;
import kmu.maplayers.ownermap.ribbon.SystemRibbonPlanner;

/**
 * Where a layer's owners come from: the seam the tier resolves who paints each system through,
 * so the pipeline reads owners from one source and never names what decides them.
 *
 * <p>A view answers it beside its owner reading, the two resolved under one sampling of whatever
 * live inputs the view has ({@link kmu.maplayers.ownermap.OwnerPaintedView#resolveViewReading}).
 * The tier hands the source the rebuild's walk of the sector and gets back everything the build
 * reads the sector for; what the source opens over that walk - a colony reading, a relay scan,
 * nothing at all - is its own, and the tier reads no colony.
 *
 * <p>Three asks, because three parts of the pipeline spend the source's rule. The whole-sector
 * answer is a rebuild's. The per-system resolve is an incremental batch's, reopened over the
 * batch's own walk: it is asked of the source the standing build was resolved under, so a marked
 * system is re-derived under the very snapshot its neighbours were painted by. The band planner is
 * the bake's, because a band explains the fill it sits inside - counting it by any rule but the
 * one that painted the cell would lead the band on an owner the cell is not painted for.
 *
 * <p>A source is one rebuild's: a view that samples anything live builds a fresh one per rebuild
 * over that sampling, and the build retains it for the batches that follow.
 */
public interface OwnerSource {

    /**
     * Resolves who paints each star system for one build, and everything else the build reads the
     * sector for.
     *
     * @param walk           the rebuild's walk of the sector, opened once where the rebuild began;
     *                       a walk over no sector yields empty owners
     * @param spotlitOwnerId the spotlit owner's ID, or null when none is spotlit - a source that
     *                       spotlights reads it, one that does not ignores it
     * @return the owners per system, the fill exceptions among them, the inhabited systems and the
     *         spotlit owner's presence among the unowned ones
     */
    ResolvedOwners resolveOwners(SectorWalk walk, String spotlitOwnerId);

    /**
     * Opens one batch's per-system resolve over the batch's own walk.
     *
     * @param walk           the batch's walk of the sector
     * @param spotlitOwnerId the spotlit owner the standing build was painted under, or null
     * @return the batch's resolve, answering the three questions of one marked system
     */
    SystemOwnerResolve openSystemResolve(SectorWalk walk, String spotlitOwnerId);

    /**
     * The mechanic this source's cells are counted by for their presence bands, over the bake's
     * walk.
     *
     * <p>Asked once per bake, so every band in it is counted off one reading and judged against one
     * grouping. A source with nothing to count answers a planner planning nothing.
     *
     * @param walk  the bake's walk of the sector - the rebuild's own in the same frame, or a
     *              batch's
     * @param rules how a band is laid: the run lengths, and how far they reach on a cell nobody
     *              contests
     * @return the planner this source's bands are counted through
     */
    SystemRibbonPlanner resolveRibbonPlanner(SectorWalk walk, RibbonPlanRules rules);
}
