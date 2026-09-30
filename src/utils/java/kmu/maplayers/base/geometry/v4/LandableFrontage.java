package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which stretches of cell border face void nothing has captured: where anything can land.
 *
 * <p>That is the whole of it. A cell's border either meets a neighbour or faces void, and
 * every stretch that faces void is somewhere something could arrive - so the frontage is those
 * stretches, read straight off the pieces of void, whose edges already say which cell each lies
 * on. Nothing is measured, because there is nothing to measure: the question of HOW something
 * lands - how far, from where, past what - belongs to whatever lays it, and each layer answers
 * it for itself.
 *
 * <p><b>It shrinks as the layers go down.</b> Water a coastline closes off is captured, and a
 * border facing captured water faces nothing anything can still arrive from. So the frontage
 * a layer is laid against is what the pieces still open leave, and it is read again after each
 * layer rather than settled once. At the base there is no line laid and every piece is open.
 * Which pieces a layer captured is that layer's to say - only it knows which side of its lines
 * is which - so this is handed the answer rather than working it out.
 *
 * <p><b>A cell a coast only touches is frontage at that one point.</b> Two reaches meeting on a
 * cell are moved onto its shore along one line to one end inside the cell, and each leaves the
 * cell where its own wall crosses the frontier - a little apart, by the overshoot past the shore
 * over the tangent of the wall's angle to it. So the water between them touches the cell along a
 * run a few units long, or, where the two crossings weld into one vertex, at a vertex with a
 * coast wall either side and no cell edge at all. Both are the same contact at the map's
 * resolution: a run shorter than the resolution is a point at its middle, and a vertex of an open
 * piece with laid lines on both sides that lies on a cell's frontier is a point on that cell.
 * A bridge may land on either, and does.
 *
 * <p>Inland void or open sea, it does not matter. The sea's shore is what the sea runs around -
 * the holes cut out of it - and a pocket's shore is its outline, and both are frontage the same
 * way. What is not is any edge naming no cell: the edge of the sector is not a cell's border,
 * and neither is a line some tier laid across the water.
 */
public final class LandableFrontage {

    // How far a vertex may sit off a frontier edge and still lie on it, in map units. The
    // vertex is the cut the walk made through that edge, so it lies on it to rounding; far
    // below anything drawn, so a vertex merely near a shore is not taken for a point of it.
    private static final double ON_THE_SHORE = 1e-3;

    private LandableFrontage() {
    }

    /**
     * Everything the open void faces, as the runs long enough to show at the map's resolution
     * and the points that are all the rest amounts to.
     *
     * @param pieces     the pieces of a partition
     * @param isCaptured whether the layers laid so far have closed a piece off
     * @param frontier   every cell edge facing void, labelled with its cell, which says whose a
     *                   vertex on it is
     * @param resolution how far the drawn frontier sits from the true one, the sagitta the
     *                   pieces were walked at; a run shorter than it is a point
     * @return the runs at least the resolution long, and the points: each shorter run at its
     *         middle, and each vertex of an open piece with laid lines on both sides that lies
     *         on a cell's frontier. Piece by piece in the order given; a vertex two open pieces
     *         share comes back once for each
     */
    public static Frontage collectLandableFrontage(
            List<Face> pieces,
            Predicate<Face> isCaptured,
            List<LabelledWall> frontier,
            double resolution) {

        var runs = new ArrayList<Run>();
        var points = new ArrayList<Point>();

        for (var piece : pieces) {

            if (isCaptured.test(piece)) {
                continue;
            }

            for (var run : collectLandableRuns(piece)) {

                if (measureLength(run.points()) >= resolution) {
                    runs.add(run);
                } else {
                    points.add(new Point(run.cell(), Points.computeMean(run.points())));
                }
            }
            addPointsAlong(points, piece.outline(), frontier);

            for (var hole : piece.holes()) {
                addPointsAlong(points, hole, frontier);
            }
        }
        return new Frontage(List.copyOf(runs), List.copyOf(points));
    }

    /**
     * Every run of border the open void faces: the runs of every piece nothing captured.
     *
     * @param pieces     the pieces of a partition
     * @param isCaptured whether the layers laid so far have closed a piece off
     * @return the open pieces' runs, piece by piece in the order given
     */
    public static List<Run> collectLandableRuns(List<Face> pieces, Predicate<Face> isCaptured) {

        var runs = new ArrayList<Run>();

        for (var piece : pieces) {

            if (!isCaptured.test(piece)) {
                runs.addAll(collectLandableRuns(piece));
            }
        }
        return List.copyOf(runs);
    }

