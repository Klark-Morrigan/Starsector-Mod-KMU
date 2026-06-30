package kmu.politicalmap.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;

import kmlib.opengl.GlColor;

import kmu.diagnostics.KmuProfiling;
import kmu.politicalmap.PoliticalMapRefresh;
import kmu.politicalmap.domain.DecivilisedPresence;
import kmu.politicalmap.domain.PoliticalMapGeometryCache;
import kmu.politicalmap.domain.SectorPolitics;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Terrain plugin that paints the political map's faction territory on the
 * sector (M) map.
 *
 * <p>Prototype scope (feature 022, ownership experiment): each faction-held
 * star system's cell is filled and outlined in the owner's bright UI color -
 * the fill at a partial alpha so the region reads as territory, the outline
 * opaque so borders stay crisp. An independent-dominated system draws at half
 * alpha - both its fill and its outline - so it reads as loosely held space
 * rather than core faction territory. Uninhabited systems are unfilled and
 * outlined
 * faintly in the neutral color (the same color decivilised markers use), and
 * only when the player opts in - {@code kmu_politicalMapShowUninhabited}, off by
 * default. A decivilised system - inhabited but factionless - always draws that
 * neutral outline regardless of the opt-in, since a known dead colony is
 * presence, not empty space. Both fill and outline are the cell inset into a
 * single closed convex polygon, so a province reads as one clean shape, not a
 * ring of line segments.
 * The geometry comes from {@link PoliticalMapBorderGeometry} and the ownership
 * colors from {@link SectorPolitics}; this plugin only draws them, always on.
 * There is deliberately no overlay toggle, sidebar, presence tiers, or blip
 * stack yet - the goal is to confirm that faction territory reads correctly.
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
 * filled, and in what color) are rebuilt only when KMU's LunaLib settings
 * change, detected off LunaLib's change event via
 * {@link KmuLunaSettings#getSettingsGeneration()} - so toggling the
 * uninhabited-systems setting takes effect live, and the per-frame path is a
 * single int compare, not a settings lookup. Ownership colors are sampled at
 * the same point - live re-sampling on ownership change (raid, colonisation) is
 * a later step and is intentionally not done per frame, which would scan the
 * whole economy every frame.
 */
public class PoliticalMapTerrainPlugin extends BaseTerrain {
    private static final float OUTLINE_LINE_WIDTH = 2f;

    // Fill alpha for an owned province: high enough that the faction color is
    // easy to read at a glance, low enough that the opaque outline still stands
    // out and neighbouring fills do not muddy where they meet.
    private static final float FILL_ALPHA = 0.4f;

    // Per-owner alpha scale applied to both fill and outline: a cell draws at
    // full strength or halved to a quieter presence that still reads as owned.
    // Halved is defined as half of full so the two stay in lockstep.
    private static final float FULL_ALPHA_MULTIPLIER = 1f;
    private static final float HALVED_ALPHA_MULTIPLIER = FULL_ALPHA_MULTIPLIER / 2f;

    // Uninhabited outlines are a faint hint, not territory, so they draw at a
    // low alpha - the neutral color is otherwise bright enough to compete with
    // the faction borders.
    private static final float NEUTRAL_BORDER_ALPHA = 0.3f;

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

    // Cell outlines keyed by system id, updated incrementally as systems gain or
    // lose access - only the cells near a change are rebuilt, not the whole map.
    private final PoliticalMapGeometryCache geometryCache = new PoliticalMapGeometryCache();

    // Drawables derived from the outlines. Owned cells carry their owner's color
    // (used for fill and outline); neutral cells carry just an outline drawn in
    // the shared neutral color.
    private List<float[]> ownedOutlines;
    private List<Color> ownedColors;
    // Per-owned-cell alpha scale, parallel to ownedOutlines: 1 for a faction
    // province, halved for an independent-held system. Applied to both the fill
    // and the outline so the whole cell reads as loosely held.
    private List<Float> ownedAlphaMultipliers;
    private List<float[]> neutralOutlines;
    private Color neutralColor;
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
        if (ownedOutlines.isEmpty() && neutralOutlines.isEmpty()) {
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
        // negligible and the cache checks above are deliberately outside.
        KmuProfiling.getProfiler().measure("politicalMap.render", () -> {
            drawOwnedFills(factor, alphaMult);
            drawOutlines(factor, alphaMult);
        });

        GL11.glPopAttrib();
    }

    // Fills each owned province with its owner's color at the fill alpha, scaled
    // by that cell's multiplier (halved for independent-held space). The outline
    // is convex, so a triangle fan from the first vertex tessellates it correctly.
    private void drawOwnedFills(float factor, float alphaMult) {
        for (var i = 0; i < ownedOutlines.size(); i++) {
            GlColor.set(ownedColors.get(i), alphaMult * FILL_ALPHA * ownedAlphaMultipliers.get(i));
            drawPolygon(GL11.GL_TRIANGLE_FAN, ownedOutlines.get(i), factor);
        }
    }

    // Draws each province outline as a closed loop on top of the fills: owned
    // outlines in the owner's color, opaque but for independent-held cells which
    // halve like their fill; neutral outlines faint.
    private void drawOutlines(float factor, float alphaMult) {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(OUTLINE_LINE_WIDTH);

        for (var i = 0; i < ownedOutlines.size(); i++) {
            GlColor.set(ownedColors.get(i), alphaMult * ownedAlphaMultipliers.get(i));
            drawPolygon(GL11.GL_LINE_LOOP, ownedOutlines.get(i), factor);
        }
        for (var outline : neutralOutlines) {
            GlColor.set(neutralColor, alphaMult * NEUTRAL_BORDER_ALPHA);
            drawPolygon(GL11.GL_LINE_LOOP, outline, factor);
        }
    }

    // Emits one flat [x, y, x, y, ...] vertex run under the given GL primitive,
    // scaling each world coordinate into map space.
    private static void drawPolygon(int mode, float[] vertices, float factor) {
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

        // ownedOutlines == null means the drawables have never been built this
        // session. The revision seeds (-1) force the first build for a freshly
        // constructed plugin, but a plugin restored from a save comes back with
        // its revision fields already advanced past -1 while the static counters
        // reset to 0 on load - so the seed trick can match and skip the build,
        // leaving the drawable lists null for renderOnMap to dereference. The
        // null check forces the build regardless of how the counters line up.
        var contentRevision = computeContentRevision();
        if (rebuiltCells || ownedOutlines == null || contentRevision != lastContentRevision) {
            rebuildDrawables();
            lastContentRevision = contentRevision;
            // Result trace: the cell counts the overlay will actually paint, so a
            // wrong or empty render can be confirmed against what was built.
            LOG.debug("Political map drawables rebuilt; contentRevision=" + contentRevision
                    + " ownedCells=" + ownedOutlines.size()
                    + " neutralCells=" + neutralOutlines.size()
                    + " geometryRebuilt=" + rebuiltCells);
        }
    }

    // Guards the render path after a failed first build: a rebuild that threw
    // before completing can leave the draw lists null, which renderOnMap would
    // dereference. Empty lists make the render a harmless no-op until a later
    // frame's retry succeeds.
    private void ensureDrawablesNonNull() {
        if (ownedOutlines == null) {
            ownedOutlines = new ArrayList<>();
            ownedColors = new ArrayList<>();
            ownedAlphaMultipliers = new ArrayList<>();
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

    // Partitions the cached outlines into owned (filled + colored) and neutral
    // (faint outline) draw lists, reading the current ownership and uninhabited
    // setting. Flattens each outline to a GL-ready vertex run here.
    private void rebuildDrawables() {
        KmuProfiling.getProfiler().measure("politicalMap.rebuildDrawables", () -> {
            var isShowingUninhabited = KmuLunaSettings.isShowUninhabitedSystemsEnabled();
            ownedOutlines = new ArrayList<>();
            ownedColors = new ArrayList<>();
            ownedAlphaMultipliers = new ArrayList<>();
            neutralOutlines = new ArrayList<>();

            var ownerBySystemId =
                    SectorPolitics.resolveDominantOwnerBySystemId(Global.getSector());
            var decivilisedSystemIds =
                    DecivilisedPresence.findRevealedDecivilisedSystemIds(Global.getSector());
            neutralColor = SectorPolitics.resolveNeutralColor(Global.getSector());

            for (var entry
                    : geometryCache.getOutlineBySystemId().entrySet()) {
                var outline = entry.getValue();
                if (outline.isEmpty()) {
                    continue;
                }
                var flat = flattenVertices(outline);
                var owner = ownerBySystemId.get(entry.getKey());
                if (owner != null) {
                    ownedOutlines.add(flat);
                    ownedColors.add(owner.color());
                    ownedAlphaMultipliers.add(resolveAlphaMultiplier(owner.factionId()));
                } else if (decivilisedSystemIds.contains(entry.getKey()) || isShowingUninhabited) {
                    // A revealed decivilised system is inhabited but unaffiliated:
                    // it always draws a neutral outline, regardless of the
                    // uninhabited toggle, which governs only genuinely empty space.
                    neutralOutlines.add(flat);
                }
            }
        });
    }

    // Picks an owned cell's alpha scale from its dominant faction: independent
    // space draws at half strength so it reads as loosely held rather than a
    // faction's core province. Every other owner draws at full. The same scale
    // dims both the fill and the outline.
    static float resolveAlphaMultiplier(String dominantFactionId) {
        return Factions.INDEPENDENT.equals(dominantFactionId)
                ? HALVED_ALPHA_MULTIPLIER
                : FULL_ALPHA_MULTIPLIER;
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
        LOG.debug("Political map overlay renderOnMap fired: ownedCells="
                + ownedOutlines.size() + " neutralCells=" + neutralOutlines.size()
                + " factor=" + factor + " alphaMult=" + alphaMult);
    }
}
