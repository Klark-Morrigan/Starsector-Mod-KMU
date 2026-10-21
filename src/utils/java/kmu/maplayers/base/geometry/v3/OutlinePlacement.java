package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.CoastMark;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.walls.DiscUnionBoundary;

import java.util.ArrayList;
import java.util.List;

/**
 * The placement stage of a coast: the visited stretches turned into a closed run of points - a
 * fillet along each cell's own border from where the coast arrives to where it leaves, and a
 * straight reach from there to the next cell.
 *
 * <p>Every landing here is decided by what will clear the cells, and by nothing else. Which
 * stretches arrived is already settled by {@link StretchSelection}, so the only freedom left is
 * where on each of them the line touches - and holding the reaches outside every cell is what
 * that freedom is spent on.
 *
 * <p>Held over one union and one set of rules, because every run of a trace is placed against
 * the same two.
 */
final class OutlinePlacement {

    private final DiscUnion union;
    private final CoastPlacement.BorderRules rules;

    /**
     * @param union the discs to draw against
     * @param rules how finely to sample, and what to do with a join that is a wall's side
     */
    OutlinePlacement(DiscUnion union, CoastPlacement.BorderRules rules) {

        this.union = union;
        this.rules = rules;
    }

    /**
     * Places the line through one run's visited stretches.
     *
     * @param coast   every stretch the walk found, in walk order
     * @param visited which of them selection kept, as positions along that walk
     * @return the line, as points that each name the cell they sit on
     */
    List<CoastVertex> placeClearedOutline(List<CoastMark> coast, List<Integer> visited) {

        // A run of one has no reach to any other cell, so there is nothing to clamp against
        // and its whole frontage is the coast. That is a cell alone in the void, whose coast
        // is its own border - drawn over the top of it and so invisible, which is right.
        if (visited.size() == 1) {

            var only = coast.get(visited.get(0));

            return buildVerticesOnMark(
                only,
                sampleFillet(only, only.fromAngle(), only.toAngle()));
        }

        var walk = new VisitedWalk(coast, visited, union);
        var landings = Landings.forWalk(walk);

        for (var step = 0; step < walk.countStretches(); step++) {

            var next = walk.findStepAfter(step);
            var from = walk.findMarkAt(step);
            var to = walk.findMarkAt(next);
            var isAdjacent = walk.isAdjacentAfter(step);
            var run = new StraightRuns.StraightRun(union, from, to);

            landings.acceptPlacedRun(step, next, isAdjacent
                && isJoinedByWall(from, to)
                    ? placeRunAlongWall(run)
                    : new PlacedRun(
                        StraightRuns.findClearEdge(run, isAdjacent, false, rules.reachAnchor()),
                        false,
                        false));
        }

        uncrossLandings(walk, landings);

        var outline = new ArrayList<CoastVertex>();

        for (var step = 0; step < walk.countStretches(); step++) {

            var mark = walk.findMarkAt(step);

            outline.addAll(buildVerticesOnMark(
                mark,
                sampleFillet(
                    mark,
                    landings.arriveAngles()[step],
                    landings.departAngles()[step])));
        }

        return outline;
    }

    // Where a cell's two landings crossed, puts them back in walk order rather than losing the
    // stretch of border between them.
    //
    // A crossing is NOT the ordinary way a cell comes to contribute one point. Two neighbours
    // that can both see a cell's middle land on that middle, the sweep is zero, and the single
    // point is what this smoothing is for. A crossing is the other case: something pushed each
    // end past the other, so there IS a run of border between them, and a cell with a stretch
    // to offer reports one place instead.
    //
    // Swapping moves BOTH runs, so both are put back through the clearance rule that placed
    // them. Where either would cut a cell the crossing stands: a point on the boundary is
    // always better than a line through a cell, which is the whole premise of the placement.
    //
    // A cell next to another that is also swapping is left alone. The run between two such
    // cells would have both its ends move at once, which neither test above asked about - and
    // refusing that case is also what keeps this order-free, so the walk could begin anywhere
    // and swap the same cells.
    private static void uncrossLandings(VisitedWalk walk, Landings landings) {

        var isSwappable = new boolean[walk.countStretches()];

        for (var step = 0; step < walk.countStretches(); step++) {

            isSwappable[step] = !landings.isHeldByWallAt(step)
                && isCrossingClearWhenSwapped(walk, landings, step);
        }

        // Read off the flags rather than off the angles, so a swap already made cannot change
        // the verdict on the cell after it.
        for (var step = 0; step < walk.countStretches(); step++) {

            if (isSwappable[step]
                    && !isSwappable[walk.findStepBefore(step)]
                    && !isSwappable[walk.findStepAfter(step)]) {

                landings.swapLandingsAt(step);
            }
        }
    }

