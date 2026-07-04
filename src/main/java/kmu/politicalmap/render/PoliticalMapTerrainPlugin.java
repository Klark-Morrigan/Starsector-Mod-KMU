package kmu.politicalmap.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;

import kmlib.profiling.Timings;

import kmu.diagnostics.KmuProfiling;
import kmu.politicalmap.domain.geometry.PoliticalMapGeometryCache;
import kmu.politicalmap.refresh.PoliticalMapRefresh;
import kmu.politicalmap.render.model.ClusterAnchor;
import kmu.politicalmap.render.model.PoliticalMapDebugDrawables;
import kmu.politicalmap.render.model.PoliticalMapDrawables;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Terrain plugin that paints the political map's faction territory on the sector (M)
 * map as merged HOI4-style clusters, where adjacent same-faction systems fuse into one
 * solid national region. This class is the terrain adapter and cache coordinator; the
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
 * system set changes or the frontier-resolution setting changes (that count seeds every
 * cell). The drawables are rebuilt in full only when KMU's LunaLib settings change
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

    // The debug cluster-anchor overlay, owned here rather than by either view above so
    // it draws over whichever is live - turning border tracing on must not hide the
    // anchors. Empty unless the "show cluster anchors" dev toggle built it. Transient
    // for the same reasons as the views; recreated lazily in rebuildStaleHalves.
    private transient List<ClusterAnchor> clusterAnchors;

    // The revisions each half of the cache was built against. Geometry rebuilds when
    // the reachable-system set changes or the frontier resolution setting changes (it
    // reseeds every cell); the drawables rebuild on a content change (settings) or
    // whenever the geometry itself was rebuilt. Start at -1 so the first render builds
    // both, and so any real segment count differs from the seed.
    private int lastGeometryRevision = -1;
    private int lastContentRevision = -1;
    private int lastBoundSegments = -1;

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
        rebuildIfStale();
        logFirstRenderOnce(factor, alphaMult);
        // Swap production and debug render on the debug toggle: the rebuild builds the debug
        // overlay only while the toggle is on, so its presence is the one branch here.
        if (debugDrawables != null) {
            PoliticalMapStaticDebugRenderer.renderOnMap(debugDrawables, factor, alphaMult);
        } else {
            PoliticalMapRenderer.renderOnMap(drawables, factor, alphaMult);
        }
        // The anchor overlay layers over whichever base view just drew - it is
        // independent of the swap above, so the two debug toggles compose.
        ClusterAnchorRenderer.renderOnMap(clusterAnchors, factor, alphaMult);
    }

    // Rebuilds only the stale half of the cache. The expensive cell geometry is rebuilt
    // only when the reachable-system set changes (a gate activating, a jump point
    // established); the cheap drawables are rebuilt on a content change (settings) or
    // whenever the geometry was just rebuilt (the drawables reference the new cells).
    // The per-frame path is otherwise just comparing a couple of ints.
    private void rebuildIfStale() {
        // Guarded because renderOnMap runs every frame the map is open: a rebuild fault
        // is recorded once (not per frame), and the catch leaves the cached revisions
        // un-advanced so the next frame retries rather than the overlay going
        // permanently stale or null.
        try {
            rebuildStaleHalves();
        } catch (RuntimeException exception) {
            if (!hasLoggedRebuildError) {
                hasLoggedRebuildError = true;
                LOG.error("Political map rebuild failed; retrying next frame, "
                        + "keeping last good draw lists", exception);
            }
            ensureDrawablesNonNull();
        }
    }

    // Rebuilds only the stale half of the cache, advancing each cached revision only
    // after its rebuild completes so a thrown rebuild is retried next frame.
    private void rebuildStaleHalves() {
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
        // Same restore path for the anchor overlay: transient, so a save-restored plugin
        // comes back with it null. Recreated empty here - before any rebuild work can
        // throw - so the render below always has a list to draw.
        if (clusterAnchors == null) {
            clusterAnchors = new ArrayList<>();
        }

        var rebuiltCells = false;
        var geometryRevision = PoliticalMapRefresh.getGeometryRevision();
        // The frontier resolution is a geometry input, not just a style: a change
        // reseeds every cell, so it makes the geometry stale the same way an access
        // change does. Read once here and let updateFromSector do the reseed.
        var boundSegments = KmuLunaSettings.getPoliticalMapCellBoundSegments();
        if (geometryRevision != lastGeometryRevision || boundSegments != lastBoundSegments) {
            // Transition trace: a stale cell or one left behind after an access change
            // can be tied to the revision step - or segment count - that drove it.
            LOG.debug("Political map geometry stale; rebuilding from revision "
                    + lastGeometryRevision + " to " + geometryRevision
                    + ", boundSegments " + lastBoundSegments + " to " + boundSegments);
            rebuildGeometry(boundSegments);
            lastGeometryRevision = geometryRevision;
            lastBoundSegments = boundSegments;
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
        var contentRevision = computeContentRevision();
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
                        clusterAnchors, geometryCache, Global.getSector());
            } else {
                drawables = DrawablesBuilder.buildDrawables(
                        geometryCache, Global.getSector());
                debugDrawables = null;
                ClusterAnchorsBuilder.rebuildClusterAnchors(
                        clusterAnchors, geometryCache, drawables.getOwnerBySystemId());
            }
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
                    geometryCache);
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
    private void ensureDrawablesNonNull() {
        if (drawables == null) {
            drawables = PoliticalMapDrawables.createEmpty();
        }
    }

    // The drawables-staleness token: a settings change restyles every cell over the
    // fixed geometry, so a settings-revision bump forces a full drawables rebuild.
    // Ownership changes no longer feed this - a resized colony, or a system the
    // sector watcher's owner diff caught, marks just its system stale now - so
    // settings is the one whole-map restyle left.
    private static int computeContentRevision() {
        return KmuLunaSettings.getSettingsRevision();
    }

    // Brings the geometry cache in line with the reachable systems, rebuilding only the
    // cells affected by an access change - or every cell, when the frontier resolution
    // changed, since that reseeds them all.
    private void rebuildGeometry(int boundSegments) {
        KmuProfiling.getProfiler().measure("politicalMap.updateGeometry",
                () -> geometryCache.updateFromSector(Global.getSector(), boundSegments));
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
