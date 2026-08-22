package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.Points;
import kmlib.math.ranges.Ranges;

/**
 * Where a straight run of coast leaves one cell and lands on the next.
 *
 * <p>The one sub-problem of coast smoothing that is geometry rather than bookkeeping. A run
 * wants to join the middles of two frontages; each end gives way to whatever the straight
 * line would cut, sliding along its own cell's border to the nearest place the line can
 * actually reach; and where both ends give way at once, the two settle on the common
 * tangent. {@link Coastlines} owns which stretches a coast keeps and how the fillets between
 * runs are sampled - everything about where one run's two ends land is answered here.
 *
 * <p>Its own class because the smoothing asks the question more than one way - placing a run
 * it will draw, and judging a jump it is thinking of making - and the answer has to come from
 * one set of rules. Two copies of the clamp is how a jump gets approved that the drawing then
 * refuses, with neither able to see the disagreement.
 */
final class StraightRuns {

    // Each pass slides one end to the furthest the other allows, so the two close on the
    // common tangent from opposite directions. Four is past the point where the movement
    // stops being visible; the clearance check afterwards is what says whether it was enough.
    private static final int CLAMP_PASSES = 4;

    // Room for the passes to stop short of what they converge on: they APPROACH a fixed
    // point rather than landing on it, so a collapsed run keeps a residue a shade above the
    // tolerance at which two points count as one place rather than nothing at all.
    private static final int APPROACH_RESIDUE = 10;

    // How short a run has to be to count as having collapsed onto the point where its two
    // circles cross rather than spanning the notch between them.
    //
    // The two sides of this are nowhere near each other: over both fixtures every collapsed
    // run measures under 1.4 units and every real one over 300, so the value has only to
    // fall in that gap rather than be tuned to it.
    //
    // Deliberately NOT the wall channel, which lands in the same gap and asks a different
    // question - whether a wall has room to hold its two sides apart - and which moves with
    // a slider. Whether an iteration converged is a fact about the arithmetic, and tying it
    // to a knob would let a wide channel start refusing runs that go somewhere.
    private static final double COLLAPSED_ONTO_A_CROSSING =
        DiscUnion.TOUCHING_TOLERANCE * APPROACH_RESIDUE;

    private StraightRuns() {
    }

    /**
     * One straight run of coast still being decided - the two stretches it joins, and the
     * discs it is drawn against.
     *
     * <p>One value rather than three arguments that happen to travel together: the marks name
     * circles by INDEX, so they mean nothing except against the union they were indexed in,
     * and a caller free to pair one construction's marks with another's discs can do it and
     * still compile.
     *
     * <p>Deliberately not the answer, only the question. Where the run ends up is an
     * {@link EdgeAngles}, worked out several ways and chosen between, so it is passed in
     * rather than held - one run, several candidate edges.
     *
     * @param union the discs the run is drawn against
     * @param from  the stretch it leaves
     * @param to    the stretch it lands on
     */
    record StraightRun(
        DiscUnion union,
        DiscUnionBoundary.CoastMark from,
        DiscUnionBoundary.CoastMark to) {

        double[] findDeparture(EdgeAngles edge) {
            return DiscUnionBoundary.findPointOnMark(union, from, edge.departAngle());
        }

        double[] findArrival(EdgeAngles edge) {
            return DiscUnionBoundary.findPointOnMark(union, to, edge.arriveAngle());
        }

        double[] findCentre(DiscUnionBoundary.CoastMark mark) {
            return union.sites().get(mark.circle());
        }

        /**
         * How far this run goes inside one cell, at one candidate edge.
         *
         * @param edge   where the run leaves and lands
         * @param circle whose cell to measure against
         * @return how far past that cell's border it goes, negative when it stays outside
         */
        double measureIncursion(EdgeAngles edge, int circle) {

            return union.measureIncursionInto(findDeparture(edge), findArrival(edge), circle);
        }

        /**
         * Whether a cell is one of the two this run goes between.
         *
         * @param circle whose cell to ask about
         * @return true when the run starts or ends on it
         */
        boolean isRunBetween(int circle) {
            return circle == from.circle() || circle == to.circle();
        }
    }

    /**
     * Where one straight run of coast leaves one cell and where it lands on the next.
     *
     * @param departAngle where on the near cell's border it leaves
     * @param arriveAngle where on the far cell's border it lands
     */
    record EdgeAngles(
        double departAngle,
        double arriveAngle) {
    }

