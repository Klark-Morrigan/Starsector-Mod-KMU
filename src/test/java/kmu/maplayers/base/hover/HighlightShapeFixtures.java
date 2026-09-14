package kmu.maplayers.base.hover;

import java.util.List;

/**
 * Hand-built stand-ins for the shapes a highlight reads: cell extents, traced border loops, and
 * the area a wash covers.
 *
 * <p>Squares are enough because every resolve here reads the geometry only as an area that does or
 * does not enclose a point, so no shaping, styling or GL is involved in the answers being pinned.
 */
final class HighlightShapeFixtures {

    // Builds shapes; never instantiated.
    private HighlightShapeFixtures() {
    }

    /**
     * An axis-aligned square, counter-clockwise, spanning {@code [minX, minX + side]} x
     * {@code [minY, minY + side]} - a stand-in cell shape.
     */
    static List<double[]> buildSquare(double minX, double minY, double side) {

        return List.of(
            new double[] {minX, minY},
            new double[] {minX + side, minY},
            new double[] {minX + side, minY + side},
            new double[] {minX, minY + side});
    }

    /** The same square as the baked {@code [x, y, x, y, ...]} run a border loop is kept in. */
    static float[] buildSquareRun(float minX, float minY, float side) {

        return new float[] {
            minX, minY,
            minX + side, minY,
            minX + side, minY + side,
            minX, minY + side};
    }

    /**
     * Sums the unsigned area of every triangle in a flat {@code [x, y, x, y, ...]} soup, six floats
     * per triangle - the area a wash actually covers, for asserting what was clipped or joined.
     */
    static double computeTotalTriangleArea(float[] triangles) {

        var floatsPerTriangle = 6;
        var total = 0.0;

        for (var i = 0; i + floatsPerTriangle <= triangles.length; i += floatsPerTriangle) {

            var ax = triangles[i];
            var ay = triangles[i + 1];
            var bx = triangles[i + 2];
            var by = triangles[i + 3];
            var cx = triangles[i + 4];
            var cy = triangles[i + 5];

            total += Math.abs((bx - ax) * (cy - ay) - (cx - ax) * (by - ay)) / 2.0;
        }
        return total;
    }
}
