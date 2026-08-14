package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;

import kmlib.profiling.Timings;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellSeedInputs;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.render.clusters.debug.ClusterBorderStageOverlay;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapDevToggles;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.render.debug.DebugBorderTracingBuilder;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterLabelStylingSnapshot;
import kmu.maplayers.politicalmap.base.render.ribbon.CellRibbonsBaker;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.TerritoryBuilder;
import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuPoliticalMapSettings;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Keeps the political map's derived draw lists fresh with the least work per frame, and hands
 * the current ones to the renderer. The terrain plugin holds one of these behind a transient
 * field and asks it to {@link #refresh} each frame the map is open; everything the map draws is
 * cached here and rebuilt only when its inputs change.
 *
 * <p>System positions in hyperspace are fixed for the life of a save, so the raw cells are built
 * once and cached; they are reseeded only when the reachable-system set changes, the
 * frontier-resolution or cell-reach setting changes (either seeds every cell), or a dev reveal
 * override flips (each changes which systems seed a cell). The territories are rebuilt in full
 * only when KMU's LunaLib settings change (detected off LunaLib's change event via
 * {@link KmuLunaSettings#getSettingsRevision()}) or when the geometry itself was rebuilt; between
 * those, a colony resize marks just its own system stale and drives an incremental re-shape. So
 * switching a colour or dragging an opacity slider takes effect live, and the per-frame path is
 * otherwise a couple of int compares, never a per-frame economy scan.
 *
 * <p>Everything held here is derived from one sector, and nothing here enters a save: the holder is
 * reached through a registered map layer and lives for the session, so no field needs transient
 * marking. That lifetime is longer than a sector's, though - a player can load a second save without
 * restarting - so a cache is discarded and rebuilt whole per load rather than reconciled. Every
 * revision starts at its rebuild-forcing seed, so the first frame against a new sector rebuilds both
 * halves from scratch.
 */
final class PoliticalMapCache {
    private static final Logger LOG = Global.getLogger(PoliticalMapCache.class);

    // The content revision's seed, below any revision the settings fold can report on a built
    // map, so the first refresh of a session finds the draw lists stale and builds them.
    private static final int UNBUILT_REVISION = -1;

    // The cut number the first cut of a session takes. Counts up from here and never repeats
    // within a cache, so no two cuts of these cells can be mistaken for each other.
    private static final int FIRST_CUT_NUMBER = 0;

    // Raw cell geometry keyed by system id, updated incrementally as systems gain or lose
    // access, paired with the number of the cut it currently holds. The cells themselves are the
    // same object for the life of the cache - it is recreated per session, so they are never
    // null once the cache exists - and only the number beside them moves, which is why the pair
    // is replaced rather than the cells.
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
    // Null until the first build this session; exactly one of this and borderStageOverlay is
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
    // record can hold is one the player can actually be under - and it is what makes the first
    // refresh of a session cut, whatever the signal happens to say.
    private CellCutInputs lastCellCut;

    // One-shot guard for rebuild faults: refresh runs every frame the map is open, so a recurring
    // rebuild failure would flood the log. The first is recorded at ERROR, the rest silenced.
    private boolean hasLoggedRebuildError;

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
     * Empties every cached half and returns each revision to its rebuild-forcing seed, so the next
     * {@link #refresh} builds the whole map from scratch rather than diffing against draw lists that
     * describe a different sector. Call once per game load.
     *
     * <p>The labels' GL buffers are released as part of it: they are freed at the moment the lists
     * are dropped rather than left to LazyLib's finalizer sweep.
     */
    public void discardCachedState() {
        LabelsBuilder.disposeAll(factionLabels);
        factionLabels.clear();
        // The placements go with the record of what they were fitted under: a statement of the
        // rules the previous sector's labels were made under must not outlive the labels.
        standingAnchors.discardAnchors();
        territories = null;
        borderStageOverlay = null;
        // Emptying the cells is itself a cut, so it takes its own number rather than rewinding to
        // a seed: a placement fitted against the previous sector's cells must not be able to
        // match the number these emptied ones now answer for.
        cellGeometry.cells().clearCachedCells();
        cellGeometry = cellGeometry.copyWithRevision(cellGeometry.revision() + 1);
        lastContentRevision = UNBUILT_REVISION;
        // Forgotten rather than zeroed, since every reading the record can hold is one the player
        // can be under - this is what forces the next refresh to cut against the new sector.
        lastCellCut = null;
        // Per-system staleness names systems of the sector being left, so it is dropped rather than
        // replayed against the next one - the rebuild this discard forces re-derives every system.
        MapLayerRefresh.drainStaleGroupingSystemIds();
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

    // Rebuilds only the stale half of the cache, advancing each cached revision only after its
    // rebuild completes so a thrown rebuild is retried next frame. Builds under the active view's
    // rules, so a view switch (folded into the content revision) rebuilds the territories under
    // the newly-selected view.
    private void rebuildStaleHalves(PoliticalMapView view) {

        var rebuiltCells = false;

        // What the cells would be cut from right now. The reachable-set revision alone does not
        // answer that: the frontier resolution, the cell reach and the two dev reveal toggles
        // each reseed every cell without it moving, so all four are read into one value and the
        // staleness question is asked of that value rather than of the signal.
        var cellCut = new CellCutInputs(
            MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.GEOMETRY),
            new CellSeedInputs(
                KmuPoliticalMapSettings.getPoliticalMapCellBoundSegments(),
                KmuPoliticalMapSettings.getPoliticalMapCellRadius()),
            PoliticalMapDevToggles.readFromLunaSettings());

        if (!cellCut.equals(lastCellCut)) {

            // Transition trace: a stale cell or one left behind after an access change can be tied
            // to the revision step - or the seed inputs or toggle flip - that drove it.
            LOG.debug("Political map geometry stale; rebuilding cut " + cellGeometry.revision()
                + " from " + lastCellCut + " to " + cellCut);

            rebuildGeometry(cellCut.seedInputs(), cellCut.devToggles());

            // A fresh cut number the moment the cells are recut, whichever of the four inputs
            // drove it - so work derived from the previous cut can never read as derived from
            // this one. Counted rather than taken from the reachable-set revision, which stands
            // still through a seed-input or dev-toggle recut.
            cellGeometry = cellGeometry.copyWithRevision(cellGeometry.revision() + 1);
            lastCellCut = cellCut;
            rebuiltCells = true;
        }

        // TODO: when only geometry changed (rebuiltCells), reshape just the cells
        // CellGeometryCache rebuilt - the system that gained or lost access and every cell
        // it touches - rather than the full territories rebuild below. Have updateFromSector report
        // its affected-cell set and drive a targeted reshape from it, the geometry-side analogue of
        // applyStalePoliticsUpdates.

        // Both views null means neither has been built this session, so the first frame forces the
        // build even if the content revision happens to match its unbuilt seed.
        var contentRevision = computeContentRevision(view);
        if (rebuiltCells
                || (territories == null && borderStageOverlay == null)
                || contentRevision != lastContentRevision) {
            var drawablesStart = System.nanoTime();

            // Build one view or the other, never both: the debug overlay replaces the normal
            // render, so in debug mode the production draw lists are not built at all, and the
            // unused view is nulled. The toggle is a KMU setting, so flipping it bumps the content
            // revision and forces this rebuild - which is what swaps the two. The anchor overlay
            // rebuilds either way - it draws over both views - borrowing the normal build's holder
            // map when there is one, resolving its own from the sector when the debug build left
            // none behind.
            if (KmuPoliticalMapSettings.shouldTraceBordersForDebug()) {
                borderStageOverlay = DebugBorderTracingBuilder.buildDebugDrawables(
                    cellGeometry.cells(),
                    Global.getSector());
                territories = null;
                ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(
                    standingAnchors,
                    cellGeometry,
                    Global.getSector(),
                    view);
            } else {
                territories = TerritoryBuilder.buildTerritories(
                    cellGeometry.cells(),
                    Global.getSector(),
                    view);
                borderStageOverlay = null;
                // The cells go over carrying the revision they stand at rather than the live
                // signal read at the top - the two agree here, and only the pair is true of the
                // geometry in hand. The standing placements go in whole beside it, which is what
                // lets the rebuild keep the ones whose clusters this pass has not moved.
                ClusterAnchorsBuilder.rebuildClusterAnchors(
                    standingAnchors,
                    cellGeometry,
                    Global.getSector(),
                    ClusterLabelStylingSnapshot.resolveFrom(territories));

                // The bands come last, after the names have places, because they are laid around
                // them: a band is cut by the room the names take, so baking one before the fit
                // would leave it running under a word rather than clear of it.
                CellRibbonsBaker.bakeAllCellRibbons(
                    territories,
                    cellGeometry.cells(),
                    Global.getSector(),
                    standingAnchors.getAnchors());
            }

            // The name labels are minted from the placements just rebuilt (empty when the names
            // toggle is off), keeping them in step with the fills and borders and reusing the one
            // placement search both consumers share. The name choice is read here rather than
            // inside the build, since whether names draw at all is this layer's own answer.
            LabelsBuilder.rebuildLabels(
                factionLabels,
                standingAnchors.getAnchors(),
                NameFormatPreference.getSelectedNameFormat().areNamesDrawn());
                    
            lastContentRevision = contentRevision;

            // A full rebuild re-derives every system, so any pending per-system staleness is
            // already reflected - drain and discard it rather than re-processing the same systems
            // immediately after.
            MapLayerRefresh.drainStaleGroupingSystemIds();
            logContentRebuild(rebuiltCells, contentRevision, drawablesStart);
            return;
        }

        // No full rebuild this frame. In the normal view, fold in any per-system holder changes
        // a colony resize marked, re-shaping only those systems and their neighbours over the
        // standing territories. The static debug overlay has no draw lists to patch, so its
        // staleness is drained instead - it refreshes on the next full rebuild (any settings or
        // geometry change). Under a filter the incremental re-shape is bypassed too: it re-derives
        // holders through the normal (non-filter) politics, which would overwrite the spotlit keys
        // and corrupt the spotlight, so a filtered map defers holder changes to the next full
        // rebuild instead.
        if (territories != null && !territories.isFiltering()) {
            // The standing pair goes in whole: a re-fit leaves its own placements and rules in
            // it, and a frame that re-fits nothing leaves both alone, since the placements it
            // did not touch are still described by the rules already recorded for them.
            IncrementalPoliticsRefresh.applyStalePoliticsUpdates(
                territories,
                standingAnchors,
                factionLabels,
                cellGeometry);
        } else {
            MapLayerRefresh.drainStaleGroupingSystemIds();
        }
    }

    // Traces the content rebuild's result: the counts the render will paint and the whole-rebuild
    // time, so a wrong or empty render can be confirmed against what was built. The count reported
    // is whichever view was built this rebuild - the normal styled cells or the debug overlay's
    // base loops.
    private void logContentRebuild(
            boolean rebuiltCells,
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
            + " geometryRebuilt=" + rebuiltCells
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
    // a change to any live input the active view samples - the alliances view's alliance set -
    // rebuilds the territories even though no setting moved. The faction view samples nothing live
    // and contributes a constant, so an alliance change never churns it; the pipeline stays
    // view-neutral by reading this off the view rather than naming the alliance signal itself. The
    // filter, recede-style, and map-style revisions are folded in at this pipeline level rather than
    // through any view, since each is a mode or an appearance toggle either view can be under: a
    // filter pick or clear, a recede Mute/Desaturate flip, or a flip of the sidebar's shared
    // appearance toggles (the uninhabited outline, the full/short name format) bumps its revision and
    // rebuilds the territories under whichever view is up, with no view naming it. Those toggles are
    // sector-memory state rather than LunaLib fields, so settingsRevision above does not cover them.
    // Objects.hash is the JDK's standard 31-multiply fold, so the inputs separate without a bespoke
    // combine here.
    private static int computeContentRevision(PoliticalMapView view) {
        return Objects.hash(
            KmuLunaSettings.getSettingsRevision(),
            view.getId(),
            view.getContentRevision(),
            MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.FILTER),
            MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE),
            MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.MAP_STYLE));
    }

    // Brings the geometry cache in line with the reachable systems, rebuilding only the cells
    // affected by an access change or a system starting or stopping moving - or every cell, when
    // the frontier resolution or the cell radius changed, since either reseeds them all. Feeds the
    // cache the currently-moving systems so they are left out of the partition (they seed no cell
    // and clip no neighbour), and the dev reveal toggles so a forced or undiscovered-colony
    // system joins the drawn set. The toggles cross into the framework as the visibility overrides
    // they amount to, so the geometry cache never learns which dev toggle wanted them.
    private void rebuildGeometry(
            CellSeedInputs seedInputs,
            PoliticalMapDevToggles devToggles) {

        var movingSystemIds = MovingSystems.getInstance().getMovingSystemIds();
        KmuProfiling
            .getProfiler()
            .measure(
                "politicalMap.updateGeometry",
                () -> cellGeometry.cells().updateFromSector(
                    Global.getSector(),
                    movingSystemIds,
                    seedInputs,
                    devToggles.convertToVisibilityOverrides()));
    }
}
