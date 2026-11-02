package kmu.maplayers.ownermap.render.rebuild;

import com.fs.starfarer.api.Global;

import kmlib.logging.SessionWarning;
import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.font.FontAtlas;

import kmu.maplayers.base.faces.SettledFaces;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellSeedRule;
import kmu.maplayers.base.hover.MapHoverTargets;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelFonts;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.render.MapFrameCache;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferences;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Keeps an owner map's derived draw lists fresh with the least work per frame, and hands
 * the current ones to the renderer. The framework's frame sequence holds one of these as the layer's
 * {@link MapFrameCache} and asks it to {@link #refreshDrawLists} each frame the map is open;
 * everything the map draws is cached here and rebuilt only when its inputs change.
 *
 * <p>System positions in hyperspace are fixed for the life of a save, so the raw cells are built
 * once and cached, and recut only when what they are cut from moves ({@link CellCutInputs}).
 * The clusters are rebuilt in full when
 * KMU's LunaLib settings change (detected off LunaLib's change event via
 * {@link KmuLunaSettings#getSettingsRevision()}), when the view switches or samples a different
 * live input, when one of the sidebar preferences the bake reads holds a different value
 * ({@link ContentInputs}), or when the geometry itself was rebuilt. So switching a colour, dragging
 * an opacity slider or picking a spotlight takes effect live. The per-frame path is otherwise one
 * sample of the sidebar preferences, held with the revisions and compared by value against what the
 * standing map was built under - never a per-frame economy scan. The sample stays per frame because a
 * screen switch moves every pick while no counter moves.
 *
 * <p>The clusters are also rebuilt when a shade the standing map was painted in moves - the player
 * picking their own colours, or a mod recolouring an owner ({@link PaintedPalettes}).
 * Each owner's shades are baked into what the frames draw, and nothing else here follows them, so
 * each frame asks the standing map's owner reading whether it still answers them.
 *
 * <p>Between those, a change in one system marks just its own system stale, and what a frame does
 * with the marked systems is the layer's {@link OwnerMapRefreshStrategy}: fold them into the
 * standing map, or owe the next frame the full rebuild. The full rebuild is the fallback either way
 * - for a strategy that does not fold, for a standing map that cannot take a fold, the debug
 * overlay's or a failed build's, and for a fold that threw.
 *
 * <p>The marked systems are drained, and the batch's walk opened, here rather than by the strategy
 * or the batch, because this cache holds the machinery: a strategy is handed the standing map and
 * names no sector, so a drain or a walk made there would have to ask the running game which sector
 * it meant. Made here, what is drained and what is walked are one sector's by construction. The walk
 * is taken under the visibility rules this frame sampled, which are the ones the standing cells
 * were cut under, since a frame reaching the strategy owes no recut.
 *
 * <p>Which of those a frame owes is {@link OwnerMapBuildLedger}'s answer, asked first and acted on
 * here: the decision is answerable from revisions and settings alone and nearly every frame stops at
 * it, while a rebuild opens a reading of the sector. The rebuild itself is
 * {@link FullOwnerMapRebuild}'s; this class is the frame around it.
 *
 * <p>One cache serves both screens, and the frame says which it is drawing for. Since what the
 * revision folds is the sampled preference <em>values</em>, two screens set alike are one bake and a
 * switch between them rebuilds nothing; two set differently rebuild at the switch, which is the same
 * rebuild changing that pick on one screen already costs, paid at a different moment.
 *
 * <p>Everything held here is derived from one sector, and nothing here enters a save: the cache
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
public final class OwnerMapCache
        implements MapFrameCache<OwnerPaintedView> {

    private static final Logger LOG = Global.getLogger(OwnerMapCache.class);

    // The machinery installed on the sector this cache draws. The sector a batch walks and the
    // board it drains come off this one handle, so the two cannot name different sectors.
    private final SectorMapMachinery machinery;

    // Everything built to draw, and how each part of it is rebuilt. Held apart from the decision
    // below, which answers when a rebuild is owed and what it carries forward rather than what it
    // produces.
    private final OwnerMapDrawables drawables = new OwnerMapDrawables(this::settleLabelFace);

    // What a frame owes, and the ledger that question is asked against. Advanced only as each
    // stage reports itself complete, so a thrown rebuild is asked again next frame.
    private final OwnerMapBuildLedger ledger;

    // The full walk a stale frame runs, and the cells and kept owners only it writes.
    private final FullOwnerMapRebuild fullRebuild;

    // What a frame owing no rebuild does with the systems marked since: the layer's choice.
    private final OwnerMapRefreshStrategy refreshStrategy;

    // Said once per session: refreshDrawLists runs every frame the map is open, so a recurring rebuild
    // failure would otherwise flood the log, and the second line says nothing the first did not.
    private final SessionWarning rebuildFaultWarning = new SessionWarning(LOG);

    // Apart from the rebuild's warning because a thrown fold means something else: the batch it
    // dropped. One shared warning would let a rebuild fault silence the news of a fold fault.
    private final SessionWarning foldFaultWarning = new SessionWarning(LOG);

    /**
     * @param machinery       the machinery installed on the sector this cache draws
     * @param bodyPreferences the drawing layer's body preferences, which every rebuild samples its
     *                        picks from
     * @param seedRule        which systems seed a cell - the layer's statement,
     *                        {@link CellSeedRule#SEED_DRAWN_SYSTEMS} for a layer with none of its own
     * @param refreshStrategy what a frame owing no rebuild does with the systems marked since -
     *                        {@link OwnerMapRefreshStrategy#INCREMENTAL} by default,
     *                        {@link OwnerMapRefreshStrategy#FULL_REBUILD} for a layer whose answer for
     *                        one system moves with systems nobody marked
     */
    public OwnerMapCache(
            SectorMapMachinery machinery,
            OwnerMapBodyPreferences bodyPreferences,
            CellSeedRule seedRule,
            OwnerMapRefreshStrategy refreshStrategy) {

        this.machinery = machinery;
        this.refreshStrategy = refreshStrategy;
        this.ledger = new OwnerMapBuildLedger(machinery, bodyPreferences);
        this.fullRebuild = new FullOwnerMapRebuild(machinery, seedRule, drawables, ledger);

        // The strategy decides what every marked batch costs, so the log states it once up front
        // rather than leaving a reader to infer it from which batch lines appear.
        LOG.debug("Owner map cache built; strategy=" + refreshStrategy);
    }

    /** @return the base view standing this frame, which the renderer paints by case */
    public BuiltView getBuiltView() {
        return drawables.getBuiltView();
    }

    /**
     * @return the production draw lists as the shapes a cursor read tests against - null while the
     *         debug overlay has replaced them or nothing has been built, which the read parks on
     */
    @Override
    public MapHoverTargets resolveHoverTargets() {

        return drawables
            .getBuiltView()
            .selectByCase(
                clusters -> clusters,
                borderStageOverlay -> null,
                () -> null);
    }

    /** @return the cluster-label placements, drawn over whichever base view is live */
    public List<ClusterAnchor> getClusterAnchors() {
        return drawables.getClusterAnchors();
    }

    /** @return the cached owner-name labels */
    public List<Label> getOwnerLabels() {
        return drawables.getOwnerLabels();
    }

    /**
     * @return what the last rebuild left for the render to paint, in the terms of whichever view
     *         it built
     */
    public String describeBuiltCounts() {
        return drawables.describeBuiltCounts();
    }

    /**
     * Releases everything built for this cache's sector, when the machinery holding it goes.
     *
     * <p>Nothing is rewound for a rebuild, because nothing rebuilds through a released cache - the
     * sector after this one is drawn by a cache of its own, which begins at its seeds. What that
     * release covers, the labels' GL buffers included, is the drawables' own.
     */
    @Override
    public void disposeCachedState() {

        // Described before the release, which leaves nothing to count.
        var releasedState = "view=" + drawables.describeBuiltViewKind()
            + " " + drawables.describeBuiltCounts()
            + " labels=" + drawables.getOwnerLabels().size()
            + " anchors=" + drawables.getClusterAnchors().size()
            + " cells=" + fullRebuild.getCellGeometry().cells().getCellEdgesByCellKey().size();

        drawables.disposeAll();
        fullRebuild.discardCells();

        LOG.debug("Owner map cache released; " + releasedState);
    }

    /**
     * Brings the cached draw lists in line with the active view, rebuilding only the stale half.
     * Guarded because it runs every frame the map is open: a fault is recorded once (not per frame),
     * and the catch owes the next frame the full rebuild rather than the overlay going permanently
     * stale. Whatever view stood before the fault keeps drawing, and before any build that is the
     * placeholder, which draws nothing.
     *
     * <p>A thrown fold does not reach this catch: the fold catches its own throw, since only the
     * fold holds the batch it dropped, and owes the rebuild the same way.
     *
     * @param view        the view being painted, whose rules the rebuild builds under
     * @param memoryScope the screen being painted for, whose panel holds every sidebar preference the
     *                    rebuild bakes under. Handed in rather than resolved here, so the view and the
     *                    picks it is drawn under come off the frame's one reading of which screen is
     *                    showing
     */
    @Override
    public void refreshDrawLists(OwnerPaintedView view, ScreenMemoryScope memoryScope) {

        try {
            rebuildStaleHalves(view, memoryScope);

        } catch (RuntimeException runtimeException) {

            rebuildFaultWarning.warnOnce(
                "Owner map rebuild failed; retrying next frame, keeping last good draw lists",
                runtimeException);

            ledger.recordRebuildOwed();
        }
    }

    // Asks what this frame owes and then either refreshes the marked systems or rebuilds. The two
    // are separate steps with the gate between them, because the rebuild opens a reading of the
    // sector and nearly every frame must open none: the decision is answerable from revisions and
    // settings alone, so it runs first and most frames stop at it.
    private void rebuildStaleHalves(OwnerPaintedView view, ScreenMemoryScope memoryScope) {

        // The placeholder standing means no build has completed for this sector, so the frame
        // forces the build whatever the keys say.
        var staleHalves = ledger.decideWhatIsStale(view, memoryScope, drawables.hasNothingBuilt());
        if (staleHalves.staleness() == Staleness.NOTHING) {

            refreshMarkedSystems(staleHalves);
            return;
        }

        fullRebuild.rebuildWhatIsStale(staleHalves, view);
        announceDrawListsRebuilt();
    }

    // Tells whatever reads what the map shows that it was rebuilt. Raised only once a rebuild or a
    // fold has completed, so one that threw part way leaves the hover box on the reading it already
    // holds - which still matches the last good draw lists left on screen.
    private void announceDrawListsRebuilt() {

        machinery
            .resolveRefreshBoard()
            .requestRefresh(MapLayerCommonRefreshSignal.DRAW_LISTS);
    }

    // Nothing stale enough to rebuild, so the systems the board marked since are the layer's
    // strategy's: folded into the standing map, or owed to the next frame's full rebuild. A
    // view that cannot take a fold - the static debug overlay, or the placeholder standing before
    // any build completes - owes the rebuild whatever the strategy, since nothing else would show
    // the change.
    //
    // The drain comes before either branch is chosen, which is what keeps the outcomes equivalent:
    // one folds what it took, the other drops it and owes the rebuild, and neither leaves a mark
    // standing for the next frame to find.
    //
    // Having drained, this is also what answers the frame that owes nothing, which is nearly every
    // frame: it returns before assembling the standing map for a fold that would find nothing in it.
    private void refreshMarkedSystems(StaleHalves staleHalves) {

        var staleSystemKeys = machinery.resolveRefreshBoard().drainStaleGroupingSystemKeys();
        if (staleSystemKeys.isEmpty()) {
            return;
        }

        // A marked system is one the sector moved under, so the owners the last rebuild resolved
        // no longer say who owns it - whether or not it is folded. Dropped on every outcome: a fold
        // edits the standing map rather than the kept owners, and a deferral leaves the change for
        // a full rebuild that has to read it for itself.
        fullRebuild.dropStandingOwners();

        var standingMap = drawables.findFoldableStandingMap(fullRebuild.getCellGeometry());
        var unfoldedReason = standingMap.isPresent()
            ? foldMarkedSystems(staleHalves, standingMap.get(), staleSystemKeys)
            : Optional.of(drawables.describeBuiltViewKind() + " standing");

        if (unfoldedReason.isPresent()) {

            LOG.debug("Owner map left " + staleSystemKeys.size()
                + " marked system(s) unfolded; reason=" + unfoldedReason.get()
                + "; next frame rebuilds");

            ledger.recordRebuildOwed();
            return;
        }

        // A fold may paint an owner the build did not, whose shades are then watched as well.
        ledger.recordPalettesPainted(drawables.samplePaintedPalettes());
        announceDrawListsRebuilt();
    }

    // Hands the batch to the layer's strategy, and answers why it went unfolded - empty where it
    // folded. The standing map goes in whole: a re-fit leaves its own placements and rules in it,
    // and a frame that re-fits nothing leaves both alone, since the placements it did not touch are
    // still described by the rules already recorded for them.
    //
    // A fold that throws is caught here rather than by the frame's boundary, because only here is
    // the batch it dropped in hand. Its batch was drained and the standing owners dropped before it
    // threw, and no revision moves for either, so the debt the caller records is what makes the
    // next frame rebuild rather than keep the old owner. The marks are not kept for a second fold:
    // a fold that threw over a batch would throw over it again, and the full rebuild reads every
    // system for itself.
    private Optional<String> foldMarkedSystems(
            StaleHalves staleHalves,
            StandingOwnerMap standingMap,
            Set<SystemKey> staleSystemKeys) {

        try {

            var outcome = refreshStrategy.refreshMarkedSystems(
                SectorWalk.openOver(
                    machinery.resolveSector(),
                    staleHalves.cellCut().visibilityRules()),
                standingMap,
                staleSystemKeys);

            return outcome == MarkedSystemsOutcome.FOLDED
                ? Optional.empty()
                : Optional.of("strategy " + refreshStrategy);

        } catch (RuntimeException foldFailure) {

            foldFaultWarning.warnOnce(
                "Owner map fold failed; dropped " + staleSystemKeys.size()
                    + " marked system(s), next frame owes the full rebuild",
                foldFailure);

            return Optional.of("fold threw");
        }
    }

    // The face this sector's labels are fitted in, off this cache's own machinery rather than the
    // running sector's: the labels name this sector's owners. Settled once for the sector and asked
    // again by each fit, which costs a lookup; the fit records it for the mint and the bake after it.
    private FontAtlas settleLabelFace() {
        return LabelFonts.settleMapLabelFace(SettledFaces.resolveFacesIn(machinery));
    }
}
