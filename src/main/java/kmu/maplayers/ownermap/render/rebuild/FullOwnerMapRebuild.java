package kmu.maplayers.ownermap.render.rebuild;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileSection;
import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellSeedRule;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.profiling.RebuildStepTerms;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshSignal;
import kmu.maplayers.base.refresh.RefreshSignalTracker;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.ResolvedOwners;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.render.clusters.OwnerMapBuilder;
import kmu.settings.KmuOwnerMapDiagnosticsSettings;

import org.apache.log4j.Logger;

import java.util.Set;

/**
 * The full walk: one rebuild of whatever a frame found stale, over one reading of the sector - the
 * cells recut where their inputs moved, then the owners, the fills, the names and the bands.
 *
 * <p>Apart from the cache that asks for it, because the two answer different questions. The cache
 * keeps the frame - the decision, the failure boundary, what a frame owing no rebuild does with
 * the systems marked since - and this is the rebuild that frame may owe, together with the two
 * things only a rebuild writes: the cells it cuts and the owners it keeps for the next one.
 *
 * <p>The cells and the kept owners live here because nothing else writes either. The cache reads
 * the cells when it hands a batch the standing map, and drops the kept owners when a batch drains a
 * marked system, since owners resolved before the sector moved cannot say who owns it.
 */
final class FullOwnerMapRebuild {

    private static final Logger LOG = Global.getLogger(FullOwnerMapRebuild.class);

    // Everything a rebuild derives from the cells. Writes its line on every call, a rebuild being
    // an event rather than a per-frame cost: the line is what says a rebuild happened at all, and
    // what it left for the render to paint.
    private static final ProfileSection REBUILD_DRAWABLES_SECTION = ProfileSection.registerSection(
        "ownerMap.rebuildDrawables", RebuildStepTerms.LOGGED_EVERY_CALL);

    // The cut number this rebuild's first cut takes. Counts up from here and never repeats within a
    // cache, so no two cuts of these cells can be mistaken for each other.
    private static final int FIRST_CUT_NUMBER = 0;

    // The sidebar preferences whose raises are traced onto a rebuild's line. These three and no
    // others, because these are the ones nothing folds into staleness: a signal a consumer does
    // read is already accounted for by the rebuild it caused.
    private static final MapLayerRefreshSignal[] TRACED_PREFERENCE_SIGNALS = {
        MapLayerCommonRefreshSignal.FILTER,
        MapLayerCommonRefreshSignal.RECEDE_STYLE,
        MapLayerCommonRefreshSignal.MAP_STYLE};

    // The machinery installed on the sector this rebuild draws. The sector it cuts cells from, the
    // movers that cut leaves out and the board it reads staleness off all come off this one handle,
    // so none of the three can name a different sector - a cut taken from the running game while
    // the drift is this sector's would leave out systems that never moved.
    private final SectorMapMachinery machinery;

    // Which systems seed a cell: the layer's statement, fixed for the life of the cache since a
    // layer has one partition whichever view it paints - a view switch must not recut the cells.
    private final CellSeedRule seedRule;

    // What the rebuild builds into, and the baselines it reports each stage complete to. Both are
    // the cache's, shared with the frame that asks for this rebuild.
    private final OwnerMapDrawables drawables;
    private final OwnerMapRebuildDecider decider;

    // Which sidebar preference a player touched since the last rebuild, for the line a rebuild
    // writes. Held here rather than by the decider because it decides nothing: a raise is context
    // read beside a rebuild, and this is what writes that rebuild's line.
    private final RefreshSignalTracker signalTracker;

    // Raw cell geometry keyed by system key, updated incrementally as systems gain or lose
    // access, paired with the number of the cut it currently holds. The cells themselves are the
    // same object for the life of the cache - one cache is made per installed sector, so they are
    // never null once the cache exists - and only the number beside them moves, which is why the
    // pair is replaced rather than the cells.
    //
    // That number counts cuts rather than echoing the reachable-set revision, because the two
    // are not the same fact: a seed-input or dev-toggle change recuts every cell while that
    // signal stands still (see CellCutInputs). Anything keying reused work off which cut it was
    // derived against needs a value that moves on all three, and a counter moves on every recut
    // by construction - where a fold of the three inputs could collide two cuts onto one number
    // and offer work fitted inside cell shapes that are gone.
    private RevisedCellGeometry cellGeometry =
        new RevisedCellGeometry(new CellGeometryCache(), FIRST_CUT_NUMBER);

    // The owners the last rebuild resolved, kept so a rebuild that owes no new reading of the
    // sector - a style pick moved and nothing else - can paint over what the last one got rather
    // than ask the source for the same answer. Dropped, not just superseded, whenever the sector
    // moves under it, which is what the marked-system drains say.
    private ResolvedOwners standingOwners;

