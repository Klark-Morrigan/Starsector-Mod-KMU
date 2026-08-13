package kmu.maplayers.base.geometry;

import java.util.List;

/**
 * Finding which of a cell's true edges a point came off.
 *
 * <p>The shaping hands back a fill polygon and a per-edge boundary flag, but not what each
 * fill edge FACES - and facing is what decides how an edge is drawn and how wide the gap
 * beyond it is. Recovering it is the same question every time: the inset is a parallel
 * offset, so the true edge nearest a fill edge's midpoint is the edge it was pulled off, and
 * that edge carries the target.
 */
final class CellEdges {

    private CellEdges() {
    }

    /**
     * The edge running closest to a point.
     *
     * @param edges the cell's true edges
     * @param x     the point's x coordinate
     * @param y     the point's y coordinate
     * @return the nearest edge, or null when there are none
     */
    static CellEdge findNearestEdge(List<CellEdge> edges, double x, double y) {

        CellEdge nearest = null;
        var shortest = Double.MAX_VALUE;

        for (var edge : edges) {

            var gap = measureGapToEdge(edge, x, y);

            if (gap < shortest) {

                shortest = gap;
                nearest = edge;
            }
        }
        return nearest;
    }

    /**
     * How far a point lies from an edge.
     *
     * <p>Clamped to the edge's own extent rather than measured to the line it lies on: two
     * edges of one cell share a line's worth of geometry only by accident, and an unclamped
     * distance would let a far-off edge win on the strength of where its line happens to run.
     *
     * @param edge the edge to measure to
     * @param x    the point's x coordinate
     * @param y    the point's y coordinate
     * @return the distance
     */
    static double measureGapToEdge(CellEdge edge, double x, double y) {

        var spanX = edge.x2() - edge.x1();
        var spanY = edge.y2() - edge.y1();
        var lengthSquared = spanX * spanX + spanY * spanY;

        if (lengthSquared <= 0) {
            return Math.hypot(x - edge.x1(), y - edge.y1());
        }

        var along = Math.max(
            0.0,
            Math.min(
                1.0,
                ((x - edge.x1()) * spanX + (y - edge.y1()) * spanY) / lengthSquared));

        return Math.hypot(
            x - (edge.x1() + along * spanX),
            y - (edge.y1() + along * spanY));
    }
}
