package kmu.politicalmap.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.terrain.BaseTerrain;

import kmlib.opengl.GlColor;

import kmu.politicalmap.domain.PoliticalMapBorderGeometry;

import org.apache.log4j.Logger;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;

/**
 * Terrain plugin that paints the political map's province borders on the
 * sector (M) map.
 *
 * <p>Prototype scope (feature 022, first experiment): the border geometry
 * (one Voronoi cell per star system, edges offset into uniform channels) is
 * built by {@link PoliticalMapBorderGeometry}; this plugin only draws it, as
 * yellow line segments, always on. There is deliberately no faction coloring,
 * fill, toggle, or sidebar yet - the only goal is to confirm that carving the
 * sector into provinces looks right on the map.
 *
 * <p>Terrain is the surface because the sector map renders terrain through
 * {@code renderOnMap} - the same hook the vanilla nebulae draw with. A custom
 * campaign entity has no map-render hook, so its {@code render} never reaches
 * the map; only the live current-location view calls it. The below-UI
 * {@code renderOnMap} pass (rather than {@code renderOnMapAbove}) keeps the
 * borders beneath system and constellation names, matching their role as a
 * quiet background layer.
 *
 * <p>System positions in hyperspace are fixed for the life of a save, so the
 * geometry is built once and cached; {@code renderOnMap} only emits the
 * cached border segments.
 */
public class PoliticalMapTerrainPlugin extends BaseTerrain {
    // Yellow, the agreed prototype outline color.
    private static final Color OUTLINE_COLOR = Color.YELLOW;
    private static final float OUTLINE_LINE_WIDTH = 2f;

    // Map rendering ignores this (the map calls the map hooks regardless), but
    // BaseTerrain requires the override; large so the terrain is never treated
    // as a tiny point elsewhere.
    private static final float RENDER_RANGE = 1_000_000f;

    private static final Logger LOG = Global.getLogger(PoliticalMapTerrainPlugin.class);

    // Cached border segments as a flat [x1, y1, x2, y2, ...] run in hyperspace
    // world coordinates, drawn as GL_LINES. Null until built on first render.
    private float[] borderSegments;

    // Diagnostic: ensures the first map render logs exactly once.
    private boolean hasLoggedFirstRender;

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
        float[] segments = getOrBuildBorderSegments();
        // One-shot diagnostic for the no-draw investigation. Guarded on
        // isDebugEnabled so the once-flag only trips when the line actually
        // emits. Set KMU log verbosity to DEBUG in LunaLib to see it.
        if (!hasLoggedFirstRender && LOG.isDebugEnabled()) {
            hasLoggedFirstRender = true;
            String sample = segments.length >= 2 ? segments[0] + "," + segments[1] : "none";
            LOG.debug("Political map overlay renderOnMap fired: segments="
                    + (segments.length / 4) + " factor=" + factor + " alphaMult=" + alphaMult
                    + " firstVertex=" + sample);
        }
        if (segments.length == 0) {
            return;
        }

        // Map space: a world coordinate maps to (world * factor). The map
        // widget has already applied the map's pan/centering to the GL matrix,
        // so only the scale is applied here. Drawn in the below-UI map pass so
        // system and constellation names stay on top.
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_HINT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        // Anti-alias the borders so they read as soft lines, not jagged edges.
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);
        GL11.glLineWidth(OUTLINE_LINE_WIDTH);
        GlColor.set(OUTLINE_COLOR, alphaMult);

        // Each consecutive pair of vertices is one border segment.
        GL11.glBegin(GL11.GL_LINES);
        for (int i = 0; i < segments.length; i += 2) {
            GL11.glVertex2f(segments[i] * factor, segments[i + 1] * factor);
        }
        GL11.glEnd();

        GL11.glPopAttrib();
    }

    // Builds and caches the drawable border buffer on first use, delegating the
    // partition to the domain geometry and flattening its segments for GL.
    private float[] getOrBuildBorderSegments() {
        if (borderSegments != null) {
            return borderSegments;
        }

        List<double[]> segments =
                PoliticalMapBorderGeometry.buildBorderSegments(Global.getSector());

        float[] flat = new float[segments.size() * 4];
        int index = 0;
        for (double[] segment : segments) {
            flat[index++] = (float) segment[0];
            flat[index++] = (float) segment[1];
            flat[index++] = (float) segment[2];
            flat[index++] = (float) segment[3];
        }
        borderSegments = flat;
        return borderSegments;
    }
}
