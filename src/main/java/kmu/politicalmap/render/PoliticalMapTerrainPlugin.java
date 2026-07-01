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
import kmu.politicalmap.domain.ClassifiedEdge;
import kmu.politicalmap.domain.DecivilisedPresence;
import kmu.politicalmap.domain.EdgeClass;
import kmu.politicalmap.domain.EdgeClassifier;
import kmu.politicalmap.domain.PoliticalMapGeometryCache;
import kmu.politicalmap.domain.SectorPolitics;
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
 * sector (M) map.
 *
 * <p>Prototype scope (feature 022, ownership experiment): each faction-held
 * star system's cell is filled and outlined in the owner's bright UI color -
 * the fill at a partial alpha so the region reads as territory, the outline
 * opaque so borders stay crisp. An independent-held system fills and outlines
 * in its own opacities, lighter than a core faction's so it reads as loosely
 * held space. Decivilised systems (inhabited but factionless) and genuinely
 * empty systems carry no fill, only a faint outline in the neutral color, each
 * at its own opacity: an empty system is drawn only when the player opts in
 * ({@code kmu_politicalMapShowUninhabited}, off by default), while a revealed
 * decivilised system always draws, since a known dead colony is presence, not
 * empty space. The independent, decivilised, and uninhabited opacities are all
 * player-tunable under the LunaLib "Visuals customisation" tab, read via
 * {@link KmuLunaSettings}. Both fill and outline are the cell inset into a
 * single closed convex polygon, so a province reads as one clean shape, not a
 * ring of line segments.
 * The geometry comes from {@link PoliticalMapGeometryCache} and the ownership
 * colors from {@link SectorPolitics}; this plugin only draws them, always on.
 * There is deliberately no master overlay toggle, sidebar, presence tiers, or
 * blip stack yet - the goal is to confirm that faction territory reads correctly.
 *
 * <p>On top of that established render sits a verification overlay for the
 * in-progress HOI4-style region work: every Voronoi edge between two cells is
 * classified as an interior seam (the same faction holds both sides) or a
 * boundary (a different owner, unowned space, or the map frontier), and the
 * classified edges are stroked - interior seams solid yellow, boundaries a faint
 * dashed white - so a wrong classification is visible on the map. These are
 * deliberately loud debug placeholders layered over the untouched province
 * outlines, not a border redesign; the classified edges sit on the true cell
 * borders (not the inset
 * outline), since two same-faction cells share a true Voronoi edge that the inset
 * channel would otherwise hide. Later steps turn this classification into merged
 * faction blocs - raw-cell fills and real seam/border strokes - at which point
 * the overlay and the per-cell outlines give way to it.
 *
 * <p>Terrain is the surface because the sector map renders terrain through
 * {@code renderOnMap} - the same hook the vanilla nebulae draw with. A custom
 * campaign entity has no map-render hook, so its {@code render} never reaches
 * the map; only the live current-location view calls it. The below-UI
 * {@code renderOnMap} pass (rather than {@code renderOnMapAbove}) keeps the
 * territory beneath system and constellation names, matching its role as a
 * quiet background layer.
 *
 * <p>System positions in hyperspace are fixed for the life of a save, so the
 * cell outlines are built once and cached. The drawables (which cells are
 * filled, in what color, at what opacity, and the classified edge runs) are
 * rebuilt only when KMU's LunaLib settings change, detected off LunaLib's change
 * event via {@link KmuLunaSettings#getSettingsGeneration()} - so toggling the
 * uninhabited-systems setting or dragging an opacity slider takes effect live,
 * and the per-frame path is a single int compare, not a settings lookup. The
 * opacities, ownership colors, and classification are baked into the draw lists
 * at that same rebuild; live re-sampling on ownership change (raid, colonisation)
 * is a later step and is intentionally not done per frame, which would scan the
 * whole economy every frame.
 */
public class PoliticalMapTerrainPlugin extends BaseTerrain {
    private static final float OUTLINE_LINE_WIDTH = 2f;

    // Fill and outline opacities for a core-faction province. Fixed (not player-
    // tunable, unlike the independent/decivilised/uninhabited opacities): the
    // fill is high enough that the faction color reads at a glance yet low enough
    // that neighbouring fills do not muddy where they meet, and the outline is
    // fully opaque so faction borders stay the crispest thing on the map.
    private static final float FACTION_FILL_ALPHA = 0.4f;
    private static final float FACTION_BORDER_ALPHA = 1f;

    // Verification-overlay tints, sitting over the province outlines so the
    // adjacency classification can be eyeballed. Interior seams (same faction both
    // sides) draw solid yellow as the primary signal; national boundaries draw as
    // a faint dashed white so they recede - they are already implied by where the
    // faction outlines are. Deliberately loud debug placeholders; the finished
    // region render restyles both borders properly.
    private static final Color INTERIOR_SEAM_TINT = Color.YELLOW;
    private static final Color BOUNDARY_TINT = new Color(1f, 1f, 1f, 0.1f);
    // Line-stipple pattern for the dashed boundary edges: 0x00FF is eight bits on
    // then eight off, and the factor scales each run into a readable dash length
    // in screen pixels (stipple is applied post-transform, so zoom-independent).
    private static final short BOUNDARY_DASH_PATTERN = (short) 0x00FF;
    private static final int BOUNDARY_DASH_FACTOR = 2;

    // Shared empty vertex run, so a map with no classified edges of a class never
    // dereferences null and allocates nothing.
    private static final float[] NO_VERTICES = new float[0];

    // Floats per classified edge in a flattened GL_LINES run: two endpoints, each
    // an (x, y) pair. The stride for sizing a run and for counting edges back out
    // of one.
    private static final int FLOATS_PER_EDGE = 4;

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

    // Cell geometry keyed by system id, updated incrementally as systems gain or
    // lose access - only the cells near a change are rebuilt, not the whole map.
    // Holds both the inset province outlines (the fills/outlines) and the raw cell
    // edges (the adjacency graph the classification reads). Transient: it is
    // derived from the sector and rebuilt each session, and it holds record types
    // (CellEdge) XStream cannot serialise, so it must never enter the save. A
    // save-restored plugin comes back with it null, so it is recreated lazily in
    // rebuildStaleHalves rather than in a field initialiser (which XStream skips).
    private transient PoliticalMapGeometryCache geometryCache;

    // Drawables derived from the outlines, all transient for the same reasons as
    // the geometry cache: rebuilt each session, record-typed, kept out of the
    // save. Filled cells (owned: faction or independent) carry their owner's color
    // and the fill/border opacities resolved for that owner; outline-only cells
    // (decivilised or genuinely empty) carry just a border opacity and draw in the
    // shared neutral color.
    private transient List<FilledCell> filledCells;
    private transient List<OutlineCell> outlineCells;
    private transient Color neutralColor;
    // The verification overlay's classified cell edges, flattened into two
    // GL_LINES vertex runs ([x, y, x, y, ...], two points per segment) grouped by
    // class so each run draws under a single colour. Transient like the rest of
    // the derived draw state.
    private transient float[] boundaryEdgeVertices = NO_VERTICES;
    private transient float[] interiorSeamEdgeVertices = NO_VERTICES;
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
        if (filledCells.isEmpty() && outlineCells.isEmpty()
                && boundaryEdgeVertices.length == 0 && interiorSeamEdgeVertices.length == 0) {
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
        // into the three passes so the profiler shows which one costs, but not
        // logged - this runs every frame the map is open, so only the profiler's
        // accumulated view is affordable here, never a per-frame log line.
        var profiler = KmuProfiling.getProfiler();
        profiler.measure("politicalMap.render", () -> {
            profiler.measure("politicalMap.render.fills", () -> drawFills(factor, alphaMult));
            profiler.measure("politicalMap.render.outlines", () -> drawOutlines(factor, alphaMult));
            profiler.measure("politicalMap.render.classifiedEdges",
                    () -> drawClassifiedEdges(factor, alphaMult));
        });

        GL11.glPopAttrib();
    }

    // Fills each owned province with its owner's color at that cell's resolved
    // fill opacity. The outline is convex, so a triangle fan from the first
    // vertex tessellates it correctly.
    private void drawFills(float factor, float alphaMult) {
        for (var cell : filledCells) {
            GlColor.set(cell.color(), alphaMult * cell.fillAlpha());
            drawVertexRun(GL11.GL_TRIANGLE_FAN, cell.outline(), factor);
        }
    }

    // Draws each province outline as a closed loop on top of the fills, each at
    // its resolved border opacity: owned outlines in the owner's color, outline-
    // only (decivilised/empty) cells in the shared neutral color.
    private void drawOutlines(float factor, float alphaMult) {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(OUTLINE_LINE_WIDTH);

        for (var cell : filledCells) {
            GlColor.set(cell.color(), alphaMult * cell.borderAlpha());
            drawVertexRun(GL11.GL_LINE_LOOP, cell.outline(), factor);
        }
        for (var cell : outlineCells) {
            GlColor.set(neutralColor, alphaMult * cell.borderAlpha());
            drawVertexRun(GL11.GL_LINE_LOOP, cell.outline(), factor);
        }
    }

    // Strokes the classified cell edges in two passes, one style each, so the
    // adjacency classification reads directly off the map. Each run is a flat
    // GL_LINES vertex list (two points per edge) already grouped by class. This
    // is the verification overlay for the region work, layered over the outlines:
    // boundaries faint and dashed (line stipple), interior seams solid.
    private void drawClassifiedEdges(float factor, float alphaMult) {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(OUTLINE_LINE_WIDTH);

        GL11.glEnable(GL11.GL_LINE_STIPPLE);
        GL11.glLineStipple(BOUNDARY_DASH_FACTOR, BOUNDARY_DASH_PATTERN);
        GlColor.set(BOUNDARY_TINT, alphaMult);
        drawVertexRun(GL11.GL_LINES, boundaryEdgeVertices, factor);
        GL11.glDisable(GL11.GL_LINE_STIPPLE);

        GlColor.set(INTERIOR_SEAM_TINT, alphaMult);
        drawVertexRun(GL11.GL_LINES, interiorSeamEdgeVertices, factor);
    }

    // Emits one flat [x, y, x, y, ...] vertex run under the given GL primitive,
    // scaling each world coordinate into map space. Serves every primitive the
    // overlay draws: GL_TRIANGLE_FAN fills, GL_LINE_LOOP outlines, and GL_LINES
    // classified-edge segments.
    private static void drawVertexRun(int mode, float[] vertices, float factor) {
        GL11.glBegin(mode);
        for (var v = 0; v < vertices.length; v += 2) {
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
            // Transition trace: a stale province or one left behind after an
            // access change can be tied to the revision step that drove it.
            LOG.debug("Political map geometry stale; rebuilding from revision "
                    + lastGeometryRevision + " to " + geometryRevision);
            rebuildGeometry();
            lastGeometryRevision = geometryRevision;
            rebuiltCells = true;
        }

        // filledCells == null means the drawables have never been built this
        // session. The revision seeds (-1) force the first build for a freshly
        // constructed plugin, but a plugin restored from a save comes back with
        // its revision fields already advanced past -1 while the static counters
        // reset to 0 on load - so the seed trick can match and skip the build,
        // leaving the drawable lists null for renderOnMap to dereference. The
        // null check forces the build regardless of how the counters line up.
        var contentRevision = computeContentRevision();
        if (rebuiltCells || filledCells == null || contentRevision != lastContentRevision) {
            var drawablesStart = System.nanoTime();
            rebuildDrawables();
            lastContentRevision = contentRevision;
            // Result trace: the counts the overlay will actually paint and the
            // whole-rebuild time, so a wrong or empty render can be confirmed
            // against what was built and how long it cost.
            LOG.debug("Political map drawables rebuilt; contentRevision=" + contentRevision
                    + " filledCells=" + filledCells.size()
                    + " outlineCells=" + outlineCells.size()
                    + " boundaryEdges=" + boundaryEdgeVertices.length / FLOATS_PER_EDGE
                    + " interiorSeamEdges=" + interiorSeamEdgeVertices.length / FLOATS_PER_EDGE
                    + " geometryRebuilt=" + rebuiltCells
                    + " took=" + Timings.formatMillis(System.nanoTime() - drawablesStart));
        }
    }

    // Guards the render path after a failed first build: a rebuild that threw
    // before completing can leave the draw lists null, which renderOnMap would
    // dereference. Empty lists make the render a harmless no-op until a later
    // frame's retry succeeds.
    private void ensureDrawablesNonNull() {
        if (filledCells == null) {
            filledCells = new ArrayList<>();
            outlineCells = new ArrayList<>();
            boundaryEdgeVertices = NO_VERTICES;
            interiorSeamEdgeVertices = NO_VERTICES;
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

    // Partitions the cached outlines into filled (owned) and outline-only
    // (decivilised/empty) draw lists, baking in each cell's color and the
    // opacities resolved from the current settings, then builds the classified-
    // edge overlay runs. Flattens each outline to a GL-ready vertex run here.
    // Reads the opacity settings once, not per cell.
    private void rebuildDrawables() {
        var profiler = KmuProfiling.getProfiler();
        profiler.measure("politicalMap.rebuildDrawables", () -> {
            var sector = Global.getSector();
            var isShowingUninhabited = KmuLunaSettings.isShowUninhabitedSystemsEnabled();
            var independentBorderOpacity = KmuLunaSettings.getIndependentBorderOpacity();
            var independentFillOpacity = KmuLunaSettings.getIndependentFillOpacity();
            var decivilisedBorderOpacity = KmuLunaSettings.getDecivilisedBorderOpacity();
            var uninhabitedBorderOpacity = KmuLunaSettings.getUninhabitedBorderOpacity();
            filledCells = new ArrayList<>();
            outlineCells = new ArrayList<>();

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

            for (var entry
                    : geometryCache.getOutlineBySystemId().entrySet()) {
                var outline = entry.getValue();
                if (outline.isEmpty()) {
                    continue;
                }
                var flat = flattenVertices(outline);
                var owner = ownerBySystemId.get(entry.getKey());
                if (owner != null) {
                    var opacity = resolveOwnedOpacity(owner.factionId(),
                            independentFillOpacity, independentBorderOpacity);
                    filledCells.add(new FilledCell(flat, owner.color(),
                            opacity.fillAlpha(), opacity.borderAlpha()));
                } else {
                    var borderOpacity = resolveNeutralBorderOpacity(
                            decivilisedSystemIds.contains(entry.getKey()), isShowingUninhabited,
                            decivilisedBorderOpacity, uninhabitedBorderOpacity);
                    borderOpacity.ifPresent(
                            opacity -> outlineCells.add(new OutlineCell(flat, (float) opacity)));
                }
            }

            var classifyStart = System.nanoTime();
            var classified = profiler.measure("politicalMap.classifyEdges",
                    () -> EdgeClassifier.classifyEdges(
                            geometryCache.getCellEdgesBySystemId(), ownerBySystemId));
            boundaryEdgeVertices = flattenEdges(classified, EdgeClass.BOUNDARY);
            interiorSeamEdgeVertices = flattenEdges(classified, EdgeClass.INTERIOR_SEAM);
            LOG.debug("Political map edges classified; total=" + classified.size()
                    + " boundary=" + boundaryEdgeVertices.length / FLOATS_PER_EDGE
                    + " interior=" + interiorSeamEdgeVertices.length / FLOATS_PER_EDGE
                    + " took=" + Timings.formatMillis(System.nanoTime() - classifyStart));
        });
    }

    // Resolves the fill and border opacities for an owned province by its owner.
    // Independent space uses the player's independent opacities so it reads as
    // loosely held; every other faction draws at the fixed, opaque-bordered
    // province default.
    static OwnedOpacity resolveOwnedOpacity(String dominantFactionId,
            double independentFillOpacity, double independentBorderOpacity) {
        if (Factions.INDEPENDENT.equals(dominantFactionId)) {
            return new OwnedOpacity((float) independentFillOpacity, (float) independentBorderOpacity);
        }
        return new OwnedOpacity(FACTION_FILL_ALPHA, FACTION_BORDER_ALPHA);
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

    // Flattens the edges of one class into a GL_LINES vertex run
    // ([x1, y1, x2, y2, ...]). Sized in a first pass so the run is a single exact
    // array rather than a growing list boxed per coordinate.
    private static float[] flattenEdges(List<ClassifiedEdge> edges, EdgeClass edgeClass) {
        var matching = 0;
        for (var edge : edges) {
            if (edge.edgeClass() == edgeClass) {
                matching++;
            }
        }
        var flat = new float[matching * FLOATS_PER_EDGE];
        var index = 0;
        for (var edge : edges) {
            if (edge.edgeClass() != edgeClass) {
                continue;
            }
            flat[index++] = (float) edge.x1();
            flat[index++] = (float) edge.y1();
            flat[index++] = (float) edge.x2();
            flat[index++] = (float) edge.y2();
        }
        return flat;
    }

    // Flattens a polygon's {x, y} vertices into a [x, y, x, y, ...] run.
    private static float[] flattenVertices(List<double[]> polygon) {
        var flat = new float[polygon.size() * 2];
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
        LOG.debug("Political map overlay renderOnMap fired: filledCells="
                + filledCells.size() + " outlineCells=" + outlineCells.size()
                + " boundaryEdges=" + boundaryEdgeVertices.length / FLOATS_PER_EDGE
                + " interiorSeamEdges=" + interiorSeamEdgeVertices.length / FLOATS_PER_EDGE
                + " factor=" + factor + " alphaMult=" + alphaMult);
    }

    // The fill and border opacities resolved for one owned province (faction or
    // independent), kept together because resolveOwnedOpacity decides both from
    // the same owner. Package-private: it is the return of the package-private
    // resolveOwnedOpacity, the seam that owner-styling is pinned at.
    record OwnedOpacity(float fillAlpha, float borderAlpha) {
    }

    // A filled province ready to draw: its flattened outline, owner color, and
    // the fill and border opacities resolved for its owner.
    private record FilledCell(float[] outline, Color color, float fillAlpha, float borderAlpha) {
    }

    // An outline-only cell (a decivilised or genuinely empty system): its
    // flattened outline and border opacity. Drawn in the shared neutral color.
    private record OutlineCell(float[] outline, float borderAlpha) {
    }
}
