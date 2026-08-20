package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.DirectedLine;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The boundary of a {@link DiscUnion}, as closed cycles of circular arcs.
 *
 * <p>A point is void when its nearest site is further than the reach, so the void is exactly
 * the complement of the union. The boundary of that union is a set of closed cycles, and each
 * cycle is either the outer silhouette of a run of overlapping cells or a hole enclosed by
 * them. The holes are the void the cells bind.
 *
 * <p>The shared radius is what makes this cheap and exact rather than a general shape union:
 * the part of one circle lying inside another is a single angular interval computed in closed
 * form, and each circle's boundary arcs are the gaps left when its neighbours' intervals are
 * merged.
 *
 * <p>The arcs link without matching any coordinates. An arc ends where its circle enters some
 * neighbour's disc, and the boundary continues on that neighbour from the point where the
 * neighbour LEAVES the first disc - a point named by the pair of circles rather than by its
 * position, so two circles computing it separately and landing a rounding apart still agree.
 *
 * <p>{@link Walls} join that walk as covering intervals of their own. Where a neighbouring
 * disc takes a stretch of circle out of the boundary, a chord takes out the mouth it comes
 * through - the stretch lying within a channel of the wall, which {@link WallMouths} works
 * out. The arcs either side of that mouth are then the two sides of the chord, one bounding
 * the void on each side of it, and the channel between them is the gap the two never claim.
 *
 * <p>So a chord is exactly a cover whose ends are named by the chord rather than by a
 * neighbouring circle, and one sweep merges both kinds. Nothing is offset and nothing is cut:
 * every vertex of every shape is a point of some circle, arrived at by arithmetic rather than
 * by finding the nearest sample on a finished outline.
 *
 * <p>Flattened onto the cells' own bound. A cell's radius bound is a regular polygon whose
 * vertices sit at ABSOLUTE angles - the same set of angles on every circle - and an arc is
 * sampled at exactly those angles and at no others. A stretch of boundary and the stretch of
 * cell bound it runs along are then ONE chain of points rather than two chains of the same
 * density at a different phase, which is what lets a piece of traced void be handed to the
 * machinery that shapes and clusters cells: two outlines sharing no vertex cannot be told to
 * abut.
 *
 * <p>Shared because the reach is a parameter rather than a fact: run at the true reach it
 * finds the pockets, run at the reach plus the channel it finds what is left of them once the
 * channel is taken out, and run at the reach minus it, what they become when the cells fill
 * right up to them. Every consumer that wants a pocket at some reach wants this, and there is
 * now more than one of them.
 */
final class DiscUnionBoundary {

    // Stands in for a neighbouring circle where there is none: a disc that overlaps nothing
    // contributes its whole circle as one arc, with no disc entered or left at either end.
    private static final int NO_CIRCLE = -1;

    // Stands in for the arc a cycle continues onto where there is none, which is a walk that
    // has run off the end of a broken chain rather than closed.
    private static final int NO_SUCCESSOR = -1;

    // No arc begins here, so an arc pointed at it simply has no successor. Beyond the range
    // either kind of real terminal can reach, rather than a value one of them might produce.
    private static final long NO_TERMINAL = Long.MIN_VALUE;

    // Which of a chord's two circles a terminal belongs to. A chord meets each of its circles
    // once, so the pair of side markers names its two mouths and nothing else does.
    private static final int FROM_SIDE = 0;
    private static final int TO_SIDE = 1;

    // Nothing laid across the void, for the callers that want the cells' own boundary. Its
    // channel is zero, which the pairing refuses for any real wall and does not need here:
    // with no chords there is no mouth for a channel to size.
    private static final Walls NO_WALLS = new Walls(List.of(), 0);

    private DiscUnionBoundary() {
    }

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
     * @param fromCircle one of the circles it runs between
     * @param toCircle   the other
     * @param line       the line it lies on, unbounded; only the stretch between the two
     *                   circles is boundary, and the sweep works that out for itself
     * @param kind       which sort of wall it is, which decides only whether it is still on
     *                   the boundary - everything else here treats the two alike
     */
    record Chord(
        int fromCircle,
        int toCircle,
        DirectedLine line,
        WallKind kind) {
    }

    /**
     * The two sorts of wall, which sit on the boundary in different ways.
     *
     * <p>A named kind rather than a flag, and carried by the wall rather than passed to the
     * test, because it is a fact about how the line was arrived at: a bridge is the line
     * joining two sites and a reach of coast is a tangent the smoothing drew. Nothing else in
     * the walk asks - both open a mouth, both take a stretch of circle out of the boundary,
     * and both close a cycle.
     */
    enum WallKind {

        /**
         * The line joining two sites, spanning the gap between their cells. It crosses its
         * circles steeply and squarely between them, so the two edges of its mouth say
         * whether the gap it spans is still there: buried, and the cells have closed over it.
         */
        BRIDGE,

        /**
         * A straight run the coast smoothing drew from one cell's frontage to another's. It
         * LEAVES along a tangent and can end exactly where two circles cross, so half its
         * mouth lies inside the neighbouring disc whatever the reach did - its own end is the
         * only thing that says whether it is on the boundary.
         */
        COAST_REACH
    }

    /**
     * The bridges as the chords they become on the boundary.
     *
     * <p>A bridge already knows the line it lies on: its two ends are where the gap it spans
     * meets the two cells, so the line through them is the line joining the sites. Taken from
     * the gap rather than recomputed from the sites, so the wall lands on the run the bridge
     * was chosen for rather than on a line that merely ought to be the same.
     *
     * @param bridges the bridges, as {@link VoidBridges} found them
     * @return one chord per bridge, in the order they were offered
     */
    static List<Chord> buildChordsFrom(List<CellGaps.CellGap> bridges) {

        var chords = new ArrayList<Chord>(bridges.size());

        for (var bridge : bridges) {

            chords.add(new Chord(
                bridge.fromSite(),
                bridge.toSite(),
                new DirectedLine(
                    bridge.start()[0],
                    bridge.start()[1],
                    bridge.end()[0] - bridge.start()[0],
                    bridge.end()[1] - bridge.start()[1]),
                WallKind.BRIDGE));
        }
        return chords;
    }

