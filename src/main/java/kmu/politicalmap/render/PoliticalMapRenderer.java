package kmu.politicalmap.render;

import kmlib.opengl.GlColor;

import kmu.diagnostics.KmuProfiling;

import org.lwjgl.opengl.GL11;

/**
 * Paints the political map's pre-built draw lists on the sector (M) map: the faction
 * fills first, then the interior seams, factionless outlines, and national borders
 * over them.
 *
 * <p>This is pure GL emission over an already-baked {@link PoliticalMapDrawables} - it
 * scales each world coordinate into map space and strokes/fills the flattened vertex
 * runs, with no knowledge of settings, caches, or how the runs were shaped. The map
 * widget has already applied the map's pan and centering to the GL matrix, so only the
 * scale is applied here.
 */
final class PoliticalMapRenderer {

    // Emits only; never instantiated.
    private PoliticalMapRenderer() {
    }

    // Draws the whole overlay for one map frame. Drawn in the below-UI map pass so
    // system and constellation names stay on top; a state push/pop isolates the blend
    // and line settings from the rest of the map render. An empty overlay skips the
    // push entirely.
    static void renderOnMap(PoliticalMapDrawables drawables, float factor, float alphaMult) {
        if (drawables.isEmpty()) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_HINT_BIT);
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
        });

        GL11.glPopAttrib();
    }

    // Fills each owned faction's cluster(s) with the faction's resolved fill color at
    // its opacity. The fill is the cluster's rounded region pre-tessellated into a
    // triangle soup, so a concave cluster (or one with an enclave) fills correctly and
    // exactly matches the stroked border. A null fill color is a "No color" choice, so
    // that faction is left unfilled; factionless cells carry no fill at all.
    private static void drawFills(PoliticalMapDrawables drawables, float factor, float alphaMult) {
        for (var territory : drawables.getFactionTerritoryByFactionId().values()) {
            if (territory.fillColor() == null) {
                continue;
            }
            GlColor.set(territory.fillColor(), alphaMult * territory.fillAlpha());
            drawVertexRun(GL11.GL_TRIANGLES, territory.fillTriangles(), factor);
        }
    }

    // Strokes the interior province seams first, then the factionless outlines, then the
    // smoothed national borders over them, so a cluster's frontier dominates its
    // internal province lines where they meet. Color, opacity, and line width are all
    // per element; a null color is a "No color" choice and skips it. An owned cluster's
    // national border is its border ring (in factionTerritories), so the per-cell
    // outline only carries factionless cells; an owned cell contributes only its seams
    // and a factionless cell only its outline.
    private static void drawBorders(PoliticalMapDrawables drawables, float factor,
            float alphaMult) {
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        for (var cell : drawables.getStyledCellBySystemId().values()) {
            if (cell.innerColor() == null) {
                continue;
            }
            GL11.glLineWidth(cell.innerWidth());
            GlColor.set(cell.innerColor(), alphaMult * cell.innerAlpha());
            drawVertexRun(GL11.GL_LINES, cell.interiorEdges(), factor);
        }
        for (var cell : drawables.getStyledCellBySystemId().values()) {
            if (cell.outerColor() == null) {
                continue;
            }
            GL11.glLineWidth(cell.outerWidth());
            GlColor.set(cell.outerColor(), alphaMult * cell.outerAlpha());
            drawVertexRun(GL11.GL_LINES, cell.boundaryEdges(), factor);
        }
        // Each border ring is a closed rounded loop, so it strokes as one continuous
        // GL_LINE_LOOP rather than the disconnected GL_LINES the per-cell edges use.
        for (var territory : drawables.getFactionTerritoryByFactionId().values()) {
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

    // Emits one flat [x, y, x, y, ...] vertex run under the given GL primitive, scaling
    // each world coordinate into map space. Serves every primitive the render draws:
    // GL_TRIANGLES fills, GL_LINES seams, and GL_LINE_LOOP border rings.
    private static void drawVertexRun(int mode, float[] vertices, float factor) {
        GL11.glBegin(mode);
        for (var v = 0; v < vertices.length; v += VertexRuns.FLOATS_PER_VERTEX) {
            GL11.glVertex2f(vertices[v] * factor, vertices[v + 1] * factor);
        }
        GL11.glEnd();
    }
}
