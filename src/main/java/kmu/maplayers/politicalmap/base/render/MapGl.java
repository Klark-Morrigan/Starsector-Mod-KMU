package kmu.maplayers.politicalmap.base.render;

import kmlib.opengl.GlVertexRuns;

import org.lwjgl.opengl.GL11;

/**
 * The shared GL emission the map renderers stride their vertex runs with, so the
 * production {@link TerritoryRenderer} and the debug {@link PoliticalMapStaticDebugRenderer}
 * pack and read a run identically - both walk it at {@link GlVertexRuns#FLOATS_PER_VERTEX}
 * per vertex - rather than each carrying its own copy of the loop.
 */
public final class MapGl {
    // Emits only; never instantiated.
    private MapGl() {
    }

    // Emits one flat [x, y, x, y, ...] vertex run under the given GL primitive, scaling
    // each world coordinate into map space. Serves every primitive the map draws:
    // GL_TRIANGLES fills, GL_LINES seams, and GL_LINE_LOOP border rings.
    public static void drawVertexRun(int mode, float[] vertices, float factor) {
        GL11.glBegin(mode);
        for (var v = 0; v < vertices.length; v += GlVertexRuns.FLOATS_PER_VERTEX) {
            GL11.glVertex2f(vertices[v] * factor, vertices[v + 1] * factor);
        }
        GL11.glEnd();
    }
}
