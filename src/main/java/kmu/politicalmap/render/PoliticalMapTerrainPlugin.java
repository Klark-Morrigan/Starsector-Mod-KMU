package kmu.politicalmap.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;

import kmlib.opengl.GlColor;
import kmlib.profiling.Timings;

import kmu.diagnostics.KmuProfiling;
import kmu.politicalmap.PoliticalMapRefresh;
import kmu.politicalmap.domain.CellShaper;
import kmu.politicalmap.domain.DecivilisedPresence;
import kmu.politicalmap.domain.PoliticalMapGeometryCache;
import kmu.politicalmap.domain.SectorPolitics;
import kmu.politicalmap.domain.ShapedCell;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Terrain plugin that paints the political map's faction territory on the
 * sector (M) map as merged HOI4-style blocs.
 *
 * <p>Adjacent star systems held by the same faction fuse into one solid region:
 * their cells are filled with a per-edge inset that leaves the shared edge on the
 * true Voronoi border, so the two fills meet exactly and read as one bloc with no
 * channel between them. Every edge against a different faction, unowned space, or
 * the map frontier is pulled inward instead, so the bloc keeps a uniform national
 * border channel against everything outside it. Each bloc is stroked twice: its
 * national-border edges in the owner's bright color and a bold width, and its
 * interior seams (the fused edges between member cells) in the owner's dark UI
 * color and a thin width, so the province lines inside a bloc recede behind the
 * border.
 * The seam ends stay within the padded border rather than reaching the raw
 * midline between cells, so the bloc's edge reads as one uniform border. The fill
 * sits at a partial alpha so territory reads without muddying where blocs meet.
 *
 * <p>An independent-held system draws at the player's independent fill/border
 * opacities, lighter than a core faction's, so it reads as loosely held space.
 * Decivilised systems (inhabited but factionless) and genuinely empty systems
 * carry no fill, only a faint inset outline in the neutral color, each at its own
 * opacity: an empty system draws only when the player opts in
 * ({@code kmu_politicalMapShowUninhabited}, off by default), while a revealed
 * decivilised system always draws, since a known dead colony is presence, not
 * empty space. The independent, decivilised, and uninhabited opacities are all
 * player-tunable under the LunaLib "Visuals customisation" tab, read via
 * {@link KmuLunaSettings}.
 *
 * <p>The raw cells and their adjacency come from {@link PoliticalMapGeometryCache}
 * and the ownership colors from {@link SectorPolitics}; {@link CellShaper} turns
 * the two into the merged-bloc geometry, and this plugin only flattens and draws
 * it. There is deliberately no master overlay toggle, sidebar, presence tiers, or
 * blip stack yet - the goal is to confirm that faction territory reads correctly.
 *
 * <p>Terrain is the surface because the sector map renders terrain through
 * {@code renderOnMap} - the same hook the vanilla nebulae draw with. A custom
 * campaign entity has no map-render hook, so its {@code render} never reaches
 * the map; only the live current-location view calls it. The below-UI
 * {@code renderOnMap} pass (rather than {@code renderOnMapAbove}) keeps the
 * territory beneath system and constellation names, matching its role as a
 * quiet background layer.
 *
 * <p>System positions in hyperspace are fixed for the life of a save, so the raw
 * cells are built once and cached. The drawables (which blocs are filled, in what
 * color, at what opacity, and where their border and seam edges run) are rebuilt
 * only when KMU's LunaLib settings change, detected off LunaLib's change event
 * via {@link KmuLunaSettings#getSettingsGeneration()} - so toggling the
 * uninhabited-systems setting or dragging an opacity slider takes effect live,
 * and the per-frame path is a single int compare, not a settings lookup. The
 * opacities, ownership colors, and merged shaping are baked into the draw lists
 * at that same rebuild; live re-sampling on ownership change (raid, colonisation)
 * is a later step and is intentionally not done per frame, which would scan the
 * whole economy every frame.
 */
public class PoliticalMapTerrainPlugin extends BaseTerrain {
    // A vertex is a 2D point packed as x then y, so every flattened run here is a
    // [x, y, x, y, ...] array. This is the stride from one vertex to the next and
    // the multiplier that sizes a run from its vertex count.
    private static final int FLOATS_PER_VERTEX = 2;

    // A stroked edge is two endpoints, so its flattened GL_LINES contribution is
    // two vertices wide. Sizes the border/seam runs in flattenEdgesOfClass.
    private static final int FLOATS_PER_EDGE = 2 * FLOATS_PER_VERTEX;

    // Map rendering ignores this (the map calls the map hooks regardless), but
    // BaseTerrain requires the override; large so the terrain is never treated
    // as a tiny point elsewhere.
    private static final float RENDER_RANGE = 1_000_000f;

    // This terrain draws only on the sector map (renderOnMap); it has no
    // world-view rendering, so it claims no engine layers. BaseTerrain's default
    // getActiveLayers() throws to force a deliberate choice, and an empty set is
    // the correct one for a map-only terrain - vanilla's RadioChatterTerrainPlugin
    // does the same. Returning it (rather than leaving the default) is what lets
    // addTerrain succeed on a fresh game; omitting it crashes onGameLoad.
    private static final EnumSet<CampaignEngineLayers> ACTIVE_LAYERS =
            EnumSet.noneOf(CampaignEngineLayers.class);

    private static final Logger LOG = Global.getLogger(PoliticalMapTerrainPlugin.class);

    // Raw cell geometry keyed by system id, updated incrementally as systems gain
    // or lose access - only the cells near a change are rebuilt, not the whole
    // map. Transient: it is derived from the sector and rebuilt each session, and
    // it holds record types (CellEdge) XStream cannot serialise, so it must never
    // enter the save. A save-restored plugin comes back with it null, so it is
    // recreated lazily in rebuildStaleHalves rather than in a field initialiser
    // (which XStream skips).
    private transient PoliticalMapGeometryCache geometryCache;

    // Drawables derived from the raw cells, all transient for the same reasons as
    // the geometry cache: rebuilt each session, record-typed, kept out of the
    // save. Owned blocs carry their owner's color, the fill/border opacities
    // resolved for that owner, and their flattened fill/border/seam runs;
    // outline-only cells (decivilised or genuinely empty) carry just a border
    // opacity and their inset loop, drawn in the shared neutral color.
    private transient List<FilledBloc> filledBlocs;
    private transient List<NeutralOutline> neutralOutlines;
    private transient Color neutralColor;
    // The revisions each half of the cache was built against. Geometry rebuilds
    // only when the reachable-system set changes; the drawables rebuild on a
    // content change (settings, discovery) or whenever the geometry itself was
    // rebuilt. Start at -1 so the first render builds both.
    private int lastGeometryRevision = -1;
    private int lastContentRevision = -1;

    // Diagnostic: ensures the first map render logs exactly once.
    private boolean hasLoggedFirstRender;

    // One-shot guard for rebuild faults: renderOnMap runs every frame the map is
    // open, so a recurring rebuild failure would flood the log. The first is
    // recorded at ERROR, the rest silenced.
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
        // Purely visual: no fleet effect, sound, or music suppression, so the
        // default BaseTerrain effect/sound pass is intentionally skipped.
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        // Nothing in the live world view - this overlay is a map-only layer.
    }

    @Override
    public void renderOnMap(float factor, float alphaMult) {
        rebuildIfStale();
        logFirstRenderOnce(factor, alphaMult);
        if (filledBlocs.isEmpty() && neutralOutlines.isEmpty()) {
            return;
        }

        // Map space: a world coordinate maps to (world * factor). The map widget
        // has already applied the map's pan/centering to the GL matrix, so only
        // the scale is applied here. Drawn in the below-UI map pass so system
        // and constellation names stay on top.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_HINT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Time only the per-frame GL emission; the surrounding state push/pop is
        // negligible and the cache checks above are deliberately outside. Broken
        // into the two passes so the profiler shows which one costs, but not
        // logged - this runs every frame the map is open, so only the profiler's
        // accumulated view is affordable here, never a per-frame log line.
        var profiler = KmuProfiling.getProfiler();
        profiler.measure("politicalMap.render", () -> {
            profiler.measure("politicalMap.render.fills", () -> drawFills(factor, alphaMult));
            profiler.measure("politicalMap.render.borders", () -> drawBorders(factor, alphaMult));
        });

        GL11.glPopAttrib();
    }

    // Fills each bloc with its owner's color at that bloc's resolved fill opacity.
    // The fill polygon is convex, so a triangle fan from the first vertex
    // tessellates it correctly.
    private void drawFills(float factor, float alphaMult) {
        for (var bloc : filledBlocs) {
            GlColor.set(bloc.color(), alphaMult * bloc.fillAlpha());
            drawVertexRun(GL11.GL_TRIANGLE_FAN, bloc.fill(), factor);
        }
    }

    // Strokes the interior seams first (thin, dark) then the national borders
    // (thick, bright) on top, so a bloc's edge dominates its internal province
    // lines. Owned edges are GL_LINES segment runs (the border and seam subsets of
    // each bloc's shaped outline); a neutral cell has no seams, so its inset loop
    // draws as one closed line loop in the shared neutral color.
    private void drawBorders(float factor, float alphaMult) {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        GL11.glLineWidth(PoliticalMapStyle.INTERIOR_LINE_WIDTH);
        for (var bloc : filledBlocs) {
            GlColor.set(bloc.interiorColor(), alphaMult * bloc.borderAlpha());
            drawVertexRun(GL11.GL_LINES, bloc.interiorEdges(), factor);
        }

        GL11.glLineWidth(PoliticalMapStyle.BOUNDARY_LINE_WIDTH);
        for (var bloc : filledBlocs) {
            GlColor.set(bloc.color(), alphaMult * bloc.borderAlpha());
            drawVertexRun(GL11.GL_LINES, bloc.boundaryEdges(), factor);
        }
        for (var outline : neutralOutlines) {
            GlColor.set(neutralColor, alphaMult * outline.borderAlpha());
            drawVertexRun(GL11.GL_LINE_LOOP, outline.outlineLoop(), factor);
        }
    }

    // Emits one flat [x, y, x, y, ...] vertex run under the given GL primitive,
    // scaling each world coordinate into map space. Serves every primitive the
    // render draws: GL_TRIANGLE_FAN fills, GL_LINE_LOOP neutral outlines, and
    // GL_LINES border/seam segments.
    private static void drawVertexRun(int mode, float[] vertices, float factor) {
        GL11.glBegin(mode);
        for (var v = 0; v < vertices.length; v += FLOATS_PER_VERTEX) {
            GL11.glVertex2f(vertices[v] * factor, vertices[v + 1] * factor);
        }
        GL11.glEnd();
    }

    // Rebuilds only the stale half of the cache. The expensive cell geometry is
    // rebuilt only when the reachable-system set changes (a gate activating, a
    // jump point established); the cheap drawables are rebuilt on a content
    // change (settings, a discovered market) or whenever the geometry was just
    // rebuilt (the drawables reference the new cells). The per-frame path is
    // otherwise just comparing a couple of ints.
    private void rebuildIfStale() {
        // Guarded because renderOnMap runs every frame the map is open: a rebuild
        // fault is recorded once (not per frame), and the catch leaves the cached
        // revisions un-advanced so the next frame retries rather than the overlay
        // going permanently stale or null.
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

    // Rebuilds only the stale half of the cache, advancing each cached revision
    // only after its rebuild completes so a thrown rebuild is retried next frame.
    private void rebuildStaleHalves() {
        // A plugin restored from a save comes back with its transient caches null:
        // XStream skips transient fields and does not run field initialisers. Bring
        // the geometry cache back and seed the revision to -1 so the geometry - and
        // through the rebuiltCells flag, the drawables - rebuild from scratch this
        // frame, regardless of how the restored revision and the reset static
        // counter happen to line up.
        if (geometryCache == null) {
            geometryCache = new PoliticalMapGeometryCache();
            lastGeometryRevision = -1;
        }

        var rebuiltCells = false;
        var geometryRevision = PoliticalMapRefresh.getGeometryRevision();
        if (geometryRevision != lastGeometryRevision) {
            // Transition trace: a stale cell or one left behind after an access
            // change can be tied to the revision step that drove it.
            LOG.debug("Political map geometry stale; rebuilding from revision "
                    + lastGeometryRevision + " to " + geometryRevision);
            rebuildGeometry();
            lastGeometryRevision = geometryRevision;
            rebuiltCells = true;
        }

        // filledBlocs == null means the drawables have never been built this
        // session. The revision seeds (-1) force the first build for a freshly
        // constructed plugin, but a plugin restored from a save comes back with
        // its revision fields already advanced past -1 while the static counters
        // reset to 0 on load - so the seed trick can match and skip the build,
        // leaving the drawable lists null for renderOnMap to dereference. The
        // null check forces the build regardless of how the counters line up.
        var contentRevision = computeContentRevision();
        if (rebuiltCells || filledBlocs == null || contentRevision != lastContentRevision) {
            var drawablesStart = System.nanoTime();
            rebuildDrawables();
            lastContentRevision = contentRevision;
            // Result trace: the counts the render will actually paint and the
            // whole-rebuild time, so a wrong or empty render can be confirmed
            // against what was built and how long it cost.
            LOG.debug("Political map drawables rebuilt; contentRevision=" + contentRevision
                    + " filledBlocs=" + filledBlocs.size()
                    + " neutralOutlines=" + neutralOutlines.size()
                    + " geometryRebuilt=" + rebuiltCells
                    + " took=" + Timings.formatMillis(System.nanoTime() - drawablesStart));
        }
    }

    // Guards the render path after a failed first build: a rebuild that threw
    // before completing can leave the draw lists null, which renderOnMap would
    // dereference. Empty lists make the render a harmless no-op until a later
    // frame's retry succeeds.
    private void ensureDrawablesNonNull() {
        if (filledBlocs == null) {
            filledBlocs = new ArrayList<>();
            neutralOutlines = new ArrayList<>();
            neutralColor = Color.GRAY;
        }
    }

    // The drawables-staleness token. Settings changes and discovered markets
    // both restyle the same fixed geometry, so they share one counter; both only
    // ever increase, so the sum increases on any such change.
    private static int computeContentRevision() {
        return KmuLunaSettings.getSettingsGeneration() + PoliticalMapRefresh.getContentRevision();
    }

    // Brings the geometry cache in line with the reachable systems, rebuilding
    // only the cells affected by an access change.
    private void rebuildGeometry() {
        KmuProfiling.getProfiler().measure("politicalMap.updateGeometry",
                () -> geometryCache.updateFromSector(Global.getSector()));
    }

    // Shapes the cached raw cells into merged faction blocs and partitions them
    // into filled (owned) and outline-only (decivilised/empty) draw lists, baking
    // in each cell's color and the opacities resolved from the current settings,
    // then flattens each to GL-ready vertex runs. Reads the opacity settings once,
    // not per cell.
    private void rebuildDrawables() {
        var profiler = KmuProfiling.getProfiler();
        profiler.measure("politicalMap.rebuildDrawables", () -> {
            var sector = Global.getSector();
            var isShowingUninhabited = KmuLunaSettings.isShowUninhabitedSystemsEnabled();
            var independentBorderOpacity = KmuLunaSettings.getIndependentBorderOpacity();
            var independentFillOpacity = KmuLunaSettings.getIndependentFillOpacity();
            var decivilisedBorderOpacity = KmuLunaSettings.getDecivilisedBorderOpacity();
            var uninhabitedBorderOpacity = KmuLunaSettings.getUninhabitedBorderOpacity();
            filledBlocs = new ArrayList<>();
            neutralOutlines = new ArrayList<>();

            // The politics scan walks the whole economy - the priciest content
            // step - so it is profiled and timed on its own, and the owner count
            // logged independent of the profiler's accumulated view.
            var politicsStart = System.nanoTime();
            var ownerBySystemId = profiler.measure("politicalMap.resolvePolitics",
                    () -> SectorPolitics.resolveDominantOwnerBySystemId(sector));
            LOG.debug("Political map politics resolved; ownedSystems=" + ownerBySystemId.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - politicsStart));

            var decivilisedStart = System.nanoTime();
            var decivilisedSystemIds = profiler.measure("politicalMap.findDecivilised",
                    () -> DecivilisedPresence.findRevealedDecivilisedSystemIds(sector));
            LOG.debug("Political map decivilised scan; systems=" + decivilisedSystemIds.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - decivilisedStart));

            neutralColor = SectorPolitics.resolveNeutralColor(sector);

            // Shape the raw cells into merged blocs once, ownership-aware, then
            // split into filled and neutral draw lists. Cells consumed by the inset
            // (fewer than three vertices left) drop out - nothing to fill or stroke.
            var shapeStart = System.nanoTime();
            var shapedCells = profiler.measure("politicalMap.shapeCells",
                    () -> CellShaper.shapeCells(geometryCache.getCellEdgesBySystemId(),
                            ownerBySystemId, PoliticalMapStyle.BORDER_INSET_DISTANCE));
            for (var entry : shapedCells.entrySet()) {
                var shaped = entry.getValue();
                // A cell the border inset consumed or collapsed comes back with an
                // empty fill (CellShaper via Polygons.insetSelectedEdges guarantees
                // empty-or-drawable), so there is nothing to fill or stroke.
                if (shaped.fillPolygon().isEmpty()) {
                    continue;
                }
                var owner = ownerBySystemId.get(entry.getKey());
                if (owner != null) {
                    var opacity = resolveOwnedOpacity(owner.factionId(),
                            independentFillOpacity, independentBorderOpacity);
                    filledBlocs.add(new FilledBloc(
                            flattenVertices(shaped.fillPolygon()),
                            flattenEdgesOfClass(shaped, true),
                            flattenEdgesOfClass(shaped, false),
                            owner.color(), owner.seamColor(),
                            opacity.fillAlpha(), opacity.borderAlpha()));
                } else {
                    var borderOpacity = resolveNeutralBorderOpacity(
                            decivilisedSystemIds.contains(entry.getKey()), isShowingUninhabited,
                            decivilisedBorderOpacity, uninhabitedBorderOpacity);
                    borderOpacity.ifPresent(opacity -> neutralOutlines.add(new NeutralOutline(
                            flattenVertices(shaped.fillPolygon()), (float) opacity)));
                }
            }
            LOG.debug("Political map cells shaped; shaped=" + shapedCells.size()
                    + " filledBlocs=" + filledBlocs.size()
                    + " neutralOutlines=" + neutralOutlines.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - shapeStart));
        });
    }

    // Resolves the fill and border opacities for an owned bloc by its owner.
    // Independent space uses the player's independent opacities so it reads as
    // loosely held; every other faction draws at the fixed, opaque-bordered
    // province default.
    static OwnedOpacity resolveOwnedOpacity(String dominantFactionId,
            double independentFillOpacity, double independentBorderOpacity) {
        if (Factions.INDEPENDENT.equals(dominantFactionId)) {
            return new OwnedOpacity((float) independentFillOpacity, (float) independentBorderOpacity);
        }
        return new OwnedOpacity(PoliticalMapStyle.FACTION_FILL_ALPHA,
                PoliticalMapStyle.FACTION_BORDER_ALPHA);
    }

    // Resolves the outline opacity for a neutral (unfilled) cell, or empty when
    // the cell is not drawn. A revealed decivilised system - a known dead colony -
    // is always outlined, at the decivilised opacity, since it is presence rather
    // than empty space. A genuinely empty system is outlined only when the player
    // has opted in, at the uninhabited opacity.
    static OptionalDouble resolveNeutralBorderOpacity(boolean isDecivilised,
            boolean isShowingUninhabited, double decivilisedBorderOpacity,
            double uninhabitedBorderOpacity) {
        if (isDecivilised) {
            return OptionalDouble.of(decivilisedBorderOpacity);
        }
        if (isShowingUninhabited) {
            return OptionalDouble.of(uninhabitedBorderOpacity);
        }
        return OptionalDouble.empty();
    }

    // Flattens the edges of a shaped bloc of one class into a GL_LINES vertex run
    // ([x1, y1, x2, y2, ...]): national borders when wantBoundary is true, interior
    // seams when false. Sized in a first pass so the run is a single exact array
    // rather than a growing list boxed per coordinate.
    private static float[] flattenEdgesOfClass(ShapedCell shaped, boolean wantBoundary) {
        var polygon = shaped.fillPolygon();
        var edgeIsBoundary = shaped.edgeIsBoundary();
        var count = polygon.size();
        var matching = 0;
        for (var i = 0; i < count; i++) {
            if (edgeIsBoundary[i] == wantBoundary) {
                matching++;
            }
        }
        var flat = new float[matching * FLOATS_PER_EDGE];
        var index = 0;
        for (var i = 0; i < count; i++) {
            if (edgeIsBoundary[i] != wantBoundary) {
                continue;
            }
            var start = polygon.get(i);
            var end = polygon.get((i + 1) % count);
            flat[index++] = (float) start[0];
            flat[index++] = (float) start[1];
            flat[index++] = (float) end[0];
            flat[index++] = (float) end[1];
        }
        return flat;
    }

    // Flattens a polygon's {x, y} vertices into a [x, y, x, y, ...] run.
    private static float[] flattenVertices(List<double[]> polygon) {
        var flat = new float[polygon.size() * FLOATS_PER_VERTEX];
        var index = 0;
        for (var vertex : polygon) {
            flat[index++] = (float) vertex[0];
            flat[index++] = (float) vertex[1];
        }
        return flat;
    }

    // One-shot diagnostic for the no-draw investigation. Guarded on
    // isDebugEnabled so the once-flag only trips when the line actually emits.
    // Set KMU log verbosity to DEBUG in LunaLib to see it.
    private void logFirstRenderOnce(float factor, float alphaMult) {
        if (hasLoggedFirstRender || !LOG.isDebugEnabled()) {
            return;
        }
        hasLoggedFirstRender = true;
        LOG.debug("Political map render renderOnMap fired: filledBlocs="
                + filledBlocs.size() + " neutralOutlines=" + neutralOutlines.size()
                + " factor=" + factor + " alphaMult=" + alphaMult);
    }

    // The fill and border opacities resolved for one owned bloc (faction or
    // independent), kept together because resolveOwnedOpacity decides both from
    // the same owner. Package-private: it is the return of the package-private
    // resolveOwnedOpacity, the seam that owner-styling is pinned at.
    record OwnedOpacity(float fillAlpha, float borderAlpha) {
    }

    // A filled bloc ready to draw: its flattened fill polygon, its national-border
    // and interior-seam edge runs (GL_LINES segments), the owner's bright color
    // for the fill and border and its dark UI color for the interior seams, and
    // the fill/border opacities resolved for its owner.
    private record FilledBloc(float[] fill, float[] boundaryEdges, float[] interiorEdges,
            Color color, Color interiorColor, float fillAlpha, float borderAlpha) {
    }

    // An outline-only cell (a decivilised or genuinely empty system): its inset
    // outline as a closed loop and border opacity. Drawn in the shared neutral
    // color; it has no interior seams, since it merges with nothing.
    private record NeutralOutline(float[] outlineLoop, float borderAlpha) {
    }
}
