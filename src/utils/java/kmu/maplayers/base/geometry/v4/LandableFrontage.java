package kmu.maplayers.base.geometry.v4;

import java.util.ArrayList;
import java.util.List;

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
 *
 * <p>Inland void or open sea, it does not matter. The sea's shore is what the sea runs around -
 * the holes cut out of it - and a pocket's shore is its outline, and both are frontage the same
 * way. What is not is the frame: the edge of the sector is not a cell's border.
 */
public final class LandableFrontage {

    private LandableFrontage() {
    }

    /**
     * Every run of border a piece of open void faces, by the cell it belongs to.
     *
     * @param piece the piece, its edges labelled with the cells they lie on
     * @return the runs in walk order round the piece's outline and then round each hole, each
     *         on one cell and each carrying both ends of every edge it covers, so a run of
     *         {@code n} points is the {@code n - 1} edges between them; the frame's own edges
     *         are nobody's frontage and are absent
     */
    public static List<Run> collectLandableRuns(Face piece) {

        var runs = new ArrayList<Run>();

        addRunsAlong(runs, new LabelledRing(piece.boundary(), piece.edgeLabels()));

        for (var hole : piece.holes()) {
            addRunsAlong(runs, hole);
        }
        return List.copyOf(runs);
    }

    // One ring's worth of runs: consecutive corners on the same cell, the frame's corners
    // breaking a run and joining none.
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
            if (cell != BareVoid.THE_FRAME) {
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
}