    /**
     * @param machinery the machinery installed on the sector this rebuild draws
     * @param seedRule  which systems seed a cell - the layer's statement
     * @param drawables what the rebuild builds into
     * @param decider   the baselines each stage reports itself complete to
     */
    FullOwnerMapRebuild(
            SectorMapMachinery machinery,
            CellSeedRule seedRule,
            OwnerMapDrawables drawables,
            OwnerMapRebuildDecider decider) {

        this.machinery = machinery;
        this.seedRule = seedRule;
        this.drawables = drawables;
        this.decider = decider;
        this.signalTracker = new RefreshSignalTracker(
            machinery.resolveRefreshBoard(),
            TRACED_PREFERENCE_SIGNALS);
    }

    /** @return the cells as last cut, paired with the cut number they stand at */
    RevisedCellGeometry getCellGeometry() {
        return cellGeometry;
    }

    /**
     * Forgets the owners the last rebuild resolved, when a batch drained a system the sector moved
     * under: those owners no longer say who owns it, whether or not the batch folds it.
     */
    void dropStandingOwners() {
        standingOwners = null;
    }

    /** Releases the cut cells, when the machinery holding the cache goes. */
    void discardCells() {

        cellGeometry
            .cells()
            .clearCachedCells();
    }

    /**
     * The rebuild itself, run only where the decision found something stale. Reports each stage
     * complete to the decider only after it completes, so a thrown rebuild is retried next frame.
     * Builds under the active view's rules, so a view switch (folded into the content revision)
     * rebuilds the clusters under the newly-selected view.
     *
     * <p>A rebuild is a pass, and is read as one: whichever stages run, they run against one reading
     * of the sector, opened here and handed to each.
     *
     * @param staleHalves this frame's decision, which found the content stale
     * @param view        the view being painted
     */
    void rebuildWhatIsStale(StaleHalves staleHalves, OwnerPaintedView view) {

        // The sector this rebuild draws, read once. Every stage below is answered from this one
        // reference, so a rebuild cannot name one sector to its cut and another to its fills.
        var sector = machinery.resolveSector();

        // The one walk of that sector this rebuild's stages share: the cell cut, the owners and
        // the bands each ask every system what it holds, so one walk per system serves all three.
        // Opened here rather than by each stage, which would have a single rebuild walk the whole
        // sector three times over.
        //
        // Paired with the visibility rules the cut is taken under, so the layer's source reads
        // each system under the very rule that decided which systems got cells.
        //
        // Discarded with the rebuild. A kept one would draw the next rebuild off the sector this
        // one saw, which is the change a rebuild exists to show.
        var walk = new SectorWalk(
            new SectorPassIndex(sector),
            staleHalves.cellCut().visibilityRules());

        if (staleHalves.isCellCutStale()) {
            rebuildGeometry(staleHalves, sector, walk.sectorIndex());
        }

        // Everything derived from the cells under one scope, the cut having timed itself: what
        // the draw lists cost this rebuild is this span, and every stage it drives sits inside it.
        try (var drawablesScope = ActiveProfiler
                .resolveProfiler()
                .open(REBUILD_DRAWABLES_SECTION)) {

            var wereOwnersReused = rebuildDrawables(staleHalves, view, walk);

            // Traces the content rebuild's result: what the render will paint, so a wrong or empty
            // render can be confirmed against what was built. The count reported is whichever view
            // was built this rebuild - the normal styled cells or the debug overlay's base loops -
            // which is why it is named rather than counted: the two are not one quantity.
            //
            // Beside it, whether the owners were carried over rather than read, and which sidebar
            // preference a player touched since the last rebuild. Those signals decide nothing -
            // the bake folds the sampled values - so this is the only place the answer lands where
            // the rebuild it preceded can be read against it. It is context rather than cause: a
            // signal may be raised with no rebuild owed, and a rebuild may be owed with none raised.
            drawablesScope.tagCall("contentRevision=" + staleHalves.contentRevision()
                + " " + drawables.describeBuiltCounts()
                + " geometryRebuilt=" + staleHalves.isCellCutStale()
                + " ownersReused=" + wereOwnersReused
                + " signalsRaised=" + signalTracker.describeRaisesSinceTheLastReading());
        }
    }

