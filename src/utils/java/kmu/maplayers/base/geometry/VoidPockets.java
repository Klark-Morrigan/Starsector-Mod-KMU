package kmu.maplayers.base.geometry;

import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The pockets of void between cells, as the holes in the union of the cells' reach discs.
 *
 * <p>A point is void when its nearest site is further than {@code cellRadius}, so the void is
 * exactly the complement of the union of one disc of that radius per site. The boundary of
 * that union is a set of closed cycles of circular arcs, and each cycle is either the outer
 * silhouette of a run of overlapping cells or a hole enclosed by them. The holes are the void
 * the cells bind; everything outside the outer cycles is the void they do not.
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
 * <p>A pocket is handed back already shaped, the same way {@link CellShaper} hands back a
 * fill polygon rather than a raw cell, and under the same rule: a border keeps the channel,
 * a same-owner seam does not. A pocket ringed entirely by one owner is interior to that
 * owner's cluster and so has no border anywhere on it - it is pushed OUT to meet the
 * surrounding fills instead, closing a gap that would otherwise sit inside a single owner's
 * area for no reason a player could read. A pocket with more than one owner around it, or
 * any unowned cell, keeps the channel and is pulled in.
 *
 * <p>Both the pulling in and the pushing out are taken by moving the REACH, not by
 * offsetting the outline. A pocket is the set of points further than {@code cellRadius} from
 * every site, and distance to the nearest site changes no faster than the distance walked, so
 * the set further than {@code cellRadius + channel} is exactly the pocket inset by the
 * channel, and the set further than {@code cellRadius - channel} is exactly the pocket grown
 * by it. Recomputing the same ring of arcs at the moved radius is therefore an exact offset,
 * and it is also cheaper: each corner is where two circles cross, so it lands where it
 * belongs instead of being mitred to a guess. A general offsetter has to mitre a corner it
 * cannot see the shape of, and a pocket's corners are cusps - two arcs meeting at a sliver -
 * where a miter shoots a long spike out through whatever lies beyond.
 *
 * <p>A pocket too narrow to give up the channel has no outline at all. Handing back its
 * true extent instead would put it flush against the cells, breaking the one rule the whole
 * shaping exists to keep - that nothing touches anything else without the channel between
 * them - and it would do so on exactly the pockets least able to justify the exception. It
 * keeps its centre, its span and the cells around it, so it can still be counted, measured
 * and divided; only the fill is withheld, and a caller with nowhere to draw it can mark the
 * centre.
 *
 * <p>This replaces a construction that only closed a boundary around a single Voronoi vertex,
 * which meant exactly three cells. On the shipped fixture that left 12 of 19 pockets with no
 * outline, no area, and nothing to draw. The count of surrounding cells is not a special case
 * here: it is however many arcs the cycle turned out to have.
 */
final class VoidPockets {

    private static final double FULL_TURN = 2 * Math.PI;
    private static final int MIN_BOUNDARY_VERTICES = 3;

    // Enough to keep a short arc from collapsing to a chord once the sampling is scaled down
    // in proportion to how little of the circle it covers.
    private static final int MIN_ARC_SAMPLES = 2;

    // Stands in for a neighbouring circle where there is none: a disc that overlaps nothing
    // contributes its whole circle as one arc, with no disc entered or left at either end.
    private static final int NO_CIRCLE = -1;
    private static final int NO_SUCCESSOR = -1;

    // Slack on the sweep comparison that decides a reshaped pocket is sound, in radians.
    // Only there to keep an arc that did not move at all from reading as one that moved the
    // wrong way.