    // Whether a cell's landings crossed, and whether both runs still pass outside every cell
    // once they are put back in order.
    private static boolean isCrossingClearWhenSwapped(
            VisitedWalk walk,
            Landings landings,
            int step) {

        if (!landings.isCrossedAt(step)) {
            return false;
        }

        var previous = walk.findStepBefore(step);
        var next = walk.findStepAfter(step);

        return StraightRuns.isRunClearOfEveryCell(
                new StraightRuns.StraightRun(
                    walk.union(),
                    walk.findMarkAt(previous),
                    walk.findMarkAt(step)),
                new StraightRuns.EdgeAngles(
                    landings.departAngles()[previous],
                    landings.departAngles()[step]))
            && StraightRuns.isRunClearOfEveryCell(
                new StraightRuns.StraightRun(
                    walk.union(), walk.findMarkAt(step),
                    walk.findMarkAt(next)),
                new StraightRuns.EdgeAngles(
                    landings.arriveAngles()[step],
                    landings.arriveAngles()[next]));
    }

    // One stretch of coast as the points the line passes through: the sampled fillet running
    // along the cell's own border, each carrying the cell it belongs to so a later pass can
    // tell which stretch a point came off without matching coordinates back to a circle.
    private static List<CoastVertex> buildVerticesOnMark(
            CoastMark mark,
            List<double[]> points) {

        var vertices = new ArrayList<CoastVertex>(points.size());

        for (var point : points) {
            vertices.add(new CoastVertex(point, mark.circle()));
        }

        return vertices;
    }

    // A run the walk joined by a wall's side, placed as the shore it is on treats walls. Which
    // ends are held is reported with the edge because the un-crossing may not move them: a
    // landing that is a wall's own edge, or its anchor, is where the wall put it.
    private PlacedRun placeRunAlongWall(StraightRuns.StraightRun run) {

        if (rules.wallPlacement() == CoastPlacement.WallPlacement.HUG_SIDES) {

            return new PlacedRun(StraightRuns.findClearEdge(
                run,
                true,
                true,
                rules.reachAnchor()),
                true,
                true);
        }

        var isDepartureMouth = rules.walls().isPinchedOn(run.from().circle());
        var isArrivalMouth = rules.walls().isPinchedOn(run.to().circle());

        // A side with width at both ends is cut across like any other stretch: the wall sits
        // on the land side of the line and the coast rounds up over it.
        if (!isDepartureMouth && !isArrivalMouth) {

            return new PlacedRun(StraightRuns.findClearEdge(
                run,
                true,
                false,
                rules.reachAnchor()),
                false,
                false);
        }
        return new PlacedRun(
            StraightRuns.findEdgeThroughMouth(
                run,
                isDepartureMouth,
                isArrivalMouth,
                rules.reachAnchor()),
            isDepartureMouth,
            isArrivalMouth);
    }

    // Whether the walk joined two adjacent stretches by a wall's side rather than at a crossing
    // of their two cells. Told by the join's length: two cells cross at a point, so the far end
    // of one stretch and the near end of the next are one place, while a wall's side runs from
    // one cell's mouth to the other's and is at least a channel long. Nothing shorter than the
    // channel can be a wall, since a wall is what holds its two sides that far apart.
    private boolean isJoinedByWall(CoastMark from, CoastMark to) {

        var channel = rules.walls().channel();

        return channel > 0
            && Points.computeDistance(
                DiscUnionBoundary.findPointOnMark(union, from, from.toAngle()),
                DiscUnionBoundary.findPointOnMark(union, to, to.fromAngle()))
                > channel;
    }

    // The run of a cell's own border the coast keeps to, from where it arrives to where it
    // leaves. Sampled rather than cut straight across, so that a cell whose two neighbours
    // pull it far apart is still traced around rather than through.
    //
    // Bounded by the cell's OWN frontage rather than by any fixed sweep. The last cell of a
    // chain faces void nearly the whole way round, so its coast has to wrap most of a turn to
    // come back down the other side - and a rule that stopped at a half turn collapsed
    // exactly those cells to a point, which is what put a spike on every chain end and drove
    // the two runs either side of it straight through the cell.
    //
    // A sweep of nothing is the ordinary case rather than a fault: two neighbours that can both
    // see this cell's middle land on it, and the single point they share is the line through the
    // middle of the frontage this whole class is for.
    //
    // A NEGATIVE sweep is the other case - the cell's two neighbours pulled each end past the
    // other - and one arriving here is one the un-crossing above refused, because putting the
    // two back in order would have driven a run through a cell. The point they are least far
    // from is then the only answer left.
    private List<double[]> sampleFillet(
            CoastMark mark,
            double arriveAngle,
            double departAngle) {

        var sweep = departAngle - arriveAngle;
        if (sweep <= 0) {

            return List.of(DiscUnionBoundary.findPointOnMark(
                union,
                mark,
                (arriveAngle + departAngle) / 2));
        }

        var steps = Math.max(
            1,
            (int) Math.ceil(rules.arcSegments() * sweep / Angles.HALF_TURN));

        var points = new ArrayList<double[]>(steps + 1);

        for (var step = 0; step <= steps; step++) {

            points.add(DiscUnionBoundary.findPointOnMark(
                union,
                mark,
                arriveAngle + sweep * step / steps));
        }

        return points;
    }

