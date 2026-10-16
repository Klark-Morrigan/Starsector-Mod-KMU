package kmu.maplayers.base.hover;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;
import kmlib.opengl.GlVertexRuns;

import java.util.ArrayList;
import java.util.List;

/**
 * Which cluster a painted cell sits in.
 *
 * <p>Every highlight over the map asks that question - the one the cursor rests on and a whole set
 * lit at once alike - and both have to answer it by one rule, or two lit cells of one cluster
 * would fuse under one path and show a seam under the other. Declaring the rule once is what makes
 * that agreement structural rather than two implementations that happen to match. Both then clamp
 * the cells to the frontier found here through the one
 * {@link kmlib.opengl.PolygonTessellator#tessellateToBoundaryLoopsWithinLoop}.
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
     * The frontier of the cluster one cell belongs to: the smallest of the candidate loops that
     * encloses it, or null when the cell has no candidates or none of them encloses it.
     *
     * <p>Smallest rather than first because only the innermost loop is the cell's own cluster - a
     * group's enclave walled inside a rival that is itself inside another cluster of that same group
     * has three loops around it.
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
        var rings = new ArrayList<List<double[]>>(frontierLoops.size());

        for (var loop : frontierLoops) {
            rings.add(GlVertexRuns.unflattenVertices(loop));
        }

        var loopIndex = PolygonRegions.findSmallestEnclosingRing(rings, point[0], point[1]);
        return loopIndex == PolygonRegions.NO_RING
            ? null
            : frontierLoops.get(loopIndex);
    }
}