    /**
     * One pocket of void, bound by the cells around it.
     *
     * <p>Bound by construction: a pocket is a hole in the union of the reach discs, and a hole
     * is enclosed by definition. Void that opens outward is not a pocket at all - it is what
     * lies outside the union's outer cycles - so there is no flag to read.
     *
     * @param outlines       what to draw for it, at the reach that leaves the border
     *                       channel, or at the reach the surrounding fills stop at when one
     *                       owner rings it. Usually one. EMPTY when the channel closes the
     *                       pocket over, because a pocket with no room for the channel must
     *                       not be drawn without one. MORE THAN ONE when taking the channel
     *                       out pinches the pocket in two, which a single outline could not
     *                       say
     * @param centre         the middle of its true extent, so a pocket with nothing to draw
     *                       can still be pointed at
     * @param adjacentCells  the sites whose cells ring it, in the order its outline meets
     *                       them, which are also its sections
     * @param span           the distance between its two most distant points, measured on
     *                       the void itself and not on what is drawn, so the threshold it is
     *                       tested against does not move with the channel width
     * @param absorbingOwner the owner whose cells ring it on every side, or null when more
     *                       than one owner does, or any unowned cell - non-null means the
     *                       pocket is part of that owner's area rather than void between
     *                       owners
     */
    record VoidPocket(
        List<List<double[]>> outlines,
        double[] centre,
        List<Integer> adjacentCells,
        double span,
        String absorbingOwner) {
    }

    private VoidPockets() {
    }

    /**
     * Finds every pocket of void the cells close around.
     *
     * @param sites        the sites
     * @param ownerBySite  each site's owner, index-aligned with {@code sites} and null where
     *                     the site is unowned, to decide which pockets sit inside one owner's
     *                     area rather than between owners
     * @param parameters   the same knobs the cells are built under, so the void takes the
     *                     reach that decides where it begins and the channel the cells leave
     * @param arcSegments  how finely a half-turn of arc is sampled; a shorter arc takes
     *                     proportionally fewer points
     * @param shouldUnownedBlockAbsorption whether an unowned cell around a pocket stops it
     *                     closing into the owner holding the rest. True matches how a cell
     *                     treats unowned space - as a border, keeping the channel - and is
     *                     what the shipped shaping does
     * @return the pockets, each with a closed outline
     */
    static List<VoidPocket> findVoidPockets(
            List<double[]> sites,
            List<String> ownerBySite,
            SectorGeometryParameters parameters,
            int arcSegments,
            boolean shouldUnownedBlockAbsorption) {

        var trueHoles = findHolesAtReach(
            sites,
            parameters.cellRadius(),
            arcSegments);

        var withChannel = findHolesAtReach(
            sites,
            parameters.cellRadius() + parameters.borderInset(),
            arcSegments);

        var atFills = findHolesAtReach(
            sites,
            parameters.cellRadius() - parameters.borderInset(),
            arcSegments);

        var pockets = new ArrayList<VoidPocket>(trueHoles.size());

        for (var hole : trueHoles) {

            var absorbingOwner = resolveAbsorbingOwner(
                hole.ringing(),
                ownerBySite,
                shouldUnownedBlockAbsorption);

            pockets.add(new VoidPocket(
                absorbingOwner == null
                    ? findHolesInside(withChannel, hole)
                    : findHoleAround(atFills, hole),
                findCentre(hole.boundary()),
                hole.ringing(),
                measureWidestSpan(hole.corners()),
                absorbingOwner));
        }
        return pockets;
    }

    // Every hole in the union of discs of one radius. Run at the true reach it finds the
    // pockets; run at the reach plus the channel it finds what is left of them once the
    // channel is taken out; run at the reach minus it, what they become when the cells fill
    // right up to them.
    //
    // Run afresh at each reach rather than redrawing one ring's arcs at another radius. A
    // ring is not the same ring at a different reach: a cell whose arc its neighbours have
    // swallowed drops out of it, and a pocket can pinch in two. Redrawing in place cannot
    // express either, and reads both as the pocket having closed.
    private static List<Hole> findHolesAtReach(
            List<double[]> sites,
            double radius,
            int arcSegments) {

        var arcs = findUncoveredArcs(sites, radius);
        var successors = linkArcsIntoCycles(arcs, sites.size());
        var holes = new ArrayList<Hole>();
        var walked = new boolean[arcs.size()];

        for (var start = 0; start < arcs.size(); start++) {

            var cycle = walkCycleFrom(start, arcs, successors, walked);
            if (cycle.isEmpty()) {
                continue;
            }

            var hole = buildHole(cycle, arcs, sites, radius, arcSegments);
            if (hole != null) {
                holes.add(hole);
            }
        }
        return holes;
    }

