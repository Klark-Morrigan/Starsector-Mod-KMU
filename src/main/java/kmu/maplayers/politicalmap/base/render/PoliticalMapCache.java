package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.logging.SessionWarning;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileSection;
import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.systems.SectorPassIndex;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.profiling.RebuildStepTerms;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshSignal;
import kmu.maplayers.base.refresh.RefreshSignalTracker;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.ResolvedHolding;
import kmu.maplayers.politicalmap.base.render.territories.TerritoryBuilder;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuPoliticalMapDiagnosticsSettings;

import org.apache.log4j.Logger;

import java.util.List;

/**
 * Keeps the political map's derived draw lists fresh with the least work per frame, and hands
 * the current ones to the renderer. The layer renderer holds one of these and asks it to
 * {@link #refresh} each frame the map is open; everything the map draws is cached here and rebuilt
 * only when its inputs change.
 *
 * <p>System positions in hyperspace are fixed for the life of a save, so the raw cells are built
 * once and cached; they are reseeded only when the reachable-system set changes, the
 * frontier-resolution or cell-reach setting changes (either seeds every cell), or a visibility setting
 * override flips (each changes which systems seed a cell). The territories are rebuilt in full when
 * KMU's LunaLib settings change (detected off LunaLib's change event via
 * {@link KmuLunaSettings#getSettingsRevision()}), when the view switches or samples a different
 * live input, when one of the sidebar preferences the bake reads holds a different value
 * ({@link ContentInputs}), or when the geometry itself was rebuilt; between those, a colony resize
 * marks just its own system stale and drives an incremental re-shape. So switching a colour,
 * dragging an opacity slider or picking a spotlight takes effect live, and the per-frame path is
 * otherwise a couple of int compares, never a per-frame economy scan.
 *
 * <p>Which of those a frame owes is {@link PoliticalMapRebuildDecider}'s answer, asked first and
 * acted on here: the decision is answerable from revisions and settings alone and nearly every
 * frame stops at it, while a rebuild opens a reading of the sector. This class is the rebuild
 * paths and the standing map they edit.
 *
 * <p>One cache serves both screens, and the frame says which it is drawing for. Since what the
 * revision folds is the sampled preference <em>values</em>, two screens set alike are one bake and a
 * switch between them rebuilds nothing; two set differently rebuild at the switch, which is the same
 * rebuild changing that pick on one screen already costs, paid at a different moment.
 *
 * <p>Everything held here is derived from one sector, and nothing here enters a save: the holder
 * belongs to that sector's installed map machinery and goes with it, so no field needs transient
 * marking and nothing needs emptying when a sector changes. Every baseline starts at its
 * rebuild-forcing seed, so the first frame after a sector is installed on builds both halves from
 * scratch.
 *
 * <p><b>One cache serves exactly one sector.</b> {@link CellGeometryCache} reconciles by system
 * <em>key</em>, so two sectors through one cache would not overwrite each other's cells but keep
 * them, cut around positions the second sector's systems never sat at. Which is why the sector a
 * rebuild reads comes off the machinery this cache was made for rather than off the running
 * game: asking the running game is how a cache comes to be handed a second sector at all.
 */
final class PoliticalMapCache {
    private static final Logger LOG = Global.getLogger(PoliticalMapCache.class);

    // Everything a rebuild derives from the cells. Writes its line on every call, a rebuild being
    // an event rather than a per-frame cost: the line is what says a rebuild happened at all, and
    // what it left for the render to paint.
    private static final ProfileSection REBUILD_DRAWABLES_SECTION = ProfileSection.registerSection(
        "politicalMap.rebuildDrawables", RebuildStepTerms.LOGGED_EVERY_CALL);

    // The cut number this cache's first cut takes. Counts up from here and never repeats within a
    // cache, so no two cuts of these cells can be mistaken for each other.
    private static final int FIRST_CUT_NUMBER = 0;

    // The sidebar preferences whose raises are traced onto a rebuild's line. These three and no
    // others, because these are the ones nothing folds into staleness: a signal a consumer does
    // read is already accounted for by the rebuild it caused.
    private static final MapLayerRefreshSignal[] TRACED_PREFERENCE_SIGNALS = {
        MapLayerCommonRefreshSignal.FILTER,
        MapLayerCommonRefreshSignal.RECEDE_STYLE,
        MapLayerCommonRefreshSignal.MAP_STYLE};

    // The machinery installed on the sector this cache draws. The sector a rebuild cuts cells from,
    // the movers that cut leaves out and the board it reads staleness off all come off this one
    // handle, so none of the three can name a different sector - a cut taken from the running game
    // while the drift is this sector's would leave out systems that never moved.
    private final SectorMapMachinery machinery;

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