    /**
     * The walls to lay across the void: which chords, and the channel every one keeps.
     *
     * <p>One value because neither half means anything without the other. A chord list with
     * no channel is not a harmless default - the two pockets either side of every wall then
     * close on the same line and read as one mass, and a zero-width mouth corrupts the cover
     * sweep's bookkeeping besides - so the pairing refuses it outright rather than trusting
     * every caller to remember.
     *
     * @param chords  the walls, as pairs of circles
     * @param channel how far each side of a wall holds back from it
     */
    record Walls(
        List<Chord> chords,
        double channel) {

        Walls {
            if (!chords.isEmpty() && channel <= 0) {
                throw new IllegalArgumentException("walls need a channel to keep");
            }
        }
    }

    /**
     * Every hole in the union.
     *
     * <p>Run at the true reach it finds the pockets; run at the reach plus the channel it
     * finds what is left of them once the channel is taken out; run at the reach minus it,
     * what they become when the cells fill right up to them.
     *
     * <p>Run afresh at each reach rather than redrawing one ring's arcs at another radius. A
     * ring is not the same ring at a different reach: a cell whose arc its neighbours have
     * swallowed drops out of it, and a pocket can pinch in two. Redrawing in place cannot
     * express either, and reads both as the pocket having closed.
     *
     * @param union         the discs to trace
     * @param boundSegments sides of the cells' own radius bound, whose vertex angles every
     *                      arc is flattened onto
     * @return the holes, wound the way any other filled shape is
     */
    static List<VoidHole> traceHoles(DiscUnion union, int boundSegments) {
        return traceHolesAcrossWalls(union, NO_WALLS, boundSegments);
    }

    /**
     * The same holes, with the given walls laid across the boundary.
     *
     * <p>A wall shuts void on one side of it off from void on the other, so a bay that was
     * open to the rest of the map becomes a closed cycle - a pocket in exactly the sense an
     * enclosed one is, and told apart from the silhouette it was cut from by which way it
     * winds. Nothing has to ask which piece holds the cells.
     *
     * <p>The channel is what keeps the two sides apart: each side closes on its own line half
     * a channel out, and the strip between them belongs to neither. It costs nothing to
     * trace, because moving the wall sideways only moves where it meets each circle - which
     * is still one angle, still exact.
     *
     * <p>A wall whose mouth is buried inside some other disc is not on the boundary at all,
     * and is dropped. That is what becomes of one whose two cells have already closed over
     * at this reach, so a bridge too short to still be a gap costs nothing to offer.
     *
     * @param union         the discs to trace
     * @param walls         the walls to lay across the void
     * @param boundSegments sides of the cells' own radius bound, whose vertex angles every
     *                      arc is flattened onto
     * @return the holes, wound the way any other filled shape is
     */
    static List<VoidHole> traceHolesAcrossWalls(
            DiscUnion union,
            Walls walls,
            int boundSegments) {

        var holes = new ArrayList<VoidHole>();

        for (var cycle : traceCycles(union, walls, boundSegments)) {

            // A cycle is a hole when it winds the opposite way to a silhouette. Each arc is
            // walked anticlockwise on its own circle, which keeps the discs' interior to the
            // left the whole way round, so an outer cycle comes out anticlockwise and a hole
            // clockwise.
            if (isHole(cycle)) {

                var shape = cycle.shape();

                // Reversed so a hole reads the same way round as any other filled shape.
                var boundary = new ArrayList<>(shape.boundary());
                java.util.Collections.reverse(boundary);

                holes.add(new VoidHole(
                    boundary,
                    shape.corners(),
                    shape.ringing(),
                    shape.reach(),
                    shape.walledBy()));
            }
        }
        return holes;
    }

    /**
     * The outer silhouettes, as the run of coast marks each is made of.
     *
     * <p>The other half of what the walk finds, and the half {@link #traceHolesAcrossWalls}
     * throws away. A silhouette is where a run of connected cells meets the open void, so it
     * is the outline anything smoothing the outer edge has to start from.
     *
     * <p>Handed back as marks in walk order rather than as a sampled outline, because what a
     * smoother wants is one point per stretch of coast and the order they are passed in - and
     * the walk already knows both. Recovering that from a finished polyline would mean
     * working out which sample belongs to which cell, which is the kind of finding that
     * strays.
     *
     * @param union         the discs to trace
     * @param walls         the walls to lay across the void
     * @param boundSegments sides of the cells' own radius bound, whose vertex angles every
     *                      arc is flattened onto, to tell a silhouette from a hole by the
     *                      area it comes out with
     * @return one run of marks per silhouette, in walk order
     */
    static List<List<CoastMark>> traceSilhouetteCoasts(
            DiscUnion union,
            Walls walls,
            int boundSegments) {

        var coasts = new ArrayList<List<CoastMark>>();

        for (var cycle : traceCycles(union, walls, boundSegments)) {

            if (isHole(cycle)) {
                continue;
            }

            var marks = new ArrayList<CoastMark>(cycle.arcs().size());

            for (var arc : cycle.arcs()) {

                marks.add(new CoastMark(arc.circle(), arc.fromAngle(), arc.toAngle()));
            }
            coasts.add(marks);
        }
        return coasts;
    }

    /**
     * Where two laid walls cross each other, which the walk does not yet know about.
     *
     * <p>Each wall is walked from its mouth on one circle to its mouth on the other, so where
     * two of them cross, each runs on past the crossing into space the other closes. That
     * overshoot is the wedge between their lines, and it is the one shape on this map bounded
     * by something the trace never asks about.
     *
     * <p>Counted before it is fixed: a handful of crossings is a case to teach the walk, and
     * hundreds would be a different construction.
     *
     * @param union the discs the walls are laid across
     * @param walls the walls on offer
     * @return one entry per crossing pair, as {@code {fromA, toA, fromB, toB}} cells
     */
    static List<int[]> findWallCrossings(DiscUnion union, Walls walls) {

        var laid = findAttachableChords(union, walls);
        var crossings = new ArrayList<int[]>();

        for (var one = 0; one < laid.size(); one++) {
            for (var other = one + 1; other < laid.size(); other++) {

                if (doWallsCross(laid.get(one), laid.get(other))) {

                    crossings.add(new int[] {
                        laid.get(one).fromCircle(),
                        laid.get(one).toCircle(),
                        laid.get(other).fromCircle(),
                        laid.get(other).toCircle()});
                }
            }
        }
        return crossings;
    }