    // The holes the channel leaves inside one pocket - none when it closes over, more than
    // one when it pinches the pocket in two. A point on a narrowed hole's outline is further
    // from every site than the true reach, so it lies in the true void, and in the very
    // pocket it came out of.
    private static List<List<double[]>> findHolesInside(List<Hole> narrowed, Hole hole) {

        var inside = new ArrayList<List<double[]>>();
        for (var candidate : narrowed) {
            if (isPointInside(candidate.boundary().get(0), hole.boundary())) {
                inside.add(candidate.boundary());
            }
        }
        return inside;
    }

    // The widened hole a pocket opens into, if it stays closed at all. The other way round
    // from the narrowed case: the pocket is inside the widened hole, so it is the pocket's
    // own outline that is tested.
    private static List<List<double[]>> findHoleAround(List<Hole> widened, Hole hole) {

        for (var candidate : widened) {
            if (isPointInside(hole.boundary().get(0), candidate.boundary())) {
                return List.of(candidate.boundary());
            }
        }
        return List.of();
    }

    // Ray casting: a ray from the point crosses a closed outline an odd number of times when
    // it starts inside it.
    private static boolean isPointInside(double[] point, List<double[]> outline) {

        var inside = false;
        for (var index = 0; index < outline.size(); index++) {

            var from = outline.get(index);
            var to = outline.get((index + 1) % outline.size());

            if (from[1] > point[1] != to[1] > point[1]
                    && point[0] < (to[0] - from[0]) * (point[1] - from[1])
                        / (to[1] - from[1]) + from[0]) {
                inside = !inside;
            }
        }
        return inside;
    }


    // Every stretch of every circle that no other disc covers, which is the whole boundary of
    // the union - outer silhouettes and holes alike, not yet told apart.
    private static List<Arc> findUncoveredArcs(List<double[]> sites, double cellRadius) {

        var arcs = new ArrayList<Arc>();
        for (var circle = 0; circle < sites.size(); circle++) {
            arcs.addAll(findUncoveredArcsOn(circle, sites, cellRadius));
        }
        return arcs;
    }

