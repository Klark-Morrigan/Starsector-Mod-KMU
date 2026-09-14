package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.DiscUnionBoundary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which parts of a cell's exposed border a straight line could arrive at from the open void.
 *
 * <p><b>Exposed is not the same as reachable, and the gap between them is the subject.</b> The
 * walk hands back every stretch of border that faces void, and a good deal of that void is at
 * the bottom of a notch between cells - real void, connected to the open sea by a path, but not
 * by a STRAIGHT one. Nothing on the map arrives anywhere except in a straight line: a wall is a
 * chord, and a border between two stretches is a reach across the gap. So a stretch no straight
 * line can arrive at is a stretch nothing can be anchored on, however much of the cell it is and
 * however plainly it faces water.
 *
 * <p><b>Answered from the discs alone.</b> Nothing here reads a coastline, and that is the point
 * of it. Where a wall may be anchored is currently read off the line already drawn, which is
 * fine for laying walls beside a finished coast and useless for deciding where that coast should
 * have gone - the answer would be the question. Reachability is a fact about the cells, settled
 * before any line exists, so it can be asked at any stage.
 *
 * <p><b>What "a straight line could arrive" comes to, exactly.</b> From a point on a cell's
 * border, every OTHER cell blackens a cone of directions - the cone it subtends, which is
 * {@code asin(reach / distance)} either side of its bearing. The point's own cell blackens
 * exactly the inward half turn, since a line leaving into its own cell has not left. What is
 * left over after all of them is the set of directions that reach open void without crossing
 * anything, and a point with none of them left is walled in whatever it faces.
 *
 * <p>So a cell's own disc needs no special case: it enters the sum as one more blocker, at a
 * distance of exactly its reach, and the cone that comes out is the half turn facing inwards.
 *
 * <p>Water the cells closed around is handled by the same sum without being mentioned in it. A
 * point on a lake shore looks across the lake into the cells on the far side, which blacken the
 * directions it would need - so an enclosed shore comes out unreachable because it IS enclosed,
 * rather than because anything here was told to treat lakes differently.
 *
 * <p><b>A diagnostic, and only that.</b> Nothing reads it: it exists to be drawn beside the
 * coasts, so that what a landing rule would be working with can be looked at before anything is
 * made to obey one.
 */
public final class LandableFrontages {

    // How wide a gap in the blackened directions has to be to count as somewhere to arrive from.
    //
    // Chiefly arithmetic. Two cells that touch subtend cones that meet exactly, and in floating
    // point "exactly" comes out as a sliver either side of nothing - so a run of touching cells
    // would otherwise leave a hairline of open sky between every pair of them and read as
    // reachable the whole way along.
    //
    // A degree is also about the narrowest opening that means anything: at the distance a
    // neighbouring cell sits, it is a corridor a few tens of units across, and nothing on this
    // map is laid down a gap that size.
    private static final double LEAST_OPEN_RADIANS = Math.toRadians(1);

    // Where a blackened cone's two ends sit within the pair it is carried as.
    private static final int CONE_FROM = 0;
    private static final int CONE_TO = 1;

    private LandableFrontages() {
    }

    /**
     * Every stretch of exposed border a straight line could arrive at, over a whole sector.
     *
     * <p>Sub-stretches of the walk's own, so one exposed stretch may come back as several, as
     * one narrower than itself, or not at all. Carried as {@link DiscUnionBoundary.CoastMark}
     * because that is what they are - a run of one cell's border between two angles - and a
     * second type saying the same thing is a second thing to keep in step.
     *
     * @param traced      the coast, for the stretches its walk found and the discs to measure
     *                    them against
     * @param arcSegments how finely a half-turn of arc is sampled, which is the resolution the
     *                    answer is decided at
     * @return one mark per reachable sub-stretch, in walk order. A stretch nothing can arrive
     *         at is absent, and one reachable at a single sampled angle comes back as a mark of
     *         no width, which is what that stretch honestly offers
     */
    public static List<DiscUnionBoundary.CoastMark> collectLandableFrontages(
            Coastlines.TracedCoasts traced,
            int arcSegments) {

        var landable = new ArrayList<DiscUnionBoundary.CoastMark>();

        for (var mark : gatherOuterMarks(traced)) {
            addLandableStretches(landable, traced.union(), mark, arcSegments);
        }
        return List.copyOf(landable);
    }

    /**
     * The same answer as lines along the borders they sit on, for drawing.
     *
     * <p>Sampled through the one flattening every reader of a raw arc uses, so a landable
     * stretch and a dropped one measured on the same border land on the same points.
     *
     * @param traced      the coast
     * @param arcSegments how finely a half-turn of arc is sampled
     * @return one open run of points per reachable sub-stretch
     */
    public static List<List<double[]>> collectLandableRuns(
            Coastlines.TracedCoasts traced,
            int arcSegments) {

        var runs = new ArrayList<List<double[]>>();

        for (var mark : collectLandableFrontages(traced, arcSegments)) {
            runs.add(Coastlines.sampleMarkArc(traced.union(), mark, arcSegments));
        }
        return List.copyOf(runs);
    }

