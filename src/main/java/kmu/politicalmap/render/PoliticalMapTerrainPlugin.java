package kmu.politicalmap.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;

import kmlib.math.geometry.Polygons;
import kmlib.opengl.GlColor;
import kmlib.opengl.PolygonTessellator;
import kmlib.profiling.Timings;

import kmu.diagnostics.KmuProfiling;
import kmu.politicalmap.PoliticalMapRefresh;
import kmu.politicalmap.domain.CellShaper;
import kmu.politicalmap.domain.DecivilisedPresence;
import kmu.politicalmap.domain.DominantOwner;
import kmu.politicalmap.domain.PoliticalMapGeometryCache;
import kmu.politicalmap.domain.SectorPolitics;
import kmu.politicalmap.domain.ShapedCell;
import kmu.politicalmap.domain.geometry.SystemClusterBorders;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;
import kmu.settings.NeutralColorChoice;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Terrain plugin that paints the political map's faction territory on the
 * sector (M) map as merged HOI4-style clusters.
 *
 * <p>Adjacent star systems held by the same faction fuse into one solid region:
 * the faction's cells are traced into a single border ({@link SystemClusterBorders}),
 * inset to leave a uniform national-border channel against everything outside the
 * cluster and with its corners rounded so a fused-cell cluster reads as one smooth
 * frontier rather than a cell mosaic. That one rounded region is both filled -
 * tessellated into a triangle soup ({@link kmlib.opengl.PolygonTessellator}) so a
 * concave cluster, and one with an enclave, fills correctly - and stroked as the
 * national border, so the fill and the border are the exact same shape with no seam
 * of fill peeking past the outline. The interior seams (the edges between member
 * cells) are stroked as faint per-cell lines over the fill, so the province lines
 * inside a cluster read beneath the border. Factionless cells (decivilised,
 * uninhabited) do not fuse and carry no fill; each keeps a single per-cell inset
 * outline.
 *
 * <p>Every element is player-styled per category under the LunaLib "Visuals
 * customisation" tab, read via {@link KmuLunaSettings}. The two owned categories -
 * core factions and independent space - each fuse into clusters and get a fill, an
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
 * the two into the merged-cluster geometry, and this plugin only flattens and draws
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
 * cells are built once and cached. The drawables (which clusters are filled, in what
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

    // The empty vertex run shared by every draw element a cluster omits (an owned
    // cell's per-cell fill and outline, a faction with no fill), so an omitted
    // element draws nothing without allocating a fresh empty array each time.
    private static final float[] NO_VERTICES = new float[0];

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
    // the same StyledCell: its flattened fill/border/seam runs and each element's
    // color, opacity, and width. A factionless category resolves both its palette
    // slots to the shared neutral color and carries only an outline.
    // Per-cell draw records keyed by system id. For an owned cell this now carries
    // only its interior seams (the fill and national border are per-cluster, in
    // factionTerritories); for a factionless cell it still carries the single
    // per-cell inset outline, since factionless cells do not fuse into clusters. Keyed
    // rather than a flat list so the incremental refresh can replace or drop just
    // the cells around an ownership change without rebuilding the rest.
    private transient Map<String, StyledCell> styledCellBySystemId;
    // The fill and national border of each owned faction's cluster(s): the rounded
    // region tessellated into fill triangles and flattened into border loops, so both
    // are the same shape. Kept apart from the per-cell records because a cluster is
    // traced across all of a faction's cells, not one cell at a time. Keyed by faction
    // id
    // so the incremental refresh rebuilds only the two factions an ownership change
    // touched. Transient for the same reasons as the other draw lists: derived,
    // record-typed, kept out of the save.
    private transient Map<String, FactionTerritory> factionTerritoryByFactionId;
    private transient Color neutralColor;
    // The last resolved owners, decivilised set, and per-category styles, retained
    // from the last full rebuild so the incremental refresh can re-derive one
    // system's owner and re-shape a handful of cells against the same inputs. A
    // settings change bumps the content revision and forces a full rebuild, so
    // these never go stale under an incremental update. Transient: derived state,
    // kept out of the save.
    private transient Map<String, DominantOwner> ownerBySystemId;
    private transient Set<String> decivilisedSystemIds;
    private transient MapStyle factionStyle;
    private transient MapStyle independentStyle;
    private transient MapStyle decivilisedStyle;
    private transient MapStyle uninhabitedStyle;
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
        if (styledCellBySystemId.isEmpty() && factionTerritoryByFactionId.isEmpty()) {
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

    // Fills each owned faction's cluster(s) with the faction's resolved fill color at
    // its opacity. The fill is the cluster's rounded region pre-tessellated into a
    // triangle soup, so a concave cluster (or one with an enclave) fills correctly and
    // exactly matches the stroked border. A null fill color is a "No color" choice,
    // so that faction is left unfilled; factionless cells carry no fill at all.
    private void drawFills(float factor, float alphaMult) {
        for (var territory : factionTerritoryByFactionId.values()) {
            if (territory.fillColor() == null) {
                continue;
            }
            GlColor.set(territory.fillColor(), alphaMult * territory.fillAlpha());
            drawVertexRun(GL11.GL_TRIANGLES, territory.fillTriangles(), factor);
        }
    }

    // Strokes the interior province seams first, then the factionless outlines, then
    // the smoothed national borders over them, so a cluster's frontier dominates its
    // internal province lines where they meet. Color, opacity, and line width are all
    // per element; a null color is a "No color" choice and skips it. An owned cluster's
    // national border is its border ring (in factionTerritories), so the per-cell
    // outline only carries factionless cells; an owned cell contributes only its
    // seams and a factionless cell only its outline.
    private void drawBorders(float factor, float alphaMult) {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        for (var cell : styledCellBySystemId.values()) {
            if (cell.innerColor() == null) {
                continue;
            }
            GL11.glLineWidth(cell.innerWidth());
            GlColor.set(cell.innerColor(), alphaMult * cell.innerAlpha());
            drawVertexRun(GL11.GL_LINES, cell.interiorEdges(), factor);
        }
        for (var cell : styledCellBySystemId.values()) {
            if (cell.outerColor() == null) {
                continue;
            }
            GL11.glLineWidth(cell.outerWidth());
            GlColor.set(cell.outerColor(), alphaMult * cell.outerAlpha());
            drawVertexRun(GL11.GL_LINES, cell.boundaryEdges(), factor);
        }
        // Each border ring is a closed rounded loop, so it strokes as one continuous
        // GL_LINE_LOOP rather than the disconnected GL_LINES the per-cell edges use.
        for (var territory : factionTerritoryByFactionId.values()) {
            if (territory.borderColor() == null) {
                continue;
            }
            GL11.glLineWidth(territory.borderWidth());
            GlColor.set(territory.borderColor(), alphaMult * territory.borderAlpha());
            for (var loop : territory.borderLoops()) {
                drawVertexRun(GL11.GL_LINE_LOOP, loop, factor);
            }
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

        // TODO: when only geometry changed (rebuiltCells), reshape just the cells
        // PoliticalMapGeometryCache rebuilt - the system that gained or lost access
        // and every cell it touches - rather than the full drawables rebuild below.
        // Have updateFromSector report its affected-cell set and drive a targeted
        // reshape from it, the geometry-side analogue of applyStalePoliticsUpdates.

        // styledCellBySystemId == null means the drawables have never been built
        // this session. The revision seeds (-1) force the first build for a freshly
        // constructed plugin, but a plugin restored from a save comes back with
        // its revision fields already advanced past -1 while the static counters
        // reset to 0 on load - so the seed trick can match and skip the build,
        // leaving the drawable map null for renderOnMap to dereference. The null
        // check forces the build regardless of how the counters line up.
        var contentRevision = computeContentRevision();
        if (rebuiltCells || styledCellBySystemId == null
                || contentRevision != lastContentRevision) {
            var drawablesStart = System.nanoTime();
            rebuildDrawables();
            lastContentRevision = contentRevision;
            // A full rebuild re-derives every system, so any pending per-system
            // staleness is already reflected - drain and discard it rather than
            // re-processing the same systems immediately after.
            PoliticalMapRefresh.drainStalePoliticsSystemIds();
            // Result trace: the counts the render will actually paint and the
            // whole-rebuild time, so a wrong or empty render can be confirmed
            // against what was built and how long it cost.
            LOG.debug("Political map drawables rebuilt; contentRevision=" + contentRevision
                    + " styledCells=" + styledCellBySystemId.size()
                    + " geometryRebuilt=" + rebuiltCells
                    + " took=" + Timings.formatMillis(System.nanoTime() - drawablesStart));
            return;
        }

        // No full rebuild this frame: fold in any per-system ownership changes a
        // colony resize marked, re-deriving and re-shaping only those systems and
        // their neighbours over the standing drawables.
        applyStalePoliticsUpdates();
    }

    // Guards the render path after a failed first build: a rebuild that threw
    // before completing can leave the draw lists null, which renderOnMap would
    // dereference. Empty lists make the render a harmless no-op until a later
    // frame's retry succeeds.
    private void ensureDrawablesNonNull() {
        if (styledCellBySystemId == null) {
            styledCellBySystemId = new LinkedHashMap<>();
            factionTerritoryByFactionId = new LinkedHashMap<>();
            neutralColor = Color.GRAY;
        }
    }

    // The drawables-staleness token: a settings change restyles every cell over the
    // fixed geometry, so a settings-generation bump forces a full drawables rebuild.
    // Ownership changes no longer feed this - a discovered market or a resized colony
    // marks just its system stale now - so settings is the one whole-map restyle left.
    private static int computeContentRevision() {
        return KmuLunaSettings.getSettingsGeneration();
    }

    // Brings the geometry cache in line with the reachable systems, rebuilding
    // only the cells affected by an access change.
    private void rebuildGeometry() {
        KmuProfiling.getProfiler().measure("politicalMap.updateGeometry",
                () -> geometryCache.updateFromSector(Global.getSector()));
    }

    // Shapes the cached raw cells into merged clusters and partitions them into filled
    // (owned) and outline-only (decivilised/uninhabited) draw lists, baking in each
    // cell's colors, opacities, and widths resolved from the current settings, then
    // flattens each to GL-ready vertex runs. Reads the settings once per category,
    // not per cell.
    private void rebuildDrawables() {
        var profiler = KmuProfiling.getProfiler();
        profiler.measure("politicalMap.rebuildDrawables", () -> {
            var sector = Global.getSector();
            // One style bundle per category, retained so the incremental refresh
            // re-shapes cells against the same styles this pass used. A factionless
            // style points its fill and inner seam at "No color", drawing only an
            // outline.
            factionStyle = readFactionStyle();
            independentStyle = readIndependentStyle();
            decivilisedStyle = readDecivilisedStyle();
            uninhabitedStyle = readUninhabitedStyle();

            // The politics scan walks the whole economy - the priciest content
            // step - so it is profiled and timed on its own, and the owner count
            // logged independent of the profiler's accumulated view.
            var politicsStart = System.nanoTime();
            ownerBySystemId = profiler.measure("politicalMap.resolvePolitics",
                    () -> SectorPolitics.resolveDominantOwnerBySystemId(sector));
            LOG.debug("Political map politics resolved; ownedSystems=" + ownerBySystemId.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - politicsStart));

            var decivilisedStart = System.nanoTime();
            decivilisedSystemIds = profiler.measure("politicalMap.findDecivilised",
                    () -> DecivilisedPresence.findRevealedDecivilisedSystemIds(sector));
            LOG.debug("Political map decivilised scan; systems=" + decivilisedSystemIds.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - decivilisedStart));

            neutralColor = SectorPolitics.resolveNeutralColor(sector);

            // Shape the raw cells into merged clusters once, ownership-aware. Cells
            // consumed by the inset (fewer than three vertices left) drop out -
            // nothing to fill or stroke.
            var shapeStart = System.nanoTime();
            var shapedCells = profiler.measure("politicalMap.shapeCells",
                    () -> CellShaper.shapeCells(geometryCache.getCellEdgesBySystemId(),
                            ownerBySystemId, PoliticalMapStyle.BORDER_INSET_DISTANCE));
            styledCellBySystemId = new LinkedHashMap<>();
            for (var entry : shapedCells.entrySet()) {
                var styled = buildStyledCellForSystem(entry.getKey(), entry.getValue());
                if (styled != null) {
                    styledCellBySystemId.put(entry.getKey(), styled);
                }
            }

            // Each owned faction's territory: one rounded region per cluster (traced
            // across all its cells so a multi-system cluster reads as one frontier),
            // tessellated for the fill and flattened for the border - the same shape
            // for both. Built off the same raw cells and owners the seams used, and
            // profiled on its own since chaining, rounding, and tessellating every
            // faction's outline is comparable in cost to shaping the cells.
            factionTerritoryByFactionId = profiler.measure(
                    "politicalMap.buildFactionTerritories", this::buildAllFactionTerritories);

            LOG.debug("Political map cells shaped; shaped=" + shapedCells.size()
                    + " styledCells=" + styledCellBySystemId.size()
                    + " factionTerritories=" + factionTerritoryByFactionId.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - shapeStart));
        });
    }

    // Folds any per-system ownership changes a colony resize marked into the
    // standing drawables, re-deriving only those systems' owners and re-shaping only
    // them and their neighbours. A resize that does not flip a system's owner costs
    // just the re-derivation; a flip re-shapes the ring of affected cells and
    // rebuilds the two factions' territories (the old owner and the new one).
    private void applyStalePoliticsUpdates() {
        var staleSystemIds = PoliticalMapRefresh.drainStalePoliticsSystemIds();
        if (staleSystemIds.isEmpty()) {
            return;
        }
        KmuProfiling.getProfiler().measure("politicalMap.applyPoliticsUpdates", () -> {
            var sector = Global.getSector();
            var systemById = indexSystemsById(sector);
            var cellsToReshape = new LinkedHashSet<String>();
            var affectedFactionIds = new LinkedHashSet<String>();
            // Re-derive every marked system first, so re-shaping below reads a fully
            // updated owner map even when two adjacent systems flipped in one batch.
            for (var systemId : staleSystemIds) {
                rederiveSystemOwner(sector, systemById, systemId, cellsToReshape,
                        affectedFactionIds);
            }
            if (affectedFactionIds.isEmpty()) {
                // Every marked system resized without flipping its owner - nothing
                // to redraw. Logged so an un-updated colour can be confirmed a no-op
                // flip rather than a missed event.
                LOG.debug("Political map politics update: no owner changed; stale="
                        + staleSystemIds.size());
                return;
            }
            for (var cellId : cellsToReshape) {
                reshapeCellInPlace(cellId);
            }
            // Only the old and new owners' territories can have changed shape; every
            // other faction's rings trace unchanged cells, so they are left as-is.
            var systemsByFaction = groupOwnedSystemsByFaction();
            for (var factionId : affectedFactionIds) {
                rebuildFactionTerritoryInPlace(factionId, systemsByFaction.get(factionId));
            }
            LOG.debug("Political map politics updated incrementally; stale="
                    + staleSystemIds.size() + " reshapedCells=" + cellsToReshape.size()
                    + " rebuiltFactions=" + affectedFactionIds.size());
        });
    }

    // Re-derives one system's owner and, when it actually changed, records the cells
    // to re-shape (the system and its neighbours, whose edge against it flips between
    // a same-faction seam and a national border) and the factions whose territory
    // must rebuild (the old and new owner). A system with no cell seeds no drawing,
    // so it is skipped: a resize changes ownership over existing cells, never map
    // membership.
    private void rederiveSystemOwner(SectorAPI sector, Map<String, StarSystemAPI> systemById,
            String systemId, Set<String> cellsToReshape, Set<String> affectedFactionIds) {
        if (!geometryCache.getCellEdgesBySystemId().containsKey(systemId)) {
            return;
        }
        var newOwner = SectorPolitics.resolveDominantOwner(sector, systemById.get(systemId));
        var oldOwner = ownerBySystemId.get(systemId);
        // DominantOwner is a record, so equality covers the faction and its palette:
        // a resize that leaves the same winner leaves the drawing identical.
        if (Objects.equals(oldOwner, newOwner)) {
            return;
        }
        if (newOwner == null) {
            ownerBySystemId.remove(systemId);
        } else {
            ownerBySystemId.put(systemId, newOwner);
        }
        if (oldOwner != null) {
            affectedFactionIds.add(oldOwner.factionId());
        }
        if (newOwner != null) {
            affectedFactionIds.add(newOwner.factionId());
        }
        cellsToReshape.add(systemId);
        cellsToReshape.addAll(neighbourSystemIdsOf(systemId));
    }

    // Indexes the sector's systems by id, so a stale system id resolves to its
    // StarSystemAPI directly rather than through SectorAPI.getStarSystem, which
    // matches by name and would miss an id that differs from the display name.
    private static Map<String, StarSystemAPI> indexSystemsById(SectorAPI sector) {
        var systemById = new HashMap<String, StarSystemAPI>();
        for (var system : sector.getStarSystems()) {
            systemById.put(system.getId(), system);
        }
        return systemById;
    }

    // The systems whose cell borders this one, read from the adjacency graph. When
    // this system's owner flips, each neighbour's shared edge flips between a
    // same-faction seam and a national border, so every neighbour re-shapes too.
    private Set<String> neighbourSystemIdsOf(String systemId) {
        var neighbours = new LinkedHashSet<String>();
        var edges = geometryCache.getCellEdgesBySystemId().get(systemId);
        if (edges != null) {
            for (var edge : edges) {
                if (edge.neighbourSystemId() != null) {
                    neighbours.add(edge.neighbourSystemId());
                }
            }
        }
        return neighbours;
    }

    // Re-shapes one cell against the now-updated owners and replaces its draw record,
    // or drops it when the cell contributes nothing (inset-collapsed, or factionless
    // with a "No color" outline).
    private void reshapeCellInPlace(String systemId) {
        var edges = geometryCache.getCellEdgesBySystemId().get(systemId);
        if (edges == null) {
            styledCellBySystemId.remove(systemId);
            return;
        }
        var owner = ownerBySystemId.get(systemId);
        var ownerFactionId = owner == null ? null : owner.factionId();
        var shaped = CellShaper.shapeCell(edges, ownerFactionId, ownerBySystemId,
                PoliticalMapStyle.BORDER_INSET_DISTANCE);
        var styled = buildStyledCellForSystem(systemId, shaped);
        if (styled == null) {
            styledCellBySystemId.remove(systemId);
        } else {
            styledCellBySystemId.put(systemId, styled);
        }
    }

    // Rebuilds (or drops) one faction's territory entry from its current members. A
    // faction that lost its last member, or whose cluster no longer yields drawable
    // geometry, is removed so its fill and border stop drawing.
    private void rebuildFactionTerritoryInPlace(String factionId, List<String> memberSystemIds) {
        var territory = memberSystemIds == null || memberSystemIds.isEmpty()
                ? null
                : buildFactionTerritory(factionId, memberSystemIds);
        if (territory == null) {
            factionTerritoryByFactionId.remove(factionId);
        } else {
            factionTerritoryByFactionId.put(factionId, territory);
        }
    }

    // Builds one system's per-cell draw record from its shaped cell, or null when
    // the cell draws nothing: an inset-collapsed cell, or a factionless cell whose
    // outline is "No color". An owned cell keeps only its interior seams (its fill
    // and national border are per-cluster, in factionTerritories); a factionless cell
    // keeps its own inset fill and outline, since factionless cells do not fuse.
    // Shared by the full rebuild and the incremental re-shape so both classify a
    // cell identically.
    private StyledCell buildStyledCellForSystem(String systemId, ShapedCell shaped) {
        // A cell the border inset consumed or collapsed comes back with an empty
        // fill (CellShaper via Polygons.insetSelectedEdges guarantees
        // empty-or-drawable), so there is nothing to fill or stroke.
        if (shaped.fillPolygon().isEmpty()) {
            return null;
        }
        var owner = ownerBySystemId.get(systemId);
        if (owner != null) {
            // Independent space styles from its own bundle; every other owner is a
            // core faction. The fill and national border are per cluster from the
            // tessellated region, so an owned cell contributes only its interior seams
            // here, in its inner-seam color resolved against the owner's palette.
            var style = Factions.INDEPENDENT.equals(owner.factionId())
                    ? independentStyle
                    : factionStyle;
            return buildStyledCell(shaped, owner.primaryColor(), owner.secondaryColor(),
                    style, false);
        }
        // Factionless: decivilised or (otherwise) uninhabited. Its style fills
        // neither palette slot, so both resolve to the shared neutral color and only
        // its per-cell outline draws; drop it when that outline is "No color".
        var style = decivilisedSystemIds.contains(systemId)
                ? decivilisedStyle
                : uninhabitedStyle;
        if (style.outerColor() == FactionPaletteChoice.NONE) {
            return null;
        }
        return buildStyledCell(shaped, neutralColor, neutralColor, style, true);
    }

    // Builds every owned faction's territory keyed by faction id. Each faction is
    // independent - its cluster(s) trace only its own cells - so the incremental
    // refresh rebuilds one faction's entry without touching the rest.
    private Map<String, FactionTerritory> buildAllFactionTerritories() {
        var territories = new LinkedHashMap<String, FactionTerritory>();
        for (var faction : groupOwnedSystemsByFaction().entrySet()) {
            var territory = buildFactionTerritory(faction.getKey(), faction.getValue());
            if (territory != null) {
                territories.put(faction.getKey(), territory);
            }
        }
        return territories;
    }

    // Groups the currently owned systems by their faction id, so each faction's
    // cluster(s) are traced from its own members.
    private Map<String, List<String>> groupOwnedSystemsByFaction() {
        var systemsByFaction = new LinkedHashMap<String, List<String>>();
        for (var entry : ownerBySystemId.entrySet()) {
            systemsByFaction
                    .computeIfAbsent(entry.getValue().factionId(), factionId -> new ArrayList<>())
                    .add(entry.getKey());
        }
        return systemsByFaction;
    }

    // Builds one faction's fill and national border from its rounded border rings,
    // traced across all the systems it holds so a multi-system cluster reads as one
    // continuous frontier. The rings are tessellated into fill triangles and
    // flattened into border loops - the same geometry - so the fill exactly matches
    // the stroked border. Disjoint clusters and enclaves each come back as their own
    // ring, so rebuilding a faction from its current members alone re-splits or
    // re-merges its clusters when the incremental refresh gains or loses one. Returns
    // null when the faction has no fill and no border color, or no borderable
    // geometry.
    private FactionTerritory buildFactionTerritory(String factionId, List<String> memberSystemIds) {
        var style = Factions.INDEPENDENT.equals(factionId) ? independentStyle : factionStyle;
        // Every system of a faction shares its palette, so any member resolves the
        // same fill and border colors.
        var palette = ownerBySystemId.get(memberSystemIds.get(0));
        var fillColor = pickPaletteColor(
                style.fillColor(), palette.primaryColor(), palette.secondaryColor());
        var borderColor = pickPaletteColor(
                style.outerColor(), palette.primaryColor(), palette.secondaryColor());
        if (fillColor == null && borderColor == null) {
            return null;
        }
        var insetRings = SystemClusterBorders.traceBorderRings(memberSystemIds,
                geometryCache.getCellEdgesBySystemId(), ownerBySystemId,
                PoliticalMapStyle.BORDER_INSET_DISTANCE,
                KmuLunaSettings.getPoliticalMapBorderWeldTolerance(),
                KmuLunaSettings.getPoliticalMapBorderMiterLimit());
        if (insetRings.isEmpty()) {
            return null;
        }
        // Resolve the inset rings to their clean outer envelope first (positive
        // winding drops any neck self-crossing), THEN round - rounding before the
        // resolve would have its arc clipped off at the crossing and left a sharp
        // corner. Fill and border are the same rounded region (triangulated vs its
        // boundary loops), so they match exactly.
        var roundedLoops = roundBorderLoops(
                PolygonTessellator.tessellateToBoundaryLoops(insetRings));
        var fillTriangles = fillColor == null
                ? NO_VERTICES
                : PolygonTessellator.tessellateToTriangles(roundedLoops);
        var borderLoops = new ArrayList<float[]>();
        if (borderColor != null) {
            for (var loop : PolygonTessellator.tessellateToBoundaryLoops(roundedLoops)) {
                borderLoops.add(flattenVertices(loop));
            }
        }
        return new FactionTerritory(fillTriangles, fillColor,
                (float) style.fillOpacity(), borderLoops, borderColor,
                (float) style.outerOpacity(), (float) style.outerWidth());
    }

    // Rounds each clean border loop with the current corner settings, so a corner's
    // arc is applied to the resolved envelope rather than a self-crossing inset (a
    // crossing would clip the arc back to a sharp point). Each loop is first despiked
    // - needle protrusions and inward cusps too thin for the rounding to sand off
    // (its step-back clamps to their tiny edges) are spliced out, so the arc runs on
    // clean geometry. Reused for every faction's rebuild.
    private static List<List<double[]>> roundBorderLoops(List<List<double[]>> cleanLoops) {
        var spikeHeight = KmuLunaSettings.getPoliticalMapBorderSpikeHeight();
        var spikeAngle = KmuLunaSettings.getPoliticalMapBorderSpikeAngleRadians();
        var radius = KmuLunaSettings.getPoliticalMapBorderCornerRadius();
        var segments = KmuLunaSettings.getPoliticalMapBorderCornerSegments();
        var chamfer = KmuLunaSettings.getPoliticalMapBorderChamferAngleRadians();
        var rounded = new ArrayList<List<double[]>>(cleanLoops.size());
        for (var loop : cleanLoops) {
            var despiked = Polygons.removeSpikes(loop, spikeHeight, spikeAngle);
            rounded.add(Polygons.roundCorners(despiked, radius, segments, chamfer));
        }
        return rounded;
    }

    // Builds one cell's per-cell draw record: its interior seams always, plus - only
    // when {@code perCellFillAndBorder} - a fill and an outer outline. An owned cell
    // passes false: its fill and national border come per cluster from the tessellated
    // region, so it contributes only its seams here. A factionless cell passes true and
    // the neutral color for both palette shades: it does not fuse into a cluster, so it
    // keeps its own inset fill and outline. Its outline is rounded with the same
    // corner settings the cluster borders use, so a lone dead system reads as smoothly
    // as a cluster rather than a sharp Voronoi cell. Each color resolves against the two
    // palette shades, null for a "No color" choice, so the draw pass skips it.
    private static StyledCell buildStyledCell(ShapedCell shaped, Color primaryColor,
            Color secondaryColor, MapStyle style, boolean perCellFillAndBorder) {
        return new StyledCell(
                perCellFillAndBorder ? flattenVertices(shaped.fillPolygon()) : NO_VERTICES,
                perCellFillAndBorder ? flattenClosedLoopAsSegments(roundCellOutline(shaped))
                        : NO_VERTICES,
                flattenEdgesOfClass(shaped, false),
                perCellFillAndBorder
                        ? pickPaletteColor(style.fillColor(), primaryColor, secondaryColor)
                        : null,
                perCellFillAndBorder
                        ? pickPaletteColor(style.outerColor(), primaryColor, secondaryColor)
                        : null,
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
    // build loop applies them per cluster without eight lookups each.
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

    // Flattens the edges of a shaped cell of one class into a GL_LINES vertex run
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

    // Rounds a factionless cell's inset outline with the same corner settings the
    // cluster borders use, so its border reads consistently. The cell is a single
    // convex inset polygon (all its edges are national border), so it needs no
    // chaining or envelope resolve - only the corner rounding.
    private static List<double[]> roundCellOutline(ShapedCell shaped) {
        return Polygons.roundCorners(shaped.fillPolygon(),
                KmuLunaSettings.getPoliticalMapBorderCornerRadius(),
                KmuLunaSettings.getPoliticalMapBorderCornerSegments(),
                KmuLunaSettings.getPoliticalMapBorderChamferAngleRadians());
    }

    // Flattens a closed ring into GL_LINES segment pairs, one per edge including the
    // wrap from the last vertex back to the first, so a rounded outline strokes as a
    // closed loop under the same GL_LINES pass the per-cell edges use.
    private static float[] flattenClosedLoopAsSegments(List<double[]> ring) {
        var count = ring.size();
        var flat = new float[count * FLOATS_PER_EDGE];
        var index = 0;
        for (var i = 0; i < count; i++) {
            var start = ring.get(i);
            var end = ring.get((i + 1) % count);
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
        LOG.debug("Political map render renderOnMap fired: styledCells="
                + styledCellBySystemId.size() + " factor=" + factor + " alphaMult=" + alphaMult);
    }

    // One category's full political-map style, read once per rebuild and applied to
    // every cluster of that category: the fill, outer-border, and inner-seam color
    // choices with their opacities, plus the two border widths. The colors are
    // choices (resolved against each cluster's two palette shades), not concrete
    // colors, since one bundle serves many clusters. A factionless category sets fill
    // and inner to NONE so only its outline draws.
    private record MapStyle(
            FactionPaletteChoice fillColor, double fillOpacity,
            FactionPaletteChoice outerColor, double outerOpacity, double outerWidth,
            FactionPaletteChoice innerColor, double innerOpacity, double innerWidth) {
    }

    // A cell ready to draw: its flattened fill polygon and its national-border and
    // interior-seam edge runs (GL_LINES segments), plus the resolved fill/outer/
    // inner colors (null for a "No color" choice, which skips that element) with
    // their opacities and the two border widths. All per cell, since colors resolve
    // against each cell's palette and widths differ by category.
    private record StyledCell(float[] fill, float[] boundaryEdges, float[] interiorEdges,
            Color fillColor, Color outerColor, Color innerColor,
            float fillAlpha, float outerAlpha, float innerAlpha,
            float outerWidth, float innerWidth) {
    }

    // One owned faction's fill and national border, both the same GLU-resolved
    // region of the border rings so they match exactly. {@code fillTriangles} is that
    // region as a GL_TRIANGLES soup ([x, y, x, y, ...], empty when the fill is "No
    // color"); {@code borderLoops} is its boundary as GL_LINE_LOOP runs (empty when
    // the border is "No color") - one loop per disjoint cluster and per enclave, with
    // any narrow-neck self-crossing resolved away. Colors are null for a "No color"
    // choice, which skips that element.
    private record FactionTerritory(float[] fillTriangles, Color fillColor, float fillAlpha,
            List<float[]> borderLoops, Color borderColor, float borderAlpha, float borderWidth) {
    }
}