    // One circle's uncovered stretches, as the gaps between its neighbours' covering
    // intervals. Kept as angles rather than points so the two ends of a gap stay attributed to
    // the neighbour that made them.
    private static List<Arc> findUncoveredArcsOn(
            int circle,
            List<double[]> sites,
            double cellRadius) {

        var covers = findCoveringIntervals(circle, sites, cellRadius);
        if (covers.isEmpty()) {
            return List.of(new Arc(circle, 0, FULL_TURN, NO_CIRCLE, NO_CIRCLE));
        }

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

                arcs.add(new Arc(circle, coveredTo, cover.start(), coveredBy, cover.other()));
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
                coveredBy,
                covers.get(0).other()));
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
            var separation = measureDistance(centre, sites.get(other));
            if (separation >= 2 * cellRadius || separation == 0) {
                continue;
            }
            var towards = Math.atan2(
                sites.get(other)[1] - centre[1],
                sites.get(other)[0] - centre[0]);

            var halfWidth = Math.acos(separation / (2 * cellRadius));

            covers.add(new Cover(
                normaliseAngle(towards - halfWidth),
                2 * halfWidth, other));
        }
        return covers;
    }

    // Which arc the boundary continues onto. An arc stops where its circle enters a
    // neighbour's disc; from there the boundary runs along that neighbour, starting where the
    // neighbour in turn leaves this circle's disc. Two circles cross twice, but only one of
    // those crossings is where the neighbour leaves, so the pair names one arc.
    private static int[] linkArcsIntoCycles(List<Arc> arcs, int siteCount) {

        var byExit = new LinkedHashMap<Long, Integer>();

        for (var index = 0; index < arcs.size(); index++) {

            var arc = arcs.get(index);
            byExit.put(formatLinkKey(arc.circle(), arc.leavesCircle(), siteCount), index);
        }

        var successors = new int[arcs.size()];

        for (var index = 0; index < arcs.size(); index++) {

            var arc = arcs.get(index);

            if (arc.entersCircle() == NO_CIRCLE) {
                // A disc overlapping nothing is its own closed cycle.
                successors[index] = index;
                continue;
            }

            var next = byExit.get(
                formatLinkKey(arc.entersCircle(), arc.circle(), siteCount));

            successors[index] = next == null
                ? NO_SUCCESSOR
                : next;
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

    // A cycle is a hole when it winds the opposite way to a silhouette. Each arc is walked
    // anticlockwise on its own circle, which keeps the discs' interior to the left the whole
    // way round, so an outer cycle comes out anticlockwise and a hole clockwise.
    private static Hole buildHole(
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
        }

        if (boundary.size() < MIN_BOUNDARY_VERTICES
                || PolygonRegions.computeSignedArea(boundary) >= 0) {
            return null;
        }

        // Reversed so a hole reads the same way round as any other filled shape.
        java.util.Collections.reverse(boundary);
        return new Hole(boundary, corners, List.copyOf(ringing));
    }

    private static double[] findCentre(List<double[]> boundary) {

        var x = 0.0;
        var y = 0.0;

        for (var point : boundary) {

            x += point[0];
            y += point[1];
        }
        return new double[] {
            x / boundary.size(),
            y / boundary.size()};
    }


    // The owner that rings a pocket on every side, if one does. What an unowned cell does
    // to that is the caller's call: counting it against absorption is what a cell does with
    // unowned space, and setting it aside asks instead whether any RIVAL is present.
    private static String resolveAbsorbingOwner(
            List<Integer> ringing,
            List<String> ownerBySite,
            boolean shouldUnownedBlockAbsorption) {

        String only = null;

        for (var site : ringing) {

            var owner = ownerBySite.get(site);
            if (owner == null) {
                if (shouldUnownedBlockAbsorption) {
                    return null;
                }
                continue;
            }
            if (only != null && !only.equals(owner)) {
                return null;
            }
            only = owner;
        }
        return only;
    }

    // How far a pocket reaches across, measured only between the points where its arcs meet.
    // Those are the only candidates: every disc bounding a pocket has its centre OUTSIDE it,
    // so each arc bulges into the pocket rather than away from it, and the pocket's extreme
    // points can only be the corners where two arcs join. That makes this the pocket's true
    // widest span and not an artefact of how finely the arcs were sampled - and it runs over
    // the arc count rather than the sample count, which is a handful either way.
    private static double measureWidestSpan(List<double[]> corners) {

        var widest = 0.0;

        for (var first = 0; first < corners.size(); first++) {
            for (var second = first + 1; second < corners.size(); second++) {

                widest = Math.max(
                    widest,
                    measureDistance(corners.get(first), corners.get(second)));
            }
        }
        return widest;
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

            var angle = fromAngle + sweep * step / steps;

            points.add(new double[] {
                centre[0] + radius * Math.cos(angle),
                centre[1] + radius * Math.sin(angle)});
        }
        return points;
    }

    private static long formatLinkKey(int circle, int neighbour, int siteCount) {
        return (long) circle * siteCount + neighbour;
    }

    private static double normaliseAngle(double angle) {
        var turned = angle % FULL_TURN;
        return turned < 0 ? turned + FULL_TURN : turned;
    }

    private static double measureDistance(double[] from, double[] to) {
        return Math.hypot(to[0] - from[0], to[1] - from[1]);
    }

    /**
     * One hole in the union of the discs at a single reach, before anything is decided about
     * it. Not a pocket yet: a pocket is a hole at the TRUE reach, together with what becomes
     * of it at the reach it is drawn at.
     *
     * @param boundary its outline
     * @param corners  where its arcs meet, which are its only extreme points
     * @param ringing  the sites whose circles it runs on, in the order it meets them
     */
    private record Hole(
        List<double[]> boundary,
        List<double[]> corners,
        List<Integer> ringing) {
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
     * One stretch of a circle that no disc covers - a single edge of the union's boundary.
     *
     * @param circle       whose circle it runs on
     * @param fromAngle    the angle it begins at
     * @param toAngle      the angle it ends at, always greater than {@code fromAngle}
     * @param leavesCircle the disc the circle comes out of at {@code fromAngle}
     * @param entersCircle the disc the circle goes into at {@code toAngle}
     */
    private record Arc(
        int circle,
        double fromAngle,
        double toAngle,
        int leavesCircle,
        int entersCircle) {
    }
}
