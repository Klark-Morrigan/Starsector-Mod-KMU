package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.VoronoiCellBuilder;

import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.VoidHole;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Whether a piece of traced void and the cells it runs along land on the same points.
 *
 * <p>The question that has to be answered before a piece of void can be handed to machinery
 * built for cells. A cell's radius bound is a polygon on the circle and a hole's arc is a
 * chain of samples on that same circle, so the two describe one line twice - and unless they
 * describe it with the SAME points, nothing downstream can be told that the one abuts the
 * other. It can only be told they are near each other, which is a tolerance, and a tolerance
 * is what every seam artefact on this map has come out of.
 *
 * <p>Three different things are asked, because a failure of each means something different:
 *
 * <ul>
 *   <li>whether every sample sits exactly on a vertex of the cell beneath it - a stray here
 *       is the two chains being out of phase, which is the whole of what the flattening
 *       convention exists to settle;</li>
 *   <li>whether neighbouring samples are neighbouring vertices of that cell - a step of more
 *       than one is a vertex the outline ran straight past, so the two shapes would agree on
 *       where they are and still cut different corners;</li>
 *   <li>how far an arc's own CORNER stands off the cell's outline. A corner is where the
 *       boundary crosses from one circle to the next, so it is a point the walk names rather
 *       than one it chose, and the cell has no vertex there at all. That distance is the
 *       residual after the convention is settled, and it is what says whether a section and
 *       a cell can be made to share an edge outright.</li>
 * </ul>
 */
public final class CellBoundSeams {

    // How far apart two reports of one vertex may be and still be the same vertex. The two
    // are worked out from the same angle by the same arithmetic, so they are either the same
    // double or a different point; this is here to absorb nothing more than the last bit.
    private static final double SAME_VERTEX_DISTANCE = 1e-9;

    // Stands in for the vertex a sample matched where it matched none, which also breaks the
    // run of neighbouring vertices - there is nothing to measure a step from.
    private static final int NO_VERTEX = -1;

    // Stands in for the circle a point was last found on where there was none yet.
    private static final int NO_CIRCLE = -1;

    private CellBoundSeams() {
    }

    /**
     * How a traced outline and the cells under it agree, counted over every hole at once.
     *
     * @param samples          outline points that are samples along an arc rather than its
     *                         ends, which are the points the convention decides
     * @param onBoundVertex    how many of those sit exactly on a vertex of the cell they run
     *                         along; equal to {@code samples} is the whole of the check
     * @param worstSampleStray the furthest any sample fell from the nearest vertex of that
     *                         cell, which is zero once none of them missed
     * @param worstVertexStep  the largest jump in that cell's own vertex numbering between
     *                         neighbouring samples, which is one while the outline visits
     *                         every vertex it passes
     * @param corners          how many arc corners were measured
     * @param worstCornerStray the furthest any corner stood from the outline of the cell it
     *                         sits on - the residual the convention cannot remove, since a
     *                         corner is a crossing and no cell has a vertex there
     */
    public record BoundSeams(
        int samples,
        int onBoundVertex,
        double worstSampleStray,
        int worstVertexStep,
        int corners,
        double worstCornerStray) {
    }

    /**
     * Measures every hole's outline against the cells it runs along.
     *
     * <p>The cells are rebuilt here rather than taken in, so the comparison is against the
     * bound as the shipped builder produces it. Handed a cell set some caller had already
     * shaped, this would be asking whether the void agrees with THAT, and a check on the
     * flattening convention has to ask about the convention rather than about a copy.
     *
     * @param holes      the holes to measure, traced at the cells' own reach
     * @param sites      the sites the cells are built around
     * @param parameters the knobs the cells are built under
     * @return the agreement, over every hole at once
     */
    public static BoundSeams measureSeamsAgainstCells(
            List<VoidHole> holes,
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        // One cell is wanted many times over and costs a pass over every site to build, so
        // each is kept once it is asked for. Only the cells some hole actually runs along are
        // ever built, which on a real sector is a fraction of them.
        var bounds = new ArrayList<List<double[]>>(sites.size());
        bounds.addAll(Collections.nCopies(sites.size(), null));

        var seams = new BoundSeams(0, 0, 0, 0, 0, 0);

        for (var hole : holes) {
            seams = mergeSeams(seams, measureHoleSeams(hole, sites, bounds, parameters));
        }
        return seams;
    }

    // Two counts of the same kind of thing as one: the totals add and the worst cases stay
    // the worst, so a run over many holes reads exactly as a run over one.
    private static BoundSeams mergeSeams(BoundSeams one, BoundSeams other) {

        return new BoundSeams(
            one.samples() + other.samples(),
            one.onBoundVertex() + other.onBoundVertex(),
            Math.max(one.worstSampleStray(), other.worstSampleStray()),
            Math.max(one.worstVertexStep(), other.worstVertexStep()),
            one.corners() + other.corners(),
            Math.max(one.worstCornerStray(), other.worstCornerStray()));
    }