    // Whether two walls cross within both of their own runs, rather than on the lines they
    // lie along - a wall bounds void only where it actually runs.
    private static boolean doWallsCross(Chord one, Chord other) {

        var oneFrom = new double[] {one.line().originX(), one.line().originY()};
        var oneTo = new double[] {
            one.line().originX() + one.line().directionX(),
            one.line().originY() + one.line().directionY()};
        var otherFrom = new double[] {other.line().originX(), other.line().originY()};
        var otherTo = new double[] {
            other.line().originX() + other.line().directionX(),
            other.line().originY() + other.line().directionY()};

        return turnsLeft(oneFrom, oneTo, otherFrom) != turnsLeft(oneFrom, oneTo, otherTo)
            && turnsLeft(otherFrom, otherTo, oneFrom) != turnsLeft(otherFrom, otherTo, oneTo);
    }

    private static boolean turnsLeft(double[] from, double[] to, double[] point) {

        return (to[0] - from[0]) * (point[1] - from[1])
            - (to[1] - from[1]) * (point[0] - from[0]) > 0;
    }

    /**
     * Where the walk runs off the end of the boundary instead of closing.
     *
     * <p>Every arc names the terminal it runs on to, and that terminal is another arc's
     * beginning - the whole walk is that pairing. An arc whose terminal nothing begins at is a
     * broken link: the run through it cannot close, so every cycle it belonged to is thrown
     * away, and void that IS enclosed comes back as no pocket at all.
     *
     * <p>Wanted by anything asking why a shape that looks shut in was not found. A count of
     * pockets cannot say; the point where the chain snapped can.
     *
     * @param union the discs to trace
     * @param walls the walls to lay across the void
     * @return one point per broken link, where the arc ended
     */
    static List<double[]> findBrokenLinks(DiscUnion union, Walls walls) {

        var laid = new Walls(findAttachableChords(union, walls), walls.channel());
        var arcs = findUncoveredArcs(union, laid);
        var successors = linkArcsIntoCycles(arcs);
        var broken = new ArrayList<double[]>();

        for (var index = 0; index < arcs.size(); index++) {

            if (successors[index] == NO_SUCCESSOR) {

                var arc = arcs.get(index);

                broken.add(findPointOnCircle(
                    union.sites().get(arc.circle()), union.reach(), arc.toAngle()));
            }
        }
        return broken;
    }

    /**
     * The chords that can actually be laid at this reach, which are the only ones traced.
     *
     * <p>Wanted by anything measuring the result: a chord that was dropped has no business
     * being looked for on an outline, and one that was laid has to be on one.
     *
     * <p>Two things stop a chord. Its own END can lie inside another disc, which is what
     * becomes of a bridge whose cells have closed over. Or its mouth can be swallowed whole by
     * a mouth an earlier chord on the same circle already took, leaving it no terminal of its
     * own to leave from or land on once the sweep merges the two.
     *
     * <p>The END, not the two edges of the mouth it opens. A mouth is a channel wide, so a
     * wall ending where two circles cross has half of it inside the neighbouring disc whatever
     * the wall did - and refusing it there leaves the void the wall shut in open to the sea
     * with the line still drawn across it.
     *
     * <p>Sharing PART of a mouth is allowed. Both walls keep an outer terminal, and the
     * sweep hands one straight on to the next rather than choosing between them.
     *
     * <p>How much this has to rule on depends on the reach it is asked at, because that is
     * what decides how wide a mouth is. Walked on the cells' own border, no two mouths on a
     * circle overlap at all and neither rule fires. Walked a channel outside it, where a wall
     * cuts into its cells rather than meeting them, mouths widen enough that a few dozen
     * pairs overlap on each sector and a handful swallow one another.
     *
     * <p>Offered in order and taken greedily, so the caller's own ordering decides which of
     * two crowding chords survives.
     *
     * @param union the discs the walls are laid across
     * @param walls the walls on offer
     * @return the chords that can be laid, in the order they were offered
     */
    static List<Chord> findAttachableChords(DiscUnion union, Walls walls) {

        var takenByCircle = new LinkedHashMap<Integer, List<double[]>>();
        var attachable = new ArrayList<Chord>();

        for (var chord : walls.chords()) {

            var fromMouth = WallMouths.measureMouth(union, chord, chord.fromCircle(), walls.channel());
            var toMouth = WallMouths.measureMouth(union, chord, chord.toCircle(), walls.channel());

            if (fromMouth == null
                    || toMouth == null
                    || !isWallOnBoundary(union, chord, chord.fromCircle(), fromMouth)
                    || !isWallOnBoundary(union, chord, chord.toCircle(), toMouth)
                    || isCrowdedOut(takenByCircle, chord, fromMouth, toMouth)) {

                continue;
            }
            recordMouth(takenByCircle, chord.fromCircle(), fromMouth);
            recordMouth(takenByCircle, chord.toCircle(), toMouth);

            attachable.add(chord);
        }
        return attachable;
    }

    /**
     * Why one wall was not laid, as the rule that stopped it.
     *
     * <p>{@link #findAttachableChords} answers WHICH walls were laid, which is what the walk
     * needs and nothing at all to someone looking at a gap on screen and asking why the line
     * across it holds nothing back. The refusals are not interchangeable: a wall whose end is
     * covered has cells that met without it, and one crowded out is competing with a
     * neighbour - the first is geometry, the second is ordering.
     *
     * <p>Replays the greedy walk rather than testing the wall alone, because whether a mouth
     * was already taken depends on every wall offered before it.
     *
     * @param union the discs the walls are laid across
     * @param walls the walls on offer, in the order they are offered
     * @param wall  the one to ask about, which must be among them
     * @return what became of it
     */
    static ChordRefusal describeChordRefusal(DiscUnion union, Walls walls, Chord wall) {

        var takenByCircle = new LinkedHashMap<Integer, List<double[]>>();
        var takers = new LinkedHashMap<Integer, List<TakenMouth>>();
        var answer = new ChordRefusal(RefusalReason.NOT_OFFERED, null);

        for (var chord : walls.chords()) {

            var fromMouth = WallMouths.measureMouth(union, chord, chord.fromCircle(), walls.channel());
            var toMouth = WallMouths.measureMouth(union, chord, chord.toCircle(), walls.channel());
            var refusal = judgeChord(union, chord, fromMouth, toMouth, takenByCircle, takers);

            if (refusal.reason() == RefusalReason.LAID) {

                recordMouth(takenByCircle, chord.fromCircle(), fromMouth);
                recordMouth(takenByCircle, chord.toCircle(), toMouth);
                recordTaker(takers, chord.fromCircle(), fromMouth, chord);
                recordTaker(takers, chord.toCircle(), toMouth, chord);
            }

            if (chord == wall) {
                answer = refusal;
            }
        }
        return answer;
    }