    /**
     * The stretches placement is working over, and the walk they were chosen from.
     *
     * <p>One value because the three are meaningless apart. A mark names its circle by index
     * into the union, and a visited position indexes into the marks, so a caller free to pair
     * one walk's positions with another's marks can do it and still compile - and would be
     * asking about a cell neither of them meant.
     *
     * <p>It also owns the wrap. Placement is a ring, so nearly every step of it needs the
     * position before or after this one modulo the count, and that arithmetic written out at
     * each use is where an off-by-one hides in plain sight.
     *
     * @param coast   every stretch the walk found, in walk order
     * @param visited which of them selection kept, as positions along that walk
     * @param union   the discs the stretches were read off
     */
    private record VisitedWalk(
        List<CoastMark> coast,
        List<Integer> visited,
        DiscUnion union) {

        int countStretches() {
            return visited.size();
        }

        CoastMark findMarkAt(int step) {
            return coast.get(visited.get(step));
        }

        int findStepAfter(int step) {
            return (step + 1) % visited.size();
        }

        int findStepBefore(int step) {
            return (step + visited.size() - 1) % visited.size();
        }

        // Whether the walk put the next visited stretch immediately after this one, which is
        // what makes the boundary's own join between them available to fall back on.
        boolean isAdjacentAfter(int step) {
            return visited.get(findStepAfter(step)) == (visited.get(step) + 1) % coast.size();
        }
    }

    /**
     * Where the line lands on each visited stretch and leaves it again, and which of those a
     * wall decided.
     *
     * <p>Three arrays indexed alike, held as one value rather than passed side by side. What
     * they describe is a single thing - the placement so far - and the un-crossing has to read
     * all three of them together to decide anything, so split apart they are three chances to
     * hand one method the arrays from two different placements.
     *
     * @param arriveAngles  where the line lands on each stretch
     * @param departAngles  where it leaves each stretch
     * @param isHeldByWall  which landings a wall's side or anchor decided, and so which the
     *                      un-crossing may not move
     */
    private record Landings(
        double[] arriveAngles,
        double[] departAngles,
        boolean[] isHeldByWall) {

        static Landings forWalk(VisitedWalk walk) {

            return new Landings(
                new double[walk.countStretches()],
                new double[walk.countStretches()],
                new boolean[walk.countStretches()]);
        }

        // One placed run written down: where it left the stretch behind it, where it landed on
        // the next, and whether a wall rather than the clamp chose either.
        void acceptPlacedRun(int step, int next, PlacedRun placed) {

            departAngles[step] = placed.edge().departAngle();
            arriveAngles[next] = placed.edge().arriveAngle();

            isHeldByWall[step] |= placed.isDepartureHeld();
            isHeldByWall[next] |= placed.isArrivalHeld();
        }

        // Whether the line leaves a stretch before it lands on it, which is a cell whose two
        // landings were pushed past each other rather than one that was met exactly.
        boolean isCrossedAt(int step) {
            return departAngles[step] < arriveAngles[step];
        }

        boolean isHeldByWallAt(int step) {
            return isHeldByWall[step];
        }

        void swapLandingsAt(int step) {

            var arrived = arriveAngles[step];

            arriveAngles[step] = departAngles[step];
            departAngles[step] = arrived;
        }
    }

    /**
     * One run placed, and which of its two landings a wall decided.
     *
     * <p>The flags travel with the edge because they are about it: a landing at a wall's own
     * edge or anchor is where the wall put it, and the un-crossing that runs afterwards may
     * move only landings the clamp chose.
     *
     * @param edge            where the run leaves and lands
     * @param isDepartureHeld whether the wall decided where it leaves
     * @param isArrivalHeld   whether the wall decided where it lands
     */
    private record PlacedRun(
        StraightRuns.EdgeAngles edge,
        boolean isDepartureHeld,
        boolean isArrivalHeld) {
    }
}