    // Everything built to draw, and how each part of it is rebuilt. Held apart from the decision
    // below, which answers when a rebuild is owed and what it carries forward rather than what it
    // produces.
    private final PoliticalMapDrawables drawables = new PoliticalMapDrawables();

    // What a frame owes, and the baselines that question is asked against. Advanced only as each
    // stage below reports itself complete, so a thrown rebuild is asked again next frame.
    private final PoliticalMapRebuildDecider decider;

    // Which sidebar preference a player touched since the last rebuild, for the line a rebuild
    // writes. Held here rather than by the decider because it decides nothing: a raise is context
    // read beside a rebuild, and this is what writes that rebuild's line.
    private final RefreshSignalTracker signalTracker;

    // The holding the last rebuild resolved, kept so a rebuild that owes no new reading of the
    // sector - a style pick moved and nothing else - can paint over what the last one read rather
    // than walk the economy for the same answer. Dropped, not just superseded, whenever the sector
    // moves under it, which is what the marked-system drains below say.
    private ResolvedHolding standingHolding;

    // Said once per session: refresh runs every frame the map is open, so a recurring rebuild
    // failure would otherwise flood the log, and the second line says nothing the first did not.
    private final SessionWarning rebuildFaultWarning = new SessionWarning(LOG);

    PoliticalMapCache(SectorMapMachinery machinery) {
        this.machinery = machinery;
        this.decider = new PoliticalMapRebuildDecider(machinery);
        this.signalTracker = new RefreshSignalTracker(
            machinery.resolveRefreshBoard(),
            TRACED_PREFERENCE_SIGNALS);
    }

    /** @return the built production draw lists, or null while the debug overlay has replaced them */
    public PoliticalMapTerritories getTerritories() {
        return drawables.getTerritories();
    }

    /** @return the built debug border-tracing overlay, or null in the normal (non-debug) view */
    public ClusterBorderStageOverlay getBorderStageOverlay() {
        return drawables.getBorderStageOverlay();
    }

    // Whether the debug border-tracing overlay is the built view this frame - the one branch the
    // renderer needs to pick which base view to paint.
    public boolean isDebug() {
        return drawables.isDebug();
    }

    /** @return the cluster-label placements, drawn over whichever base view is live */
    public List<ClusterAnchor> getClusterAnchors() {
        return drawables.getClusterAnchors();
    }

    /** @return the cached faction-name labels */
    public List<Label> getFactionLabels() {
        return drawables.getFactionLabels();
    }

    /**
     * Releases everything built for this cache's sector, when the machinery holding it goes.
     *
     * <p>Nothing is rewound for a rebuild, because nothing rebuilds through a released cache - the
     * sector after this one is drawn by a cache of its own, which begins at its seeds. What that
     * release covers, the labels' GL buffers included, is the drawables' own.
     */
    public void disposeCachedState() {
        drawables.disposeAll();
        cellGeometry.cells().clearCachedCells();
    }

    /**
     * Brings the cached draw lists in line with the active view, rebuilding only the stale half.
     * Guarded because it runs every frame the map is open: a rebuild fault is recorded once (not
     * per frame), and the catch leaves the baselines un-advanced so the next frame retries
     * rather than the overlay going permanently stale, plus installs an empty placeholder so the
     * renderer never dereferences a null draw list.
     *
     * @param view        the view being painted, whose rules the rebuild builds under
     * @param memoryScope the screen being painted for, whose panel holds every sidebar preference the
     *                    rebuild bakes under. Handed in rather than resolved here, so the view and the
     *                    picks it is drawn under come off the frame's one reading of which screen is
     *                    showing
     */
    public void refresh(PoliticalMapView view, ScreenMemoryScope memoryScope) {
        try {
            rebuildStaleHalves(view, memoryScope);
        } catch (RuntimeException exception) {
            rebuildFaultWarning.warnOnce(
                "Political map rebuild failed; retrying next frame, keeping last good draw lists",
                exception);
            drawables.ensureTerritoriesNonNull(view);
        }
    }

    // Asks what this frame owes and then either patches the standing map or rebuilds it. The two
    // are separate steps with the gate between them, because the rebuild opens a reading of the
    // sector and nearly every frame must open none: the decision is answerable from revisions and
    // settings alone, so it runs first and most frames stop at it.
    private void rebuildStaleHalves(PoliticalMapView view, ScreenMemoryScope memoryScope) {

        // Both views null means neither has been built for this sector, so the first frame forces
        // the build even if the content revision happens to match its unbuilt seed.
        var staleHalves = decider.decideWhatIsStale(view, memoryScope, drawables.hasNothingBuilt());

        if (!staleHalves.isContentStale()) {
            applyStandingMapUpdates();
            return;
        }
        rebuildWhatIsStale(staleHalves, view);
    }