    // One wall's verdict, in the same order findAttachableChords asks its questions - the two
    // read the same rules, and a verdict that disagreed with what was laid would be worse than
    // no verdict at all.
    private static ChordRefusal judgeChord(
            DiscUnion union,
            Chord chord,
            double[] fromMouth,
            double[] toMouth,
            Map<Integer, List<double[]>> takenByCircle,
            Map<Integer, List<TakenMouth>> takers) {

        if (fromMouth == null || toMouth == null) {
            return new ChordRefusal(RefusalReason.NO_MOUTH, null);
        }

        if (!isWallOnBoundary(union, chord, chord.fromCircle(), fromMouth)) {
            return new ChordRefusal(
                RefusalReason.OFF_BOUNDARY, "on cell " + chord.fromCircle());
        }

        if (!isWallOnBoundary(union, chord, chord.toCircle(), toMouth)) {
            return new ChordRefusal(RefusalReason.OFF_BOUNDARY, "on cell " + chord.toCircle());
        }

        if (isCrowdedOut(takenByCircle, chord, fromMouth, toMouth)) {
            return new ChordRefusal(
                RefusalReason.CROWDED_OUT, nameTaker(takers, chord, fromMouth, toMouth));
        }
        return new ChordRefusal(RefusalReason.LAID, null);
    }

    /**
     * What became of one wall the walk was offered.
     *
     * @param reason what the walk did with it
     * @param detail which cell or which rival wall made that the answer, or null where the
     *               reason says the whole of it
     */
    record ChordRefusal(RefusalReason reason, String detail) {

        @Override
        public String toString() {
            return detail == null ? reason.describe() : reason.describe() + " " + detail;
        }
    }

    /** The verdicts the walk can reach about a wall it was offered. */
    enum RefusalReason {

        /** Laid, and the boundary runs along it. */
        LAID("laid"),

        /** The wall passes too far from one of its circles to open a mouth on it at all. */
        NO_MOUTH("no mouth"),

        /** One of its sides lies inside another disc, so it is not on the boundary. */
        OFF_BOUNDARY("off the boundary"),

        /** A wall already laid holds the mouth this one would have left from or landed on. */
        CROWDED_OUT("crowded out by"),

        /** Not among the walls offered, so the walk never ruled on it. */
        NOT_OFFERED("not offered");

        private final String wording;

        RefusalReason(String wording) {
            this.wording = wording;
        }

        String describe() {
            return wording;
        }
    }

    /**
     * A mouth a laid wall holds, and the wall holding it.
     *
     * @param mouth the stretch of circle taken, as {@code {start, width}}
     * @param wall  the wall that took it
     */
    private record TakenMouth(double[] mouth, Chord wall) {
    }

    private static void recordTaker(
            Map<Integer, List<TakenMouth>> takers,
            int circle,
            double[] mouth,
            Chord chord) {

        takers.computeIfAbsent(circle, key -> new ArrayList<>())
            .add(new TakenMouth(mouth, chord));
    }

    // The laid wall whose mouth swallowed this one, named by its own cells so a crowding
    // refusal points at its rival rather than leaving it to be hunted for.
    private static String nameTaker(
            Map<Integer, List<TakenMouth>> takers,
            Chord chord,
            double[] fromMouth,
            double[] toMouth) {

        var named = nameTakerOn(takers, chord.fromCircle(), fromMouth);

        return named != null ? named : nameTakerOn(takers, chord.toCircle(), toMouth);
    }

    private static String nameTakerOn(
            Map<Integer, List<TakenMouth>> takers,
            int circle,
            double[] mouth) {

        for (var taken : takers.getOrDefault(circle, List.of())) {

            if (isMouthTaken(Map.of(circle, List.of(taken.mouth())), circle, mouth)) {

                return taken.wall().kind() + " " + taken.wall().fromCircle()
                    + "-" + taken.wall().toCircle() + " on cell " + circle;
            }
        }
        return "an earlier wall";
    }

    /**
     * The two lines a laid chord actually becomes, as their end points.
     *
     * <p>A chord with a channel is two lines rather than one: each runs half a channel from
     * the centre, so each meets the two circles at its own pair of points, and the void on
     * one side of the wall closes on one of them while the void on the other side closes on
     * the other. Handed back paired rather than as four loose points, because the pairing is
     * not the obvious one - a line leaves one circle on the near edge of its mouth and
     * arrives at the other on the FAR edge, since the two circles face opposite ways.
     *
     * <p>No search and no snapping: the mouth's half-width is the angle whose sine is the
     * channel over the reach, and the ends are that angle either side of facing.
     *
     * @param union   the discs the chord runs between
     * @param chord   the chord
     * @param channel how far each side of it holds back from the centre
     * @return the two lines, each as its pair of end points
     */
    static List<List<double[]>> findChordSides(
            DiscUnion union,
            Chord chord,
            double channel) {

        var fromMouth = WallMouths.measureMouth(union, chord, chord.fromCircle(), channel);
        var toMouth = WallMouths.measureMouth(union, chord, chord.toCircle(), channel);

        if (fromMouth == null || toMouth == null) {
            return List.of();
        }
        var from = union.sites().get(chord.fromCircle());
        var to = union.sites().get(chord.toCircle());

        return List.of(
            List.of(
                findPointOnCircle(from, union.reach(), fromMouth[0]),
                findPointOnCircle(to, union.reach(), toMouth[0] + toMouth[1])),
            List.of(
                findPointOnCircle(to, union.reach(), toMouth[0]),
                findPointOnCircle(from, union.reach(), fromMouth[0] + fromMouth[1])));
    }

