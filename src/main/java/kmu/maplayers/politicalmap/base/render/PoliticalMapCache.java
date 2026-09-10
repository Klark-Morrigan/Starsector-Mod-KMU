package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.logging.SessionWarning;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileSection;
import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellSeedInputs;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.profiling.RebuildStepTerms;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshSignal;
import kmu.maplayers.base.refresh.RefreshSignalRevisions;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.ResolvedHolding;
import kmu.maplayers.politicalmap.base.render.territories.TerritoryBuilder;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuPoliticalMapDiagnosticsSettings;
import kmu.settings.KmuPoliticalMapGeometrySettings;

import org.apache.log4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.Set;

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
 * <p>One cache serves both screens, and the frame says which it is drawing for. Since what the
 * revision folds is the sampled preference <em>values</em>, two screens set alike are one bake and a
 * switch between them rebuilds nothing; two set differently rebuild at the switch, which is the same
 * rebuild changing that pick on one screen already costs, paid at a different moment.
 *
 * <p>Everything held here is derived from one sector, and nothing here enters a save: the holder
 * belongs to that sector's installed map machinery and goes with it, so no field needs transient
 * marking and nothing needs emptying when a sector changes. Every revision starts at its
 * rebuild-forcing seed, so the first frame after a sector is installed on builds both halves from
 * scratch.
 *
 * <p><b>One cache serves exactly one sector.</b> {@link CellGeometryCache} reconciles by system
 * <em>id</em>, so two sectors through one cache would not overwrite each other's cells but keep
 * them, cut around positions the second sector's systems never sat at. Which is why the sector a
 * rebuild reads comes off the installation this cache was made for rather than off the running
 * game: asking the running game is how a cache comes to be handed a second sector at all.
 */
final class PoliticalMapCache {
    private static final Logger LOG = Global.getLogger(PoliticalMapCache.class);

    // Everything a rebuild derives from the cells. Writes its line on every call, a rebuild being
    // an event rather than a per-frame cost: the line is what says a rebuild happened at all, and
    // what it left for the render to paint.
    private static final ProfileSection REBUILD_DRAWABLES_SECTION = ProfileSection.registerSection(
        "politicalMap.rebuildDrawables", RebuildStepTerms.LOGGED_EVERY_CALL);

    // The content revision's seed, below any revision the settings fold can report on a built
    // map, so this cache's first refresh finds the draw lists stale and builds them.
    private static final int UNBUILT_REVISION = -1;

    // The cut number this cache's first cut takes. Counts up from here and never repeats within a
    // cache, so no two cuts of these cells can be mistaken for each other.
    private static final int FIRST_CUT_NUMBER = 0;

    // The sidebar preferences whose raises are traced onto a rebuild's tag. These three and no
    // others, because these are the ones nothing folds into staleness any more: a signal a
    // consumer does read is already accounted for by the rebuild it caused.
    private static final MapLayerRefreshSignal[] TRACED_PREFERENCE_SIGNALS = {
        MapLayerCommonRefreshSignal.FILTER,
        MapLayerCommonRefreshSignal.RECEDE_STYLE,
        MapLayerCommonRefreshSignal.MAP_STYLE};

    // The machinery installed on the sector this cache draws. The sector a rebuild cuts cells from,
    // the movers that cut leaves out and the board it reads staleness off all come off this one
    // handle, so none of the three can name a different sector - a cut taken from the running game
    // while the drift is this sector's would leave out systems that never moved.
    private final MapLayerInstallation installation;

    // Raw cell geometry keyed by system id, updated incrementally as systems gain or lose
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

    // Everything built to draw, and how each part of it is rebuilt. Held apart from the
    // revisions and the staleness decision here, which answer when a rebuild is owed and what
    // it carries forward rather than what it produces.
    private final PoliticalMapDrawables drawables = new PoliticalMapDrawables();

    // The revision the territories were built against. They rebuild on a content change
    // (settings) or whenever the geometry itself was rebuilt. This starts at the unbuilt seed and
    // the value fields at null, so the first refresh builds both halves.
    private int lastContentRevision = UNBUILT_REVISION;

    // The holding the last rebuild resolved and the revision it resolved it under, kept so a
    // rebuild that owes no new reading of the sector - a style pick moved and nothing else - can
    // paint over what the last one read rather than walk the economy for the same answer. The
    // revision is the content revision's holding half: the inputs that reach the resolve, and none
    // of the picks that only reach the paint. Dropped, not just superseded, whenever the sector
    // moves under it, which is what the marked-system drains below say.
    private int lastHoldingRevision = UNBUILT_REVISION;
    private ResolvedHolding standingHolding;