    // The rebuild itself, run only where the decision found something stale. Reports each stage
    // complete to the decider only after it completes, so a thrown rebuild is retried next frame.
    // Builds under the active view's rules, so a view switch (folded into the content revision)
    // rebuilds the territories under the newly-selected view.
    //
    // A rebuild is a pass, and is read as one: whichever stages run, they run against one reading
    // of the sector, opened here and handed to each.
    private void rebuildWhatIsStale(StaleHalves staleHalves, PoliticalMapView view) {

        // The sector this rebuild draws, read once. Every stage below is answered from this one
        // reference, so a rebuild cannot name one sector to its cut and another to its fills.
        var sector = machinery.resolveSector();

        // The one reading of that sector this rebuild's stages share: the cell cut, the fills and
        // the bands each ask every system who lives there, so one walk per system serves all
        // three. Opened here rather than by each stage, which is what let a single rebuild walk
        // the whole sector three times over.
        //
        // Discarded with the rebuild. A kept one would draw the next rebuild off the sector this
        // one saw, which is the change a rebuild exists to show.
        var sectorIndex = new SectorPassIndex(sector);

        if (staleHalves.isCellCutStale()) {
            rebuildGeometry(staleHalves, sector, sectorIndex);
        }
        // Everything derived from the cells under one scope, the cut having timed itself: what
        // the draw lists cost this rebuild is this span, and every stage it drives sits inside it.
        try (var drawablesScope = ActiveProfiler
                .resolveProfiler()
                .open(REBUILD_DRAWABLES_SECTION)) {

            var wasHoldingReused = rebuildDrawables(staleHalves, view, sector, sectorIndex);

            // Traces the content rebuild's result: what the render will paint, so a wrong or empty
            // render can be confirmed against what was built. The count reported is whichever view
            // was built this rebuild - the normal styled cells or the debug overlay's base loops -
            // which is why it is named rather than counted: the two are not one quantity.
            //
            // Beside it, whether the holding was carried over rather than read, and which sidebar
            // preference a player touched since the last rebuild. Those signals decide nothing -
            // the bake folds the sampled values - so this is the only place the answer lands where
            // the rebuild it preceded can be read against it. It is context rather than cause: a
            // signal may be raised with no rebuild owed, and a rebuild may be owed with none raised.
            drawablesScope.tagCall("contentRevision=" + staleHalves.contentRevision()
                + " " + drawables.describeBuiltCounts()
                + " geometryRebuilt=" + staleHalves.isCellCutStale()
                + " holdingReused=" + wasHoldingReused
                + " signalsRaised=" + signalTracker.describeRaisesSinceTheLastReading());
        }
    }

    // The draw lists themselves: one view or the other, the names minted from the placements it
    // left, and the staleness this rebuild has just answered drained.
    //
    // Reports whether the holding was carried over from the last rebuild rather than read, for the
    // line the rebuild writes: a rebuild that reused it cost what the paint costs, and a reader of
    // that line should be able to tell the two apart without the scan rows beneath.
    private boolean rebuildDrawables(
            StaleHalves staleHalves,
            PoliticalMapView view,
            SectorAPI sector,
            SectorPassIndex sectorIndex) {

        // Drained before the holding is chosen rather than after the build, because whether any
        // system is marked is half of whether the standing holding may be kept: a marked system is
        // one the sector moved under, and a holding read before it moved no longer says who holds
        // it. A system marked after this drain sits on the board for the next frame's fold, which
        // is what the fold is for - where a drain after the build discarded it on the assumption
        // that the build had read everything, an assumption a reused holding does not meet.
        var staleSystemKeys = machinery.resolveRefreshBoard().drainStaleGroupingSystemKeys();
        var wasHoldingReused = false;

        // Build one view or the other, never both: the debug overlay replaces the normal
        // render, so in debug mode the production draw lists are not built at all, and the
        // unused view is nulled. The toggle is a KMU setting, so flipping it bumps the content
        // revision and forces this rebuild - which is what swaps the two.
        if (KmuPoliticalMapDiagnosticsSettings.shouldTraceBordersForDebug()) {

            drawables.rebuildBorderTracingOverlay(
                cellGeometry,
                sector,
                view,
                staleHalves.contentInputs());

            // The overlay reads holding from the sector for itself and drops what was marked, so
            // nothing standing can be trusted to say who holds what once it has run.
            standingHolding = null;

        } else {

            var pass = new HolderPass(
                view.resolveGrouping(),
                staleHalves.cellCut().visibilityRules().colonyVisibility(),
                sectorIndex);

            wasHoldingReused = decider.canReuseStandingHolding(
                staleHalves,
                standingHolding != null,
                staleSystemKeys);

            if (!wasHoldingReused) {
                standingHolding = TerritoryBuilder.resolveHolding(
                    pass,
                    view,
                    staleHalves.contentInputs());
                decider.recordHoldingResolved(staleHalves);
            }
            drawables.rebuildTerritoriesAndBands(
                cellGeometry,
                pass,
                view,
                staleHalves.contentInputs(),
                standingHolding);
        }

        // The name choice comes off this rebuild's own sampling rather than the preference, so
        // what is minted matches what was fitted.
        drawables.rebuildLabels(staleHalves.contentInputs().nameFormat().areNamesDrawn());

        decider.recordContentRebuilt(staleHalves);

        return wasHoldingReused;
    }

