package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segment;
import kmlib.math.geometry.Segments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cuts a bag of lines apart wherever they cross, so that afterwards they only ever meet at
 * their ends.
 *
 * <p>The step that turns lines into a graph. A face walk follows edges from vertex to vertex,
 * so a crossing in the middle of a line is a junction the walk cannot see and simply runs past -
 * and two pieces that should have been divided come back as one. Cutting first means every
 * place two lines meet is somewhere a walk can turn.
 *
 * <p>Separate from the welding that follows it because the two are answering different
 * questions. This asks where lines meet; the welding asks which of the points that came back
 * are the same point. Run together they would weld a crossing before knowing every line that
 * passes through it.
 *
 * <p>Every piece keeps the label of the line it was cut from: a line is one line however many
 * pieces it is in, and a face bounded by half of one still has to say which.
 *
 * <p><b>Two lines lying along each other are not cut.</b> An overlap has no single crossing
 * point to cut at, so the intersection this rests on reports nothing for it, and the pieces come
 * back uncut. The exactly-duplicated case survives anyway - two lines with the same two ends
 * become one edge when the graph is built - but a partial overlap does not, and a walk over one
 * is wrong wherever the overlap runs.
 */
final class SegmentCrossings {

    private SegmentCrossings() {
    }

    /**
     * The same lines, cut at every point another one crosses them.
     *
     * @param lines the lines to cut, in any order
     * @return the pieces, each too short to hold a crossing of its own and each labelled as
     *         the line it came from; lines with no crossings come back whole, and lines too
     *         short to have a direction are dropped
     */
    static List<LabelledWall> splitAtCrossings(List<LabelledWall> lines) {

        // One list of cut points per line rather than per pair, because a line crossed four
        // times has to be cut into five in one pass - cutting it per crossing would leave the
        // later crossings measured against a piece rather than the whole.
        var cuts = new ArrayList<List<double[]>>(lines.size());

        for (var index = 0; index < lines.size(); index++) {
            cuts.add(new ArrayList<>());
        }

        var boxes = measureBoxes(lines);

        // Only lines that share a square of the grid are asked about each other. Asking every
        // pair is a square of the work for an answer that is nearly always no - a sector's
        // worth of cell borders is thousands of lines, of which any one meets a handful.
        for (var neighbours : gatherByGridSquare(boxes).values()) {
            for (var first = 0; first < neighbours.size(); first++) {
                for (var second = first + 1; second < neighbours.size(); second++) {

                    var one = neighbours.get(first);
                    var other = neighbours.get(second);

                    if (!boxes.get(one).overlaps(boxes.get(other))) {
                        continue;
                    }

                    var crossing = findCrossing(
                        lines.get(one).segment(), lines.get(other).segment());

                    if (crossing == null) {
                        continue;
                    }

                    // A pair sharing several squares is asked several times and reports the
                    // same point each time. Harmless: two cuts at one place leave nothing
                    // between them, and a piece of no length is dropped rather than emitted.
                    cuts.get(one).add(crossing);
                    cuts.get(other).add(crossing);
                }
            }
        }

        var pieces = new ArrayList<LabelledWall>(lines.size());

        for (var index = 0; index < lines.size(); index++) {
            addPieces(lines.get(index), cuts.get(index), pieces);
        }
        return pieces;
    }

    // Which lines fall in which square of a grid over the whole set, each line filed under
    // every square its box reaches.
    //
    // The squares are sized so there are about as many of them as there are lines, which puts
    // a handful of lines in each and keeps the pairing inside a square small. A line longer
    // than a square is filed in each one it crosses - a long border is in many squares and is
    // asked about more often, which is the cost of it genuinely being near more things.
    private static Map<GridSquare, List<Integer>> gatherByGridSquare(List<Bounds> boxes) {

        var bySquare = new LinkedHashMap<GridSquare, List<Integer>>();

        if (boxes.isEmpty()) {
            return bySquare;
        }

        var side = measureGridSide(boxes);

        for (var index = 0; index < boxes.size(); index++) {

            var box = boxes.get(index);

            var fromColumn = (long) Math.floor(box.minX() / side);
            var toColumn = (long) Math.floor(box.maxX() / side);
            var fromRow = (long) Math.floor(box.minY() / side);
            var toRow = (long) Math.floor(box.maxY() / side);

            for (var column = fromColumn; column <= toColumn; column++) {
                for (var row = fromRow; row <= toRow; row++) {

                    bySquare
                        .computeIfAbsent(new GridSquare(column, row), square -> new ArrayList<>())
                        .add(index);
                }
            }
        }
        return bySquare;
    }

