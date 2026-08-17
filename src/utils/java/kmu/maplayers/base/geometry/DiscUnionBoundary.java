package kmu.maplayers.base.geometry;

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
 * through: the stretch holding the end where the wall meets it, as wide as the channel it
 * keeps. The arcs either side of that mouth are then the two sides of the chord, one bounding
 * the void on each side of it, and the channel between them is the gap the two never claim.
 *
 * <p>So a chord is exactly a cover whose ends are named by the chord rather than by a
 * neighbouring circle, and one sweep merges both kinds. Nothing is offset and nothing is cut:
 * every vertex of every shape is a point of some circle, arrived at by arithmetic rather than
 * by finding the nearest sample on a finished outline.
 *
 * <p>Shared because the reach is a parameter rather than a fact: run at the true reach it
 * finds the pockets, run at the reach plus the channel it finds what is left of them once the
 * channel is taken out, and run at the reach minus it, what they become when the cells fill
 * right up to them. Every consumer that wants a pocket at some reach wants this, and there is
 * now more than one of them.
 */
final class DiscUnionBoundary {

    // Enough to keep a short arc from collapsing to a chord once the sampling is scaled down
    // in proportion to how little of the circle it covers.
    private static final int MIN_ARC_SAMPLES = 2;

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
     */
    record Chord(
        int fromCircle,
        int toCircle,
        DirectedLine line) {
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
                    bridge.end()[1] - bridge.start()[1])));
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
     * @param union       the discs to trace
     * @param arcSegments how finely a half-turn of arc is sampled
     * @return the holes, wound the way any other filled shape is
     */
    static List<VoidHole> traceHoles(DiscUnion union, int arcSegments) {
        return traceHolesAcrossWalls(union, NO_WALLS, arcSegments);
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
     * @param union       the discs to trace
     * @param walls       the walls to lay across the void
     * @param arcSegments how finely a half-turn of arc is sampled
     * @return the holes, wound the way any other filled shape is
     */
    static List<VoidHole> traceHolesAcrossWalls(
            DiscUnion union,
            Walls walls,
            int arcSegments) {

        var holes = new ArrayList<VoidHole>();

        for (var cycle : traceCycles(union, walls, arcSegments)) {

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
     * @param union       the discs to trace
     * @param walls       the walls to lay across the void
     * @param arcSegments how finely a half-turn of arc is sampled, to tell a silhouette from
     *                    a hole by the area it comes out with
     * @return one run of marks per silhouette, in walk order
     */
    static List<List<CoastMark>> traceSilhouetteCoasts(
            DiscUnion union,
            Walls walls,
            int arcSegments) {

        var coasts = new ArrayList<List<CoastMark>>();

        for (var cycle : traceCycles(union, walls, arcSegments)) {

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
     * The chords that can actually be laid at this reach, which are the only ones traced.
     *
     * <p>Wanted by anything measuring the result: a chord that was dropped has no business
     * being looked for on an outline, and one that was laid has to be on one.
     *
     * <p>Two things stop a chord. Its mouth can be buried inside another disc, which is what
     * becomes of a bridge whose cells have closed over. Or it can be swallowed whole by a
     * mouth an earlier chord on the same circle already took, leaving it no terminal of its
     * own to leave from or land on once the sweep merges the two.
     *
     * <p>Sharing part of a mouth is allowed, and is how a coast turning on a cell's border
     * gets laid at all: the two reaches either side of the turn meet at one point, so their
     * mouths overlap by whatever the channel adds to each. Both keep an outer terminal, and
     * the sweep hands one wall straight on to the next.
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

            var fromMouth = measureMouth(union, chord, chord.fromCircle(), walls.channel());
            var toMouth = measureMouth(union, chord, chord.toCircle(), walls.channel());

            if (fromMouth == null
                    || toMouth == null
                    || !isMouthOnBoundary(union, chord.fromCircle(), fromMouth)
                    || !isMouthOnBoundary(union, chord.toCircle(), toMouth)
                    || isMouthTaken(takenByCircle, chord.fromCircle(), fromMouth)
                    || isMouthTaken(takenByCircle, chord.toCircle(), toMouth)) {

                continue;
            }
            recordMouth(takenByCircle, chord.fromCircle(), fromMouth);
            recordMouth(takenByCircle, chord.toCircle(), toMouth);

            attachable.add(chord);
        }
        return attachable;
    }

    /**
     * The stretch of one circle a wall's mouth takes out of the boundary.
     *
     * <p>The one place a wall's position is turned into angles, and the reason a coast's
     * reaches and a bridge can be the same kind of thing. A mouth is the arc of a circle lying
     * within a channel of the wall - of the wall itself, which is a segment, and not of the
     * unbounded line it sits on.
     *
     * @param union   the discs the wall is laid across
     * @param chord   the wall
     * @param circle  which of its circles to measure the mouth on
     * @param channel how far each side of the wall holds back from it
     * @return the mouth as {@code {start, width}}, or null when the wall passes too far from
     *         this circle to open one at all
     */
    private static double[] measureMouth(
            DiscUnion union,
            Chord chord,
            int circle,
            double channel) {

        var line = chord.line().toUnitLine();

        if (line == null) {
            return null;
        }
        var centre = union.sites().get(circle);

        var awayX = centre[0] - line.originX();
        var awayY = centre[1] - line.originY();

        // Across the wall and along it. A point at angle t on the circle sits
        // offset + reach * cos(t - axis) from the origin along either axis, so the two
        // conditions are the same shape and the mouth is the stretch where both hold.
        var across = findArcsWithin(
            union.reach(),
            Math.atan2(line.directionX(), -line.directionY()),
            Points.projectPointOnto(awayX, awayY, -line.directionY(), line.directionX()),
            -channel,
            channel);

        // Bounded by the wall's own ENDS as well as by its line. A wall is a segment, and
        // past its ends there is no wall to hold anything back - but a line near tangent to a
        // circle stays within a channel of it for most of a radian, nearly all of it beyond
        // where the wall stops. Unbounded, that stretch is claimed anyway, and a cell the
        // coast turns on has its arrival mouth swallow its departure mouth whole.
        var along = findArcsWithin(
            union.reach(),
            Math.atan2(line.directionY(), line.directionX()),
            Points.projectPointOnto(awayX, awayY, line.directionX(), line.directionY()),
            0,
            Math.hypot(chord.line().directionX(), chord.line().directionY()));

        // And rounded off at each end, because what is within a channel of a SEGMENT is a
        // capsule and not a box: past an end the channel is measured to the end itself.
        //
        // Whether that matters turns on where the wall's ends sit. Laid across discs one
        // channel wider than the border it was built on, an end is a channel inside this
        // circle and its cap meets the circle at a point, so the ends round off nothing.
        // Laid across the border itself - which is where a coast is walked - an end sits ON
        // the circle, and its cap is the whole of the mouth behind it. Left out, every
        // bridge in the coast's own walk loses the half of its mouth behind its end.
        var pieces = new ArrayList<>(intersectArcs(across, along));

        pieces.addAll(findCapArc(union, circle, line.originX(), line.originY(), channel));
        pieces.addAll(findCapArc(
            union,
            circle,
            line.originX() + chord.line().directionX(),
            line.originY() + chord.line().directionY(),
            channel));

        var towards = measureAngleToWallEnd(chord, circle, centre);
        var arcs = mergeArcs(pieces, towards);

        if (arcs.isEmpty()) {
            return null;
        }
        var mouth = arcs.size() < 2 ? arcs.get(0) : pickArcHolding(towards, arcs);

        // Folded into the first turn only now that it has been chosen. The choice is an
        // interval test at an exact boundary, and folding a start by a whole turn moves that
        // boundary by a rounding - so the arcs are compared in the turn they were built in
        // and put in a standard one on the way out.
        return new double[] {Angles.normalise(mouth[0]), mouth[1]};
    }

    /**
     * The stretches of one circle whose offset along an axis falls in a given range.
     *
     * <p>A point at angle {@code t} sits {@code offset + reach * cos(t - axisAngle)} along the
     * axis, so the answer is the run of {@code t} keeping that between the two bounds - which
     * is a run of {@code cos t}, and so either two stretches or one.
     *
     * <p>Both halves of a mouth are this shape, which is why it is one routine. Across the
     * wall the range is the channel either side of its line; along the wall it is nothing to
     * the wall's own length.
     *
     * <p>TWO while the band cuts clean through the circle: it crosses twice, going in and
     * coming out, on opposite stretches with the rest of the circle between them.
     *
     * <p>ONE once either edge of the band clears the circle entirely. The two stretches then
     * join up round the near end or the far one and are a single stretch, and handing back
     * half of it leaves the circle uncovered where the wall actually crosses - so the boundary
     * walks straight past the wall and the void behind it never closes.
     *
     * <p>NONE when the band misses the circle altogether.
     *
     * @param reach     the circle's radius
     * @param axisAngle the direction from the circle's centre along the axis
     * @param offset    how far the centre sits along that axis from where the range is
     *                  measured
     * @param low       the near bound
     * @param high      the far bound
     * @return the stretches as {@code {start, width}} pairs - none, one or two of them - each
     *         starting in the turn it was built in rather than folded into the first
     */
    private static List<double[]> findArcsWithin(
            double reach,
            double axisAngle,
            double offset,
            double low,
            double high) {

        var nearest = (high - offset) / reach;
        var furthest = (low - offset) / reach;

        if (furthest >= 1 || nearest <= -1) {
            return List.of();
        }

        // Joined round the far end of the circle, the band's far edge having cleared it. A
        // coast reach sits exactly on that threshold across its line: it is drawn tangent to
        // a cell's own border and laid across discs one channel wider, so whether it clears
        // turns on a rounding. A bridge runs through both sites and is nowhere near it.
        if (furthest <= -1) {

            var half = Math.PI - Math.acos(Math.min(1, nearest));
            return List.of(new double[] {axisAngle + Math.PI - half, 2 * half});
        }

        // Joined round the near end, the band's near edge having cleared it instead.
        if (nearest >= 1) {

            var half = Math.acos(furthest);
            return List.of(new double[] {axisAngle - half, 2 * half});
        }

        var inner = Math.acos(nearest);
        var outer = Math.acos(furthest);

        return List.of(
            new double[] {axisAngle + inner, outer - inner},
            new double[] {axisAngle - outer, outer - inner});
    }

    /**
     * The stretches two sets of them have in common.
     *
     * <p>Each pair is tried in the turn before, the same turn and the turn after, because two
     * stretches built about different axes need not have been built in the same turn and a
     * comparison of raw angles would miss an overlap that is there. One pair can leave two
     * pieces, which is a genuine answer rather than a duplicate: a long stretch can meet
     * another at both of its ends.
     *
     * @param first  one set
     * @param second the other
     * @return what they share, in the turn the first set was built in
     */
    private static List<double[]> intersectArcs(
            List<double[]> first,
            List<double[]> second) {

        var shared = new ArrayList<double[]>();

        for (var one : first) {
            for (var other : second) {
                for (var turn = -1; turn <= 1; turn++) {

                    var from = Math.max(one[0], other[0] + turn * Angles.FULL_TURN);
                    var to = Math.min(
                        one[0] + one[1],
                        other[0] + other[1] + turn * Angles.FULL_TURN);

                    if (to > from) {
                        shared.add(new double[] {from, to - from});
                    }
                }
            }
        }
        return shared;
    }

    /**
     * The stretch of one circle lying within {@code channel} of a single point.
     *
     * <p>What rounds off a mouth at one of the wall's ends. Two circles meeting is the same
     * closed form the discs' own crossings use, taken here between this circle and the
     * channel's reach about the end.
     *
     * @param union   the discs
     * @param circle  the circle to measure on
     * @param pointX  x of the end
     * @param pointY  y of the end
     * @param channel how far from the end still counts as against the wall
     * @return the stretch as one {@code {start, width}} pair, or none where the end is too
     *         far from the circle to round anything off
     */
    private static List<double[]> findCapArc(
            DiscUnion union,
            int circle,
            double pointX,
            double pointY,
            double channel) {

        var centre = union.sites().get(circle);

        var awayX = pointX - centre[0];
        var awayY = pointY - centre[1];
        var away = Points.computeVectorLength(awayX, awayY);

        if (away < Limits.MIN_EDGE_LENGTH) {
            return List.of();
        }
        var reach = union.reach();
        var cosine = (reach * reach + away * away - channel * channel) / (2 * reach * away);

        if (cosine >= 1) {
            return List.of();
        }
        var half = Math.acos(Math.max(-1, cosine));

        return List.of(new double[] {Math.atan2(awayY, awayX) - half, 2 * half});
    }

    /**
     * Overlapping stretches joined into the runs they make up.
     *
     * <p>Placed in the turn beginning half a turn before {@code about} first, so that pieces
     * built about different axes - across the wall, along it, and round each of its ends -
     * are comparable at all. A mouth surrounds the wall's own end, so taking that as the
     * middle of the window is what keeps a run from being split across its edge.
     *
     * @param pieces what to join
     * @param about  the direction the runs are expected to gather around
     * @return the maximal runs, in order
     */
    private static List<double[]> mergeArcs(List<double[]> pieces, double about) {

        var placed = new ArrayList<double[]>(pieces.size());

        for (var piece : pieces) {
            placed.add(new double[] {
                Angles.placeAfter(piece[0], about - Angles.HALF_TURN), piece[1]});
        }
        placed.sort(Comparator.comparingDouble(piece -> piece[0]));

        var merged = new ArrayList<double[]>();

        for (var piece : placed) {

            var last = merged.isEmpty() ? null : merged.get(merged.size() - 1);

            if (last != null && piece[0] <= last[0] + last[1]) {

                last[1] = Math.max(last[1], piece[0] + piece[1] - last[0]);
                continue;
            }
            merged.add(new double[] {piece[0], piece[1]});
        }
        return merged;
    }

    /**
     * Which of the arcs a channel cuts is the one the wall actually meets.
     *
     * <p>The arc that CONTAINS the wall's end, not the one whose middle is nearest it.
     * Nearest is a tiebreak, and a tiebreak needs its candidates to be far apart. On a cell
     * facing void most of the way round - the end of a chain, or either half of a two-cell
     * island - the arcs close up on each other and the tiebreak stops meaning anything.
     * Picked wrong there, the wall wraps the far side of the circle and the void it closes
     * runs off along the line instead of stopping at the cells: long wedges out to sea that
     * no side-of-the-line check will complain about, because they lie between two
     * near-parallel reaches and a pair of those bounds a slab rather than a shape.
     *
     * <p>Containment cannot degenerate that way. The wall meets the circle at one known
     * angle, and exactly one of the arcs holds it.
     *
     * @param towards where the wall meets this circle, as an angle from its centre
     * @param arcs    the arcs to choose between
     * @return the arc holding it
     */
    private static double[] pickArcHolding(double towards, List<double[]> arcs) {

        for (var arc : arcs) {

            if (Angles.placeAfter(towards, arc[0]) <= arc[0] + arc[1]) {
                return arc;
            }
        }

        // None holds it, which the geometry says cannot happen: the wall meets the circle, so
        // its end is on one of the arcs its own channel opens. Falling back to the nearer of
        // them keeps a rounding at an arc's edge from dropping the wall.
        var nearest = arcs.get(0);

        for (var arc : arcs) {

            if (Angles.measureGap(towards, arc[0] + arc[1] / 2)
                    < Angles.measureGap(towards, nearest[0] + nearest[1] / 2)) {

                nearest = arc;
            }
        }
        return nearest;
    }

    /**
     * Where a wall meets one of its circles, as an angle from that circle's centre.
     *
     * <p>The wall's own end rather than the direction to the cell at its far end. Those agree
     * for a bridge, whose line runs through both sites, and they are most of a right angle
     * apart for a reach of coast, which leaves a cell along its tangent. Taking the far cell's
     * direction opened half a coast's mouths on the wrong side of the cell, which walled off
     * pockets inland of it and left the void it had actually shut in open to the sea.
     *
     * @param chord  the wall
     * @param circle which of its circles the angle is measured at
     * @param centre that circle's centre
     * @return the angle
     */
    private static double measureAngleToWallEnd(Chord chord, int circle, double[] centre) {

        var line = chord.line();
        var isFromSide = circle == chord.fromCircle();

        var endX = isFromSide ? line.originX() : line.originX() + line.directionX();
        var endY = isFromSide ? line.originY() : line.originY() + line.directionY();

        return Math.atan2(endY - centre[1], endX - centre[0]);
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

        var fromMouth = measureMouth(union, chord, chord.fromCircle(), channel);
        var toMouth = measureMouth(union, chord, chord.toCircle(), channel);

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
            int arcSegments) {

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

            var built = buildHole(cycle, arcs, union, laid.chords(), arcSegments);
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
                formatDiscTerminal(circle, other, sites.size())));
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
            var mouth = measureMouth(union, chord, circle, walls.channel());

            if (mouth == null) {
                continue;
            }

            covers.add(new Cover(
                mouth[0],
                mouth[1],
                formatChordTerminal(index, isFromSide ? TO_SIDE : FROM_SIDE),
                formatChordTerminal(index, isFromSide ? FROM_SIDE : TO_SIDE)));
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

                // The boundary comes back along one wall and leaves along the next with
                // nothing on the circle in between, so the stretch between them is empty
                // rather than absent - given as an arc of no width, since what the walk wants
                // of it is that its two terminals name each other rather than any length.
                //
                // Two walls meeting at one point on this circle. Each mouth is as wide as
                // the channel makes it, so a pair that meet overlap.
                //
                // Only where WALLS are what overlap. A stretch a neighbouring disc swallows
                // really is off the boundary, and joining its ends would run a cycle through
                // space the union covers - which is why a mouth overlapping a disc is refused
                // outright, leaving this the only kind of overlap that reaches here.
                arcs.add(new Arc(
                    circle, coveredTo, coveredTo, departingFrom, cover.arrival()));
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
            int arcSegments) {

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
                arcSegments);

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

    // Sampled in proportion to how much of the circle the arc covers, so a long arc is not
    // left coarser than a short one merely because both got the same number of points.
    private static List<double[]> sampleArc(
            double[] centre,
            double reach,
            double fromAngle,
            double toAngle,
            int arcSegments) {

        var sweep = toAngle - fromAngle;

        // A stretch of no width is still a join - it is where two walls meet at a point on
        // this circle - and the single point they share is the whole of it.
        if (sweep <= 0) {
            return List.of(findPointOnCircle(centre, reach, fromAngle));
        }
        var steps = Math.max(
            MIN_ARC_SAMPLES,
            (int) Math.ceil(arcSegments * sweep / Angles.HALF_TURN));

        var points = new ArrayList<double[]>(steps);

        // The far end is left off: the next arc round the cycle begins on it.
        for (var step = 0; step < steps; step++) {

            points.add(findPointOnCircle(centre, reach, fromAngle + sweep * step / steps));
        }
        return points;
    }

    // A mouth sits on the boundary when no disc but its own circle's swallows either edge of
    // it. Asked of the points rather than of the angles, because that is the definition of
    // the boundary and needs no interval arithmetic to agree with. A third disc reaching into
    // the middle of a mouth without touching an edge changes nothing: its cover nests inside
    // the mouth's, so the merged sweep still opens the arcs at the mouth's own edges.
    private static boolean isMouthOnBoundary(
            DiscUnion union,
            int circle,
            double[] mouth) {

        var sites = union.sites();

        for (var edge : List.of(mouth[0], mouth[0] + mouth[1])) {

            var point = findPointOnCircle(sites.get(circle), union.reach(), edge);

            for (var site = 0; site < sites.size(); site++) {

                if (site != circle
                        && Points.computeDistance(point, sites.get(site)) < union.reach()) {

                    return false;
                }
            }
        }
        return true;
    }

    // A mouth is taken when an earlier wall's mouth swallows it whole, or is swallowed by it.
    // The sweep merges what overlaps and the merged cover keeps only the outer pair of
    // terminals, so a mouth wholly inside another loses both of its own: the wall it belongs
    // to then has nowhere to leave from and nowhere to land, which breaks the walk rather than
    // spoiling a shape.
    //
    // Overlapping in part is not that. Two walls meeting at a point on a circle overlap by
    // whatever the channel adds to each of their mouths, and each keeps the outer terminal the
    // walk needs; the sweep hands one straight on to the other. Refused, the void behind one
    // of the two is left open to the sea with nothing to close it.
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

    private static void recordMouth(
            Map<Integer, List<double[]>> takenByCircle,
            int circle,
            double[] mouth) {

        takenByCircle.computeIfAbsent(circle, held -> new ArrayList<>()).add(mouth);
    }

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
     */
    private record Cover(
        double start,
        double width,
        long arrival,
        long departure) {
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