    // One hole's outline, walked in the order it was traced so that neighbouring points are
    // neighbours on the shape as well as in the list.
    private static BoundSeams measureHoleSeams(
            VoidHole hole,
            List<double[]> sites,
            List<List<double[]>> bounds,
            SectorGeometryParameters parameters) {

        var samples = 0;
        var onBoundVertex = 0;
        var worstSampleStray = 0.0;
        var worstVertexStep = 0;
        var corners = 0;
        var worstCornerStray = 0.0;

        var lastCircle = NO_CIRCLE;
        var lastVertex = NO_VERTEX;

        for (var point : hole.boundary()) {

            var circle = findCircleUnder(point, hole, sites);
            var bound = findCellBound(circle, bounds, sites, parameters);

            // A corner belongs to two circles and to neither cell's vertices, so it is asked
            // a different question and it ends the run of neighbours either way.
            if (isCorner(point, hole.corners())) {

                corners++;
                worstCornerStray = Math.max(
                    worstCornerStray, PolygonRegions.computeDistanceToBoundary(bound, point));
                lastCircle = circle;
                lastVertex = NO_VERTEX;
                continue;
            }

            samples++;

            var vertex = findNearestVertex(bound, point);
            var stray = Points.computeDistance(bound.get(vertex), point);

            if (stray > SAME_VERTEX_DISTANCE) {

                worstSampleStray = Math.max(worstSampleStray, stray);
                lastCircle = circle;
                lastVertex = NO_VERTEX;
                continue;
            }

            onBoundVertex++;

            // Only against the point before it on the SAME cell. Where the outline has just
            // crossed to another circle there is no step to measure, and measuring one would
            // compare two cells' vertex numbering as though it were one.
            if (lastVertex != NO_VERTEX && lastCircle == circle) {

                worstVertexStep = Math.max(
                    worstVertexStep, measureVertexStep(lastVertex, vertex, bound.size()));
            }
            lastCircle = circle;
            lastVertex = vertex;
        }
        return new BoundSeams(
            samples,
            onBoundVertex,
            worstSampleStray,
            worstVertexStep,
            corners,
            worstCornerStray);
    }

    // Which of the cells ringing this hole the point lies on, as the circle it sits nearest
    // to the reach of. Every outline point is on one of them by construction, so this asks
    // which rather than whether - and a point that is on none comes back attached to the
    // least wrong circle, where it shows up as a stray rather than being quietly dropped.
    private static int findCircleUnder(double[] point, VoidHole hole, List<double[]> sites) {

        var nearest = NO_CIRCLE;
        var offReach = Double.POSITIVE_INFINITY;

        for (var circle : hole.ringing()) {

            var offThisReach = Math.abs(
                Points.computeDistance(sites.get(circle), point) - hole.reach());

            if (offThisReach < offReach) {
                offReach = offThisReach;
                nearest = circle;
            }
        }
        return nearest;
    }

    // The cell's own bound, built once and kept. Built at the same reach and the same number
    // of sides the map is drawn at, because a bound built at any other is a different polygon
    // and the comparison would be against a shape nothing draws.
    private static List<double[]> findCellBound(
            int circle,
            List<List<double[]>> bounds,
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        if (bounds.get(circle) == null) {

            bounds.set(circle, VoronoiCellBuilder.buildLabelledCell(
                circle,
                sites,
                parameters.cellRadius(),
                parameters.boundSegments()).vertices());
        }
        return bounds.get(circle);
    }

    // Whether this point is one of the arc corners, by position rather than by identity: a
    // corner is a point of the outline, and what is being asked is which of the outline's
    // points it is.
    private static boolean isCorner(double[] point, List<double[]> corners) {

        for (var corner : corners) {

            if (Points.computeDistance(corner, point) <= SAME_VERTEX_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private static int findNearestVertex(List<double[]> bound, double[] point) {

        var nearest = 0;
        var nearestDistance = Double.POSITIVE_INFINITY;

        for (var index = 0; index < bound.size(); index++) {

            var distance = Points.computeDistance(bound.get(index), point);

            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = index;
            }
        }
        return nearest;
    }

    // How far apart two vertices are in the cell's own numbering, counted the short way round
    // its ring. Direction is not asked for: a hole is wound against the cell it runs on, so
    // neighbours count backwards along it, and what is being checked is that they are
    // neighbours at all.
    private static int measureVertexStep(int from, int to, int vertices) {

        var step = Math.abs(from - to);
        return Math.min(step, vertices - step);
    }
}
