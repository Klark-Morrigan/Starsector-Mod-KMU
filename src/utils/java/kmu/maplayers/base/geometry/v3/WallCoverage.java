package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Segments;
import kmlib.math.geometry.UniformGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether a line about to be laid would be drawing something already drawn.
 *
 * <p>A wall laid where walls already run closes nothing: the void either side of it was
 * divided before it arrived. Drawn, it is one line under another - and a reader looking at two
 * lines on one path cannot tell which of them is doing the work.
 *
 * <p><b>Covered piecewise, not matched to one wall.</b> The line that has to go is rarely a
 * copy of any single wall - it is a long span whose first half runs beside a shorter span and
 * whose second half runs beside the coast, so no one wall covers it and every one of them
 * covers some. Asking each wall in turn whether IT covers the whole line misses exactly that,
 * which is why the question is asked the other way round: walk the line, and let any wall
 * answer for the stretch under it.
 *
 * <p>Its own class rather than part of whatever lays the spans. This works on drawn lines,
 * before any of them is a graph edge or bounds any piece, so it shares nothing with the walk
 * that finds the pieces and needs nothing from it. Kept together with the span search, it read
 * as part of a rule about bridges when it is a rule about lines.
 *
 * <p>The walls are filed on a grid as they are laid, so a place is weighed only against the
 * walls near it. A coast is thousands of segments, and every span offered asks about a handful
 * of places.
 */
public final class WallCoverage {

    // How many places along a line are asked about when something has to be asked of its whole
    // length. A handful of steps catches a line clipping a cell's corner, or slipping off a
    // wall for a stretch, without either question costing a sample per map unit.
    static final int LINE_SAMPLES = 8;

    private final double tolerance;
    private final UniformGrid<double[][]> wallGrid;

    private WallCoverage(double tolerance, UniformGrid<double[][]> wallGrid) {

        this.tolerance = tolerance;
        this.wallGrid = wallGrid;
    }

    /**
     * An empty set of walls, its grid sized for the walls that will be filed into it.
     *
     * @param extent    the box every wall to be filed fits inside
     * @param wallCount how many walls will be filed, which sizes the grid's squares
     * @param tolerance how far off a wall a place may be and still count as walled
     * @return the walls, none filed yet
     */
    static WallCoverage createWithin(Bounds extent, int wallCount, double tolerance) {

        return new WallCoverage(
            tolerance,
            new UniformGrid<>(UniformGrid.measureAdaptiveCellSide(extent, wallCount)));
    }

    /**
     * Files one wall, so every later question counts it.
     *
     * @param start one end
     * @param end   the other
     */
    void fileWall(double[] start, double[] end) {
        wallGrid.fileSegment(start, end, new double[][] {start, end});
    }

    /**
     * Whether every part of a line is already walled by something.
     *
     * <p>Its ends prove nothing on their own. Corners are where walls meet, so almost every
     * line begins and ends on one; a line that leaves a corner and strikes out across open void
     * is the ordinary case, and only one that never leaves the walls is a doubling.
     *
     * @param start one end
     * @param end   the other
     * @return whether the whole line is already walled
     */
    boolean isAlreadyWalled(double[] start, double[] end) {

        // The ends included, because a stretch left uncovered at either end is exactly the gap
        // that makes a line worth laying.
        for (var step = 0; step <= LINE_SAMPLES; step++) {

            if (!isPointWalled(findPointAlong(start, end, step))) {
                return false;
            }
        }
        return true;
    }

    /**
     * One of the places along a line the sampling asks about.
     *
     * <p>By step rather than by fraction, so that every question sampling a line steps the same
     * way and two of them cannot come to disagree about where its middle is.
     *
     * @param start one end
     * @param end   the other
     * @param step  which of the {@link #LINE_SAMPLES} steps
     * @return the place
     */
    static double[] findPointAlong(double[] start, double[] end, int step) {

        var along = (double) step / LINE_SAMPLES;

        return new double[] {
            start[0] + (end[0] - start[0]) * along,
            start[1] + (end[1] - start[1]) * along};
    }

    /**
     * A drawn coastline as the wall it is, segment by segment.
     *
     * <p>The whole line rather than its straight reaches alone. A span can run beside a fillet
     * as readily as beside a reach - the coast is one line to the eye and one wall to the void,
     * and which of its parts a span happens to shadow is not a distinction anything downstream
     * makes.
     *
     * @param rings the closed coastlines
     * @return every segment of every ring
     */
    static List<double[][]> collectRingWalls(List<List<double[]>> rings) {

        var walls = new ArrayList<double[][]>();

        for (var ring : rings) {
            for (var index = 0; index < ring.size(); index++) {

                walls.add(new double[][] {
                    ring.get(index),
                    ring.get((index + 1) % ring.size())});
            }
        }
        return walls;
    }

    // Whether any wall runs within the tolerance of a place. A wall that close has its box
    // within the tolerance too, so the walls filed near the place are the only ones that can.
    private boolean isPointWalled(double[] point) {

        for (var wall : wallGrid.listItemsNear(point[0], point[1], tolerance)) {

            if (Segments.computeDistanceToPoint(wall[0], wall[1], point) <= tolerance) {
                return true;
            }
        }
        return false;
    }
}
