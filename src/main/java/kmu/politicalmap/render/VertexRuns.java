package kmu.politicalmap.render;

import kmu.politicalmap.domain.geometry.ShapedCell;

import java.util.List;

/**
 * The single source of truth for how the political map packs geometry into the flat
 * [x, y, x, y, ...] float runs GL consumes, and the conversions that produce them.
 *
 * <p>The builder produces these runs from polygon and edge geometry; the renderer
 * strides through them at {@link #FLOATS_PER_VERTEX} per vertex. Keeping the stride,
 * the empty run, and the flatten conversions here means the producer and the consumer
 * agree on the packing by construction rather than by two matching magic numbers.
 */
final class VertexRuns {
    // A vertex is a 2D point packed as x then y, so every flattened run is a
    // [x, y, x, y, ...] array. This is the stride from one vertex to the next and the
    // multiplier that sizes a run from its vertex count.
    static final int FLOATS_PER_VERTEX = 2;

    // A stroked edge is two endpoints, so its flattened GL_LINES contribution is two
    // vertices wide. Sizes the border/seam runs in flattenEdgesOfClass.
    static final int FLOATS_PER_EDGE = 2 * FLOATS_PER_VERTEX;

    // The empty vertex run shared by every draw element a cluster omits (an owned
    // cell's per-cell fill and outline, a faction with no fill), so an omitted element
    // draws nothing without allocating a fresh empty array each time.
    static final float[] NO_VERTICES = new float[0];

    // Conversions only; never instantiated.
    private VertexRuns() {
    }

    // Flattens a polygon's {x, y} vertices into a [x, y, x, y, ...] run.
    static float[] flattenVertices(List<double[]> polygon) {
        var flat = new float[polygon.size() * FLOATS_PER_VERTEX];
        var index = 0;
        for (var vertex : polygon) {
            flat[index++] = (float) vertex[0];
            flat[index++] = (float) vertex[1];
        }
        return flat;
    }

    // Flattens a closed ring into GL_LINES segment pairs, one per edge including the
    // wrap from the last vertex back to the first, so a rounded outline strokes as a
    // closed loop under the same GL_LINES pass the per-cell edges use.
    static float[] flattenClosedLoopAsSegments(List<double[]> ring) {
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

    // Flattens the edges of a shaped cell of one class into a GL_LINES vertex run
    // ([x1, y1, x2, y2, ...]): national borders when wantBoundary is true, interior
    // seams when false. Sized in a first pass so the run is a single exact array
    // rather than a growing list boxed per coordinate.
    static float[] flattenEdgesOfClass(ShapedCell shaped, boolean wantBoundary) {
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
}
