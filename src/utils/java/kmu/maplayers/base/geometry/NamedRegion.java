package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.List;

/**
 * A closed piece of map with a name on it.
 *
 * <p>One type for a cell and for a section of void alike, because everything asked of either
 * once it has a name is the same question: where does the name go, and is the pointer in it. A
 * cell and a section are different things to the geometry and the same thing to a reader
 * pointing at one.
 *
 * <p>The anchor and the box travel with the outline rather than being worked out where they are
 * used. Both are walks over the whole ring, and the pointer readout asks the second of them of
 * every region on the map at every mouse move - which is a walk per region per pixel of pointer
 * travel if it is not done once up front.
 *
 * @param name    what to write on it
 * @param outline its closed boundary
 * @param anchor  where in it the name is written
 * @param box     the box it lies in, which is both the cheap half of a hit test and what says
 *                whether there is room on screen to write the name at all
 */
record NamedRegion(
    String name,
    List<double[]> outline,
    double[] anchor,
    Bounds box) {

    // Two crossings bound a run across a shape, and a radius either side of a centre makes a
    // width.
    private static final int HALVES = 2;

    /**
     * Names one region, working out where its name goes and what it spans.
     *
     * @param name    what to write on it
     * @param outline its closed boundary, which must enclose an area
     * @return the named region
     */
    static NamedRegion nameRegion(String name, List<double[]> outline) {

        return new NamedRegion(
            name,
            outline,
            findLabelAnchor(outline),
            Bounds.computeEnclosingBounds(outline));
    }

    /**
     * Whether a point lies in this region.
     *
     * <p>The box first, since almost every region on the map is nowhere near the pointer and a
     * box turns those away in four comparisons rather than in a walk round a ring.
     *
     * @param x where to test
     * @param y the same
     * @return whether the point is inside
     */
    boolean holds(double x, double y) {

        return x >= box.minX()
            && x <= box.maxX()
            && y >= box.minY()
            && y <= box.maxY()
            && PolygonRegions.isPointInsideRing(outline, x, y);
    }

    // Where in a region its name goes.
    //
    // Not the mean of its outline, which is what a marker usually hangs on: a piece of void is
    // very often a crescent between two cells, and the mean of a crescent sits in the bite -
    // out on a cell, naming the wrong thing. Across the two fixtures 16 and 15 sections of 319
    // and 449 have their mean outside their own outline.
    //
    // Instead the widest run of the region along one horizontal line, taken at the height of
    // the mean. That is inside by construction, because the run is bounded by two crossings of
    // the outline and the same crossing rule is what decides whether a point is in a ring at
    // all - and being the WIDEST such run, it is also where there is most room to write.
    private static double[] findLabelAnchor(List<double[]> outline) {

        var mean = Points.computeMean(outline);
        var crossings = collectCrossingsAt(outline, mean[1]);

        var widest = -1.0;
        var anchor = mean;

        for (var entry = 0; entry + 1 < crossings.size(); entry += HALVES) {

            var width = crossings.get(entry + 1) - crossings.get(entry);

            if (width > widest) {

                widest = width;
                anchor = new double[] {
                    (crossings.get(entry) + crossings.get(entry + 1)) / HALVES, mean[1]};
            }
        }
        return anchor;
    }

    // Where a horizontal line crosses an outline, left to right. An edge counts when its two
    // ends straddle the line, so a vertex sitting exactly on it is counted once rather than
    // twice - which is what keeps the crossings in inside/outside pairs.
    private static List<Double> collectCrossingsAt(List<double[]> outline, double y) {

        var crossings = new ArrayList<Double>();

        for (var index = 0; index < outline.size(); index++) {

            var from = outline.get(index);
            var to = outline.get((index + 1) % outline.size());

            if (from[1] > y == to[1] > y) {
                continue;
            }
            crossings.add(from[0] + (y - from[1]) / (to[1] - from[1]) * (to[0] - from[0]));
        }
        crossings.sort(Double::compare);

        return crossings;
    }
}