    /**
     * The straight run between two cells where one can be drawn, and the boundary's own join
     * where it cannot.
     *
     * <p>The run is what buys the pocket space, and it is what nearly every pair gets: two
     * cells that overlap still have an outer tangent, and a run along it spans the notch
     * between them from outside, taking the void in the notch with it.
     *
     * <p>What stops it is a THIRD cell covering the direction the tangent needs. The tangent
     * point is then not on this cell's frontage at all, and clamping it back onto the
     * frontage moves it - by most of a right angle in the worst measured case - which leaves
     * a line that is no longer tangent to anything and cuts straight through a cell. For
     * those pairs there is no straight run from one frontage to the other that stays outside
     * both, so inventing one can only produce a bad one.
     *
     * <p>Two stretches the walk itself put next to each other need no line invented for them:
     * the boundary already joins them, at the far end of one frontage and the near end of the
     * other, which is one point where the cells overlap and the two sides of a mouth where a
     * bridge runs between them. That join cannot cut anything, because it IS the boundary.
     *
     * <p>Only where the run fails, though. Taking this join for every adjacent pair - which
     * an earlier version did - hugs the whole coast and gives up every pocket the smoothing
     * was for.
     *
     * @param run        the run being placed
     * @param isAdjacent whether the walk put its two stretches next to each other, which is
     *                   what makes the boundary's own join available as the fallback
     * @return where the run leaves and lands
     */
    static EdgeAngles findClearEdge(StraightRun run, boolean isAdjacent) {

        var reach = resolveEdge(run);

        if (!isAdjacent || isRunClearOfEveryCell(run, reach)) {

            return reach;
        }
        return new EdgeAngles(run.from().toAngle(), run.to().fromAngle());
    }

    /**
     * Where one straight run leaves one cell and lands on the next, clamped clear of both.
     *
     * <p>Each end is slid to the nearest place to its own middle that the other end can see,
     * and each pass moves one of them to the furthest the other now allows - so two ends
     * that both block settle towards the common tangent instead of one of them winning
     * outright.
     *
     * <p>The passes leave one end a step stale, though: the arrival was worked out against
     * the departure BEFORE the departure last moved. Where that still clears both cells it
     * is the answer, and where it does not the two are put straight onto the tangent, which
     * is the configuration they were converging on and is exact rather than approached.
     *
     * @param run the run being placed
     * @return where the run leaves and lands
     */
    static EdgeAngles resolveEdge(StraightRun run) {

        var clamped = clampEdgeEnds(run);

        // Clear is asked of EVERY cell rather than only the two the run joins. The two are
        // what the clamp was working against, so a run can satisfy both and still shave a
        // third cell that neither end knows about - and that run was then accepted, which is
        // where the shallow crossings came from.
        return isRunSpanningTheNotch(run, clamped)
            && isKeepingCellsLeft(run, clamped)
            && isRunClearOfEveryCell(run, clamped)
            ? clamped
            : findTangentEdge(run);
    }

    // Whether a clamped run goes anywhere at all.
    //
    // The clamp has TWO fixed points and only one of them is wanted. Sliding each end to the
    // nearest place the other can see converges on the common tangent - or, where the two
    // circles cross, onto the crossing itself, which lies on both of them and which each end
    // can therefore see from the other trivially. Settled there, the run leaves one cell and
    // arrives on the next without crossing any distance, so the notch it exists to span is
    // left open and the coast walks down into it instead.
    //
    // Refused on its own terms because the two tests beside it cannot see this: a run of no
    // length goes inside nothing, so it reads as clear of every cell, and its two ends make
    // no line to have a side, so both centres count as left of it. Both pass vacuously, and
    // the collapse is rubber-stamped as the answer.
    //
    // The tangent is exactly what such a pair needs - it spans the full width of the notch,
    // which for equal reaches is the distance between the two sites - so refusing the
    // collapse is enough on its own to hand the pair the line it should have had.
    private static boolean isRunSpanningTheNotch(StraightRun run, EdgeAngles edge) {

        return Points.computeDistance(run.findDeparture(edge), run.findArrival(edge))
            > COLLAPSED_ONTO_A_CROSSING;
    }