    // Every stretch of border the sector shows the outside world: the continents' silhouettes,
    // and each lone cell's whole turn.
    //
    // The islands are put in by hand because the walk leaves them out - a cell alone in the void
    // is on no silhouette, having no coast, and the whole of its border nonetheless faces the
    // void. Leaving them out here would report the shapes a link most needs to reach as offering
    // nowhere to reach.
    private static List<DiscUnionBoundary.CoastMark> gatherOuterMarks(
            Coastlines.TracedCoasts traced) {

        var marks = new ArrayList<DiscUnionBoundary.CoastMark>();

        for (var silhouette : traced.silhouettes()) {
            marks.addAll(silhouette);
        }
        for (var island : traced.islands()) {
            marks.add(new DiscUnionBoundary.CoastMark(island, 0, Angles.FULL_TURN));
        }
        return marks;
    }

    // One exposed stretch cut into the runs of it that can be arrived at. Walked as samples
    // rather than solved, because whether a direction survives every cell on the map is not a
    // question with a closed form along the arc - so the arc is asked at the density everything
    // else on this map is drawn at.
    private static void addLandableStretches(
            List<DiscUnionBoundary.CoastMark> landable,
            DiscUnion union,
            DiscUnionBoundary.CoastMark mark,
            int arcSegments) {

        var sweep = mark.toAngle() - mark.fromAngle();
        var steps = Math.max(1, (int) Math.ceil(arcSegments * sweep / Angles.HALF_TURN));

        var openedAt = -1;

        for (var step = 0; step <= steps; step++) {

            var angle = mark.fromAngle() + sweep * step / steps;

            if (isLandableAt(union, mark, angle)) {

                if (openedAt < 0) {
                    openedAt = step;
                }
                continue;
            }

            addStretch(landable, mark, sweep, steps, openedAt, step - 1);
            openedAt = -1;
        }
        addStretch(landable, mark, sweep, steps, openedAt, steps);
    }

    // One run of consecutive reachable samples as the stretch of border it covers.
    private static void addStretch(
            List<DiscUnionBoundary.CoastMark> landable,
            DiscUnionBoundary.CoastMark mark,
            double sweep,
            int steps,
            int openedAt,
            int closedAt) {

        if (openedAt < 0 || closedAt < openedAt) {
            return;
        }

        landable.add(new DiscUnionBoundary.CoastMark(
            mark.circle(),
            mark.fromAngle() + sweep * openedAt / steps,
            mark.fromAngle() + sweep * closedAt / steps));
    }

    // Whether any direction out of one point on a cell's border survives every disc on the map.
    private static boolean isLandableAt(
            DiscUnion union,
            DiscUnionBoundary.CoastMark mark,
            double angle) {

        var from = DiscUnionBoundary.findPointOnMark(union, mark, angle);
        var cones = new ArrayList<double[]>(union.sites().size() + 1);

        for (var circle = 0; circle < union.sites().size(); circle++) {
            addBlockedCone(cones, union, from, circle);
        }
        return !isTurnFullyBlocked(cones);
    }

    // The directions out of a point that run into one disc: its bearing, give or take the angle
    // it subtends from there.
    //
    // The ratio is held at one for a point ON a disc rather than branched on, since that is what
    // the arithmetic already says - a point at exactly the reach subtends a quarter turn either
    // side, which is the inward half turn, and is the case a cell's own disc always is.
    private static void addBlockedCone(
            List<double[]> cones,
            DiscUnion union,
            double[] from,
            int circle) {

        var centre = union.sites().get(circle);
        var distance = Points.computeDistance(from, centre);
        var subtended = Math.asin(Math.min(1, union.reach() / distance));
        var bearing = Math.atan2(centre[1] - from[1], centre[0] - from[0]);

        addCone(cones, bearing - subtended, bearing + subtended);
    }

    // One cone, in the first turn, split where it runs over the end of it so that the sweep
    // below can read every cone as an ordinary interval.
    private static void addCone(List<double[]> cones, double from, double to) {

        var start = Angles.normalise(from);
        var end = start + (to - from);

        if (end <= Angles.FULL_TURN) {

            cones.add(new double[] {start, end});
            return;
        }

        cones.add(new double[] {start, Angles.FULL_TURN});
        cones.add(new double[] {0, end - Angles.FULL_TURN});
    }

    // Whether the cones between them leave no direction open. One sweep in order: a gap appears
    // wherever the next cone begins past everything covered so far.
    private static boolean isTurnFullyBlocked(List<double[]> cones) {

        cones.sort(Comparator.comparingDouble(cone -> cone[CONE_FROM]));

        var covered = 0.0;

        for (var cone : cones) {

            if (cone[CONE_FROM] > covered + LEAST_OPEN_RADIANS) {
                return false;
            }
            covered = Math.max(covered, cone[CONE_TO]);
        }
        return covered >= Angles.FULL_TURN - LEAST_OPEN_RADIANS;
    }
}