    // The holes the channel leaves inside one pocket - none when it closes over, more than
    // one when it pinches the pocket in two. A point on a narrowed hole's outline is further
    // from every site than the true reach, so it lies in the true void, and in the very
    // pocket it came out of.
    static List<List<double[]>> findHolesInside(List<VoidHole> narrowed, VoidHole hole) {

        var inside = new ArrayList<List<double[]>>();
        for (var candidate : narrowed) {

            var probe = candidate.boundary().get(0);

            if (PolygonRegions.isPointInsideRing(hole.boundary(), probe[0], probe[1])) {
                inside.add(candidate.boundary());
            }
        }
        return inside;
    }

    // The whole walk, in the order its four steps depend on each other: drop the walls that
    // cannot attach, cut every circle into the arcs nothing covers, join each arc to the one
    // it names, then follow the joins round until they close.
    private static List<TracedCycle> traceCycles(
            DiscUnion union,
            Walls walls,
            int boundSegments) {

        var laid = new Walls(findAttachableChords(union, walls), walls.channel());
        var arcs = findUncoveredArcs(union, laid);
        var successors = linkArcsIntoCycles(arcs);
        var cycles = new ArrayList<TracedCycle>();
        var walked = new boolean[arcs.size()];

        for (var start = 0; start < arcs.size(); start++) {

            var cycle = walkCycleFrom(start, arcs, successors, walked);
            if (cycle.isEmpty()) {
                continue;
            }

            var built = buildHole(cycle, arcs, union, laid.chords(), boundSegments);
            if (built != null) {
                cycles.add(new TracedCycle(collectArcs(cycle, arcs), built));
            }
        }
        return cycles;
    }

    // Walked anticlockwise on every circle, which keeps the discs' interior to the left the
    // whole way round - so an outer cycle comes out anticlockwise and a hole clockwise, and
    // the sign of the area is the whole test.
    private static boolean isHole(TracedCycle cycle) {
        return PolygonRegions.computeSignedArea(cycle.shape().boundary()) < 0;
    }

    // A walked cycle as the arcs it ran along rather than as indices into every arc on the
    // map, so nothing downstream has to keep the two lists side by side to read one of them.
    private static List<Arc> collectArcs(List<Integer> cycle, List<Arc> arcs) {

        var walked = new ArrayList<Arc>(cycle.size());

        for (var index : cycle) {
            walked.add(arcs.get(index));
        }
        return walked;
    }

    // Every stretch of every circle that nothing covers, which is the whole boundary of the
    // union - outer silhouettes and holes alike, not yet told apart - with the walls' mouths
    // taken out of it alongside the neighbouring discs.
    private static List<Arc> findUncoveredArcs(DiscUnion union, Walls walls) {

        var arcs = new ArrayList<Arc>();
        for (var circle = 0; circle < union.sites().size(); circle++) {
            arcs.addAll(findUncoveredArcsOn(circle, union, walls));
        }
        return arcs;
    }

    // One circle's uncovered stretches, as the gaps between everything that covers it. Kept
    // as angles rather than points so the two ends of a gap stay attributed to whatever made
    // them, be that a neighbouring disc or a chord's mouth.
    private static List<Arc> findUncoveredArcsOn(
            int circle,
            DiscUnion union,
            Walls walls) {

        var covers = findCoveringIntervals(circle, union);
        covers.addAll(findChordCovers(circle, union, walls));

        if (covers.isEmpty()) {

            // A disc that nothing overlaps and no chord reaches is a closed cycle on its own.
            var whole = formatDiscTerminal(circle, NO_CIRCLE, union.sites().size());
            return List.of(new Arc(circle, 0, Angles.FULL_TURN, whole, whole));
        }
        return buildArcsBetweenCovers(circle, covers);
    }

    // The stretch of one circle lying inside a neighbour's disc. With equal radii the two
    // circles cross symmetrically about the line joining the sites, so the interval is the
    // half-angle whose cosine is half the separation over the reach, either side of that
    // line - no intersection points needed to find it.
    private static List<Cover> findCoveringIntervals(int circle, DiscUnion union) {

        var sites = union.sites();
        var centre = sites.get(circle);
        var covers = new ArrayList<Cover>();

        for (var other = 0; other < sites.size(); other++) {

            if (other == circle) {
                continue;
            }
            var separation = Points.computeDistance(centre, sites.get(other));

            if (separation >= 2 * union.reach() || separation == 0) {
                continue;
            }
            var towards = measureAngleTowards(union, circle, other);
            var halfWidth = Math.acos(separation / (2 * union.reach()));

            covers.add(new Cover(
                Angles.normalise(towards - halfWidth),
                2 * halfWidth,
                formatDiscTerminal(other, circle, sites.size()),
                formatDiscTerminal(circle, other, sites.size()),
                null));
        }
        return covers;
    }

    // The stretch of one circle taken up by a chord's mouth: the arc facing the circle at the
    // chord's far end, wide enough for the channel to pass through. The boundary arriving at
    // its near edge leaves along the chord and reappears at the far circle's own mouth; the
    // boundary leaving its far edge is where the chord coming the other way put it down.
    private static List<Cover> findChordCovers(
            int circle,
            DiscUnion union,
            Walls walls) {

        var covers = new ArrayList<Cover>();

        for (var index = 0; index < walls.chords().size(); index++) {

            var chord = walls.chords().get(index);
            var isFromSide = chord.fromCircle() == circle;

            if (!isFromSide && chord.toCircle() != circle) {
                continue;
            }
            var mouth = WallMouths.measureMouth(union, chord, circle, walls.channel());

            if (mouth == null) {
                continue;
            }

            covers.add(new Cover(
                mouth[0],
                mouth[1],
                formatChordTerminal(index, isFromSide ? TO_SIDE : FROM_SIDE),
                formatChordTerminal(index, isFromSide ? FROM_SIDE : TO_SIDE),
                chord.kind()));
        }
        return covers;
    }