    /**
     * Whether a straight run passes outside every cell on the map.
     *
     * <p>Every cell, including the two it goes between - those it touches rather than
     * enters, so a hair of slack is what tells the two apart.
     *
     * @param run  the run to test
     * @param edge where it leaves and lands
     * @return whether it stays outside everything
     */
    static boolean isRunClearOfEveryCell(StraightRun run, EdgeAngles edge) {

        for (var circle = 0; circle < run.union().sites().size(); circle++) {

            if (run.measureIncursion(edge, circle) > DiscUnion.TOUCHING_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    // Whether a straight run keeps the cells it joins on its left, which is the side the walk
    // puts them on and so the only side a coast may pass them.
    //
    // The clamp cannot tell this for itself. It slides each end towards that cell's own
    // middle, and the two outer tangents sit the same distance either side of that middle, so
    // it settles on whichever one it happened to drift towards. The wrong one clears every
    // cell perfectly well - it is a tangent - and is still wrong: it leaves a cell where the
    // coast ought to arrive and arrives where it ought to leave, so the border between the two
    // sweeps backwards, collapses to a point, and the cell contributes nothing.
    //
    // On a long coast that costs one cell. On a two-cell island it costs both, and an outline
    // of two points is not a shape, so the island vanished off the map entirely.
    private static boolean isKeepingCellsLeft(StraightRun run, EdgeAngles edge) {

        var departure = run.findDeparture(edge);
        var arrival = run.findArrival(edge);

        return isLeftOfRun(departure, arrival, run.findCentre(run.from()))
            && isLeftOfRun(departure, arrival, run.findCentre(run.to()));
    }

    // On the line counts as left: a run that departs straight along a diameter has its own
    // cell's centre exactly on it, and that is the ordinary case rather than a failure.
    private static boolean isLeftOfRun(double[] from, double[] to, double[] point) {

        return (to[0] - from[0]) * (point[1] - from[1])
            - (to[1] - from[1]) * (point[0] - from[0]) >= 0;
    }

    // Each end slid to the nearest place to its own middle that the other end can see, over
    // and over, so two ends that both block settle towards the common tangent instead of one
    // of them winning outright.
    private static EdgeAngles clampEdgeEnds(StraightRun run) {

        var union = run.union();
        var departAngle = run.from().midAngle();
        var arriveAngle = run.to().midAngle();

        for (var pass = 0; pass < CLAMP_PASSES; pass++) {

            arriveAngle = findReachableAngle(
                union,
                run.to(),
                DiscUnionBoundary.findPointOnMark(union, run.from(), departAngle));
            departAngle = findReachableAngle(
                union,
                run.from(),
                DiscUnionBoundary.findPointOnMark(union, run.to(), arriveAngle));
        }
        return new EdgeAngles(departAngle, arriveAngle);
    }

    // The common tangent, put on each cell's own frontage. Where the frontage does not reach
    // the tangent point - a third cell covering that direction - this clamp moves it, and the
    // run stops being tangent to anything; that is the case the boundary's own join exists
    // for, and it is why the clamp is named rather than buried.
    private static EdgeAngles findTangentEdge(StraightRun run) {

        var tangent = measureTangentAngle(run);

        return new EdgeAngles(
            clampIntoFrontage(run.from(), tangent), clampIntoFrontage(run.to(), tangent));
    }

    // Equal reaches, so the outer tangent runs parallel to the line joining the two sites and
    // meets both circles square to it - one angle, the same on each.
    private static double measureTangentAngle(StraightRun run) {

        var fromCentre = run.findCentre(run.from());
        var toCentre = run.findCentre(run.to());

        return Math.atan2(
            -(toCentre[0] - fromCentre[0]),
            toCentre[1] - fromCentre[1]);
    }

    // An angle held to the stretch of border a cell actually offers. A run slid to clear
    // one neighbour can otherwise leave the cell's own frontage entirely, which puts the
    // coast on a piece of border that belongs to no stretch of the walk.
    private static double clampIntoFrontage(DiscUnionBoundary.CoastMark mark, double angle) {

        return Ranges.clampInto(
            Angles.placeAfter(angle, mark.fromAngle()), mark.fromAngle(), mark.toAngle());
    }

    // The place on one cell's frontage nearest its middle that a straight line from somewhere
    // else can touch without cutting through the cell. Seen from a point, the reachable part
    // of a circle is the arc facing it, half a turn wide less acos(reach / distance) - there
    // is nothing to search for. Narrowed again to the frontage itself, because a coast has no
    // business on the part of a cell's border that faces another cell rather than the void.
    private static double findReachableAngle(
            DiscUnion union,
            DiscUnionBoundary.CoastMark mark,
            double[] viewer) {

        var centre = union.sites().get(mark.circle());
        var distance = Points.computeDistance(centre, viewer);

        // A viewer inside the cell can reach no part of its border at all, so there is no
        // window to clamp into. The middle is the least wrong answer, and the clearance check
        // is what reports that this one could not be satisfied.
        if (distance <= union.reach()) {
            return mark.midAngle();
        }

        var facing = Angles.placeAfter(
            Math.atan2(viewer[1] - centre[1], viewer[0] - centre[0]),
            mark.fromAngle());

        var reachable = Math.acos(union.reach() / distance);

        return Ranges.clampInto(
            mark.midAngle(),
            Math.max(mark.fromAngle(), facing - reachable),
            Math.min(mark.toAngle(), facing + reachable));
    }
}