    // The draw lists themselves: one view or the other, the names minted from the placements it
    // left, and the staleness this rebuild has just answered drained.
    //
    // Reports whether the owners were carried over from the last rebuild rather than read, for the
    // line the rebuild writes: a rebuild that reused it cost what the paint costs, and a reader of
    // that line should be able to tell the two apart without the scan rows beneath.
    private boolean rebuildDrawables(
            StaleHalves staleHalves,
            OwnerPaintedView view,
            SectorWalk walk) {

        // Drained before the owners are chosen rather than after the build, because whether any
        // system is marked is half of whether the standing owners may be kept: a marked system is
        // one the sector moved under, and owners resolved before it moved cannot say who owns it.
        // A system marked after this drain sits on the board for the next frame's fold, which is
        // what the fold is for - where a drain after the build would discard it on the assumption
        // that the build had read everything, an assumption reused owners do not meet.
        var staleSystemKeys = machinery.resolveRefreshBoard().drainStaleGroupingSystemKeys();
        var wereOwnersReused = false;

        // The view's two answers about its owners, resolved once for this rebuild under one
        // sampling of whatever it reads live, and handed to every stage below.
        var viewReading = view.resolveViewReading(walk.sector());

        // Sampled once beside the sidebar preferences and carried, for the same reason they are:
        // the overlay decides whether the names are fitted, and a batch folding into this build
        // after it has to fit under what the build was baked under. Flipping it is a KMU setting
        // change, so it forces the rebuild that samples it afresh.
        var isAnchorOverlayShown = KmuOwnerMapDiagnosticsSettings.getOwnerMapShowClusterAnchors();

        // Build one view or the other, never both: the debug overlay replaces the normal
        // render, so in debug mode the production draw lists are not built at all, and the
        // unused view is nulled. The toggle is a KMU setting, so flipping it bumps the content
        // revision and forces this rebuild - which is what swaps the two.
        if (KmuOwnerMapDiagnosticsSettings.shouldTraceBordersForDebug()) {

            drawables.rebuildBorderTracingOverlay(
                cellGeometry,
                walk,
                viewReading,
                staleHalves.contentInputs(),
                isAnchorOverlayShown);

            // The overlay read owners for itself and drops what was marked, so nothing standing
            // can be trusted to say who owns what once it has run.
            standingOwners = null;

        } else {

            wereOwnersReused = rebuildProductionView(
                staleHalves,
                viewReading,
                isAnchorOverlayShown,
                walk,
                staleSystemKeys);
        }

        // The name choice comes off this rebuild's own sampling rather than the preference, so
        // what is minted matches what was fitted.
        drawables.rebuildLabels(staleHalves.contentInputs().nameFormat().areNamesDrawn());
        decider.recordContentRebuilt(staleHalves);

        return wereOwnersReused;
    }

    // The production view, off the view's source over this rebuild's walk of the sector. Apart
    // from the dispatch because it alone decides whether the standing owners are kept or asked for
    // afresh, and reports which for the line the rebuild writes.
    private boolean rebuildProductionView(
            StaleHalves staleHalves,
            ViewReading viewReading,
            boolean isAnchorOverlayShown,
            SectorWalk walk,
            Set<SystemKey> staleSystemKeys) {

        var wereOwnersReused = decider.canReuseStandingOwners(
            staleHalves,
            standingOwners != null,
            staleSystemKeys);

        if (!wereOwnersReused) {

            standingOwners = OwnerMapBuilder.resolveOwners(
                viewReading.source(),
                walk,
                staleHalves.contentInputs());
            decider.recordOwnersResolved(staleHalves);
        }

        drawables.rebuildClustersAndBands(
            cellGeometry,
            walk,
            viewReading,
            staleHalves.contentInputs(),
            isAnchorOverlayShown,
            standingOwners);

        return wereOwnersReused;
    }

    // Brings the geometry cache in line with the reachable systems, rebuilding only the cells
    // affected by an access change or a system starting or stopping moving - or every cell, when
    // the frontier resolution or the cell radius changed, since either reseeds them all. Feeds the
    // cache the currently-moving systems so they are left out of the partition (they seed no cell
    // and clip no neighbour), the rebuild's reading of the sector, whose visibility rules put a
    // forced or undiscovered settled system on the drawn set, and the layer's own rule for which of
    // the systems that reading draws seed a cell at all.
    //
    // TODO: when only geometry changed, reshape just the cells CellGeometryCache rebuilt - the
    // system that gained or lost access and every cell it touches - rather than the full clusters
    // rebuild that follows this cut. Have updateFromSector report its affected-cell set and drive a
    // targeted reshape from it, the geometry-side analogue of the marked-system fold.
    private void rebuildGeometry(
            StaleHalves staleHalves,
            SectorAPI sector,
            SectorPassIndex sectorIndex) {

        var cellCut = staleHalves.cellCut();

        // Transition trace: a stale cell or one left behind after an access change can be tied
        // to the revision step - or the seed inputs or toggle flip - that drove it. Described by
        // the decider, which holds the reading being moved away from, and read before the cut is
        // recorded below.
        LOG.debug("Owner map geometry stale; rebuilding cut " + cellGeometry.revision()
            + " " + decider.describeCellCutTransition(staleHalves));

        var pass = new MapVisibilityPass(
            sectorIndex,
            VisibleStars.scan(sector),
            cellCut.visibilityRules());

        // Not wrapped in a section of its own: the cut opens one, so a scope here would be a
        // second row over the same span, differing only in which of the two names it carried.
        cellGeometry.cells().updateFromSector(
            pass,
            seedRule,
            machinery.resolveMovingSystems().getMovingSystemKeys(),
            cellCut.seedInputs());

        // A fresh cut number the moment the cells are recut, whichever of the four inputs
        // drove it - so work derived from the previous cut can never read as derived from
        // this one. Counted rather than taken from the reachable-set revision, which stands
        // still through a seed-input or dev-toggle recut.
        cellGeometry = cellGeometry.copyWithRevision(cellGeometry.revision() + 1);

        decider.recordCellsCut(staleHalves);
    }
}
