package kmu.politicalmap.render;

import kmlib.opengl.GlColor;

import kmu.diagnostics.KmuProfiling;
import kmu.politicalmap.render.model.PoliticalMapDrawables;

import org.lwjgl.opengl.GL11;

/**
 * Paints the political map's pre-built draw lists on the sector (M) map: the faction
 * fills first, then the interior seams, factionless outlines, and national borders
 * over them, and last the debug cluster anchors when the dev toggle built any.
 *
 * <p>This is pure GL emission over an already-baked {@link PoliticalMapDrawables} - it
 * scales each world coordinate into map space and strokes/fills the flattened vertex
 * runs, with no knowledge of settings, caches, or how the runs were shaped. The map
 * widget has already applied the map's pan and centering to the GL matrix, so only the
 * scale is applied here.
 */
final class PoliticalMapRenderer {
    // The debug anchor dot's diameter in screen pixels (GL_POINTS sizes in pixels, so it
    // stays a constant dot at any zoom) and its axis line's width. Sized to read over the
    // fills and borders without swamping the systems they mark.
    private static final float ANCHOR_DOT_SIZE = 10f;
    private static final float ANCHOR_AXIS_WIDTH = 2f;

    // Emits only; never instantiated.
    private PoliticalMapRenderer() {
    }

    // Draws the whole overlay for one map frame. Drawn in the below-UI map pass so
    // system and constellation names stay on top; a state push/pop isolates the blend
    // and line settings from the rest of the map render. An empty overlay skips the
    // push entirely.
    static void renderOnMap(PoliticalMapDrawables drawables, float factor, float alphaMult) {
        // A fully faded-out overlay (alphaMult 0, at the ends of the map's fade) would
        // emit every run at zero effective alpha - all cost, nothing on screen - so the
        // whole GL pass is skipped, not just left to blend away.
        if (drawables.isEmpty() || alphaMult <= 0f) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_POINT_BIT
                | GL11.GL_HINT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Time only the per-frame GL emission; the surrounding state push/pop is
        // negligible. Broken into the two passes so the profiler shows which one costs,
        // but not logged - this runs every frame the map is open, so only the profiler's
        // accumulated view is affordable here, never a per-frame log line.
        var profiler = KmuProfiling.getProfiler();
        profiler.measure("politicalMap.render", () -> {
            profiler.measure("politicalMap.render.fills", () -> drawFills(drawables, factor, alphaMult));
            profiler.measure("politicalMap.render.borders",
                    () -> drawBorders(drawables, factor, alphaMult));
            profiler.measure("politicalMap.render.anchors",
                    () -> drawClusterAnchors(drawables, factor, alphaMult));
        });

        GL11.glPopAttrib();
    }

    // Fills each owned faction's cluster(s) with the faction's resolved fill color at
    // its opacity. The fill is the cluster's rounded region pre-tessellated into a
    // triangle soup, so a concave cluster (or one with an enclave) fills correctly and
    // exactly matches the stroked border. A hidden fill (ElementPaint.isHidden) is
    // skipped, its geometry kept to shape its neighbours but never emitted.
    private static void drawFills(PoliticalMapDrawables drawables, float factor, float alphaMult) {
        for (var territory : drawables.getFactionTerritoryByFactionId().values()) {
            var fill = territory.fill();
            if (fill.isHidden()) {
                continue;
            }
            GlColor.set(fill.color(), alphaMult * fill.alpha());
            MapGl.drawVertexRun(GL11.GL_TRIANGLES, territory.fillTriangles(), factor);
        }
    }

    // Strokes the interior province seams first, then the factionless outlines, then the
    // smoothed national borders over them, so a cluster's frontier dominates its
    // internal province lines where they meet. Color, opacity, and line width are all
    // per element, and a hidden element (ElementPaint.isHidden) is skipped - its geometry
    // stays baked to shape its neighbours, but nothing invisible is emitted. An owned
    // cluster's national border is its border ring (in factionTerritories), so the
    // per-cell outline only carries factionless cells; an owned cell contributes only its
    // seams and a factionless cell only its outline.
    private static void drawBorders(PoliticalMapDrawables drawables, float factor,
            float alphaMult) {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        for (var cell : drawables.getStyledCellBySystemId().values()) {
            var inner = cell.inner();
            if (inner.isHidden()) {
                continue;
            }
            GL11.glLineWidth(cell.innerWidth());
            GlColor.set(inner.color(), alphaMult * inner.alpha());
            MapGl.drawVertexRun(GL11.GL_LINES, cell.interiorEdges(), factor);
        }
        for (var cell : drawables.getStyledCellBySystemId().values()) {
            var outer = cell.outer();
            if (outer.isHidden()) {
                continue;
            }
            GL11.glLineWidth(cell.outerWidth());
            GlColor.set(outer.color(), alphaMult * outer.alpha());
            MapGl.drawVertexRun(GL11.GL_LINES, cell.boundaryEdges(), factor);
        }
        // Each border ring is a closed rounded loop, so it strokes as one continuous
        // GL_LINE_LOOP rather than the disconnected GL_LINES the per-cell edges use.
        for (var territory : drawables.getFactionTerritoryByFactionId().values()) {
            var border = territory.border();
            if (border.isHidden()) {
                continue;
            }
            GL11.glLineWidth(territory.borderWidth());
            GlColor.set(border.color(), alphaMult * border.alpha());
            for (var loop : territory.borderLoops()) {
                MapGl.drawVertexRun(GL11.GL_LINE_LOOP, loop, factor);
            }
        }
    }

    // Draws each cluster's debug label anchor over the fills and borders: a dot at the
    // centroid and a line down the long axis, both in the owning faction's bright shade.
    // Empty (nothing emitted) unless the dev toggle built the anchors, so the normal map
    // pays only an empty-list check. The dot is a fixed-pixel GL_POINTS mark; the axis is
    // one GL_LINES segment, collapsed to a point for a single-system cluster.
    private static void drawClusterAnchors(PoliticalMapDrawables drawables, float factor,
            float alphaMult) {
        var anchors = drawables.getClusterAnchors();
        if (anchors.isEmpty()) {
            return;
        }
        GL11.glPointSize(ANCHOR_DOT_SIZE);
        GL11.glLineWidth(ANCHOR_AXIS_WIDTH);
        for (var anchor : anchors) {
            GlColor.set(anchor.color(), alphaMult);
            GL11.glBegin(GL11.GL_POINTS);
            GL11.glVertex2f(anchor.centroidX() * factor, anchor.centroidY() * factor);
            GL11.glEnd();
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(anchor.axisStartX() * factor, anchor.axisStartY() * factor);
            GL11.glVertex2f(anchor.axisEndX() * factor, anchor.axisEndY() * factor);
            GL11.glEnd();
        }
    }
}
