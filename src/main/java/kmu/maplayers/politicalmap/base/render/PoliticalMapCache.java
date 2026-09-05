package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.profiling.ActiveProfiler;
import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.systems.SystemColoniesIndex;
import kmlib.time.Timings;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellSeedInputs;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.render.debug.DebugBorderTracingBuilder;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterLabelStylingSnapshot;
import kmu.maplayers.politicalmap.base.render.ribbon.CellRibbonsBaker;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.TerritoryBuilder;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuPoliticalMapDiagnosticsSettings;
import kmu.settings.KmuPoliticalMapGeometrySettings;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Keeps the political map's derived draw lists fresh with the least work per frame, and hands
 * the current ones to the renderer. The layer renderer holds one of these and asks it to
 * {@link #refresh} each frame the map is open; everything the map draws is cached here and rebuilt
 * only when its inputs change.
 *
 * <p>System positions in hyperspace are fixed for the life of a save, so the raw cells are built
 * once and cached; they are reseeded only when the reachable-system set changes, the
 * frontier-resolution or cell-reach setting changes (either seeds every cell), or a visibility setting
 * override flips (each changes which systems seed a cell). The territories are rebuilt in full
 * only when KMU's LunaLib settings change (detected off LunaLib's change event via
 * {@link KmuLunaSettings#getSettingsRevision()}) or when the geometry itself was rebuilt; between
 * those, a colony resize marks just its own system stale and drives an incremental re-shape. So
 * switching a colour or dragging an opacity slider takes effect live, and the per-frame path is
 * otherwise a couple of int compares, never a per-frame economy scan.
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

    // The content revision's seed, below any revision the settings fold can report on a built
    // map, so this cache's first refresh finds the draw lists stale and builds them.
    private static final int UNBUILT_REVISION = -1;

    // The cut number this cache's first cut takes. Counts up from here and never repeats within a
    // cache, so no two cuts of these cells can be mistaken for each other.
    private static final int FIRST_CUT_NUMBER = 0;

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

    // The built draw lists plus the holding and style inputs an incremental re-shape needs.
    // Null until this cache's first build; exactly one of this and borderStageOverlay is
    // non-null after a build.
    private PoliticalMapTerritories territories;

    // The debug border-tracing overlay, built instead of the territories above while the "debug
    // border tracing" dev toggle is on. The toggle is a KMU setting, so flipping it bumps the
    // content revision and forces the rebuild that swaps which view is built.
    private ClusterBorderStageOverlay borderStageOverlay;

    // The cluster-label placements and what they were fitted under, held here rather than by
    // either view above so they draw over whichever is live - turning border tracing on must not
    // hide them. Empty unless the names or the anchor overlay is on; they feed both. The two are
    // one value because a placement is only reusable as a pair with the rules it was sized under,
    // and this cache is what owns them across the rebuilds that reuse them.
    private final StandingClusterAnchors standingAnchors = new StandingClusterAnchors();

    // The cached faction-name labels, built from the placements above. Each owns a GL buffer, so
    // the builder disposes the standing strings whenever it rebuilds this list. Empty unless the
    // "show faction names" toggle is on.
    private final List<Label> factionLabels = new ArrayList<>();

    // The revision the territories were built against. They rebuild on a content change
    // (settings) or whenever the geometry itself was rebuilt. This starts at the unbuilt seed and
    // the value fields at null, so the first refresh builds both halves.
    private int lastContentRevision = UNBUILT_REVISION;

    // What the cells currently held were cut from. One value rather than a revision, the seed
    // inputs and the dev toggles side by side, because each of the three recuts every cell and
    // the question asked of them is the single one they answer together: would the cells be cut
    // differently now. Null (not a zero reading) is the never-cut state, since every reading the
    // record can hold is one the player can actually be under - and it is what makes this cache's
    // first refresh cut, whatever the signal happens to say.
    private CellCutInputs lastCellCut;

    // One-shot guard for rebuild faults: refresh runs every frame the map is open, so a recurring
    // rebuild failure would flood the log. The first is recorded at ERROR, the rest silenced.
    private boolean hasLoggedRebuildError;

    PoliticalMapCache(MapLayerInstallation installation) {
        this.installation = installation;
    }

    /** @return the built production draw lists, or null while the debug overlay has replaced them */
    public PoliticalMapTerritories getTerritories() {
        return territories;
    }

    /** @return the built debug border-tracing overlay, or null in the normal (non-debug) view */
    public ClusterBorderStageOverlay getBorderStageOverlay() {
        return borderStageOverlay;
    }

    // Whether the debug border-tracing overlay is the built view this frame - the one branch the
    // renderer needs to pick which base view to paint.
    public boolean isDebug() {
        return borderStageOverlay != null;
    }

    /** @return the cluster-label placements, drawn over whichever base view is live */
    public List<ClusterAnchor> getClusterAnchors() {
        return standingAnchors.getAnchors();
    }

    /** @return the cached faction-name labels */
    public List<Label> getFactionLabels() {
        return factionLabels;
    }

    /**
     * Releases everything built for this cache's sector, when the machinery holding it goes.
     *
     * <p>The labels' GL buffers are released as part of it: they are freed at the moment the lists
     * are dropped rather than left to LazyLib's finalizer sweep. Nothing is rewound for a rebuild,
     * because nothing rebuilds through a released cache - the sector after this one is drawn by a
     * cache of its own, which begins at its seeds.
     */
    public void disposeCachedState() {

        LabelsBuilder.disposeAll(factionLabels);
        factionLabels.clear();

        // The placements go with the record of what they were fitted under: a statement of the
        // rules this sector's labels were made under must not outlive the labels.
        standingAnchors.discardAnchors();
        territories = null;
        borderStageOverlay = null;

        cellGeometry.cells().clearCachedCells();
    }

    /**
     * Brings the cached draw lists in line with the active view, rebuilding only the stale half.
     * Guarded because it runs every frame the map is open: a rebuild fault is recorded once (not
     * per frame), and the catch leaves the cached revisions un-advanced so the next frame retries
     * rather than the overlay going permanently stale, plus installs an empty placeholder so the
     * renderer never dereferences a null draw list.
     */
    public void refresh(PoliticalMapView view) {
        try {
            rebuildStaleHalves(view);
        } catch (RuntimeException exception) {
            if (!hasLoggedRebuildError) {
                hasLoggedRebuildError = true;
                LOG.error(
                    "Political map rebuild failed; retrying next frame, "
                        + "keeping last good draw lists",
                    exception);
            }
            ensureDrawablesNonNull(view);
        }
    }

    // Asks what this frame owes and then either patches the standing map or rebuilds it. The two
    // are separate steps with the gate between them, because the rebuild opens a reading of the
    // sector and nearly every frame must open none: the decision is answerable from revisions and
    // settings alone, so it runs first and most frames stop at it.
    private void rebuildStaleHalves(PoliticalMapView view) {

        var staleHalves = decideWhatIsStale(view);

        if (!staleHalves.isContentStale()) {
            applyStandingMapUpdates();
            return;
        }
        rebuildWhatIsStale(staleHalves, view);
    }

    // What this frame owes, decided off revisions and settings alone so it costs nothing on a frame
    // that owes nothing - which is nearly all of them.
    private StaleHalves decideWhatIsStale(PoliticalMapView view) {

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

        // TODO: when only geometry changed (isCellCutStale), reshape just the cells
        // CellGeometryCache rebuilt - the system that gained or lost access and every cell
        // it touches - rather than the full territories rebuild below. Have updateFromSector report
        // its affected-cell set and drive a targeted reshape from it, the geometry-side analogue of
        // applyStalePoliticsUpdates.

        // Both views null means neither has been built for this sector, so the first frame forces
        // the build even if the content revision happens to match its unbuilt seed.
        var contentRevision = computeContentRevision(view);
        var isContentStale = isCellCutStale
            || (territories == null && borderStageOverlay == null)
            || contentRevision != lastContentRevision;

        return new StaleHalves(cellCut, isCellCutStale, contentRevision, isContentStale);
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
        var drawablesStart = System.nanoTime();

        // Build one view or the other, never both: the debug overlay replaces the normal
        // render, so in debug mode the production draw lists are not built at all, and the
        // unused view is nulled. The toggle is a KMU setting, so flipping it bumps the content
        // revision and forces this rebuild - which is what swaps the two. The anchor overlay
        // rebuilds either way - it draws over both views - borrowing the normal build's holder
        // map when there is one, resolving its own from the sector when the debug build left
        // none behind.
        if (KmuPoliticalMapDiagnosticsSettings.shouldTraceBordersForDebug()) {
            rebuildBorderTracingOverlay(sector, view);

        } else {

            rebuildTerritoriesAndBands(
                new HolderPass(
                    view.resolveGrouping(),
                    staleHalves.cellCut().visibilityRules().colonyVisibility(),
                    colonies),
                view);
        }

        // The name labels are minted from the placements just rebuilt (empty when the names
        // toggle is off), keeping them in step with the fills and borders and reusing the one
        // placement search both consumers share. The name choice is read here rather than
        // inside the build, since whether names draw at all is this layer's own answer.
        LabelsBuilder.rebuildLabels(
            factionLabels,
            standingAnchors.getAnchors(),
            NameFormatPreference.getSelectedNameFormat().areNamesDrawn());

        lastContentRevision = staleHalves.contentRevision();

        // A full rebuild re-derives every system, so any pending per-system staleness is
        // already reflected - drain and discard it rather than re-processing the same systems
        // immediately after.
        installation.resolveRefreshBoard().drainStaleGroupingSystemIds();

        logContentRebuild(
            staleHalves.isCellCutStale(),
            staleHalves.contentRevision(),
            drawablesStart);
    }

    // The debug border-tracing view, which replaces the production draw lists outright. It reads
    // holding from the sector rather than through the rebuild's own reading, and deliberately: the
    // overlay is gated behind a dev toggle and builds no draw lists for the anchors to borrow a
    // holder map from, so what it costs is paid only while somebody is looking at it.
    private void rebuildBorderTracingOverlay(SectorAPI sector, PoliticalMapView view) {

        borderStageOverlay = DebugBorderTracingBuilder.buildDebugDrawables(
            cellGeometry.cells(),
            sector);

        territories = null;

        ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(
            standingAnchors,
            cellGeometry,
            sector,
            view);
    }

    // The production view's three stages, driven off the one reading of the sector the rebuild
    // opened: the fills, the names fitted inside the borders they trace, and the bands laid
    // around wherever those names ended up.
    //
    // The grouping the pass was opened under is the view's, sampled once, which is the condition
    // the bake shares this reading on: a pass folded by another grouping would plan bands against
    // blocs the fills never drew.
    private void rebuildTerritoriesAndBands(HolderPass pass, PoliticalMapView view) {

        territories = TerritoryBuilder.buildTerritories(cellGeometry.cells(), pass, view);
        borderStageOverlay = null;

        // The cells go over carrying the revision they stand at rather than the live
        // signal read at the top - the two agree here, and only the pair is true of the
        // geometry in hand. The standing placements go in whole beside it, which is what
        // lets the rebuild keep the ones whose clusters this pass has not moved.
        ClusterAnchorsBuilder.rebuildClusterAnchors(
            standingAnchors,
            cellGeometry,
            pass.sector(),
            ClusterLabelStylingSnapshot.resolveFrom(territories));

        // The bands come last, after the names have places, because they are laid around
        // them: a band is cut by the room the names take, so baking one before the fit
        // would leave it running under a word rather than clear of it. What the fit
        // reports about the names it moved is dropped here: every cell was just rebuilt
        // from nothing, so all of them owe a band whatever the names did.
        //
        // Baked through this rebuild's own reading rather than one of its own: the bake runs in
        // the same frame the fills were painted in, so a fresh reading could only report the
        // same sector at the cost of walking it again.
        CellRibbonsBaker
            .createForPass(
                territories,
                cellGeometry.cells(),
                pass,
                standingAnchors.getAnchors())
            .bakeAllCellRibbons();
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

        if (staleSystemIds.isEmpty() || territories == null || territories.isFiltering()) {
            return;
        }
        // The four halves go over as one value, and the standing pair goes in whole: a re-fit
        // leaves its own placements and rules in it, and a frame that re-fits nothing leaves both
        // alone, since the placements it did not touch are still described by the rules already
        // recorded for them.
        IncrementalPoliticsRefresh.applyStalePoliticsUpdates(
            installation.resolveSector(),
            new StandingPoliticalMap(territories, standingAnchors, factionLabels, cellGeometry),
            staleSystemIds);
    }

    // Traces the content rebuild's result: the counts the render will paint and the whole-rebuild
    // time, so a wrong or empty render can be confirmed against what was built. The count reported
    // is whichever view was built this rebuild - the normal styled cells or the debug overlay's
    // base loops.
    private void logContentRebuild(
            boolean isCellCutStale,
            int contentRevision,
            long drawablesStart) {

        if (!LOG.isDebugEnabled()) {
            return;
        }
        var builtCounts = borderStageOverlay != null
            ? "debugBaseLoops=" + borderStageOverlay.baseLoops().size()
            : "styledCells=" + territories.getStyledCellByCellId().size();

        LOG.debug("Political map territories rebuilt; contentRevision="
            + contentRevision + " " + builtCounts
            + " geometryRebuilt=" + isCellCutStale
            + " took=" + Timings.formatMillis(System.nanoTime() - drawablesStart));
    }

    // Guards the render path after a failed first build: a rebuild that threw before completing can
    // leave the draw lists null, which the renderer would dereference. An empty placeholder makes
    // the render a harmless no-op until a later frame's retry succeeds.
    private void ensureDrawablesNonNull(PoliticalMapView view) {
        if (territories == null) {
            territories = PoliticalMapTerritories.createEmpty(view);
        }
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
    // constant and is never churned by a change it does not render. The
    // filter, recede-style, and map-style revisions are folded in at this pipeline level rather than
    // through any view, since each is a mode or an appearance toggle either view can be under: a
    // filter pick or clear, a recede Mute/Desaturate flip, or a flip of the sidebar's shared
    // appearance toggles (the uninhabited outline, the full/short name format) bumps its revision and
    // rebuilds the territories under whichever view is up, with no view naming it. Those toggles are
    // sector-memory state rather than LunaLib fields, so settingsRevision above does not cover them.
    // Objects.hash is the JDK's standard 31-multiply fold, so the inputs separate without a bespoke
    // combine here. The three counters come off this cache's own board, and the view is handed that
    // same board to fold its own live inputs from, so a signal raised in another sector cannot
    // restyle these cells - and a signal raised in this one cannot fail to.
    private int computeContentRevision(PoliticalMapView view) {

        var board = installation.resolveRefreshBoard();

        return Objects.hash(
            KmuLunaSettings.getSettingsRevision(),
            view.getId(),
            view.getContentRevision(board),
            board.getRevision(MapLayerCommonRefreshSignal.FILTER),
            board.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE),
            board.getRevision(MapLayerCommonRefreshSignal.MAP_STYLE));
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

        var movingSystemIds = installation.resolveMovingSystems().getMovingSystemIds();
        ActiveProfiler
            .resolveProfiler()
            .measure(
                "politicalMap.updateGeometry",
                () -> cellGeometry.cells().updateFromSector(
                    pass,
                    movingSystemIds,
                    cellCut.seedInputs()));

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
     * @param contentRevision the fold of every input the territories are styled under
     * @param isContentStale  whether anything at all is owed, the cut included - false is the frame
     *                        that stops at the decision
     */
    private record StaleHalves(
        CellCutInputs cellCut,
        boolean isCellCutStale,
        int contentRevision,
        boolean isContentStale) {
    }
}
