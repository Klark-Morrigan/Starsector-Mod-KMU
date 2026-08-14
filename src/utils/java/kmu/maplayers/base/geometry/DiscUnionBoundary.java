package kmu.maplayers.base.geometry;

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
 * The boundary of the union of the cells' reach discs, as closed cycles of circular arcs.
 *
 * <p>A point is void when its nearest site is further than the reach, so the void is exactly
 * the complement of the union of one disc of that radius per site. The boundary of that union
 * is a set of closed cycles, and each cycle is either the outer silhouette of a run of
 * overlapping cells or a hole enclosed by them. The holes are the void the cells bind.
 *
 * <p>Every disc has the same radius, which is what makes this cheap and exact rather than a
 * general shape union. Equal radii mean no disc can contain another, so two discs either miss
 * each other or cross at exactly two points, and the part of one circle lying inside another
 * is a single angular interval computed in closed form. Each circle's boundary arcs are then
 * the gaps left when its neighbours' intervals are merged.
 *
 * <p>The arcs link without matching any coordinates. An arc ends where its circle enters some
 * neighbour's disc, and the boundary continues on that neighbour from the point where the
 * neighbour LEAVES the first disc - a point named by the pair of circles rather than by its
 * position, so two circles computing it separately and landing a rounding apart still agree.
 *
 * <p>A {@link Chord} joins that walk as a covering interval of its own. Where a neighbouring
 * disc takes a stretch of circle out of the boundary, a chord takes out the mouth it comes
 * through: the stretch facing the circle at its far end, as wide as the channel it keeps. The
 * arcs either side of that mouth are then the two sides of the chord, one bounding the void
 * on each side of it, and the channel between them is the gap the two never claim.
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

    private static final double FULL_TURN = 2 * Math.PI;
    private static final double HALF_TURN = Math.PI;

    // Enough to keep a short arc from collapsing to a chord once the sampling is scaled down
    // in proportion to how little of the circle it covers.
    private static final int MIN_ARC_SAMPLES = 2;

    // Stands in for a neighbouring circle where there is none: a disc that overlaps nothing
    // contributes its whole circle as one arc, with no disc entered or left at either end.
    private static final int NO_CIRCLE = -1;
    private static final int NO_SUCCESSOR = -1;

    // No arc begins here, so an arc pointed at it simply has no successor. Beyond the range
    // either kind of real terminal can reach, rather than a value one of them might produce.
    private static final long NO_TERMINAL = Long.MIN_VALUE;

    // Which of a chord's two circles a terminal belongs to. A chord meets each of its circles
    // once, so the pair of side markers names its two mouths and nothing else does.
    private static final int FROM_SIDE = 0;
    private static final int TO_SIDE = 1;

    private DiscUnionBoundary() {
    }

    /**
     * A straight run of boundary between two circles, walling void off from the rest.
     *
     * <p>Named by its two circles rather than by two points, for the same reason the arcs
     * are: it meets each circle where that circle faces the other, so naming the pair fixes
     * both ends exactly and leaves nothing to be matched up by position afterwards.
     *
     * @param fromCircle one of the circles it runs between
     * @param toCircle   the other
     */
    record Chord(
        int fromCircle,
        int toCircle) {
    }

    /**
     * Every hole in the union of discs of one radius.
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
     * @param sites       the sites
     * @param radius      how far each cell reaches
     * @param arcSegments how finely a half-turn of arc is sampled
     * @return the holes, wound the way any other filled shape is
     */
    static List<VoidHole> traceHolesAtReach(
            List<double[]> sites,
            double radius,
            int arcSegments) {

        return traceHolesAcrossChords(sites, radius, arcSegments, List.of(), 0);
    }

    /**
     * The same holes, with the given chords laid across the boundary as walls.
     *
     * <p>A chord shuts void on one side of it off from void on the other, so a bay that was
     * open to the rest of the map becomes a closed cycle - a pocket in exactly the sense an
     * enclosed one is, and told apart from the silhouette it was cut from by which way it
     * winds. Nothing has to ask which piece holds the cells.
     *
     * <p>The channel is what keeps the two sides apart. A chord walled with no channel is one
     * line that both pockets close on, so they meet along it and read as one shape; given a
     * channel, each side closes on its own line half a channel out, and the strip between
     * them belongs to neither. It costs nothing to trace, because moving the wall sideways
     * only moves where it meets each circle - which is still one angle, still exact.
     *
     * <p>A chord whose mouth is buried inside some other disc is not on the boundary at all,
     * and is dropped. That is what becomes of one whose two cells have already closed over
     * at this reach, so a bridge too short to still be a gap costs nothing to offer.
     *
     * @param sites       the sites
     * @param radius      how far each cell reaches
     * @param arcSegments how finely a half-turn of arc is sampled
     * @param chords      the walls to lay across the void, as pairs of circles
     * @param channel     how far each side of a wall holds back from it
     * @return the holes, wound the way any other filled shape is
     */
    static List<VoidHole> traceHolesAcrossChords(
            List<double[]> sites,
            double radius,
            int arcSegments,
            List<Chord> chords,
            double channel) {

        var holes = new ArrayList<VoidHole>();

        for (var cycle : traceCyclesAtReach(sites, radius, arcSegments, chords, channel)) {

            // A cycle is a hole when it winds the opposite way to a silhouette. Each arc is
            // walked anticlockwise on its own circle, which keeps the discs' interior to the
            // left the whole way round, so an outer cycle comes out anticlockwise and a hole
            // clockwise.
            if (PolygonRegions.computeSignedArea(cycle.boundary()) < 0) {

                // Reversed so a hole reads the same way round as any other filled shape.
                var boundary = new ArrayList<>(cycle.boundary());
                java.util.Collections.reverse(boundary);

                holes.add(new VoidHole(
                    boundary, cycle.corners(), cycle.ringing(), cycle.reach()));
            }
        }
        return holes;
    }

    /**
     * The chords that can actually be laid at this reach, which are the only ones traced.
     *
     * <p>Wanted by anything measuring the result: a chord that was dropped has no business
     * being looked for on an outline, and one that was laid has to be on one.
     *
     * <p>Two things stop a chord. Its mouth can be buried inside another disc, which is what
     * becomes of a bridge whose cells have closed over. Or it can land on a mouth already
     * taken by an earlier chord on the same circle, two bridges leaving a cell within a
     * channel's width of each other - and one mouth cannot serve two walls, because the
     * merged cover would swallow the arc that one of them needs to start from.
     *
     * <p>Offered in order and taken greedily, so the caller's own ordering decides which of
     * two crowding chords survives.
     *
     * @param sites   the sites
     * @param radius  how far each cell reaches
     * @param chords  the chords on offer
     * @param channel how far each side of a wall holds back from it
     * @return those that can be laid, in the order they were offered
     */
    static List<Chord> findAttachableChords(
            List<double[]> sites,
            double radius,
            List<Chord> chords,
            double channel) {

        var mouth = measureMouthHalfWidth(radius, channel);
        var takenByCircle = new LinkedHashMap<Integer, List<Double>>();
        var attachable = new ArrayList<Chord>();

        for (var chord : chords) {

            var facingFrom = measureAngleTowards(sites, chord.fromCircle(), chord.toCircle());
            var facingTo = measureAngleTowards(sites, chord.toCircle(), chord.fromCircle());

            if (!isMouthOnBoundary(sites, radius, mouth, chord.fromCircle(), facingFrom)
                    || !isMouthOnBoundary(sites, radius, mouth, chord.toCircle(), facingTo)
                    || isMouthTaken(takenByCircle, chord.fromCircle(), facingFrom, mouth)
                    || isMouthTaken(takenByCircle, chord.toCircle(), facingTo, mouth)) {

                continue;
            }
            recordMouth(takenByCircle, chord.fromCircle(), facingFrom);
            recordMouth(takenByCircle, chord.toCircle(), facingTo);

            attachable.add(chord);
        }
        return attachable;
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
     * channel over the radius, and the ends are that angle either side of facing.
     *
     * @param sites   the sites
     * @param radius  how far each cell reaches
     * @param chord   the chord
     * @param channel how far each side of it holds back from the centre
     * @return the two lines, each as its pair of end points
     */
    static List<List<double[]>> findChordSides(
            List<double[]> sites,
            double radius,
            Chord chord,
            double channel) {

        var mouth = measureMouthHalfWidth(radius, channel);
        var from = sites.get(chord.fromCircle());
        var to = sites.get(chord.toCircle());

        var facingFrom = measureAngleTowards(sites, chord.fromCircle(), chord.toCircle());
        var facingTo = measureAngleTowards(sites, chord.toCircle(), chord.fromCircle());

        return List.of(
            List.of(
                findPointOnCircle(from, radius, facingFrom - mouth),
                findPointOnCircle(to, radius, facingTo + mouth)),
            List.of(
                findPointOnCircle(to, radius, facingTo - mouth),
                findPointOnCircle(from, radius, facingFrom + mouth)));
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

    private static List<VoidHole> traceCyclesAtReach(
            List<double[]> sites,
            double radius,
            int arcSegments,
            List<Chord> chords,
            double channel) {

        var laid = findAttachableChords(sites, radius, chords, channel);
        var arcs = findUncoveredArcs(sites, radius, laid, channel);
        var successors = linkArcsIntoCycles(arcs);
        var cycles = new ArrayList<VoidHole>();
        var walked = new boolean[arcs.size()];

        for (var start = 0; start < arcs.size(); start++) {

            var cycle = walkCycleFrom(start, arcs, successors, walked);
            if (cycle.isEmpty()) {
                continue;
            }

            var built = buildHole(cycle, arcs, sites, radius, arcSegments);
            if (built != null) {
                cycles.add(built);
            }
        }
        return cycles;
    }

    // Every stretch of every circle that nothing covers, which is the whole boundary of the
    // union - outer silhouettes and holes alike, not yet told apart - with the chords' mouths
    // taken out of it alongside the neighbouring discs.
    private static List<Arc> findUncoveredArcs(
            List<double[]> sites,
            double cellRadius,
            List<Chord> chords,
            double channel) {

        var arcs = new ArrayList<Arc>();
        for (var circle = 0; circle < sites.size(); circle++) {
            arcs.addAll(findUncoveredArcsOn(circle, sites, cellRadius, chords, channel));
        }
        return arcs;
    }

    // One circle's uncovered stretches, as the gaps between everything that covers it. Kept
    // as angles rather than points so the two ends of a gap stay attributed to whatever made
    // them, be that a neighbouring disc or a chord's mouth.
    private static List<Arc> findUncoveredArcsOn(
            int circle,
            List<double[]> sites,
            double cellRadius,
            List<Chord> chords,
            double channel) {

        var covers = findCoveringIntervals(circle, sites, cellRadius);
        covers.addAll(findChordCovers(circle, sites, cellRadius, chords, channel));

        if (covers.isEmpty()) {

            // A disc that nothing overlaps and no chord reaches is a closed cycle on its own.
            var whole = formatDiscTerminal(circle, NO_CIRCLE, sites.size());
            return List.of(new Arc(circle, 0, FULL_TURN, whole, whole));
        }
        return buildArcsBetweenCovers(circle, covers);
    }

    // The stretch of one circle lying inside a neighbour's disc. With equal radii the two
    // circles cross symmetrically about the line joining the sites, so the interval is the
    // half-angle whose cosine is half the separation over the radius, either side of that
    // line - no intersection points needed to find it.
    private static List<Cover> findCoveringIntervals(
            int circle,
            List<double[]> sites,
            double cellRadius) {

        var centre = sites.get(circle);
        var covers = new ArrayList<Cover>();

        for (var other = 0; other < sites.size(); other++) {

            if (other == circle) {
                continue;
            }
            var separation = Points.computeDistance(centre, sites.get(other));

            if (separation >= 2 * cellRadius || separation == 0) {
                continue;
            }
            var towards = measureAngleTowards(sites, circle, other);
            var halfWidth = Math.acos(separation / (2 * cellRadius));

            covers.add(new Cover(
                normaliseAngle(towards - halfWidth),
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
            List<double[]> sites,
            double cellRadius,
            List<Chord> chords,
            double channel) {

        var mouth = measureMouthHalfWidth(cellRadius, channel);
        var covers = new ArrayList<Cover>();

        for (var index = 0; index < chords.size(); index++) {

            var chord = chords.get(index);
            var isFromSide = chord.fromCircle() == circle;

            if (!isFromSide && chord.toCircle() != circle) {
                continue;
            }
            var other = isFromSide ? chord.toCircle() : chord.fromCircle();

            covers.add(new Cover(
                normaliseAngle(measureAngleTowards(sites, circle, other) - mouth),
                2 * mouth,
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
        var windowEnd = origin + FULL_TURN;

        // An interval running past the far end of the window covers the near end of it as
        // well, so the sweep has to start already covered up to wherever that reaches.
        // Without this the sweep reports a gap that the wrapping interval actually fills.
        var coveredTo = origin;
        var departingFrom = NO_TERMINAL;

        for (var cover : covers) {

            var wrapped = cover.start() + cover.width() - FULL_TURN;

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

    private static VoidHole buildHole(
            List<Integer> cycle,
            List<Arc> arcs,
            List<double[]> sites,
            double radius,
            int arcSegments) {

        var boundary = new ArrayList<double[]>();
        var corners = new ArrayList<double[]>(cycle.size());
        var ringing = new LinkedHashSet<Integer>();

        for (var index : cycle) {

            var arc = arcs.get(index);

            ringing.add(arc.circle());

            var points = sampleArc(
                sites.get(arc.circle()),
                radius,
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

                boundary.add(findPointOnCircle(
                    sites.get(arc.circle()), radius, arc.toAngle()));
            }
        }

        if (boundary.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return null;
        }
        return new VoidHole(boundary, corners, List.copyOf(ringing), radius);
    }

    // Sampled in proportion to how much of the circle the arc covers, so a long arc is not
    // left coarser than a short one merely because both got the same number of points.
    private static List<double[]> sampleArc(
            double[] centre,
            double radius,
            double fromAngle,
            double toAngle,
            int arcSegments) {

        var sweep = toAngle - fromAngle;
        var steps = Math.max(
            MIN_ARC_SAMPLES,
            (int) Math.ceil(arcSegments * sweep / HALF_TURN));

        var points = new ArrayList<double[]>(steps);

        // The far end is left off: the next arc round the cycle begins on it.
        for (var step = 0; step < steps; step++) {

            points.add(findPointOnCircle(centre, radius, fromAngle + sweep * step / steps));
        }
        return points;
    }

    // A mouth sits on the boundary when no disc but its own circle's swallows either edge of
    // it. Asked of the points rather than of the angles, because that is the definition of
    // the boundary and needs no interval arithmetic to agree with. A third disc reaching into
    // the middle of a mouth without touching an edge changes nothing: its cover nests inside
    // the mouth's, so the merged sweep still opens the arcs at the mouth's own edges.
    private static boolean isMouthOnBoundary(
            List<double[]> sites,
            double radius,
            double mouth,
            int circle,
            double facing) {

        for (var edge : List.of(facing - mouth, facing + mouth)) {

            var point = findPointOnCircle(sites.get(circle), radius, edge);

            for (var site = 0; site < sites.size(); site++) {

                if (site != circle && Points.computeDistance(point, sites.get(site)) < radius) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isMouthTaken(
            Map<Integer, List<Double>> takenByCircle,
            int circle,
            double facing,
            double mouth) {

        for (var taken : takenByCircle.getOrDefault(circle, List.of())) {

            if (measureAngleGap(taken, facing) < 2 * mouth) {
                return true;
            }
        }
        return false;
    }

    private static void recordMouth(
            Map<Integer, List<Double>> takenByCircle,
            int circle,
            double facing) {

        takenByCircle.computeIfAbsent(circle, held -> new ArrayList<>()).add(facing);
    }

    // Half the angle a channel takes up on a circle: the wall runs half a channel either side
    // of the line joining the sites, and a line that far off centre meets a circle of this
    // radius at the angle whose sine is the one over the other.
    private static double measureMouthHalfWidth(double radius, double channel) {
        return channel <= 0 || channel >= radius ? 0 : Math.asin(channel / radius);
    }

    private static double measureAngleTowards(
            List<double[]> sites,
            int fromCircle,
            int toCircle) {

        return normaliseAngle(Math.atan2(
            sites.get(toCircle)[1] - sites.get(fromCircle)[1],
            sites.get(toCircle)[0] - sites.get(fromCircle)[0]));
    }

    // How far apart two directions are, whichever way round is shorter.
    private static double measureAngleGap(double from, double to) {

        var turned = normaliseAngle(to - from);
        return turned > HALF_TURN ? FULL_TURN - turned : turned;
    }

    private static double[] findPointOnCircle(double[] centre, double radius, double angle) {

        return new double[] {
            centre[0] + radius * Math.cos(angle),
            centre[1] + radius * Math.sin(angle)};
    }

    // Offset by one so the "no neighbour" marker cannot land on the name of a real pairing,
    // and kept positive so a chord's terminal can never be mistaken for a crossing.
    private static long formatDiscTerminal(int circle, int neighbour, int siteCount) {
        return (long) circle * (siteCount + 1) + neighbour + 1;
    }

    private static long formatChordTerminal(int chord, int side) {
        return -(2L * chord + side + 1);
    }

    private static boolean isChordTerminal(long terminal) {
        return terminal < 0 && terminal != NO_TERMINAL;
    }

    private static double normaliseAngle(double angle) {
        var turned = angle % FULL_TURN;
        return turned < 0 ? turned + FULL_TURN : turned;
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