    // Nothing stale enough to rebuild. In the normal view, fold in any per-system holder changes a
    // colony resize marked, re-shaping only those systems and their neighbours over the standing
    // territories; that batch opens a reading of its own, since none was opened for this frame.
    // The static debug overlay has no draw lists to patch, so its staleness is dropped instead -
    // it refreshes on the next full rebuild (any settings or geometry change). Under a filter the
    // incremental re-shape is bypassed too: it re-derives holders through the normal (non-filter)
    // politics, which would overwrite the spotlit keys and corrupt the spotlight, so a filtered
    // map defers holder changes to the next full rebuild instead.
    //
    // The drain happens here rather than inside the fold, and before either branch is chosen,
    // because this cache is what holds the board: the fold is reached through static entry points
    // naming no sector, so a drain made there would have to ask the running game whose staleness it
    // was emptying. Draining unconditionally is what keeps the two outcomes equivalent - one folds
    // what it took, the other drops it, and neither leaves a mark standing for the next frame to
    // find.
    //
    // Having drained, this is also what answers the frame that owes nothing, which is nearly every
    // frame: it returns before assembling the standing map for a fold that would find nothing in it.
    private void applyStandingMapUpdates() {

        var staleSystemKeys = machinery.resolveRefreshBoard().drainStaleGroupingSystemKeys();

        if (staleSystemKeys.isEmpty()) {
            return;
        }
        // A marked system is one the sector moved under, so the holding the last rebuild read no
        // longer says who holds it - whether or not the fold below is allowed to run. Dropped on
        // both outcomes: the fold edits the standing map rather than the kept holding, and the
        // deferral leaves the change for a full rebuild that has to read it for itself.
        standingHolding = null;

        if (!drawables.canFoldHolderChanges()) {
            return;
        }
        // The four halves go over as one value, and the standing pair goes in whole: a re-fit
        // leaves its own placements and rules in it, and a frame that re-fits nothing leaves both
        // alone, since the placements it did not touch are still described by the rules already
        // recorded for them.
        IncrementalPoliticsRefresh.applyStalePoliticsUpdates(
            machinery.resolveSector(),
            drawables.toStandingMap(cellGeometry),
            staleSystemKeys);
    }

    // Brings the geometry cache in line with the reachable systems, rebuilding only the cells
    // affected by an access change or a system starting or stopping moving - or every cell, when
    // the frontier resolution or the cell radius changed, since either reseeds them all. Feeds the
    // cache the currently-moving systems so they are left out of the partition (they seed no cell
    // and clip no neighbour), and the rebuild's reading of the sector, whose visibility rules put
    // a forced or undiscovered-colony system on the drawn set.
    private void rebuildGeometry(
            StaleHalves staleHalves,
            SectorAPI sector,
            SectorPassIndex sectorIndex) {

        var cellCut = staleHalves.cellCut();

        // Transition trace: a stale cell or one left behind after an access change can be tied
        // to the revision step - or the seed inputs or toggle flip - that drove it. Described by
        // the decider, which holds the reading being moved away from, and read before the cut is
        // recorded below.
        LOG.debug("Political map geometry stale; rebuilding cut " + cellGeometry.revision()
            + " " + decider.describeCellCutTransition(staleHalves));

        var pass = new MapVisibilityPass(
            sectorIndex,
            VisibleStars.scan(sector),
            cellCut.visibilityRules());

        // Not wrapped in a section of its own: the cut opens one, so a scope here would be a
        // second row over the same span, differing only in which of the two names it carried.
        cellGeometry.cells().updateFromSector(
            pass,
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
