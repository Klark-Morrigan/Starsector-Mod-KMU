package kmu.maplayers.base.render.clusters;

import kmlib.opengl.GlVertexRuns;

import kmu.maplayers.base.geometry.ShapedCell;

/**
 * Packs a shaped cell's classified edges into the flat GL_LINES runs a renderer
 * strokes - the cell-specific complement to the generic {@link GlVertexRuns} flatten
 * conversions.
 *
 * <p>The generic {@code {x, y}} to {@code [x, y, x, y, ...]} packing lives in
 * {@link GlVertexRuns}; what stays here is the one conversion that needs to know a
 * {@link ShapedCell} - selecting the edges of one class (cluster border or
 * interior seam) before packing them.
 */
public final class VertexRuns {
    // Conversions only; never instantiated.
    private VertexRuns() {
    }

    // Flattens the edges of a shaped cell of one class into a GL_LINES vertex run
    // ([x1, y1, x2, y2, ...]): cluster borders when wantBoundary is true, interior
    // seams when false. Sized in a first pass so the run is a single exact array
    // rather than a growing list boxed per coordinate.
    public static float[] flattenEdgesOfClass(ShapedCell shaped, boolean wantBoundary) {
        var polygon = shaped.fillPolygon();
        var edgeIsBoundary = shaped.edgeIsBoundary();
        var count = polygon.size();
        var matching = 0;
        for (var i = 0; i < count; i++) {
            if (edgeIsBoundary[i] == wantBoundary) {
                matching++;
            }
        }
        var flat = new float[matching * GlVertexRuns.FLOATS_PER_SEGMENT];
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
}