    // The gaps a circle's covers leave between them, swept once round in order. Each gap runs
    // from wherever the last cover let go to wherever the next takes hold, so its two ends are
    // named by the covers that made them and nothing has to be matched up by position.
    private static List<Arc> buildArcsBetweenCovers(int circle, List<Cover> covers) {

        covers.sort(Comparator.comparingDouble(Cover::start));

        var origin = covers.get(0).start();
        var windowEnd = origin + Angles.FULL_TURN;

        // An interval running past the far end of the window covers the near end of it as
        // well, so the sweep has to start already covered up to wherever that reaches.
        // Without this the sweep reports a gap that the wrapping interval actually fills.
        var coveredTo = origin;
        var departingFrom = NO_TERMINAL;

        for (var cover : covers) {

            var wrapped = cover.start() + cover.width() - Angles.FULL_TURN;

            if (wrapped > coveredTo) {
                coveredTo = wrapped;
                departingFrom = cover.departure();
            }
        }

        var arcs = new ArrayList<Arc>();
        for (var cover : covers) {

            if (cover.start() > coveredTo) {

                arcs.add(new Arc(
                    circle, coveredTo, cover.start(), departingFrom, cover.arrival()));

            } else if (isChordTerminal(departingFrom) && isChordTerminal(cover.arrival())) {

                // Two walls whose mouths overlap. The boundary comes back along one and leaves
                // along the next with nothing on the circle in between, so the stretch between
                // them is empty rather than absent - given as an arc of no width, since what
                // the walk wants of it is that its two terminals name each other rather than
                // any length.
                //
                // Only where WALLS are what overlap. A stretch a neighbouring disc swallows
                // really is off the boundary, and joining its ends would run a cycle through
                // space the union covers - which is why a mouth overlapping a disc is refused
                // outright, leaving this the only kind of overlap that reaches here.
                arcs.add(new Arc(
                    circle, coveredTo, coveredTo, departingFrom, cover.arrival()));

                // A mouth swallowed WHOLE reaches no further round the circle than the one
                // that swallowed it, so the update below never runs for it and its far
                // terminal is dropped - leaving that wall an arrival and no departure, which
                // is a chain the walk runs off rather than a cycle. Handing the departure on
                // makes the swallowed wall the one the next stretch of boundary leaves from,
                // which is what the two of them do on the map: the boundary goes out along
                // one wall and back along the other with nothing on the circle in between.
                //
                // A REACH OF COAST only. A coast leaves a cell along its tangent, so the
                // mouth it arrives by swallows the mouth it leaves by wherever the coast
                // turns on a cell, and both walls are real. A bridge crosses its circles
                // squarely, so its mouth is swallowed only once the gap it spans has closed
                // over - and a wall across a gap that is no longer there is exactly what must
                // not be laid.
                if (cover.kind() == WallKind.COAST_REACH
                        && cover.start() + cover.width() <= coveredTo) {

                    departingFrom = cover.departure();
                }
            }

            if (cover.start() + cover.width() > coveredTo) {

                coveredTo = cover.start() + cover.width();
                departingFrom = cover.departure();
            }
        }
        if (coveredTo < windowEnd) {

            arcs.add(new Arc(
                circle, coveredTo, windowEnd, departingFrom, covers.get(0).arrival()));
        }
        return arcs;
    }

    // Which arc the boundary continues onto, as a plain join: every arc names the terminal it
    // begins at and the terminal it runs on to, and the two are the same name. That holds for
    // a crossing between two discs and for a chord's two mouths alike, so nothing here has to
    // know which kind it is looking at.
    private static int[] linkArcsIntoCycles(List<Arc> arcs) {

        var byStart = new LinkedHashMap<Long, Integer>();

        for (var index = 0; index < arcs.size(); index++) {
            byStart.put(arcs.get(index).startsAt(), index);
        }

        var successors = new int[arcs.size()];

        for (var index = 0; index < arcs.size(); index++) {

            var next = byStart.get(arcs.get(index).endsAt());
            successors[index] = next == null ? NO_SUCCESSOR : next;
        }
        return successors;
    }

    // One cycle, followed from a starting arc until it returns to it. A run that stops on an
    // arc it has already walked, rather than on the one it set out from, is a chain leading
    // into some earlier cycle and not a cycle of its own.
    private static List<Integer> walkCycleFrom(
            int start,
            List<Arc> arcs,
            int[] successors,
            boolean[] walked) {

        if (walked[start]) {
            return List.of();
        }

        var cycle = new ArrayList<Integer>();
        var at = start;

        while (at != NO_SUCCESSOR && !walked[at]) {

            walked[at] = true;
            cycle.add(at);
            at = successors[at];
        }
        // A run that stopped on an arc it had already walked, rather than on the one it
        // started from, is a chain into an earlier cycle and not a cycle of its own.
        return at == start ? cycle : List.of();
    }

    // A walked cycle as the shape it stands for: its arcs sampled into a closed outline, the
    // corners where they meet, the cells it runs on and the walls it closes against. Null
    // where too few points came back to enclose anything, which is a cycle that has collapsed
    // rather than a pocket too small to matter.
    private static VoidHole buildHole(
            List<Integer> cycle,
            List<Arc> arcs,
            DiscUnion union,
            List<Chord> laid,
            int boundSegments) {

        var boundary = new ArrayList<double[]>();
        var corners = new ArrayList<double[]>(cycle.size());
        var ringing = new LinkedHashSet<Integer>();
        var walledBy = new LinkedHashSet<Chord>();

        for (var index : cycle) {

            var arc = arcs.get(index);

            ringing.add(arc.circle());

            var points = sampleArc(
                union.sites().get(arc.circle()),
                union.reach(),
                arc.fromAngle(),
                arc.toAngle(),
                boundSegments);

            corners.add(points.get(0));
            boundary.addAll(points);

            // An arc running on to a chord has to carry its own far end, because the next arc
            // round the cycle begins on the OTHER circle and so puts in the chord's far end
            // rather than this one. Left off, the straight run would start a sample short of
            // where the chord actually does, which is the whole failure this construction
            // exists to avoid.
            if (isChordTerminal(arc.endsAt())) {

                walledBy.add(laid.get(readChordFrom(arc.endsAt())));

                boundary.add(findPointOnCircle(
                    union.sites().get(arc.circle()), union.reach(), arc.toAngle()));
            }
        }

        if (boundary.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return null;
        }
        return new VoidHole(
            boundary, corners, List.copyOf(ringing), union.reach(), List.copyOf(walledBy));
    }

