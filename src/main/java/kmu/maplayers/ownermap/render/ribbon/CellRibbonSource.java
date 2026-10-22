package kmu.maplayers.ownermap.render.ribbon;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.profiling.IterationScope;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.ribbon.CellRibbon;
import kmu.maplayers.base.ribbon.CellRibbonBuilder;
import kmu.maplayers.base.ribbon.RibbonBakePhases;
import kmu.maplayers.base.ribbon.RibbonBakeTally;
import kmu.maplayers.base.ribbon.RibbonPathTracer;
import kmu.maplayers.base.ribbon.RibbonStyle;
import kmu.maplayers.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.base.ribbon.overlay.CellRibbonPath;
import kmu.maplayers.ownermap.owners.OwnerSource;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.ribbon.RibbonPlanRules;

import java.util.List;
import java.util.Map;

/**
 * One bake's band source: everything a cell's band is settled from, sampled once, so asking
 * for a cell's band is a call rather than a fresh set of reads.
 *
 * <p>What it holds is what must not vary across a pass: the planner the owner source counts by, so
 * no cell is counted by a different mechanic; the pass's one read of the player's proportions
 * ({@link RibbonBakeSettings}), so a slider moved mid-pass cannot leave two cells drawn to
 * different designs; and the map those bands go on ({@link RibbonBakeSurface}). Sampling them here mirrors how the rest of a rebuild is driven - one
 * snapshot, then a per-item call over it - and is what lets the incremental re-shape bake a band
 * identical to the one the full rebuild would have.
 *
 * <p>The planner is the source's answer ({@link OwnerSource#resolveRibbonPlanner}) - which mechanic
 * counts a cell, and who stands together in a contest it judges - so nothing here names what
 * supplies either. It is asked of the source the build's owners came from, which is what keeps a
 * band counted under the same sampling of the layer's live inputs the fill was painted under.
 *
 * <p>The walk the counts are made off arrives rather than being opened here, because how current it
 * has to be is the caller's question and not this one's: a bake in the same frame as the build
 * before it counts off that build's walk of each system, while one on its own cadence opens a fresh
 * walk rather than report a sector as it stood some flips ago.
 *
 * <p>The surface gates on inhabitation rather than on this layer's holding, because that is the
 * question a band is actually about: a system splits whether or not the mechanic painting the map
 * gives it to anybody, and a layer's holding can leave a settled system unaccounted for.
 * That gate is also what keeps a bake affordable, most of the sector being empty space and every
 * ask walking what a system holds - for some mechanics settling a whole contest over it. What
 * its looser reading costs is weighed in this package's README.
 *
 * <p>Which cells are offered a band at all is settled here: a cell nothing lives in or with nowhere
 * to start from, and a system the sector no longer lists, are refused before anything is counted.
 * Every other cell is counted, and its plan handed to {@link CellRibbonBuilder#layCellRibbon}, which
 * answers a plan with nothing in it before any ring is walked.
 *
 * <p>The diagnostic overlay's paths are traced through the same pass, from the same sizes, the same
 * gate and the same kept rings, for the reason the overlay exists at all: it is worth looking at
 * only while it is the cells' own paths it is drawing.
 *
 * <p>Named a source rather than a builder because the per-cell work is
 * {@link CellRibbonBuilder}'s: what this adds is the pass the work is done under, which is the
 * same split a mechanic's own ribbon source makes one
 * level up between a mechanic's answer and the rule that shapes it.
 */
public final class CellRibbonSource {

    private final SystemRibbonPlanner planner;
    private final RibbonStyle style;
    private final RibbonBakeSurface surface;
    private final Map<SystemKey, StarSystemAPI> systemByKey;

    private CellRibbonSource(
            SystemRibbonPlanner planner,
            RibbonStyle style,
            RibbonBakeSurface surface,
            Map<SystemKey, StarSystemAPI> systemByKey) {

        this.planner = planner;
        this.style = style;
        this.surface = surface;
        this.systemByKey = systemByKey;
    }

    /**
     * The bake's band source, stated over everything one pass's bands are settled from.
     *
     * <p>Only a pass that draws bands builds one. A pass with the bands switched off answers every
     * cell "no band" before reaching here, so no mechanic is resolved and no sector index is read.
     *
     * @param walk      the walk of the sector the counts are made off - the rebuild's own, or a
     *                  batch's. Whose walk it is, and so how current it is, is the caller's to
     *                  decide
     * @param source    the owner source the build's owners came from, supplying the mechanic its
     *                  cells are counted by - the same one they were painted by
     * @param surface   the drawn map the bands go on: which cells take one, where each starts, the
     *                  room it keeps clear of, and the rings already traced. Taken as the one value
     *                  this reads rather than as the cells and caches holding those answers, so
     *                  what a band is laid out from is stated in the signature rather than
     *                  reachable through it
     * @param style     the sizes every band in the pass is laid at, as the pass sampled them
     * @param planRules how far a band's runs reach, as the pass sampled them; handed to whatever
     *                  planner the source resolves, so every mechanic lays its cells by one rule
     * @return the source the pass bakes its bands through
     */
    public static CellRibbonSource createForPass(
            SectorWalk walk,
            OwnerSource source,
            RibbonBakeSurface surface,
            RibbonStyle style,
            RibbonPlanRules planRules) {

        // Off the walk's own index rather than a traversal of this bake's own: the cells were cut
        // from that same index, and a second traversal here would put a rebuild over the time the
        // frame allows it.
        return new CellRibbonSource(
            source.resolveRibbonPlanner(walk, planRules),
            style,
            surface,
            walk.sectorIndex().readSystemsByKey());
    }

