package kmu.maplayers.base.geometry;

import kmlib.math.geometry.DirectedLine;

import java.util.ArrayList;
import java.util.List;

/**
 * A straight run of boundary between two circles, walling void off from the rest.
 *
 * <p>Two circles and the line it lies on. The circles say which arcs it joins and are what
 * its terminals are named by; the line says where it actually runs, which for a bridge is
 * the line joining the two sites and for a wall the coast smoothing laid down is nowhere
 * near it.
 *
 * <p>The line rather than an angle on each circle, because a wall has to be found again at
 * more than one reach - once at the reach the void is defined at and once at the reach it
 * is drawn at - and a pair of angles only means anything at the reach it was measured at.
 * A line is the same line at any reach, so the sweep recomputes where it meets each circle
 * instead of being handed a stale answer.
 *
 * <p>Vocabulary rather than machinery, which is why it sits here and the tracing does not.
 * Saying that a wall runs between two circles costs nothing and commits to nothing; LAYING one
 * across a trace is the part that carries the accommodations a zero-width wall forced, and that
 * lives in {@code kmu.maplayers.base.geometry.walls} where what may not reach it cannot.
 *
 * @param fromCircle one of the circles it runs between
 * @param toCircle   the other
 * @param line       the line it lies on, unbounded; only the stretch between the two
 *                   circles is boundary, and the sweep works that out for itself
 * @param kind       which sort of wall it is, which decides only whether it is still on
 *                   the boundary - everything else here treats the two alike
 */
public record Chord(
    int fromCircle,
    int toCircle,
    DirectedLine line,
    WallKind kind) {

    // Where the wall begins and ends, which is the stretch of its line that is actually a
    // wall. Asked of the wall itself because everything that draws one, measures to one or
    // walks across one needs the same two points, and rebuilding them from the origin and
    // the direction is four arithmetic expressions that can each be got wrong.
    public double[] findStart() {
        return new double[] {line.originX(), line.originY()};
    }

    public double[] findEnd() {
        return new double[] {
            line.originX() + line.directionX(), line.originY() + line.directionY()};
    }

    // Whichever of the two ends sits on the named circle. A wall runs between two, and
    // which end is on which is fixed when the wall is built.
    public double[] findEndOn(int circle) {
        return circle == fromCircle ? findStart() : findEnd();
    }

    /**
     * The bridges as the chords they become on the boundary.
     *
     * <p>A bridge already knows the line it lies on: its two ends are where the gap it spans
     * meets the two cells, so the line through them is the line joining the sites. Taken from
     * the gap rather than recomputed from the sites, so the wall lands on the run the bridge
     * was chosen for rather than on a line that merely ought to be the same.
     *
     * @param bridges the bridges, as the search for them handed them over
     * @return one chord per bridge, in the order they were offered
     */
    public static List<Chord> buildChordsFrom(List<CellGap> bridges) {
        return buildChordsFrom(bridges, WallKind.BRIDGE);
    }

    /**
     * Spans as the chords they become on the boundary, each carrying the water it was laid
     * over.
     *
     * @param spans the spans
     * @param kind  which water they cross, which is what a hole they close is later read as
     * @return one chord per span, on the line joining its two sites
     */
    public static List<Chord> buildChordsFrom(List<CellGap> spans, WallKind kind) {

        var chords = new ArrayList<Chord>(spans.size());

        for (var span : spans) {

            chords.add(new Chord(
                span.fromSite(),
                span.toSite(),
                new DirectedLine(
                    span.start()[0],
                    span.start()[1],
                    span.end()[0] - span.start()[0],
                    span.end()[1] - span.start()[1]),
                kind));
        }
        return chords;
    }
}