    // Sampled at the angles the cells' own radius bound has its vertices at, and at no others,
    // so an arc and the run of cell bound beneath it are the same chain of points. Sampling at
    // its own even spacing instead put the two chains half a step out of phase, sharing no
    // vertex at all, and left every stretch of them a sagitta apart - which is what stopped a
    // traced piece of void being handed to machinery built for cells.
    //
    // The arc's own ends are not samples and are not moved onto that set. Each is a crossing
    // the walk names rather than a point it chose: an end is where this circle enters or
    // leaves a neighbour's disc, and the next arc round the cycle picks the boundary up from
    // the same crossing on the other circle. Snapping either would part the two.
    private static List<double[]> sampleArc(
            double[] centre,
            double reach,
            double fromAngle,
            double toAngle,
            int boundSegments) {

        var points = new ArrayList<double[]>();

        // The near end is the arc's own, and the far end is left off: the next arc round the
        // cycle begins on it. A stretch of no width is still a join - it is where two walls
        // meet at a point on this circle - and that one point is the whole of it.
        points.add(findPointOnCircle(centre, reach, fromAngle));

        var firstVertex = (int) Math.floor(fromAngle * boundSegments / Angles.FULL_TURN) + 1;

        // Counted rather than walked until the far end is reached, because no arc sweeps more
        // than one turn: a turn holds one bound vertex per side, the arc's own start stands in
        // for the one it begins at, and a full-turn arc whose end lands a rounding past its
        // start would otherwise close on a repeat of that start.
        for (var offset = 0; offset < boundSegments - 1; offset++) {

            var vertex = firstVertex + offset;

            if (measureBoundVertexAngle(vertex, boundSegments) >= toAngle) {
                break;
            }
            points.add(findBoundVertexOnCircle(centre, reach, vertex, boundSegments));
        }
        return points;
    }

    // Where a cell's radius bound puts vertex number {@code vertex}. Absolute rather than
    // measured from anything the arc knows: the same angles on every circle, which is the
    // whole of what makes two shapes on one circle land on each other.
    private static double measureBoundVertexAngle(int vertex, int boundSegments) {
        return Angles.FULL_TURN * vertex / boundSegments;
    }

    // One of those vertices as a point, with the count taken back into the first turn before
    // the angle is worked out. A vertex a turn along is the same vertex, but the angle naming
    // it that way is a turn's worth of rounding away from the one the cell's own bound was
    // built at - and the two shapes then land near each other rather than on each other.
    private static double[] findBoundVertexOnCircle(
            double[] centre,
            double reach,
            int vertex,
            int boundSegments) {

        return findPointOnCircle(
            centre,
            reach,
            measureBoundVertexAngle(Math.floorMod(vertex, boundSegments), boundSegments));
    }

    // Whether a wall still reaches the boundary at this circle - asked of whichever part of it
    // carries the answer for the kind of wall it is.
    //
    // A BRIDGE is asked about its mouth's two edges. Its line runs through both sites, so it
    // crosses squarely between them and its mouth lies where the gap it spans lies: buried,
    // and the two cells have closed over the gap, which is exactly when the bridge must go.
    //
    // A COAST REACH is asked about its own END. It leaves a cell along a tangent and can end
    // exactly where two circles cross, and a mouth is a channel wide by construction - so half
    // of it is inside the neighbouring disc whatever the reach did, and the mouth test refuses
    // a wall that is on the boundary. That refusal is what left the void behind a coast's own
    // reach open to the sea, with the line drawn across the gap and no wall laid under it.
    //
    // The two agree at the cells' own reach and part company a channel outside it, where a
    // wall's end sits inside its own circle rather than on it. One rule serves both once
    // nothing is traced out there.
    private static boolean isWallOnBoundary(
            DiscUnion union,
            Chord chord,
            int circle,
            double[] mouth) {

        return chord.kind() == WallKind.COAST_REACH
            ? isPointOnBoundary(union, circle, findWallEnd(chord, circle))
            : isMouthOnBoundary(union, circle, mouth);
    }

    // A mouth sits on the boundary when no disc but its own circle's swallows either edge of
    // it. A third disc reaching into the middle of a mouth without touching an edge changes
    // nothing: its cover nests inside the mouth's, so the merged sweep still opens the arcs at
    // the mouth's own edges.
    private static boolean isMouthOnBoundary(DiscUnion union, int circle, double[] mouth) {

        for (var edge : List.of(mouth[0], mouth[0] + mouth[1])) {

            if (!isPointOnBoundary(union, circle, findPointOnCircle(
                    union.sites().get(circle), union.reach(), edge))) {

                return false;
            }
        }
        return true;
    }

    // Where a wall meets one of its circles, which for a bridge is a gap's own end and for a
    // reach of coast is the point the smoothing put down.
    private static double[] findWallEnd(Chord chord, int circle) {

        var line = chord.line();
        var isFromSide = circle == chord.fromCircle();

        return new double[] {
            isFromSide ? line.originX() : line.originX() + line.directionX(),
            isFromSide ? line.originY() : line.originY() + line.directionY()};
    }

    // Whether a point is on the union's boundary rather than inside it, which is the one
    // reading both tests above are built out of. Asked of the point rather than of an angle,
    // because that is the definition of the boundary and needs no interval arithmetic to
    // agree with.
    private static boolean isPointOnBoundary(DiscUnion union, int circle, double[] point) {

        var sites = union.sites();

        for (var site = 0; site < sites.size(); site++) {

            if (site != circle
                    && Points.computeDistance(point, sites.get(site)) < union.reach()) {

                return false;
            }
        }
        return true;
    }

    // Whether an earlier wall's mouth leaves this one nowhere to attach.
    //
    // Not asked of a REACH OF COAST, because the sweep now hands a swallowed reach the
    // boundary rather than dropping it. A coast leaves a cell along its tangent, so where the
    // coast turns on a cell the mouth it arrives by swallows the mouth it leaves by - two
    // real walls, one of which was being refused for the shape of the other.
    //
    // Still asked of a BRIDGE. A bridge crosses its circles squarely between its two sites,
    // so its mouth is swallowed only where the gap it spans has closed over, and a wall laid
    // across a gap that is no longer there cuts through cells that have already met.
    private static boolean isCrowdedOut(
            Map<Integer, List<double[]>> takenByCircle,
            Chord chord,
            double[] fromMouth,
            double[] toMouth) {

        return chord.kind() != WallKind.COAST_REACH
            && (isMouthTaken(takenByCircle, chord.fromCircle(), fromMouth)
                || isMouthTaken(takenByCircle, chord.toCircle(), toMouth));
    }

