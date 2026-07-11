package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;

import kmlib.profiling.Timings;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.politicalmap.base.PoliticalMapDevOverrides;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.geometry.PoliticalMapGeometryCache;
import kmu.maplayers.politicalmap.base.refresh.MovingSystems;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefresh;
import kmu.maplayers.politicalmap.base.render.model.ClusterAnchor;
import kmu.maplayers.politicalmap.base.render.model.PoliticalMapDebugDrawables;
import kmu.maplayers.politicalmap.base.render.model.PoliticalMapDrawables;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Terrain plugin that paints the political map on the sector (M) map as merged
 * HOI4-style clusters, where adjacent same-grouping systems fuse into one solid
 * national region. It is the shared political-map terrain surface, living in
 * {@code base.render} so any political-map view reuses one grouping-agnostic pipeline;
 * it draws whichever view {@link PoliticalMapViewRegistry#getActiveView()} reports, or
 * stays dark when none is active. This class is the terrain
 * adapter and cache coordinator; the
 * visual design lives in its collaborators: {@link DrawablesBuilder} shapes the cells
 * and bakes each element's colors, opacities, and widths from the LunaLib "Visuals
 * customisation" settings, {@link IncrementalPoliticsRefresh} folds in per-system
 * ownership changes, and {@link PoliticalMapRenderer} emits the GL. While the "debug
 * border tracing" dev toggle is on, {@link DebugBorderTracingBuilder} and
 * {@link PoliticalMapStaticDebugRenderer} replace the normal build and render with a
 * layered view of the border-smoothing pipeline's stages. The debug cluster anchors are
 * independent of that swap: {@link ClusterAnchorsBuilder} rebuilds them alongside
 * whichever view was built and {@link ClusterAnchorRenderer} draws them over it, so the
 * two debug facilities compose instead of the border view hiding the anchors.
 *
 * <p>Terrain is the surface because the sector map renders terrain through
 * {@code renderOnMap} - the same hook the vanilla nebulae draw with. A custom campaign
 * entity has no map-render hook, so its {@code render} never reaches the map; only the
 * live current-location view calls it. The below-UI {@code renderOnMap} pass (rather
 * than {@code renderOnMapAbove}) keeps the territory beneath system and constellation
 * names, matching its role as a quiet background layer.
 *
 * <p>The plugin's own job is to keep the cached draw lists fresh with the least work
 * per frame. System positions in hyperspace are fixed for the life of a save, so the
 * raw cells are built once and cached; they are reseeded only when the reachable-
 * system set changes, the frontier-resolution setting changes (that count seeds every
 * cell), or a dev reveal override flips (each changes which systems seed a cell). The
 * drawables are rebuilt in full only when KMU's LunaLib settings change
 * (detected off LunaLib's change event via
 * {@link KmuLunaSettings#getSettingsRevision()}) or when the geometry itself was
 * rebuilt; between those, a colony resize marks just its own system stale and drives an
 * incremental re-shape. So switching a color or dragging an opacity slider takes effect
 * live, and the per-frame path is otherwise a couple of int compares, never a per-frame
 * economy scan.
 *
 * <p>The geometry cache and drawables are {@code transient}: they are derived from the
 * sector and rebuilt each session, and they hold record types XStream cannot serialise,
 * so they must never enter the save. A save-restored plugin comes back with them null -
 * XStream skips transient fields and does not run field initialisers - so they are
 * recreated lazily in {@link #rebuildStaleHalves} rather than in a field initialiser.
 */
public class PoliticalMapTerrainPlugin extends BaseTerrain {
    // Map rendering ignores this (the map calls the map hooks regardless), but
    // BaseTerrain requires the override; large so the terrain is never treated as a
    // tiny point elsewhere.
    private static final float RENDER_RANGE = 1_000_000f;

    // This terrain draws only on the sector map (renderOnMap); it has no world-view
    // rendering, so it claims no engine layers. BaseTerrain's default getActiveLayers()
    // throws to force a deliberate choice, and an empty set is the correct one for a
    // map-only terrain - vanilla's RadioChatterTerrainPlugin does the same. Returning it
    // (rather than leaving the default) is what lets addTerrain succeed on a fresh game;
    // omitting it crashes onGameLoad.
    private static final EnumSet<CampaignEngineLayers> ACTIVE_LAYERS =
            EnumSet.noneOf(CampaignEngineLayers.class);

    private static final Logger LOG = Global.getLogger(PoliticalMapTerrainPlugin.class);

    // Raw cell geometry keyed by system id, updated incrementally as systems gain or
    // lose access. Transient: derived from the sector, record-typed (CellEdge), and kept
    // out of the save. A save-restored plugin comes back with it null, so it is
    // recreated lazily in rebuildStaleHalves rather than in a field initialiser (which
    // XStream skips).
    private transient PoliticalMapGeometryCache geometryCache;

    // The built draw lists plus the ownership and style inputs an incremental re-shape
    // needs. Transient for the same reasons as the geometry cache: derived each session,
    // record-typed, kept out of the save. Null until the first build this session.
    private transient PoliticalMapDrawables drawables;

    // The debug border-tracing overlay, built instead of the drawables above while the
    // "debug border tracing" dev toggle is on - exactly one of the two is non-null, and it
    // is the one rendered. The toggle is a KMU setting, so flipping it bumps the settings
    // generation and forces the content rebuild that swaps which view is built. Transient
    // for the same reasons as the drawables above.
    private transient PoliticalMapDebugDrawables debugDrawables;

    // The cluster-label placements, owned here rather than by either view above so they
    // draw over whichever is live - turning border tracing on must not hide them. Empty
    // unless the names or the anchor overlay is on; they feed both. Transient for the same
    // reasons as the views; recreated lazily in rebuildStaleHalves.
    private transient List<ClusterAnchor> clusterAnchors;

    // The cached faction-name labels, built from the placements above. Each owns a GL
    // buffer, so the builder disposes the standing strings whenever it rebuilds this list.
    // Empty unless the "show faction names" toggle is on. Transient for the same reasons as
    // the views; recreated lazily in rebuildStaleHalves.
    private transient List<Label> factionLabels;

    // The revisions each half of the cache was built against. Geometry rebuilds when
    // the reachable-system set changes or the frontier resolution setting changes (it
    // reseeds every cell); the drawables rebuild on a content change (settings) or
    // whenever the geometry itself was rebuilt. Start at -1 so the first render builds
    // both, and so any real segment count differs from the seed.
    private int lastGeometryRevision = -1;
    private int lastContentRevision = -1;
    private int lastBoundSegments = -1;
    // The cell reach the cached geometry was last seeded at. Like the frontier resolution
    // it reseeds every cell, so a change forces a geometry rebuild rather than a restyle.
    // Starts NaN so any real radius differs from the seed and the first render rebuilds.
    private double lastCellRadius = Double.NaN;
    // The dev reveal overrides the cached geometry was last seeded under. Like the
    // frontier resolution, they change which systems seed a cell, so a flip reseeds the
    // partition - the settings-revision bump alone only restyles fixed geometry. Held
    // here so the stale check catches a flip and forces a geometry rebuild the same
    // frame, rather than waiting on the paused-on-map sector watcher's next poll.
    private boolean lastShowsAllFactions;
    private boolean lastForcesAllSystemsOnMap;

    // Diagnostic: ensures the first map render logs exactly once.
    private boolean hasLoggedFirstRender;

    // One-shot guard for rebuild faults: renderOnMap runs every frame the map is open,
    // so a recurring rebuild failure would flood the log. The first is recorded at
    // ERROR, the rest silenced.
    private boolean hasLoggedRebuildError;

    @Override
    public EnumSet<CampaignEngineLayers> getActiveLayers() {
        return ACTIVE_LAYERS;
    }

    @Override
    public float getRenderRange() {
        return RENDER_RANGE;
    }

    @Override
    public void advance(float amount) {
        // Purely visual: no fleet effect, sound, or music suppression, so the default
        // BaseTerrain effect/sound pass is intentionally skipped.
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        // Nothing in the live world view - this overlay is a map-only layer.
    }

    @Override
    public void renderOnMap(float factor, float alphaMult) {
        // Paints whichever political-map view is active, or nothing when none is - the map is off
        // (No Layer tab) or dark (political-map tab open, view deselected). Gating the whole draw
        // (and its rebuild) on one view read keeps a hidden overlay near-free per frame, and reading
        // the view - not a named faction gate - is what lets any registered view paint here.
        var view = PoliticalMapViewRegistry.getActiveView();
        if (view == null) {
            return;
        }
        rebuildIfStale(view);
        logFirstRenderOnce(factor, alphaMult);
        // Swap production and debug render on the debug toggle: the rebuild builds the debug
        // overlay only while the toggle is on, so its presence is the one branch here.
        if (debugDrawables != null) {
            PoliticalMapStaticDebugRenderer.renderOnMap(debugDrawables, factor, alphaMult);
        } else {
            PoliticalMapRenderer.renderOnMap(drawables, factor, alphaMult);
        }
        // The anchor overlay layers over whichever base view just drew - it is
        // independent of the swap above, so the two debug toggles compose. Gated on its own
        // toggle here (not by the list being empty): the placements are also built for the
        // faction names, so the list can be non-empty while the debug overlay is off.
        if (KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            ClusterAnchorRenderer.renderOnMap(clusterAnchors, factor, alphaMult);
        }
        // The faction names draw last of the map passes, so a name reads over its territory
        // and the debug band, but still beneath the vanilla star and constellation names
        // (drawn after every terrain renderOnMap). The list is empty unless the names toggle
        // is on, so this is an empty-list check when they are off.
        LabelRenderer.renderOnMap(factionLabels, factor, alphaMult);
    }

    // Rebuilds only the stale half of the cache. The expensive cell geometry is rebuilt
    // only when the reachable-system set changes (a gate activating, a jump point
    // established); the cheap drawables are rebuilt on a content change (settings) or
    // whenever the geometry was just rebuilt (the drawables reference the new cells).
    // The per-frame path is otherwise just comparing a couple of ints.
    private void rebuildIfStale(PoliticalMapView view) {
        // Guarded because renderOnMap runs every frame the map is open: a rebuild fault
        // is recorded once (not per frame), and the catch leaves the cached revisions
        // un-advanced so the next frame retries rather than the overlay going
        // permanently stale or null.
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

    // Rebuilds only the stale half of the cache, advancing each cached revision only
    // after its rebuild completes so a thrown rebuild is retried next frame. Builds under the
    // active view's rules, so a view switch (folded into the content revision) rebuilds the
    // drawables under the newly-selected view.
    private void rebuildStaleHalves(PoliticalMapView view) {
        // A plugin restored from a save comes back with its transient caches null:
        // XStream skips transient fields and does not run field initialisers. Bring the
        // geometry cache back and seed the revision to -1 so the geometry - and through
        // the rebuiltCells flag, the drawables - rebuild from scratch this frame,
        // regardless of how the restored revision and the reset static counter happen to
        // line up.
        if (geometryCache == null) {
            geometryCache = new PoliticalMapGeometryCache();
            lastGeometryRevision = -1;
        }
        // Same restore path for the placements and the label cache: transient, so a
        // save-restored plugin comes back with them null. Recreated empty here - before any
        // rebuild work can throw - so the render below always has lists to draw.
        if (clusterAnchors == null) {
            clusterAnchors = new ArrayList<>();
        }
        if (factionLabels == null) {
            factionLabels = new ArrayList<>();
        }

        var rebuiltCells = false;
        var geometryRevision = PoliticalMapRefresh.getGeometryRevision();
        // The frontier resolution is a geometry input, not just a style: a change
        // reseeds every cell, so it makes the geometry stale the same way an access
        // change does. Read once here and let updateFromSector do the reseed.
        var boundSegments = KmuLunaSettings.getPoliticalMapCellBoundSegments();
        // The cell reach is a geometry input for the same reason as the resolution: it
        // sets how far each cell extends into empty space, so a change reseeds every cell
        // and must rebuild the partition here rather than only restyle it below.
        var cellRadius = KmuLunaSettings.getPoliticalMapCellRadius();
        // The two dev reveal overrides are geometry inputs for the same reason: each
        // changes which systems seed a cell, so a flip must reseed the partition here
        // rather than only restyle it through the content revision below.
        var devOverrides = PoliticalMapDevOverrides.readFromLunaSettings();
        if (geometryRevision != lastGeometryRevision || boundSegments != lastBoundSegments
                || cellRadius != lastCellRadius
                || devOverrides.isShowingAllFactions() != lastShowsAllFactions
                || devOverrides.isForcingAllSystemsOnMap() != lastForcesAllSystemsOnMap) {
            // Transition trace: a stale cell or one left behind after an access change
            // can be tied to the revision step - or segment count or cell reach - that
            // drove it.
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
        // PoliticalMapGeometryCache rebuilt - the system that gained or lost access and
        // every cell it touches - rather than the full drawables rebuild below. Have
        // updateFromSector report its affected-cell set and drive a targeted reshape
        // from it, the geometry-side analogue of applyStalePoliticsUpdates.

        // Both views null means neither has been built this session. The revision seeds
        // (-1) force the first build for a freshly constructed plugin, but a plugin
        // restored from a save comes back with its revision fields already advanced past
        // -1 while the static counters reset to 0 on load - so the seed trick can match
        // and skip the build, leaving both views null for renderOnMap to dereference. The
        // both-null check forces the build regardless of how the counters line up.
        var contentRevision = computeContentRevision(view);
        if (rebuiltCells || (drawables == null && debugDrawables == null)
                || contentRevision != lastContentRevision) {
            var drawablesStart = System.nanoTime();
            // Build one view or the other, never both: the debug overlay replaces the
            // normal render, so in debug mode the production draw lists are not built at
            // all, and the unused view is nulled. The toggle is a KMU setting, so flipping
            // it bumps the content revision and forces this rebuild - which is what swaps
            // the two. The anchor overlay rebuilds either way - it draws over both views -
            // borrowing the normal build's owner map when there is one, resolving its own
            // from the sector when the debug build left none behind.
            if (KmuLunaSettings.shouldTraceBordersForDebug()) {
                debugDrawables = DebugBorderTracingBuilder.buildDebugDrawables(
                        geometryCache, Global.getSector());
                drawables = null;
                ClusterAnchorsBuilder.rebuildClusterAnchorsFromSector(
                        clusterAnchors, geometryCache, Global.getSector(), view);
            } else {
                drawables = DrawablesBuilder.buildDrawables(
                        geometryCache, Global.getSector(), view);
                debugDrawables = null;
                ClusterAnchorsBuilder.rebuildClusterAnchors(clusterAnchors, geometryCache,
                        drawables.getOwnerBySystemId(), Global.getSector(), view,
                        drawables.getGrouping(), drawables.isFiltering(),
                        drawables.getRecedeAdjustment(), drawables.getSelectedBlocId());
            }
            // The name labels are minted from the placements just rebuilt (empty when the
            // names toggle is off), keeping them in step with the fills and borders and
            // reusing the one placement search both consumers share.
            LabelsBuilder.rebuildLabels(factionLabels, clusterAnchors);
            lastContentRevision = contentRevision;
            // A full rebuild re-derives every system, so any pending per-system
            // staleness is already reflected - drain and discard it rather than
            // re-processing the same systems immediately after.
            PoliticalMapRefresh.drainStalePoliticsSystemIds();
            logContentRebuild(rebuiltCells, contentRevision, drawablesStart);
            return;
        }

        // No full rebuild this frame. In the normal view, fold in any per-system ownership
        // changes a colony resize marked, re-shaping only those systems and their
        // neighbours over the standing drawables. The static debug overlay has no draw
        // lists to patch, so its staleness is drained instead - it refreshes on the next
        // full rebuild (any settings or geometry change).
        if (drawables != null) {
            IncrementalPoliticsRefresh.applyStalePoliticsUpdates(drawables, clusterAnchors,
                    factionLabels, geometryCache);
        } else {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();
        }
    }

    // Traces the content rebuild's result: the counts the render will paint and the whole-
    // rebuild time, so a wrong or empty render can be confirmed against what was built. The
    // count reported is whichever view was built this rebuild - the normal styled cells or
    // the debug overlay's base loops.
    private void logContentRebuild(boolean rebuiltCells, int contentRevision, long drawablesStart) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        var builtCounts = debugDrawables != null
                ? "debugBaseLoops=" + debugDrawables.baseLoops().size()
                : "styledCells=" + drawables.getStyledCellBySystemId().size();
        LOG.debug("Political map drawables rebuilt; contentRevision=" + contentRevision
                + " " + builtCounts + " geometryRebuilt=" + rebuiltCells
                + " took=" + Timings.formatMillis(System.nanoTime() - drawablesStart));
    }

    // Guards the render path after a failed first build: a rebuild that threw before
    // completing can leave the draw lists null, which the renderer would dereference. An
    // empty placeholder makes the render a harmless no-op until a later frame's retry
    // succeeds.
    private void ensureDrawablesNonNull(PoliticalMapView view) {
        if (drawables == null) {
            drawables = PoliticalMapDrawables.createEmpty(view);
        }
    }

    // The drawables-staleness token: a settings change restyles every cell over the
    // fixed geometry, so a settings-revision bump forces a full drawables rebuild.
    // Ownership changes no longer feed this - a resized colony, or a system the
    // sector watcher's owner diff caught, marks just its system stale now. The active view
    // is folded in so switching views (their grouping, styling, and labels differ) rebuilds
    // the drawables under the newly-selected view rather than reusing the previous view's.
    // The view's content fingerprint is folded in too, so a change to any live input the active view
    // samples - the alliances view's alliance set or its recede toggles - rebuilds the drawables even
    // though no setting moved. The faction view samples nothing live and contributes a constant, so an
    // alliance change never churns it; the plugin stays view-neutral by reading this off the view
    // rather than naming the alliance revision itself. Objects.hash is the JDK's standard 31-multiply
    // fold, so the three inputs separate without a bespoke combine here.
    private static int computeContentRevision(PoliticalMapView view) {
        return Objects.hash(
                KmuLunaSettings.getSettingsRevision(),
                view.getId(),
                view.getContentRevision());
    }

    // Brings the geometry cache in line with the reachable systems, rebuilding only the
    // cells affected by an access change or a system starting or stopping moving - or
    // every cell, when the frontier resolution or the cell radius changed, since either
    // reseeds them all. Feeds the cache the currently-moving systems so they are left out
    // of the partition (they seed no cell and clip no neighbour), and the dev reveal
    // overrides so a forced or undiscovered-colony system joins the drawn set.
    private void rebuildGeometry(int boundSegments, double cellRadius,
            PoliticalMapDevOverrides overrides) {
        var movingSystemIds = MovingSystems.getInstance().getMovingSystemIds();
        KmuProfiling.getProfiler().measure("politicalMap.updateGeometry",
                () -> geometryCache.updateFromSector(
                        Global.getSector(), movingSystemIds, boundSegments, cellRadius, overrides));
    }

    // One-shot diagnostic for the no-draw investigation. Guarded on isDebugEnabled so
    // the once-flag only trips when the line actually emits. Set KMU log verbosity to
    // DEBUG in LunaLib to see it.
    private void logFirstRenderOnce(float factor, float alphaMult) {
        if (hasLoggedFirstRender || !LOG.isDebugEnabled()) {
            return;
        }
        hasLoggedFirstRender = true;
        // Report whichever view is live: the normal draw lists, or the debug overlay when
        // it has replaced them (drawables is null in debug mode).
        var builtCounts = debugDrawables != null
                ? "debugBaseLoops=" + debugDrawables.baseLoops().size()
                : "styledCells=" + drawables.getStyledCellBySystemId().size();
        LOG.debug("Political map render renderOnMap fired: " + builtCounts
                + " factor=" + factor + " alphaMult=" + alphaMult);
    }
}