    /**
     * Every run of border a piece of open void faces, by the cell it belongs to.
     *
     * @param piece the piece, its edges labelled with the cells they lie on
     * @return the runs in walk order round the piece's outline and then round each hole, each
     *         on one cell and each carrying both ends of every edge it covers, so a run of
     *         {@code n} points is the {@code n - 1} edges between them; an edge naming no cell -
     *         the frame, or a laid line - is nobody's frontage and is absent
     */
    public static List<Run> collectLandableRuns(Face piece) {

        var runs = new ArrayList<Run>();

        addRunsAlong(runs, piece.outline());

        for (var hole : piece.holes()) {
            addRunsAlong(runs, hole);
        }
        return List.copyOf(runs);
    }

    private static double measureLength(List<double[]> points) {

        var length = 0.0;

        for (var index = 0; index + 1 < points.size(); index++) {
            length += Points.computeDistance(points.get(index), points.get(index + 1));
        }
        return length;
    }

    // One ring's worth of points: each corner with laid lines either side of it, filed under
    // the cell whose frontier it lies on. A corner with a cell edge on either side is the end
    // of a run and already frontage.
    private static void addPointsAlong(
            List<Point> points, LabelledRing ring, List<LabelledWall> frontier) {

        var corners = ring.vertices();
        var labels = ring.edgeLabels();

        for (var corner = 0; corner < corners.size(); corner++) {

            var inbound = labels[(corner + corners.size() - 1) % corners.size()];

            if (EdgeLabels.isCell(inbound) || EdgeLabels.isCell(labels[corner])) {
                continue;
            }

            for (var edge : frontier) {

                if (Segments.computeDistanceToPoint(
                        edge.segment().readStart(), edge.segment().readEnd(), corners.get(corner))
                        <= ON_THE_SHORE) {

                    points.add(new Point(edge.label(), corners.get(corner)));
                    break;
                }
            }
        }
    }

    // One ring's worth of runs: consecutive corners on the same cell, a corner on anything
    // that names no cell breaking a run and joining none.
    //
    // Walked from a place the cell changes rather than from the ring's first corner, so a run
    // straddling the ring's own start comes back as the one run it is rather than as two ending
    // nowhere.
    private static void addRunsAlong(List<Run> runs, LabelledRing ring) {

        var corners = ring.vertices();
        var labels = ring.edgeLabels();
        var count = corners.size();
        var start = findRunStart(labels);

        var points = new ArrayList<double[]>();
        var runCell = labels[start];

        for (var step = 0; step < count; step++) {

            var corner = (start + step) % count;
            var cell = labels[corner];

            if (cell != runCell) {
                closeRun(runs, runCell, points, corners.get(corner));
                points = new ArrayList<>();
                runCell = cell;
            }
            if (EdgeLabels.isCell(cell)) {
                points.add(corners.get(corner));
            }
        }
        closeRun(runs, runCell, points, corners.get(start));
    }

    // One run, ended at the corner where the next begins.
    //
    // A label names the edge LEAVING a corner, so gathering the corners of a run's edges
    // gathers their starts and leaves the last one's far end out. That end is the corner the
    // next run begins at - on this cell's border still, since it is where this cell's border
    // stops - so the two runs share it, and a run of one edge is the two corners it joins
    // rather than a lone point. Left off, every run falls one edge short of the junction it
    // runs up to, and the frontage has a notch at every corner where one cell gives way to the
    // next.
    private static void closeRun(
            List<Run> runs, int cell, List<double[]> points, double[] endsAt) {

        if (points.isEmpty()) {
            return;
        }
        points.add(endsAt);
        runs.add(new Run(cell, List.copyOf(points)));
    }

    // Where to start walking a ring so that no run is split across the ends of the list. A
    // ring on one cell the whole way round has no such place, and anywhere is as good as
    // anywhere else.
    private static int findRunStart(int[] labels) {

        for (var index = 0; index < labels.length; index++) {

            if (labels[index] != labels[(index + labels.length - 1) % labels.length]) {
                return index;
            }
        }
        return 0;
    }

    /**
     * One run of border a piece of open void faces, on one cell.
     *
     * @param cell   whose border it is
     * @param points its corners in walk order, both ends of every edge it covers - so two
     *               points for a cell offering one edge, and never just one
     */
    public record Run(int cell, List<double[]> points) {
    }

    /**
     * One point of border a piece of open void faces, on one cell, with no stretch to it that
     * the map could show.
     *
     * @param cell  whose border it is
     * @param point where the void touches it
     */
    public record Point(int cell, double[] point) {
    }

    /**
     * What the open void faces, at the map's resolution.
     *
     * @param runs   the stretches long enough to show
     * @param points the contacts that are not
     */
    public record Frontage(List<Run> runs, List<Point> points) {
    }
}
