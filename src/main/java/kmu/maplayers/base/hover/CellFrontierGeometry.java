package kmu.maplayers.base.hover;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;
import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;

import java.util.List;

/**
 * Which cluster a painted cell sits in, and cells clamped to the frontier of the cluster holding
 * them.
 *
 * <p>Every highlight over the map asks those two questions - the one the cursor rests on and a
 * whole set lit at once alike - and both have to answer them by one rule, or two lit cells of one
 * cluster would fuse under one path and show a seam under the other. Declaring the rule once is
 * what makes that agreement structural rather than two implementations that happen to match.
 *
 * <p>The cluster has to be searched for because a map bakes its borders per cluster group, not per
 * cluster: a group carries one loop for each of its disjoint clusters and one for each enclave
 * bitten out of them, with nothing naming which is which. Which cluster a cell belongs to is
 * therefore identified geometrically - by which loop contains it - rather than by an index, which
 * would mean keying the whole build per cluster to answer a question only a highlight asks.
 *
 * <p>Loop identity doubles as the "same cluster" test for a set of cells: two cells of one cluster
 * resolve the very same loop instance, so grouping on it needs no adjacency model of its own.
 */
public final class CellFrontierGeometry {

    // Answers about geometry it is handed; never instantiated.
    private CellFrontierGeometry() {
    }

    /**
     * Cells as the boundary loops a wash fills and traces, clamped to the frontier they sit inside
     * so neither spills past the rounded border - the wash stops at the exact line the border
     * strokes instead of keeping the sharp mitered corner the border's rounding cut away, the same
     * clip a cluster's own fill applies to itself.
     *
     * <p>Several cells resolve together rather than one at a time: abutting cells wind into one
     * region under the tessellator's positive winding rule, so the edge two neighbours share is
     * dropped and the group comes back with the outline of its combined reach. That is what a
     * highlight over a whole cluster needs - one lit region, not a grid of seams - and a single
     * cell is the same call with a list of one.
     *
     * @param paintedExtents the cells' painted extents, each as {x, y} vertex pairs
     * @param enclosingLoop  the frontier to clamp them to, or null for cells no loop encloses,
     *                       which resolve to their own combined boundary
     * @return the boundary loops of what is left; the clip can bite the extents into more than one
     *         loop, so it returns however many the overlap has
     */
    public static List<List<double[]>> clipCellsToFrontier(
            List<List<double[]>> paintedExtents,
            float[] enclosingLoop) {

        if (enclosingLoop == null) {
            return PolygonTessellator.tessellateToBoundaryLoops(paintedExtents);
        }
        return PolygonTessellator.tessellateIntersectionToBoundaryLoops(
            paintedExtents,
            List.of(GlVertexRuns.unflattenVertices(enclosingLoop)));
    }

    /**
     * The frontier of the cluster one cell belongs to: the smallest of the candidate loops that
     * encloses it, or null when the cell has no candidates or none of them encloses it.
     *
     * <p>Smallest rather than first because nested loops all enclose the point and only the
     * innermost is the cell's own cluster - a group's enclave walled inside a rival that is itself
     * inside another cluster of that same group has three loops around it. Area is compared by
     * magnitude, since a hole ring winds against its outer ring.
     *
     * @param frontierLoops the loops the cell might sit inside, as {@code [x, y, x, y, ...]} runs
     * @param paintedExtent the cell's painted extent as {x, y} vertex pairs
     * @return the enclosing loop, by the identity the caller was handed it under, or null
     */
    public static float[] findEnclosingLoop(
            List<float[]> frontierLoops,
            List<double[]> paintedExtent) {

        // A point standing in for the whole cell, well clear of its edges: the mean of its
        // vertices. Not the cursor, which can rest a pixel inside an edge and so fall outside a
        // frontier whose corners rounding has cut inward - dropping the halo just as the player
        // pushes into a corner.
        var point = Points.computeMean(paintedExtent);
        float[] smallestLoop = null;
        var smallestArea = Double.MAX_VALUE;

        for (var loop : frontierLoops) {
            var ring = GlVertexRuns.unflattenVertices(loop);
            if (!PolygonRegions.isPointInsideRing(ring, point[0], point[1])) {
                continue;
            }
            var area = Math.abs(PolygonRegions.computeSignedArea(ring));
            if (area < smallestArea) {
                smallestArea = area;
                smallestLoop = loop;
            }
        }
        return smallestLoop;
    }
}
