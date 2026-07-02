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
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;
import kmu.settings.NeutralColorChoice;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

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
 * national-border (outer) edges, then its interior seams (the fused edges between
 * member cells) beneath them, so the province lines inside a bloc recede behind
 * the border. The seam ends stay within the padded border rather than reaching the
 * raw midline between cells, so the bloc's edge reads as one uniform border.
 *
 * <p>Every element is player-styled per category under the LunaLib "Visuals
 * customisation" tab, read via {@link KmuLunaSettings}. The two owned categories -
 * core factions and independent space - each fuse into blocs and get a fill, an
 * outer border, and an inner seam; each element draws in one of the owner faction's
 * two palette colors (its bright "primary" or dark "secondary" shade) or "No
 * color" to omit it, at its own opacity, and - for the borders - its own line
 * width. Independent draws from its own bundle (lighter opacities by default) so it
 * reads as loosely held space.
 *
 * <p>Decivilised systems (inhabited but factionless) and uninhabited systems do
 * not merge and carry no fill - only a single inset outline in the shared neutral
 * color, or "No color" to hide it, each at its own opacity and width. A revealed
 * decivilised system draws by default, since a known dead colony is presence, not
 * empty space; uninhabited systems default to "No color" (hidden), so only
 * faction-held, independent, and decivilised systems draw unless the player turns
 * uninhabited on.
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
 * via {@link KmuLunaSettings#getSettingsGeneration()} - so switching a category's
 * color or dragging an opacity slider takes effect live, and the per-frame path is
 * a single int compare, not a settings lookup. The colors, opacities, widths, and
 * merged shaping are baked into the draw lists
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

    // Drawables derived from the raw cells, transient for the same reasons as the
    // geometry cache: rebuilt each session, record-typed, kept out of the save.
    // Every category - faction, independent, decivilised, uninhabited - resolves to
    // the same StyledBloc: its flattened fill/border/seam runs and each element's
    // color, opacity, and width. A factionless category resolves both its palette
    // slots to the shared neutral color and carries only an outline.
    private transient List<StyledBloc> styledBlocs;
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
        if (styledBlocs.isEmpty()) {
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

    // Fills each bloc with its resolved fill color at its resolved fill opacity. A
    // null fill color is a "No color" choice (and every factionless bloc), so that
    // bloc is left unfilled. The fill polygon is convex, so a triangle fan from the
    // first vertex tessellates it correctly.
    private void drawFills(float factor, float alphaMult) {
        for (var bloc : styledBlocs) {
            if (bloc.fillColor() == null) {
                continue;
            }
            GlColor.set(bloc.fillColor(), alphaMult * bloc.fillAlpha());
            drawVertexRun(GL11.GL_TRIANGLE_FAN, bloc.fill(), factor);
        }
    }

    // Strokes the interior seams first, then the national borders over them, so a
    // bloc's edge dominates its internal province lines where they meet. Color,
    // opacity, and line width are all per bloc, so the width is set per bloc; a null
    // color is a "No color" choice and skips that element. A factionless bloc has no
    // seams, so only its outer outline draws.
    private void drawBorders(float factor, float alphaMult) {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        for (var bloc : styledBlocs) {
            if (bloc.innerColor() == null) {
                continue;
            }
            GL11.glLineWidth(bloc.innerWidth());
            GlColor.set(bloc.innerColor(), alphaMult * bloc.innerAlpha());
            drawVertexRun(GL11.GL_LINES, bloc.interiorEdges(), factor);
        }
        for (var bloc : styledBlocs) {
            if (bloc.outerColor() == null) {
                continue;
            }
            GL11.glLineWidth(bloc.outerWidth());
            GlColor.set(bloc.outerColor(), alphaMult * bloc.outerAlpha());
            drawVertexRun(GL11.GL_LINES, bloc.boundaryEdges(), factor);
        }
    }

    // Emits one flat [x, y, x, y, ...] vertex run under the given GL primitive,
    // scaling each world coordinate into map space. Serves both primitives the
    // render draws: GL_TRIANGLE_FAN fills and GL_LINES border/seam segments.
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

        // styledBlocs == null means the drawables have never been built this
        // session. The revision seeds (-1) force the first build for a freshly
        // constructed plugin, but a plugin restored from a save comes back with
        // its revision fields already advanced past -1 while the static counters
        // reset to 0 on load - so the seed trick can match and skip the build,
        // leaving the drawable list null for renderOnMap to dereference. The null
        // check forces the build regardless of how the counters line up.
        var contentRevision = computeContentRevision();
        if (rebuiltCells || styledBlocs == null || contentRevision != lastContentRevision) {
            var drawablesStart = System.nanoTime();
            rebuildDrawables();
            lastContentRevision = contentRevision;
            // Result trace: the counts the render will actually paint and the
            // whole-rebuild time, so a wrong or empty render can be confirmed
            // against what was built and how long it cost.
            LOG.debug("Political map drawables rebuilt; contentRevision=" + contentRevision
                    + " styledBlocs=" + styledBlocs.size()
                    + " geometryRebuilt=" + rebuiltCells
                    + " took=" + Timings.formatMillis(System.nanoTime() - drawablesStart));
        }
    }

    // Guards the render path after a failed first build: a rebuild that threw
    // before completing can leave the draw lists null, which renderOnMap would
    // dereference. Empty lists make the render a harmless no-op until a later
    // frame's retry succeeds.
    private void ensureDrawablesNonNull() {
        if (styledBlocs == null) {
            styledBlocs = new ArrayList<>();
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

    // Shapes the cached raw cells into merged blocs and partitions them into filled
    // (owned) and outline-only (decivilised/uninhabited) draw lists, baking in each
    // cell's colors, opacities, and widths resolved from the current settings, then
    // flattens each to GL-ready vertex runs. Reads the settings once per category,
    // not per cell.
    private void rebuildDrawables() {
        var profiler = KmuProfiling.getProfiler();
        profiler.measure("politicalMap.rebuildDrawables", () -> {
            var sector = Global.getSector();
            // One style bundle per category, applied below by the cell's category.
            // A factionless style points its fill and inner seam at "No color" and
            // draws only an outline.
            var factionStyle = readFactionStyle();
            var independentStyle = readIndependentStyle();
            var decivilisedStyle = readDecivilisedStyle();
            var uninhabitedStyle = readUninhabitedStyle();
            styledBlocs = new ArrayList<>();

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

            // Shape the raw cells into merged blocs once, ownership-aware. Cells
            // consumed by the inset (fewer than three vertices left) drop out -
            // nothing to fill or stroke.
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
                    // Independent space styles from its own bundle; every other
                    // owner is a core faction. Each element resolves against the
                    // owner's two palette shades.
                    var style = Factions.INDEPENDENT.equals(owner.factionId())
                            ? independentStyle
                            : factionStyle;
                    styledBlocs.add(buildStyledBloc(shaped, owner.primaryColor(),
                            owner.secondaryColor(), style));
                } else {
                    // Factionless: decivilised or (otherwise) uninhabited. Its style
                    // fills neither palette slot, so both resolve to the shared
                    // neutral color and only its outline draws; skip it when that
                    // outline is "No color".
                    var style = decivilisedSystemIds.contains(entry.getKey())
                            ? decivilisedStyle
                            : uninhabitedStyle;
                    if (style.outerColor() != FactionPaletteChoice.NONE) {
                        styledBlocs.add(
                                buildStyledBloc(shaped, neutralColor, neutralColor, style));
                    }
                }
            }
            LOG.debug("Political map cells shaped; shaped=" + shapedCells.size()
                    + " styledBlocs=" + styledBlocs.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - shapeStart));
        });
    }

    // Builds one bloc's draw record: its flattened geometry, plus each element's
    // color resolved against this bloc's two palette shades (null for a "No color"
    // choice) and the opacities and widths from its category style. A factionless
    // bloc passes the neutral color for both shades.
    private static StyledBloc buildStyledBloc(ShapedCell shaped, Color primaryColor,
            Color secondaryColor, MapStyle style) {
        return new StyledBloc(
                flattenVertices(shaped.fillPolygon()),
                flattenEdgesOfClass(shaped, true),
                flattenEdgesOfClass(shaped, false),
                pickPaletteColor(style.fillColor(), primaryColor, secondaryColor),
                pickPaletteColor(style.outerColor(), primaryColor, secondaryColor),
                pickPaletteColor(style.innerColor(), primaryColor, secondaryColor),
                (float) style.fillOpacity(), (float) style.outerOpacity(),
                (float) style.innerOpacity(),
                (float) style.outerWidth(), (float) style.innerWidth());
    }

    // Picks the palette shade the player pointed an element at: the secondary
    // (dark) shade for a SECONDARY choice, the primary (bright) shade for a PRIMARY
    // choice, or null for NONE ("No color") so the caller skips that element.
    static Color pickPaletteColor(FactionPaletteChoice choice, Color primaryColor,
            Color secondaryColor) {
        return switch (choice) {
            case PRIMARY -> primaryColor;
            case SECONDARY -> secondaryColor;
            case NONE -> null;
        };
    }

    // Reads each owned category's eight style settings into one bundle, so the
    // build loop applies them per bloc without eight lookups each.
    private static MapStyle readFactionStyle() {
        return new MapStyle(
                KmuLunaSettings.getFactionFillColor(), KmuLunaSettings.getFactionFillOpacity(),
                KmuLunaSettings.getFactionOuterBorderColor(),
                KmuLunaSettings.getFactionOuterBorderOpacity(),
                KmuLunaSettings.getFactionOuterBorderWidth(),
                KmuLunaSettings.getFactionInnerBorderColor(),
                KmuLunaSettings.getFactionInnerBorderOpacity(),
                KmuLunaSettings.getFactionInnerBorderWidth());
    }

    private static MapStyle readIndependentStyle() {
        return new MapStyle(
                KmuLunaSettings.getIndependentFillColor(), KmuLunaSettings.getIndependentFillOpacity(),
                KmuLunaSettings.getIndependentOuterBorderColor(),
                KmuLunaSettings.getIndependentOuterBorderOpacity(),
                KmuLunaSettings.getIndependentOuterBorderWidth(),
                KmuLunaSettings.getIndependentInnerBorderColor(),
                KmuLunaSettings.getIndependentInnerBorderOpacity(),
                KmuLunaSettings.getIndependentInnerBorderWidth());
    }

    // A factionless category resolves to the same style with no fill and no inner
    // seam - only its single outline draws, in the neutral color both palette slots
    // will carry, or "No color" to hide it.
    private static MapStyle readDecivilisedStyle() {
        return neutralStyle(KmuLunaSettings.getDecivilisedBorderColor(),
                KmuLunaSettings.getDecivilisedBorderOpacity(),
                KmuLunaSettings.getDecivilisedBorderWidth());
    }

    private static MapStyle readUninhabitedStyle() {
        return neutralStyle(KmuLunaSettings.getUninhabitedBorderColor(),
                KmuLunaSettings.getUninhabitedBorderOpacity(),
                KmuLunaSettings.getUninhabitedBorderWidth());
    }

    // Assembles a factionless outline's style: its outline as the outer border (in
    // the neutral color via a PRIMARY choice, or NONE to hide it), with no fill and
    // no inner seam. Both slots hold the neutral color at draw time, so PRIMARY and
    // SECONDARY would paint identically; PRIMARY is the drawn arm here.
    private static MapStyle neutralStyle(NeutralColorChoice color, double opacity, double width) {
        var outerColor = color.isDrawn() ? FactionPaletteChoice.PRIMARY : FactionPaletteChoice.NONE;
        return new MapStyle(FactionPaletteChoice.NONE, 0, outerColor, opacity, width,
                FactionPaletteChoice.NONE, 0, 0);
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
        LOG.debug("Political map render renderOnMap fired: styledBlocs="
                + styledBlocs.size() + " factor=" + factor + " alphaMult=" + alphaMult);
    }

    // One category's full political-map style, read once per rebuild and applied to
    // every bloc of that category: the fill, outer-border, and inner-seam color
    // choices with their opacities, plus the two border widths. The colors are
    // choices (resolved against each bloc's two palette shades), not concrete
    // colors, since one bundle serves many blocs. A factionless category sets fill
    // and inner to NONE so only its outline draws.
    private record MapStyle(
            FactionPaletteChoice fillColor, double fillOpacity,
            FactionPaletteChoice outerColor, double outerOpacity, double outerWidth,
            FactionPaletteChoice innerColor, double innerOpacity, double innerWidth) {
    }

    // A bloc ready to draw: its flattened fill polygon and its national-border and
    // interior-seam edge runs (GL_LINES segments), plus the resolved fill/outer/
    // inner colors (null for a "No color" choice, which skips that element) with
    // their opacities and the two border widths. All per bloc, since colors resolve
    // against each bloc's palette and widths differ by category.
    private record StyledBloc(float[] fill, float[] boundaryEdges, float[] interiorEdges,
            Color fillColor, Color outerColor, Color innerColor,
            float fillAlpha, float outerAlpha, float innerAlpha,
            float outerWidth, float innerWidth) {
    }
}
