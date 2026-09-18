package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A growing set of points in which two close enough together are one point.
 *
 * <p>What decides whether two lines meet. Every line arriving at a shared corner reports that
 * corner from its own arithmetic, and two reports of it differ in the last few digits - so
 * taken literally the lines end near each other and never touch, and a walk over them finds a
 * plane cut by loose threads rather than a division. Welding is what turns near into the same,
 * and the tolerance is how near counts.
 *
 * <p><b>The first report of a corner is the one kept.</b> Averaging as more lines arrive would
 * move a vertex after edges had already been welded to it, so a line welded early would end
 * somewhere its own endpoint no longer is.
 *
 * <p>Held as a grid a tolerance across rather than searched through, because every endpoint of
 * every line is asked and a sector's worth of them against each other is a square of the work
 * for an answer that is always within one square of where it was asked.
 */
final class WeldedPoints {

    private final Map<GridSquare, List<Integer>> buckets = new HashMap<>();

    private final List<double[]> points = new ArrayList<>();

    private final double tolerance;

    WeldedPoints(double tolerance) {
        // Never zero: a grid of no width has no square to look in, and two reports of one corner
        // are never bit-identical anyway, so an exact match is not a tolerance a caller can
        // usefully ask for.
        this.tolerance = Math.max(tolerance, Limits.MIN_EDGE_LENGTH);
    }

    /**
     * Which point this is, welding it onto one already held when it is close enough.
     *
     * @param point the point to place
     * @return its index among the welded points, whether it joined one or started one
     */
    int weldPoint(double[] point) {

        var column = (long) Math.floor(point[0] / tolerance);
        var row = (long) Math.floor(point[1] / tolerance);

        // The nine squares around this one. A square is exactly one tolerance across, so
        // nothing outside that block can be within the tolerance, and nothing inside it is
        // missed.
        for (var acrossColumns = -1; acrossColumns <= 1; acrossColumns++) {
            for (var acrossRows = -1; acrossRows <= 1; acrossRows++) {

                var held = findHeldPointNear(
                    point, new GridSquare(column + acrossColumns, row + acrossRows));

                if (held != null) {
                    return held;
                }
            }
        }

        var fresh = points.size();

        points.add(point);
        buckets
            .computeIfAbsent(new GridSquare(column, row), square -> new ArrayList<>())
            .add(fresh);

        return fresh;
    }

    /**
     * Every point held, in the order they were first reported.
     *
     * @return the points, so that a caller holding indices from {@link #weldPoint} can read
     *         back where each one stands
     */
    List<double[]> collectPoints() {
        return List.copyOf(points);
    }

    private Integer findHeldPointNear(double[] point, GridSquare square) {

        var bucket = buckets.get(square);

        if (bucket == null) {
            return null;
        }

        for (var held : bucket) {
            if (Points.computeDistance(point, points.get(held)) <= tolerance) {
                return held;
            }
        }
        return null;
    }

    // Which square of the grid a point falls in. A record rather than a packed key, so that two
    // squares are the same square by their coordinates rather than by an encoding two callers
    // have to agree on.
    private record GridSquare(long column, long row) {
    }
}
