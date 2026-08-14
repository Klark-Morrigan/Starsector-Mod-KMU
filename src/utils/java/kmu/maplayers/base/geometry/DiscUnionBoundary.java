package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

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
 * <p>A {@link Chord} joins that walk on the same terms. It is a straight wall between two
 * circles, and it meets each of them at the single angle where that circle faces the other,
 * so the arc is simply split there and the chord's ends are outline vertices by construction.
 * That is the whole reason chords are traced with the arcs rather than cut into the finished
 * outline afterwards: a cut has to find where the chord went, and finding is what strays.
 *
 * <p>Shared because the reach is a parameter rather than a fact: run at the true reach it
 * finds the pockets, run at the reach plus the channel it finds what is left of them once the
 * channel is taken out, and run at the reach minus it, what they become when the cells fill
 * right up to them. Every consumer that wants a pocket at some reach wants this, and there is
 * now more than one of them.
 */
final class DiscUnionBoundary {

    private static final double FULL_TURN = 2 * Math.PI;

    // Enough to keep a short arc from collapsing to a chord once the sampling is scaled down
    // in proportion to how little of the circle it covers.
    private static final int MIN_ARC_SAMPLES = 2;

    // Stands in for a neighbouring circle where there is none: a disc that overlaps nothing
    // contributes its whole circle as one arc, with no disc entered or left at either end.
    private static final int NO_CIRCLE = -1;
    private static final int NO_SUCCESSOR = -1;

    // Which of a chord's two circles a terminal belongs to. A chord meets each of its circles
    // once, so the pair of side markers names its two ends and nothing else does.
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

