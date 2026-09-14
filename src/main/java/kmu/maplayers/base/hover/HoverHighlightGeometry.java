package kmu.maplayers.base.hover;

import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;
import kmlib.starsector.systems.SystemKey;

import java.util.List;

/**
 * Works out what the cursor's hover lights up: the frontier of the cluster the hovered cell sits
 * in, and that cell's own painted extent as fillable geometry, both resolved through
 * {@link CellFrontierGeometry} so one lit cell reads as part of the same cluster a whole lit set
 * would.
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
    private SystemKey resolvedCellKey;
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
            MapHover hover) {

        if (!hover.isHovering()) {
            return HoverHighlight.NONE;
        }
        var cellKey = hover.hoveredSystemKey();
        var paintedExtent = source.resolvePaintedExtentOf(cellKey);
        if (paintedExtent.isEmpty()) {
            return HoverHighlight.NONE;
        }
        // A cell that fuses into no cluster - or one whose group traced no border at all - has no
        // candidates, so nothing encloses it and it washes without a halo.
        var frontierLoops = source.resolveCandidateFrontierLoopsOf(cellKey);
        if (cellKey.equals(resolvedCellKey)
                && frontierLoops == resolvedFrontierLoops
                && paintedExtent == resolvedPaintedExtent) {
            return resolvedHighlight;
        }
        resolvedCellKey = cellKey;
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

        var enclosingLoop = CellFrontierGeometry.findEnclosingLoop(frontierLoops, paintedExtent);

        // Resolve the wash to boundary loops once, then fill and trace both come off it - so the
        // wash and its outline are the same cluster by construction (as a cluster's fill and its
        // border already are), and the clip runs a single tessellation rather than one per half.
        var washLoops = CellFrontierGeometry.clipCellsToFrontier(
            List.of(paintedExtent),
            enclosingLoop);

        // The halo traces the cluster's own frontier rather than the washed cell: what the cursor
        // is telling the player is which cluster it has landed in.
        return new HoverHighlight(
            enclosingLoop == null
                ? List.of()
                : List.of(enclosingLoop),
            PolygonTessellator.tessellateToTriangles(washLoops),
            GlVertexRuns.flattenLoops(washLoops));
    }
}