    // How wide to make a square: the whole set divided into about as many squares as there are
    // lines. Never zero, since a set whose boxes all sit at one point has no spread to divide.
    private static double measureGridSide(List<Bounds> boxes) {

        var minX = Double.POSITIVE_INFINITY;
        var minY = Double.POSITIVE_INFINITY;
        var maxX = Double.NEGATIVE_INFINITY;
        var maxY = Double.NEGATIVE_INFINITY;

        for (var box : boxes) {

            minX = Math.min(minX, box.minX());
            minY = Math.min(minY, box.minY());
            maxX = Math.max(maxX, box.maxX());
            maxY = Math.max(maxY, box.maxY());
        }

        var span = Math.max(maxX - minX, maxY - minY);

        return Math.max(span / Math.sqrt(boxes.size()), Limits.MIN_EDGE_LENGTH);
    }

    // One square of that grid. A record rather than a packed key, so that two squares are the
    // same square by their coordinates rather than by an encoding two callers have to agree on.
    private record GridSquare(long column, long row) {
    }

    // One box per line, measured before the pairing rather than inside it, so the cheap test
    // costs a lookup per pair rather than a measurement.
    private static List<Bounds> measureBoxes(List<LabelledWall> lines) {

        var boxes = new ArrayList<Bounds>(lines.size());

        for (var line : lines) {
            boxes.add(Bounds.computeEnclosingBounds(
                List.of(line.segment().readStart(), line.segment().readEnd())));
        }
        return boxes;
    }

    private static double[] findCrossing(Segment first, Segment second) {

        return Segments.intersectSegments(
            first.readStart(), first.readEnd(), second.readStart(), second.readEnd());
    }

    // Cuts one line at the points gathered for it and adds the pieces. The cuts arrive in
    // whatever order the pairs were asked in, so they are put back into the order they fall
    // along the line before anything is emitted - otherwise a piece would be built between two
    // points that are not neighbours and would run back over its own line.
    private static void addPieces(
            LabelledWall line, List<double[]> cuts, List<LabelledWall> pieces) {

        var start = line.segment().readStart();
        var end = line.segment().readEnd();

        if (Points.computeDistance(start, end) < Limits.MIN_EDGE_LENGTH) {
            return;
        }

        cuts.sort(Comparator.comparingDouble(cut -> measureAlong(start, end, cut)));

        var from = start;

        for (var cut : cuts) {
            addPiece(from, cut, line.label(), pieces);
            from = cut;
        }
        addPiece(from, end, line.label(), pieces);
    }

    // How far along start..end a point lies, as the projection onto the line's own direction.
    // Unscaled, because this only ever orders points on one line against each other and
    // dividing by the length would not change that order.
    private static double measureAlong(double[] start, double[] end, double[] point) {

        return Points.projectPointOnto(
            point[0] - start[0],
            point[1] - start[1],
            end[0] - start[0],
            end[1] - start[1]);
    }

    // A piece, unless it has collapsed. Two lines crossing at the same point both cut here, and
    // a cut landing on an end leaves nothing between it and that end - in both cases what would
    // be added is a piece with no direction, which has no place in a rotation around a vertex.
    private static void addPiece(
            double[] from, double[] to, int label, List<LabelledWall> pieces) {

        if (Points.computeDistance(from, to) < Limits.MIN_EDGE_LENGTH) {
            return;
        }
        pieces.add(new LabelledWall(new Segment(from[0], from[1], to[0], to[1]), label));
    }
}
