package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segment;
import kmlib.math.geometry.Segments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
     * @param segments the lines to cut, in any order
     * @return the pieces, each too short to hold a crossing of its own; lines with no crossings
     *         come back whole, and lines too short to have a direction are dropped
     */
    static List<Segment> splitAtCrossings(List<Segment> segments) {

        // One list of cut points per line rather than per pair, because a line crossed four
        // times has to be cut into five in one pass - cutting it per crossing would leave the
        // later crossings measured against a piece rather than the whole.
        var cuts = new ArrayList<List<double[]>>(segments.size());

        for (var index = 0; index < segments.size(); index++) {
            cuts.add(new ArrayList<>());
        }

        var boxes = measureBoxes(segments);

        for (var first = 0; first < segments.size(); first++) {
            for (var second = first + 1; second < segments.size(); second++) {

                // The box test is not a correctness matter but a cost one: every pair is asked,
                // and most pairs in a sector are nowhere near each other, so the cheap
                // rejection carries nearly all of them.
                if (!boxes.get(first).overlaps(boxes.get(second))) {
                    continue;
                }

                var crossing = findCrossing(segments.get(first), segments.get(second));

                if (crossing == null) {
                    continue;
                }
                cuts.get(first).add(crossing);
                cuts.get(second).add(crossing);
            }
        }

        var pieces = new ArrayList<Segment>(segments.size());

        for (var index = 0; index < segments.size(); index++) {
            addPieces(segments.get(index), cuts.get(index), pieces);
        }
        return pieces;
    }

    // One box per line, measured before the pairing rather than inside it, so the cheap test
    // costs a lookup per pair rather than a measurement.
    private static List<Bounds> measureBoxes(List<Segment> segments) {

        var boxes = new ArrayList<Bounds>(segments.size());

        for (var segment : segments) {
            boxes.add(Bounds.computeEnclosingBounds(
                List.of(segment.readStart(), segment.readEnd())));
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
    private static void addPieces(Segment segment, List<double[]> cuts, List<Segment> pieces) {

        var start = segment.readStart();
        var end = segment.readEnd();

        if (Points.computeDistance(start, end) < Limits.MIN_EDGE_LENGTH) {
            return;
        }

        cuts.sort(Comparator.comparingDouble(cut -> measureAlong(start, end, cut)));

        var from = start;

        for (var cut : cuts) {
            addPiece(from, cut, pieces);
            from = cut;
        }
        addPiece(from, end, pieces);
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
    private static void addPiece(double[] from, double[] to, List<Segment> pieces) {

        if (Points.computeDistance(from, to) < Limits.MIN_EDGE_LENGTH) {
            return;
        }
        pieces.add(new Segment(from[0], from[1], to[0], to[1]));
    }
}