    /**
     * Bakes the band of one cell.
     *
     * @param cellKey         the cell being baked, the key its traced ring is kept under
     * @param drawnSystemKey  the system the cell draws as, or null for a cell with no star of its
     *                        own - which nothing paints, so it carries no band
     * @param fillPolygon     the cell's painted outline, the ring the band runs inside
     * @param bakeScope       the pass's open scope, whose turn is this cell and whose phases this
     *                        cell's count and the geometry that follows it are marked on
     * @param bakeTally       the bake call's counts, which a cell refused here is left out of: it
     *                        was never offered a band, so there is nothing about one to count
     * @return the cell's baked band, or {@link CellRibbon#NONE} where it draws none
     */
    public CellRibbon buildCellRibbon(
            SystemKey cellKey,
            SystemKey drawnSystemKey,
            List<double[]> fillPolygon,
            IterationScope bakeScope,
            RibbonBakeTally bakeTally) {

        var site = resolveBandLayoutSite(drawnSystemKey);
        var system = site == null ? null : systemByKey.get(drawnSystemKey);

        // A system the sector no longer lists leaves the band with nothing to count, which is the
        // same answer as an unpainted cell: no band, rather than one counted off a stand-in.
        if (system == null) {
            return CellRibbon.NONE;
        }

        // Marked apart from the geometry that follows it because it is the one phase of a bake
        // that grows with what the systems hold rather than with the cells: the first ask about a
        // system walks what it holds, so how much the sector holds moves this and the cells' own
        // ring work by different factors.
        var plan = planner.planSystemRibbon(system);

        bakeScope.markPhase(RibbonBakePhases.PLAN_PHASE);

        return CellRibbonBuilder.layCellRibbon(
            cellKey,
            fillPolygon,
            site,
            plan,
            style,
            surface.nameBoxes(),
            surface.ringPathCache(),
            bakeScope,
            bakeTally);
    }

    /**
     * Traces one cell's band path for the diagnostic overlay, whether or not a band was laid on
     * it.
     *
     * <p>Over the cells the band pass considered rather than every cell on the map: a band is only
     * ever laid where somebody lives, so tracing the rest would bury the cells the overlay is asked
     * about under a ring around every piece of empty space in the sector. What it adds to the pass
     * above is everything downstream of the ring - a cell whose plan came back empty, and one
     * refused the room to draw what it planned, both still show the path they would have used.
     *
     * @param cellKey        the cell being traced, the key its traced rings are kept under
     * @param drawnSystemKey the system the cell draws as, or null for a cell with no star of its
     *                       own - which nothing paints, so no band is ever laid on it
     * @param fillPolygon    the cell's painted outline, the ring the path runs inside
     * @return the cell's traced path, or {@link CellRibbonPath#NONE} where none was traced
     */
    public CellRibbonPath traceCellRibbonPath(
            SystemKey cellKey,
            SystemKey drawnSystemKey,
            List<double[]> fillPolygon) {

        var site = resolveBandLayoutSite(drawnSystemKey);

        return site == null
            ? CellRibbonPath.NONE
            : RibbonPathTracer.traceInspectedRibbonPath(
                cellKey,
                fillPolygon,
                site,
                style,
                surface.ringPathCache());
    }

    // The point a cell's band is laid out from, or null on a cell no band is laid on at all.
    //
    // Two refusals in one answer, since both are the same fact about the cell rather than about
    // the band. Nobody lives in it, so there are no stakes for a band to report how a system
    // splits between - a band over empty space would be a readout of nothing; or it has no recorded
    // site, leaving the band nowhere to start from rather than starting it somewhere arbitrary.
    //
    // Shared by the two calls above so the overlay covers exactly the cells the band pass
    // considered: a diagnostic answering for a wider set than the pass it reports on would show
    // paths where no band was ever going to be laid.
    private double[] resolveBandLayoutSite(SystemKey drawnSystemKey) {

        // The inhabitation gate and the site are both asked under the system's own key, which is
        // the one address the whole build is written and read under.
        if (drawnSystemKey == null
                || !surface.inhabitedSystemKeys().contains(drawnSystemKey)) {
            return null;
        }
        return surface.siteBySystemKey().get(drawnSystemKey);
    }
}