    // A mouth is taken when an earlier wall's mouth swallows it whole, or is swallowed by it.
    // The sweep merges what overlaps and the merged cover keeps only the outer pair of
    // terminals, so a mouth wholly inside another loses both of its own: the wall it belongs
    // to then has nowhere to leave from and nowhere to land, which breaks the walk rather than
    // spoiling a shape.
    //
    // Overlapping in part is not that. Each wall keeps the outer terminal the walk needs and
    // the sweep hands one straight on to the other, so refusing those would leave the void
    // behind one of them open to the sea with nothing to close it.
    private static boolean isMouthTaken(
            Map<Integer, List<double[]>> takenByCircle,
            int circle,
            double[] mouth) {

        for (var taken : takenByCircle.getOrDefault(circle, List.<double[]>of())) {

            if (Angles.measureGap(taken[0] + taken[1] / 2, mouth[0] + mouth[1] / 2)
                    <= Math.abs(taken[1] - mouth[1]) / 2) {

                return true;
            }
        }
        return false;
    }

    // Remembers a mouth as taken, so the walls offered after this one are held to what is
    // left of the circle rather than to the whole of it.
    private static void recordMouth(
            Map<Integer, List<double[]>> takenByCircle,
            int circle,
            double[] mouth) {

        takenByCircle.computeIfAbsent(circle, held -> new ArrayList<>()).add(mouth);
    }

    // The direction from one circle's centre to another's, which is where the stretch of the
    // first that the second swallows is centred.
    private static double measureAngleTowards(
            DiscUnion union,
            int fromCircle,
            int toCircle) {

        var from = union.sites().get(fromCircle);
        var to = union.sites().get(toCircle);

        return Angles.normalise(Math.atan2(to[1] - from[1], to[0] - from[0]));
    }

    /**
     * The point at one angle on one circle.
     *
     * @param centre where the circle is centred
     * @param reach  its radius
     * @param angle  the direction from the centre
     * @return the point on the circle
     */
    static double[] findPointOnCircle(double[] centre, double reach, double angle) {

        return new double[] {
            centre[0] + reach * Math.cos(angle),
            centre[1] + reach * Math.sin(angle)};
    }

    // Offset by one so the "no neighbour" marker cannot land on the name of a real pairing,
    // and kept positive so a chord's terminal can never be mistaken for a crossing.
    private static long formatDiscTerminal(int circle, int neighbour, int siteCount) {
        return (long) circle * (siteCount + 1) + neighbour + 1;
    }

    // Negative, so a wall's terminal can never collide with a crossing between two discs -
    // those are the positive half of the same space. Offset by one because a wall at index
    // zero would otherwise name terminal zero, which is neither negative nor free.
    private static long formatChordTerminal(int chord, int side) {
        return -(2L * chord + side + 1);
    }

    // Which half of the terminal space a name came from. The sweep needs it to tell a mouth
    // from a disc's cover: two mouths that overlap hand the boundary from one wall to the
    // next, while a stretch a disc swallows is off the boundary and joins nothing.
    private static boolean isChordTerminal(long terminal) {
        return terminal < 0 && terminal != NO_TERMINAL;
    }

    // Which wall a chord terminal names, undoing formatChordTerminal. The side marker is the
    // low bit, so the wall is what is left once it is taken off.
    private static int readChordFrom(long terminal) {
        return (int) ((-terminal - 1) / 2);
    }

    /**
     * One stretch of a silhouette's coast, as the single point standing for it.
     *
     * <p>The whole stretch rather than a single point on it, because a smoothed coast wants
     * two different things from it: the middle, which is where the line would pass if nothing
     * were in the way, and the two ends, which bound how far along the cell's border the line
     * may be slid when something is.
     *
     * @param circle    whose cell the stretch of coast belongs to
     * @param fromAngle the angle it begins at
     * @param toAngle   the angle it ends at, always greater than {@code fromAngle}
     */
    record CoastMark(
        int circle,
        double fromAngle,
        double toAngle) {

        /**
         * The middle of the stretch - where a coast passes when nothing blocks it.
         *
         * @return the angle halfway along
         */
        double midAngle() {
            return (fromAngle + toAngle) / 2;
        }
    }

    /**
     * One closed cycle of the boundary, kept both ways it is wanted.
     *
     * <p>As arcs for anything asking which cell each stretch belongs to and in what order, and
     * as a sampled shape for anything asking which way it winds or wanting to draw it. Built
     * together because both come out of one walk, and walking twice to get them separately is
     * how two answers about the same cycle start to disagree.
     *
     * @param arcs  the arcs it runs along, in walk order
     * @param shape the same cycle sampled into a closed outline
     */
    private record TracedCycle(
        List<Arc> arcs,
        VoidHole shape) {
    }

    /**
     * One stretch of a circle that the boundary does not run along.
     *
     * <p>Either a neighbouring disc swallowing it or a chord's mouth passing through it - the
     * sweep merges both the same way, and each names what the arcs either side of it link to
     * so that nothing downstream has to know which kind made the gap.
     *
     * @param start     the angle it begins at, anticlockwise
     * @param width     how far it runs
     * @param arrival   what an arc ending at {@code start} continues onto
     * @param departure what an arc beginning at the far end of it is named by
     * @param kind      which sort of wall opened it, or null where a neighbouring disc
     *                  swallowed the stretch and no wall is involved. Carried because one
     *                  case turns on it: a mouth swallowed whole by another
     */
    private record Cover(
        double start,
        double width,
        long arrival,
        long departure,
        WallKind kind) {
    }

    /**
     * One stretch of a circle that nothing covers - a single edge of the union's boundary.
     *
     * @param circle    whose circle it runs on
     * @param fromAngle the angle it begins at
     * @param toAngle   the angle it ends at, always greater than {@code fromAngle}
     * @param startsAt  the terminal it begins at, naming either the disc it comes out of or
     *                  the chord mouth it leaves
     * @param endsAt    the terminal it runs on to, which is the {@code startsAt} of whichever
     *                  arc the boundary continues along
     */
    private record Arc(
        int circle,
        double fromAngle,
        double toAngle,
        long startsAt,
        long endsAt) {
    }
}