        return traceHolesAcrossChords(sites, radius, arcSegments, List.of());
    }

    /**
     * The same holes, with the given chords laid across the boundary as walls.
     *
     * <p>A chord shuts void on one side of it off from void on the other, so a bay that was
     * open to the rest of the map becomes a closed cycle - a pocket in exactly the sense an
     * enclosed one is, and told apart from the silhouette it was cut from by which way it
     * winds. Nothing has to ask which piece holds the cells.
     *
     * <p>A chord whose end is buried inside some other disc is not on the boundary at all,
     * and is dropped. That is what becomes of one whose two cells have already closed over
     * at this reach, so a bridge too short to still be a gap costs nothing to offer.
     *
     * @param sites       the sites
     * @param radius      how far each cell reaches
     * @param arcSegments how finely a half-turn of arc is sampled
     * @param chords      the walls to lay across the void, as pairs of circles
     * @return the holes, wound the way any other filled shape is
     */
    static List<VoidHole> traceHolesAcrossChords(
            List<double[]> sites,
            double radius,
            int arcSegments,
            List<Chord> chords) {

        var holes = new ArrayList<VoidHole>();

        for (var cycle : traceCyclesAtReach(sites, radius, arcSegments, chords)) {

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
     * @param sites  the sites
     * @param radius how far each cell reaches
     * @param chords the chords on offer
     * @return those with both ends on the boundary, in the order they were offered
     */
    static List<Chord> findAttachableChords(
            List<double[]> sites,
            double radius,
            List<Chord> chords) {

        var attachable = new ArrayList<Chord>();

        for (var chord : chords) {

            if (isChordEndOnBoundary(sites, radius, chord.fromCircle(), chord.toCircle())
                    && isChordEndOnBoundary(
                        sites,
                        radius,
                        chord.toCircle(),
                        chord.fromCircle())) {

                attachable.add(chord);
            }
        }
        return attachable;
    }

    /**
     * Where a chord meets one of its circles.
     *
     * <p>The point of that circle facing the other one - no search, no snapping. One formula
     * so that whatever lays the chord and whatever checks it cannot land in two places.
     *
     * @param sites          the sites
     * @param radius         how far each cell reaches
     * @param onCircle       whose circle the end sits on
     * @param towardsCircle  the circle at the chord's far end
     * @return the meeting point
     */
    static double[] findChordEnd(
            List<double[]> sites,
            double radius,
            int onCircle,
            int towardsCircle) {

        return findPointOnCircle(
            sites.get(onCircle),
            radius,
            measureAngleTowards(sites, onCircle, towardsCircle));
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
            List<Chord> chords) {

        var laid = findAttachableChords(sites, radius, chords);
        var arcs = findUncoveredArcs(sites, radius, laid);
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

    // Every stretch of every circle that no other disc covers, which is the whole boundary of
    // the union - outer silhouettes and holes alike, not yet told apart - cut at every point
    // where a chord comes down on it.
    private static List<Arc> findUncoveredArcs(
            List<double[]> sites,
            double cellRadius,
            List<Chord> chords) {

        var arcs = new ArrayList<Arc>();
        for (var circle = 0; circle < sites.size(); circle++) {
            arcs.addAll(findUncoveredArcsOn(circle, sites, cellRadius, chords));
        }
        return arcs;
    }

    // One circle's uncovered stretches, as the gaps between its neighbours' covering
    // intervals. Kept as angles rather than points so the two ends of a gap stay attributed to
    // the neighbour that made them.
    private static List<Arc> findUncoveredArcsOn(
            int circle,
            List<double[]> sites,
            double cellRadius,
            List<Chord> chords) {

        var attachments = findAttachmentsOn(circle, sites, chords);
        var covers = findCoveringIntervals(circle, sites, cellRadius);

        if (covers.isEmpty()) {

            // A disc overlapping nothing is a closed cycle on its own, and stays one unless a
            // chord comes down on it, in which case the chords alone cut it up.
            if (attachments.isEmpty()) {

                var whole = formatDiscTerminal(circle, NO_CIRCLE, sites.size());
                return List.of(new Arc(circle, 0, FULL_TURN, whole, whole));
            }
            return buildWholeCircleArcs(circle, attachments);
        }
        return splitArcsAtAttachments(
            buildArcsBetweenCovers(circle, covers, sites.size()), attachments);
    }

    // The gaps a circle's covering intervals leave between them, swept once round in order.
    // Each gap runs from wherever the last interval let go to wherever the next takes hold,
    // so its two ends are named by the neighbours that made them.
    private static List<Arc> buildArcsBetweenCovers(
            int circle,
            List<Cover> covers,
            int siteCount) {

        covers.sort(Comparator.comparingDouble(Cover::start));

        var origin = covers.get(0).start();
        var windowEnd = origin + FULL_TURN;

        // An interval running past the far end of the window covers the near end of it as
        // well, so the sweep has to start already covered up to wherever that reaches.
        // Without this the sweep reports a gap that the wrapping interval actually fills.
        var coveredTo = origin;
        var coveredBy = NO_CIRCLE;

        for (var cover : covers) {

            var wrapped = cover.start() + cover.width() - FULL_TURN;

            if (wrapped > coveredTo) {
                coveredTo = wrapped;
                coveredBy = cover.other();
            }
        }

        var arcs = new ArrayList<Arc>();
        for (var cover : covers) {

            if (cover.start() > coveredTo) {

                arcs.add(new Arc(
                    circle,
                    coveredTo,
                    cover.start(),
                    formatDiscTerminal(circle, coveredBy, siteCount),
                    formatDiscTerminal(cover.other(), circle, siteCount)));
            }

            if (cover.start() + cover.width() > coveredTo) {

                coveredTo = cover.start() + cover.width();
                coveredBy = cover.other();
            }
        }
        if (coveredTo < windowEnd) {
            arcs.add(new Arc(
                circle,
                coveredTo,
                windowEnd,
                formatDiscTerminal(circle, coveredBy, siteCount),
                formatDiscTerminal(covers.get(0).other(), circle, siteCount)));
        }
        return arcs;
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
                2 * halfWidth, other));
        }
        return covers;
    }

    // Where the chords come down on one circle. A chord meets it at the single angle facing
    // the circle at its far end, so there is nothing to search for and nothing to round.
    private static List<Attachment> findAttachmentsOn(
            int circle,
            List<double[]> sites,
            List<Chord> chords) {

        var attachments = new ArrayList<Attachment>();

        for (var index = 0; index < chords.size(); index++) {

            var chord = chords.get(index);

            if (chord.fromCircle() == circle) {

                attachments.add(new Attachment(
                    measureAngleTowards(sites, circle, chord.toCircle()), index, FROM_SIDE));

            } else if (chord.toCircle() == circle) {

                attachments.add(new Attachment(
                    measureAngleTowards(sites, circle, chord.fromCircle()), index, TO_SIDE));
            }
        }
        return attachments;
    }

    // A circle no disc covers, cut into arcs by the chords alone: each runs from one
    // attachment round to the next, and the last wraps back to the first.
    private static List<Arc> buildWholeCircleArcs(int circle, List<Attachment> attachments) {

        var ordered = new ArrayList<>(attachments);
        ordered.sort(Comparator.comparingDouble(Attachment::angle));

        var arcs = new ArrayList<Arc>(ordered.size());

        for (var index = 0; index < ordered.size(); index++) {

            var from = ordered.get(index);
            var to = ordered.get((index + 1) % ordered.size());

            // A single attachment wraps the whole way round to itself, so the far angle is
            // never taken as the near one.
            var toAngle = to.angle() > from.angle()
                ? to.angle()
                : to.angle() + FULL_TURN;

            arcs.add(new Arc(
                circle,
                from.angle(),
                toAngle,
                formatChordTerminal(from.chord(), from.side()),
                formatChordTerminal(to.chord(), crossSide(to.side()))));
        }
        return arcs;
    }

    private static List<Arc> splitArcsAtAttachments(
            List<Arc> arcs,
            List<Attachment> attachments) {

        if (attachments.isEmpty()) {
            return arcs;
        }

        var split = new ArrayList<Arc>(arcs.size() + attachments.size());
        for (var arc : arcs) {
            split.addAll(splitArcAtAttachments(arc, attachments));
        }
        return split;
    }

    // One uncovered stretch, cut wherever a chord comes down inside it. The piece arriving at
    // an attachment carries on along the chord, so its far terminal is the one the chord's
    // OTHER end starts at; the piece leaving carries that end's own terminal.
    private static List<Arc> splitArcAtAttachments(Arc arc, List<Attachment> attachments) {

        var inside = new ArrayList<Attachment>();

        for (var attachment : attachments) {

            // Turned into the arc's own window before comparing, because an arc's angles are
            // measured from wherever its circle's sweep began rather than from zero.
            var placed = arc.fromAngle() + normaliseAngle(attachment.angle() - arc.fromAngle());

            if (placed < arc.toAngle()) {
                inside.add(new Attachment(placed, attachment.chord(), attachment.side()));
            }
        }
        if (inside.isEmpty()) {
            return List.of(arc);
        }
        inside.sort(Comparator.comparingDouble(Attachment::angle));

        var pieces = new ArrayList<Arc>(inside.size() + 1);
        var fromAngle = arc.fromAngle();
        var startsAt = arc.startsAt();

        for (var attachment : inside) {

            pieces.add(new Arc(
                arc.circle(),
                fromAngle,
                attachment.angle(),
                startsAt,
                formatChordTerminal(attachment.chord(), crossSide(attachment.side()))));

            fromAngle = attachment.angle();
            startsAt = formatChordTerminal(attachment.chord(), attachment.side());
        }
        pieces.add(new Arc(arc.circle(), fromAngle, arc.toAngle(), startsAt, arc.endsAt()));

        return pieces;
    }

    // Which arc the boundary continues onto, as a plain join: every arc names the terminal it
    // begins at and the terminal it runs on to, and the two are the same name. That holds for
    // a crossing between two discs and for a chord's two ends alike, so nothing here has to
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
            (int) Math.ceil(arcSegments * sweep / Math.PI));

        var points = new ArrayList<double[]>(steps);

        // The far end is left off: the next arc round the cycle begins on it.
        for (var step = 0; step < steps; step++) {

            points.add(findPointOnCircle(centre, radius, fromAngle + sweep * step / steps));
        }
        return points;
    }

    // A chord end sits on the boundary when no disc but its own circle's swallows it. Asked
    // of the point rather than of the angles, because that is the definition of the boundary
    // and needs no interval arithmetic to agree with. The chord's own partner is asked like
    // any other: a partner nearer than two radii covers the end, which is exactly the case
    // where the two cells have closed over and there is no gap left to wall off.
    private static boolean isChordEndOnBoundary(
            List<double[]> sites,
            double radius,
            int onCircle,
            int towardsCircle) {

        var end = findChordEnd(sites, radius, onCircle, towardsCircle);

        for (var site = 0; site < sites.size(); site++) {

            if (site == onCircle) {
                continue;
            }
            if (Points.computeDistance(end, sites.get(site)) < radius) {
                return false;
            }
        }
        return true;
    }

    private static double measureAngleTowards(
            List<double[]> sites,
            int fromCircle,
            int toCircle) {

        return normaliseAngle(Math.atan2(
            sites.get(toCircle)[1] - sites.get(fromCircle)[1],
            sites.get(toCircle)[0] - sites.get(fromCircle)[0]));
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
        return terminal < 0;
    }

    private static int crossSide(int side) {
        return side == FROM_SIDE ? TO_SIDE : FROM_SIDE;
    }

    private static double normaliseAngle(double angle) {
        var turned = angle % FULL_TURN;
        return turned < 0 ? turned + FULL_TURN : turned;
    }

    /**
     * One stretch of a circle that lies inside a neighbour's disc.
     *
     * @param start the angle it begins at, anticlockwise
     * @param width how far it runs, always under a half turn
     * @param other the neighbouring circle that covers it
     */
    private record Cover(
        double start,
        double width,
        int other) {
    }

    /**
     * Where one chord comes down on one circle.
     *
     * @param angle the angle on that circle, which faces the chord's other circle
     * @param chord which chord it belongs to
     * @param side  which of that chord's two circles this is
     */
    private record Attachment(
        double angle,
        int chord,
        int side) {
    }

    /**
     * One stretch of a circle that no disc covers - a single edge of the union's boundary.
     *
     * @param circle    whose circle it runs on
     * @param fromAngle the angle it begins at
     * @param toAngle   the angle it ends at, always greater than {@code fromAngle}
     * @param startsAt  the terminal it begins at, naming either the disc it comes out of or
     *                  the chord end it leaves
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
