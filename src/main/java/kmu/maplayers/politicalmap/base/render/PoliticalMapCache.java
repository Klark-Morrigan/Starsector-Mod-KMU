package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;

import kmlib.profiling.Timings;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.politicalmap.base.PoliticalMapDevOverrides;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.geometry.PoliticalMapGeometryCache;
import kmu.maplayers.politicalmap.base.refresh.MovingSystems;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefresh;
import kmu.maplayers.politicalmap.base.render.debug.DebugBorderTracingBuilder;
import kmu.maplayers.politicalmap.base.render.debug.PoliticalMapDebugTerritories;
import kmu.maplayers.politicalmap.base.render.labels.Label;
import kmu.maplayers.politicalmap.base.render.labels.LabelsBuilder;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchor;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.TerritoryBuilder;
import kmu.settings.KmuLunaSettings;

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
 * switching a color or dragging an opacity slider takes effect live, and the per-frame path is
 * otherwise a couple of int compares, never a per-frame economy scan.
 *
 * <p>Everything held here is derived from the sector and holds record types XStream cannot
 * serialise, so this whole object is transient (the plugin's reference to it is): a save-restored
 * plugin recreates it fresh, and every revision starts at its rebuild-forcing seed so the first
 * frame rebuilds both halves from scratch.
 */
final class PoliticalMapCache {
    private static final Logger LOG = Global.getLogger(PoliticalMapCache.class);

    // Raw cell geometry keyed by system id, updated incrementally as systems gain or lose
    // access. Final because this whole cache is recreated per session, so it is never null once
    // the cache exists - no lazy re-init as the old save-restored plugin needed.
    private final PoliticalMapGeometryCache geometryCache = new PoliticalMapGeometryCache();

    // The built draw lists plus the ownership and style inputs an incremental re-shape needs.
    // Null until the first build this session; exactly one of this and debugTerritories is
    // non-null after a build.
    private PoliticalMapTerritories territories;

    // The debug border-tracing overlay, built instead of the territories above while the "debug
    // border tracing" dev toggle is on. The toggle is a KMU setting, so flipping it bumps the
    // content revision and forces the rebuild that swaps which view is built.
    private PoliticalMapDebugTerritories debugTerritories;

    // The cluster-label placements, held here rather than by either view above so they draw over
    // whichever is live - turning border tracing on must not hide them. Empty unless the names or
    // the anchor overlay is on; they feed both.
    private final List<ClusterAnchor> clusterAnchors = new ArrayList<>();

    // The cached faction-name labels, built from the placements above. Each owns a GL buffer, so
    // the builder disposes the standing strings whenever it rebuilds this list. Empty unless the
    // "show faction names" toggle is on.
    private final List<Label> factionLabels = new ArrayList<>();

    // The revisions each half of the cache was built against. Geometry rebuilds when the
    // reachable-system set changes or the frontier resolution/cell reach changes (either reseeds
    // every cell); the territories rebuild on a content change (settings) or whenever the geometry
    // itself was rebuilt. Start at -1 (NaN for the radius) so the first refresh builds both.
    private int lastGeometryRevision = -1;
    private int lastContentRevision = -1;
    private int lastBoundSegments = -1;
    private double lastCellRadius = Double.NaN;
    // The dev reveal overrides the cached geometry was last seeded under. Like the frontier
    // resolution they change which systems seed a cell, so a flip reseeds the partition - the
    // settings-revision bump alone only restyles fixed geometry.
    private boolean lastShowsAllFactions;
    private boolean lastForcesAllSystemsOnMap;

    // One-shot guard for rebuild faults: refresh runs every frame the map is open, so a recurring
    // rebuild failure would flood the log. The first is recorded at ERROR, the rest silenced.
    private boolean hasLoggedRebuildError;

    /** @return the built production draw lists, or null while the debug overlay has replaced them */
    public PoliticalMapTerritories getTerritories() {
        return territories;
    }

    /** @return the built debug border-tracing overlay, or null in the normal (non-debug) view */
    public PoliticalMapDebugTerritories getDebugTerritories() {
        return debugTerritories;
    }

    // Whether the debug border-tracing overlay is the built view this frame - the one branch the
    // renderer needs to pick which base view to paint.
    public boolean isDebug() {
        return debugTerritories != null;
    }

    /** @return the cluster-label placements, drawn over whichever base view is live */
    public List<ClusterAnchor> getClusterAnchors() {
        return clusterAnchors;
    }

    /** @return the cached faction-name labels */
    public List<Label> getFactionLabels() {
        return factionLabels;
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
                LOG.error("Political map rebuild failed; retrying next frame, "
                        + "keeping last good draw lists", exception);
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
        var geometryRevision = PoliticalMapRefresh.getGeometryRevision();
        // The frontier resolution is a geometry input, not just a style: a change reseeds every
        // cell, so it makes the geometry stale the same way an access change does. Read once here
        // and let updateFromSector do the reseed.
        var boundSegments = KmuLunaSettings.getPoliticalMapCellBoundSegments();
        // The cell reach is a geometry input for the same reason as the resolution: it sets how
        // far each cell extends into empty space, so a change reseeds every cell and must rebuild
        // the partition here rather than only restyle it below.
        var cellRadius = KmuLunaSettings.getPoliticalMapCellRadius();
        // The two dev reveal overrides are geometry inputs for the same reason: each changes which
        // systems seed a cell, so a flip must reseed the partition here rather than only restyle it
        // through the content revision below.
        var devOverrides = PoliticalMapDevOverrides.readFromLunaSettings();
        if (geometryRevision != lastGeometryRevision || boundSegments != lastBoundSegments
                || cellRadius != lastCellRadius
                || devOverrides.isShowingAllFactions() != lastShowsAllFactions
                || devOverrides.isForcingAllSystemsOnMap() != lastForcesAllSystemsOnMap) {
            // Transition trace: a stale cell or one left behind after an access change can be tied
            // to the revision step - or segment count or cell reach - that drove it.
            LOG.debug("Political map geometry stale; rebuilding from revision "
                    + lastGeometryRevision + " to " + geometryRevision
                    + ", boundSegments " + lastBoundSegments + " to " + boundSegments
                    + ", cellRadius " + lastCellRadius + " to " + cellRadius
                    + ", showAllFactions " + lastShowsAllFactions + " to "
                    + devOverrides.isShowingAllFactions()
                    + ", forceAllSystems " + lastForcesAllSystemsOnMap + " to "
                    + devOverrides.isForcingAllSystemsOnMap());
            rebuildGeometry(boundSegments, cellRadius, devOverrides);
            lastGeometryRevision = geometryRevision;
            lastBoundSegments = boundSegments;
            lastCellRadius = cellRadius;
            lastShowsAllFactions = devOverrides.isShowingAllFactions();
            lastForcesAllSystemsOnMap = devOverrides.isForcingAllSystemsOnMap();
            rebuiltCells = true;
        }

        // TODO: when only geometry changed (rebuiltCells), reshape just the cells
        // PoliticalMapGeometryCache rebuilt - the system that gained or lost access and every cell
        // it touches - rather than the full territories rebuild below. Have updateFromSector report
        // its affected-cell set and drive a targeted reshape from it, the geometry-side analogue of
        // applyStalePoliticsUpdates.

        // Both views null means neither has been built this session, so the first frame forces the
        // build even if the content revision happens to match its -1 seed.
        var contentRevision = computeContentRevision(view);
        if (rebuiltCells || (territories == null && debugTerritories == null)
                || contentRevision != lastContentRevision) {
            var drawablesStart = System.nanoTime();
            // Build one view or the other, never both: the debug overlay replaces the normal
            // render, so in debug mode the production draw lists are not built at all, and the
            // unused view is nulled. The toggle is a KMU setting, so flipping it bumps the content
            // revision and forces this rebuild - which is what swaps the two. The anchor overlay
            // rebuilds either way - it draws over both views - borrowing the normal build's owner
            // map when there is one, resolving its own from the sector when the debug build left
            // none behind.
            if (KmuLunaSettings.shouldTraceBordersForDebug()) {
                debugTerritories = DebugBorderTracingBuilder.buildDebugDrawables(
                        geometryCache, Global.getSector());
                territories = null;
                ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(
                        clusterAnchors, geometryCache, Global.getSector(), view);
            } else {
                territories = TerritoryBuilder.buildTerritories(
                        geometryCache, Global.getSector(), view);
                debugTerritories = null;
                ClusterAnchorsBuilder.rebuildClusterAnchors(clusterAnchors, geometryCache,
                        territories.getOwnerBySystemId(), Global.getSector(),
                        territories.getDesaturationPalette(), view,
                        territories.getGrouping(), territories.isFiltering(),
                        territories.getRecedeAdjustment(), territories.getSelectedBlocId());
            }
            // The name labels are minted from the placements just rebuilt (empty when the names
            // toggle is off), keeping them in step with the fills and borders and reusing the one
            // placement search both consumers share.
            LabelsBuilder.rebuildLabels(factionLabels, clusterAnchors);
            lastContentRevision = contentRevision;
            // A full rebuild re-derives every system, so any pending per-system staleness is
            // already reflected - drain and discard it rather than re-processing the same systems
            // immediately after.
            PoliticalMapRefresh.drainStalePoliticsSystemIds();
            logContentRebuild(rebuiltCells, contentRevision, drawablesStart);
            return;
        }

        // No full rebuild this frame. In the normal view, fold in any per-system ownership changes
        // a colony resize marked, re-shaping only those systems and their neighbours over the
        // standing territories. The static debug overlay has no draw lists to patch, so its
        // staleness is drained instead - it refreshes on the next full rebuild (any settings or
        // geometry change). Under a filter the incremental re-shape is bypassed too: it re-derives
        // owners through the normal (non-filter) politics, which would overwrite the spotlit keys
        // and corrupt the spotlight, so a filtered map defers ownership changes to the next full
        // rebuild instead.
        if (territories != null && !territories.isFiltering()) {
            IncrementalPoliticsRefresh.applyStalePoliticsUpdates(territories, clusterAnchors,
                    factionLabels, geometryCache);
        } else {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();
        }
    }

    // Traces the content rebuild's result: the counts the render will paint and the whole-rebuild
    // time, so a wrong or empty render can be confirmed against what was built. The count reported
    // is whichever view was built this rebuild - the normal styled cells or the debug overlay's
    // base loops.
    private void logContentRebuild(boolean rebuiltCells, int contentRevision, long drawablesStart) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        var builtCounts = debugTerritories != null
                ? "debugBaseLoops=" + debugTerritories.baseLoops().size()
                : "styledCells=" + territories.getStyledCellByCellId().size();
        LOG.debug("Political map territories rebuilt; contentRevision=" + contentRevision
                + " " + builtCounts + " geometryRebuilt=" + rebuiltCells
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
    // geometry, so a settings-revision bump forces a full territories rebuild. Ownership changes no
    // longer feed this - a resized colony, or a system the sector watcher's owner diff caught,
    // marks just its system stale now. The active view is folded in so switching views (their
    // grouping, styling, and labels differ) rebuilds the territories under the newly-selected view
    // rather than reusing the previous view's. The view's content fingerprint is folded in too, so
    // a change to any live input the active view samples - the alliances view's alliance set -
    // rebuilds the territories even though no setting moved. The faction view samples nothing live
    // and contributes a constant, so an alliance change never churns it; the pipeline stays
    // view-neutral by reading this off the view rather than naming the alliance revision itself. The
    // filter and recede-style revisions are folded in at this pipeline level rather than through any
    // view, since each is a mode either view can be under: a filter pick or clear, or a recede
    // Mute/Desaturate flip, bumps its revision and rebuilds the territories under whichever view is
    // up, with no view naming it. Objects.hash is the JDK's standard 31-multiply fold, so the inputs
    // separate without a bespoke combine here.
    private static int computeContentRevision(PoliticalMapView view) {
        return Objects.hash(
                KmuLunaSettings.getSettingsRevision(),
                view.getId(),
                view.getContentRevision(),
                PoliticalMapRefresh.getFilterRevision(),
                PoliticalMapRefresh.getRecedeStyleRevision());
    }

    // Brings the geometry cache in line with the reachable systems, rebuilding only the cells
    // affected by an access change or a system starting or stopping moving - or every cell, when
    // the frontier resolution or the cell radius changed, since either reseeds them all. Feeds the
    // cache the currently-moving systems so they are left out of the partition (they seed no cell
    // and clip no neighbour), and the dev reveal overrides so a forced or undiscovered-colony
    // system joins the drawn set.
    private void rebuildGeometry(int boundSegments, double cellRadius,
            PoliticalMapDevOverrides overrides) {
        var movingSystemIds = MovingSystems.getInstance().getMovingSystemIds();
        KmuProfiling.getProfiler().measure("politicalMap.updateGeometry",
                () -> geometryCache.updateFromSector(
                        Global.getSector(), movingSystemIds, boundSegments, cellRadius, overrides));
    }
}
