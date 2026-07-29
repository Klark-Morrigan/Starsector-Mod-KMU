package kmu.maplayers.base.hover;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;
import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;

import java.util.List;

/**
 * Works out what a hover lights up: which of the candidate border loops encloses the hovered
 * cell, and that cell's own painted extent as fillable geometry.
 *
 * <p>The loop has to be searched for because a map bakes its borders per region, not per
 * cluster: a region carries one loop for each of its disjoint clusters and one for each
 * enclave bitten out of them, with nothing naming which is which. The cluster the cursor is in
 * is therefore identified geometrically - by which loop contains the hovered cell - rather
 * than by an index, which would mean keying the whole build per cluster to answer a question
 * only the hover asks.
 *
 * <p>Two details make that search exact. The cell is represented by the average of its
 * vertices rather than by the cursor itself: a cursor a pixel inside the cell's edge can fall
 * outside a frontier whose corners rounding has cut inward, which would drop the halo just as
 * the player pushes into a corner. And where loops nest - a region's enclave inside a rival
 * inside that same region's own cluster - three loops contain the point, so the smallest one
 * wins, which is the enclave's own frontier rather than the distant cluster's.
 *
 * <p>Both answers are memoised against the hovered cell and the geometry behind it, since
 * they change only when the cursor crosses into another cell or a rebuild replaces that
 * cell's shape - not sixty times a second while it rests on one.
 */
public final class HoverHighlightGeometry {
    // What the retained answer was resolved from. Held by identity, not by value: a rebuild or
    // an incremental re-shape replaces the whole loop list and the whole extent rather than
    // editing either in place, so a reference that still matches is the same geometry. Keyed on
    // what the source handed back rather than on whatever it derived that from, so no layer's
    // own model has to be named here for the memo to be exact.
    private String resolvedCellId;
    private List<float[]> resolvedFrontierLoops;
    private List<double[]> resolvedPaintedExtent;
    private HoverHighlight resolvedHighlight = HoverHighlight.NONE;

    /**
     * The geometry the current hover lights up.
     *
     * @param source the layer's answers about the frame it painted - the hovered cell's extent
     *               and the loops it might sit inside both come from here, so the highlight can
     *               only ever trace what was painted
     * @param hover  what the cursor is over this frame
     * @return the loops and runs to draw, or {@link HoverHighlight#NONE} when nothing is
     *         hovered or the hovered cell has no drawable shape
     */
    public HoverHighlight resolveHighlightFor(
            HoverHighlightSource source,
            PoliticalMapHover hover) {

        if (!hover.isHovering()) {
            return HoverHighlight.NONE;
        }
        var cellId = hover.hoveredSystemId();
        var paintedExtent = source.resolvePaintedExtentOf(cellId);
        if (paintedExtent.isEmpty()) {
            return HoverHighlight.NONE;
        }
        // A cell that fuses into no region - or one whose region traced no border at all - has no
        // candidates, so nothing encloses it and it washes without a halo.
        var frontierLoops = source.resolveCandidateFrontierLoopsOf(cellId);
        if (cellId.equals(resolvedCellId)
                && frontierLoops == resolvedFrontierLoops
                && paintedExtent == resolvedPaintedExtent) {
            return resolvedHighlight;
        }
        resolvedCellId = cellId;
        resolvedFrontierLoops = frontierLoops;
        resolvedPaintedExtent = paintedExtent;
        resolvedHighlight = buildHighlight(frontierLoops, paintedExtent);
        return resolvedHighlight;
    }

    // Assembles the two halves: the frontier loop enclosing the cell, and the cell - clamped to
    // that frontier - washed and traced from one resolved set of loops.
    private static HoverHighlight buildHighlight(
            List<float[]> frontierLoops,
            List<double[]> paintedExtent) {

        var enclosingLoop = findEnclosingLoop(frontierLoops, paintedExtent);

        // Resolve the wash to boundary loops once, then fill and trace both come off it - so the
        // wash and its outline are the same region by construction (as a region's fill and its
        // border already are), and the clip runs a single tessellation rather than one per half.
        var washLoops = clipCellToFrontier(paintedExtent, enclosingLoop);
        return new HoverHighlight(
                enclosingLoop == null
                        ? List.of()
                        : List.of(enclosingLoop),
                PolygonTessellator.tessellateToTriangles(washLoops),
                washLoops
                        .stream()
                        .map(GlVertexRuns::flattenVertices)
                        .toList());
    }

    // The hovered cell as the boundary loops its wash fills and traces, clamped to the frontier it
    // sits inside so neither spills past the rounded border - it stops at the exact line the border
    // strokes instead of keeping the sharp mitered corner the border's rounding cut away, the same
    // clip a region's own fill applies to itself. A cell no loop encloses has no frontier (null
    // loop), so it resolves to the cell's own boundary. The clip can bite the extent into more than
    // one loop, so it returns however many the overlap has.
    private static List<List<double[]>> clipCellToFrontier(
            List<double[]> paintedExtent,
            float[] enclosingLoop) {

        var cell = List.of(paintedExtent);
        if (enclosingLoop == null) {
            return PolygonTessellator.tessellateToBoundaryLoops(cell);
        }
        return PolygonTessellator.tessellateIntersectionToBoundaryLoops(
                cell,
                List.of(GlVertexRuns.unflattenVertices(enclosingLoop)));
    }

    // The hovered cluster's frontier: the smallest of the candidate loops that encloses the cell,
    // or null when the cell has no candidates or none of them encloses it. Smallest rather than
    // first because nested loops all enclose the point and only the innermost is the cell's own
    // cluster; area is compared by magnitude since a hole ring winds against its outer ring.
    private static float[] findEnclosingLoop(
            List<float[]> frontierLoops,
            List<double[]> paintedExtent) {

        // A point standing in for the whole cell, well clear of its edges: the mean of its
        // vertices. Not the cursor, which can rest a pixel inside an edge.
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