    // What the cells currently held were cut from. One value rather than a revision, the seed
    // inputs and the dev toggles side by side, because each of the three recuts every cell and
    // the question asked of them is the single one they answer together: would the cells be cut
    // differently now. Null (not a zero reading) is the never-cut state, since every reading the
    // record can hold is one the player can actually be under - and it is what makes this cache's
    // first refresh cut, whatever the signal happens to say.
    private CellCutInputs lastCellCut;

    // Said once per session: refresh runs every frame the map is open, so a recurring rebuild
    // failure would otherwise flood the log, and the second line says nothing the first did not.
    private final SessionWarning rebuildFaultWarning = new SessionWarning(LOG);

    // Where the traced preference signals stood when this cache last rebuilt, so a rebuild can name
    // which of them a player has touched since. Seeded at construction rather than left empty: the
    // board outlives no cache but may already carry raises from a load, and reporting those against
    // this cache's first rebuild would name flips that happened before it existed.
    private RefreshSignalRevisions signalsAtLastRebuild;

    PoliticalMapCache(MapLayerInstallation installation) {

        this.installation = installation;
        this.signalsAtLastRebuild = RefreshSignalRevisions.readRevisionsOf(
            installation.resolveRefreshBoard(),
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
     * per frame), and the catch leaves the cached revisions un-advanced so the next frame retries
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

        var staleHalves = decideWhatIsStale(view, memoryScope);

        if (!staleHalves.isContentStale()) {
            applyStandingMapUpdates();
            return;
        }
        rebuildWhatIsStale(staleHalves, view);
    }

    // What this frame owes, decided off revisions and settings alone so it costs nothing on a frame
    // that owes nothing - which is nearly all of them.
    private StaleHalves decideWhatIsStale(PoliticalMapView view, ScreenMemoryScope memoryScope) {

        // What the cells would be cut from right now. The reachable-set revision alone does not
        // answer that: the frontier resolution, the cell reach and the two visibility overrides
        // each reseed every cell without it moving, so all four are read into one value and the
        // staleness question is asked of that value rather than of the signal.
        //
        // This is also the rebuild's one sampling of the visibility rules: the cut, the fills and
        // the bands all resolve under what it holds, so a gate flipped mid-rebuild cannot leave
        // cells cut under one rule and painted under another.
        var cellCut = new CellCutInputs(
            installation.resolveRefreshBoard().getRevision(MapLayerCommonRefreshSignal.GEOMETRY),
            new CellSeedInputs(
                KmuPoliticalMapGeometrySettings.getPoliticalMapCellBoundSegments(),
                KmuPoliticalMapGeometrySettings.getPoliticalMapCellRadius()),
            MapVisibilityRules.readFromLunaSettings());

        var isCellCutStale = !cellCut.equals(lastCellCut);

        // The rebuild's one sampling of the sidebar preferences, taken here beside the cut's for
        // the same reason: every stage that bakes one reads this reading, and the staleness
        // question below is asked of the values rather than of the counters their flips raise.
        //
        // Sampled under the screen the frame handed over, which is the screen it also took the view
        // off, so the picks a rebuild bakes and the view it builds under name one panel.
        var contentInputs = ContentInputs.sampleForView(view, memoryScope);

        // TODO: when only geometry changed (isCellCutStale), reshape just the cells
        // CellGeometryCache rebuilt - the system that gained or lost access and every cell
        // it touches - rather than the full territories rebuild below. Have updateFromSector report
        // its affected-cell set and drive a targeted reshape from it, the geometry-side analogue of
        // applyStalePoliticsUpdates.

        // Both views null means neither has been built for this sector, so the first frame forces
        // the build even if the content revision happens to match its unbuilt seed.
        var contentRevision = computeContentRevision(view, contentInputs);
        var isContentStale = isCellCutStale
            || drawables.hasNothingBuilt()
            || contentRevision != lastContentRevision;

        return new StaleHalves(
            cellCut,
            isCellCutStale,
            contentInputs,
            contentRevision,
            computeHoldingRevision(view, contentInputs),
            isContentStale);
    }

    // The rebuild itself, run only where the decision above found something stale. Advances each
    // cached revision only after its rebuild completes, so a thrown rebuild is retried next frame.
    // Builds under the active view's rules, so a view switch (folded into the content revision)
    // rebuilds the territories under the newly-selected view.
    //
    // A rebuild is a pass, and is read as one: whichever stages run, they run against one reading
    // of the sector, opened here and handed to each.
    private void rebuildWhatIsStale(StaleHalves staleHalves, PoliticalMapView view) {

        // The sector this rebuild draws, read once. Every stage below is answered from this one
        // reference, so a rebuild cannot name one sector to its cut and another to its fills.
        var sector = installation.resolveSector();

        // The one reading of that sector this rebuild's stages share: the cell cut, the fills and
        // the bands each ask every system who lives there, so one walk per system serves all
        // three. Opened here rather than by each stage, which is what let a single rebuild walk
        // the whole sector three times over.
        //
        // Discarded with the rebuild. A kept one would draw the next rebuild off the sector this
        // one saw, which is the change a rebuild exists to show.
        var colonies = new SystemColoniesIndex(sector);

        if (staleHalves.isCellCutStale()) {
            rebuildGeometry(staleHalves.cellCut(), sector, colonies);
        }
        // Everything derived from the cells under one scope, the cut having timed itself: what
        // the draw lists cost this rebuild is this span, and every stage it drives sits inside it.
        try (var drawablesScope = ActiveProfiler
                .resolveProfiler()
                .open(REBUILD_DRAWABLES_SECTION)) {

            var wasHoldingReused = rebuildDrawables(staleHalves, view, sector, colonies);

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
                + " signalsRaised=" + describeSignalsRaisedSinceTheLastRebuild());
        }
    }

    // Which of the traced signals moved since the rebuild before this one, and the reading advanced
    // to this rebuild. Advanced here rather than where the reading is taken, so a rebuild that
    // threw before reaching its tag leaves the raises for the retry to report rather than swallowing
    // them.
    private String describeSignalsRaisedSinceTheLastRebuild() {

        var raisedNow = RefreshSignalRevisions.readRevisionsOf(
            installation.resolveRefreshBoard(),
            TRACED_PREFERENCE_SIGNALS);

        var raisedSince = raisedNow.describeSignalsRaisedSince(signalsAtLastRebuild);

        signalsAtLastRebuild = raisedNow;

        return raisedSince;
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
            SystemColoniesIndex colonies) {

        // Drained before the holding is chosen rather than after the build, because whether any
        // system is marked is half of whether the standing holding may be kept: a marked system is
        // one the sector moved under, and a holding read before it moved no longer says who holds
        // it. A system marked after this drain sits on the board for the next frame's fold, which
        // is what the fold is for - where a drain after the build discarded it on the assumption
        // that the build had read everything, an assumption a reused holding does not meet.
        var staleSystemIds = installation.resolveRefreshBoard().drainStaleGroupingSystemIds();
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
                colonies);

            wasHoldingReused = canReuseStandingHolding(staleHalves, staleSystemIds);

            if (!wasHoldingReused) {
                standingHolding = TerritoryBuilder.resolveHolding(
                    pass,
                    view,
                    staleHalves.contentInputs());
                lastHoldingRevision = staleHalves.holdingRevision();
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

        lastContentRevision = staleHalves.contentRevision();

        return wasHoldingReused;
    }

    // Whether the holding the last rebuild read still says who holds what. It does unless the
    // sector moved under it - a marked system, or a recut, which admits or drops systems - or the
    // rule reading it did, which the holding revision folds. Never on a cache's first rebuild,
    // there being nothing standing to keep.
    private boolean canReuseStandingHolding(StaleHalves staleHalves, Set<String> staleSystemIds) {

        return standingHolding != null
            && staleSystemIds.isEmpty()
            && !staleHalves.isCellCutStale()
            && staleHalves.holdingRevision() == lastHoldingRevision;
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

        var staleSystemIds = installation.resolveRefreshBoard().drainStaleGroupingSystemIds();

        if (staleSystemIds.isEmpty()) {
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
            installation.resolveSector(),
            drawables.toStandingMap(cellGeometry),
            staleSystemIds);
    }

    // The territories-staleness token: a settings change restyles every cell over the fixed
    // geometry, so a settings-revision bump forces a full territories rebuild. Holder changes no
    // longer feed this - a resized colony, or a system the sector watcher's holder diff caught,
    // marks just its system stale now. The active view is folded in so switching views (their
    // grouping, styling, and labels differ) rebuilds the territories under the newly-selected view
    // rather than reusing the previous view's. The view's content fingerprint is folded in too, so
    // a change to any live input the active view samples - the alliance set every political view
    // reads, whether to fuse blocs or only to judge a contest - rebuilds the territories even though
    // no setting moved. The pipeline stays view-neutral by reading this off the view rather than
    // naming the alliance signal itself, so a view that samples nothing simply contributes a
    // constant and is never churned by a change it does not render.
    //
    // The sidebar preferences are folded in as the values this frame sampled, rather than as the
    // filter, recede-style and map-style counters their flips raise. Those toggles are
    // sector-memory state rather than LunaLib fields, so settingsRevision above does not cover
    // them - but a counter only reports that somebody clicked something, not what the map is now
    // under. Folding the values asks the only question worth asking: would this build come out the
    // same. Two readings holding the same picks are one bake whatever has been clicked between
    // them, and a pick that really moved rebuilds under whichever view is up with no view naming
    // it.
    //
    // Which leaves those three counters deciding nothing, and they go on being raised anyway: a
    // raise writes a line naming the signal as the board takes it, and the rebuild below reports
    // which of them moved since the last one. So they are the record of what the player touched,
    // read beside a rebuild rather than causing it - which is worth more than the flip they no
    // longer trigger.
    //
    // Objects.hash is the JDK's standard 31-multiply fold, so the inputs separate without a bespoke
    // combine here. The view is handed this cache's own board to fold its own live inputs from, so
    // a signal raised in another sector cannot restyle these cells - and a signal raised in this
    // one cannot fail to.
    private int computeContentRevision(PoliticalMapView view, ContentInputs contentInputs) {

        var board = installation.resolveRefreshBoard();

        return Objects.hash(
            KmuLunaSettings.getSettingsRevision(),
            view.getId(),
            view.getContentRevision(board),
            contentInputs);
    }

    // The content revision's holding half: the same fold over only what reaches the resolve. Of the
    // sidebar picks that is the spotlight alone - the two recedes, the name format and the outline
    // reach the paint and nothing before it. The settings revision stays in, since the rule a view
    // resolves holding by can read LunaLib fields, and the view's own live inputs stay in since the
    // alliance set is exactly what moves an alliances view's holding. A style pick moves the content
    // revision and leaves this one where it was, which is what lets the rebuild it owes keep the
    // holding.
    private int computeHoldingRevision(PoliticalMapView view, ContentInputs contentInputs) {

        var board = installation.resolveRefreshBoard();

        return Objects.hash(
            KmuLunaSettings.getSettingsRevision(),
            view.getId(),
            view.getContentRevision(board),
            contentInputs.selectedBlocId());
    }

    // Brings the geometry cache in line with the reachable systems, rebuilding only the cells
    // affected by an access change or a system starting or stopping moving - or every cell, when
    // the frontier resolution or the cell radius changed, since either reseeds them all. Feeds the
    // cache the currently-moving systems so they are left out of the partition (they seed no cell
    // and clip no neighbour), and the rebuild's reading of the sector, whose visibility rules put
    // a forced or undiscovered-colony system on the drawn set.
    private void rebuildGeometry(
            CellCutInputs cellCut,
            SectorAPI sector,
            SystemColoniesIndex colonies) {

        // Transition trace: a stale cell or one left behind after an access change can be tied
        // to the revision step - or the seed inputs or toggle flip - that drove it.
        LOG.debug("Political map geometry stale; rebuilding cut " + cellGeometry.revision()
            + " from " + lastCellCut + " to " + cellCut);

        var pass = new MapVisibilityPass(
            colonies,
            VisibleStars.scan(sector),
            cellCut.visibilityRules());

        // Not wrapped in a section of its own: the cut opens one, so a scope here would be a
        // second row over the same span, differing only in which of the two names it carried.
        cellGeometry.cells().updateFromSector(
            pass,
            installation.resolveMovingSystems().getMovingSystemIds(),
            cellCut.seedInputs());

        // A fresh cut number the moment the cells are recut, whichever of the four inputs
        // drove it - so work derived from the previous cut can never read as derived from
        // this one. Counted rather than taken from the reachable-set revision, which stands
        // still through a seed-input or dev-toggle recut.
        cellGeometry = cellGeometry.copyWithRevision(cellGeometry.revision() + 1);
        lastCellCut = cellCut;
    }

    /**
     * What one frame's staleness question answered: which of the cache's two halves are stale, and
     * the two values a rebuild would carry forward - the inputs its cells would be cut under, and
     * the content revision it would advance to.
     *
     * <p>A value crossing between two steps rather than four more fields on the cache, because the
     * question is asked before a reading of the sector is opened and answered after. Nearly every
     * frame answers "nothing stale" and must open no reading at all, so the two cannot be one step -
     * and what the decision found has to reach the rebuild without being re-derived, or the rebuild
     * would sample the settings a second time and could cut under one reading while the decision
     * was taken under another.
     *
     * @param cellCut         what the cells would be cut from now: the reachable-set revision, the
     *                        seed knobs and the visibility rules, sampled once
     * @param isCellCutStale  whether those differ from what the standing cells were cut from
     * @param contentInputs   the sidebar preferences a rebuild would bake under, sampled once
     * @param contentRevision the fold of every input the territories are styled under
     * @param holdingRevision the fold of only those inputs that reach the resolve of who holds
     *                        what, so a rebuild can tell a pick that moved the paint from one that
     *                        moved the holding
     * @param isContentStale  whether anything at all is owed, the cut included - false is the frame
     *                        that stops at the decision
     */
    private record StaleHalves(
        CellCutInputs cellCut,
        boolean isCellCutStale,
        ContentInputs contentInputs,
        int contentRevision,
        int holdingRevision,
        boolean isContentStale) {
    }
}
