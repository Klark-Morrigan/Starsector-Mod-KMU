package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a coast's line actually goes: which stretches of border it visits, and where on each
 * cell it runs.
 *
 * <p>The half of tracing a coast that is not the walk. {@link DiscUnionBoundary} answers what
 * the boundary IS - a run of marks, each naming a cell and an arc - and this answers what a
 * line drawn along those marks looks like: which of them are worth visiting at all, where on a
 * visited arc the line touches down, and what to do where a wall is in the way.
 *
 * <p>Apart from {@link Coastlines} because the two answer to different things. Which stretches
 * survive and where a run lands are decided by the frontage floor, the reach anchor and the
 * walls; what a trace then IS - continents, lakes, puddles, shores - is decided by the water
 * floor and the shape of the sector. Held together, a change to either read as a change to
 * both, and the file was long enough that neither could be read without the other in the way.
 *
 * <p>Package-private throughout. Nothing outside this package has any business placing a line
 * on a cell: what a caller wants is a traced coast, and the entries that hand one back are
 * {@link Coastlines}'.
 */
final class CoastPlacement {

    // A skip put back changes the jump either side of it, which can expose a different cell.
    // Bounded rather than run to a fixed point, because each pass keeps strictly more cells
    // and the worst case - every cell visited - is the unsmoothed coast rather than a wrong one.
    private static final int REPAIR_PASSES = 4;

    // No stretch stands between two visited ones, or none of those that do is in anything's way.
    private static final int NOTHING_BLOCKING = -1;

    private CoastPlacement() {
    }

    /**
     * What selection and placement are run under, in the units those two stages work in.
     *
     * <p>One knob each, and they do not interact: the first is the whole of what selection
     * decides on, the second is how finely placement samples what selection left it. Rounding
     * takes nothing from here, since it happens to a finished border rather than while one is
     * being built.
     *
     * <p>Apart from {@link CoastRules}, which is the same settings as a caller sets them -
     * multiples of a cell radius, and a rounding no stage below reads. This is what those come
     * to once converted, so no stage has to convert them itself.
     *
     * @param minFrontageShare how much of its own border a cell has to face the void with to
     *                         be worth visiting, as a share of the whole turn. Selection's
     *                         only knob
     * @param arcSegments      how finely a half-turn of arc is sampled, which is how smooth
     *                         the fillets placement lays come out
     * @param walls            the walls the coast was walked against. Their channel is what
     *                         tells placement a join between two adjacent stretches is a
     *                         wall's side rather than the crossing of two cells - a crossing
     *                         has no length, a wall's side has at least the channel's - and
     *                         which cells they are pinched on is where a side ends in a point
     * @param wallPlacement    what placement does with a join that is a wall's side
     * @param reachAnchor      which turn the reachable window is read on, carried through from
     *                         {@link CoastRules} because selection's repair pass and placement
     *                         both ask the clamp and have to ask it the same way
     */
    record BorderRules(
        double minFrontageShare,
        int arcSegments,
        DiscUnionBoundary.Walls walls,
        WallPlacement wallPlacement,
        StraightRuns.ReachAnchor reachAnchor) {
    }

    /**
     * What placement does with a join the walk made along a wall's side.
     *
     * <p>Two answers because the two shores stand on opposite sides of every wall. A shore
     * inside a wall's water is the wall's own edge, so it follows the side. The outer coast has
     * the wall on its land side and rounds up over it, so a side there is a stretch to cut
     * across like any other - except where the mouth at its end has closed to a single point,
     * where there is no width to cut and the coast is to meet the anchor.
     */
    enum WallPlacement {
        HUG_SIDES,
        TOUCH_POINT_MOUTHS
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
        List<DiscUnionBoundary.CoastMark> coast,
        List<Integer> visited,
        DiscUnion union) {

        int countStretches() {
            return visited.size();
        }

        DiscUnionBoundary.CoastMark findMarkAt(int step) {
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

    /**
     * Builds the outer border of every run of connected cells: selection, then placement.
     *
     * <p>Judged on the points that came out rather than on the cells that went in. A cell
     * contributes a whole run of border rather than a single point, so two cells that touch
     * enclose the pair perfectly well. Only a run that came out too small to be a shape at all
     * is dropped, which after the winding is settled means one that could not be built. Runs
     * of one cell never arrive here at all - a lone island is not a coast.
     *
     * @param silhouettes the stretches of coast the cells make, in walk order
     * @param union       the discs to draw against
     * @param bridged     the cells a laid wall attaches to, which selection never drops
     * @param rules       what selection drops, and how finely placement samples
     * @return one closed run of points per run of connected cells
     */
    static PlacedCoasts buildSilhouetteBorders(
            List<List<DiscUnionBoundary.CoastMark>> silhouettes,
            DiscUnion union,
            Set<Integer> bridged,
            BorderRules rules) {

        var placed = new ArrayList<List<Coastlines.CoastVertex>>();
        var dropped = new ArrayList<DiscUnionBoundary.CoastMark>();

        for (var silhouette : silhouettes) {

            var one = buildOneBorder(silhouette, union, bridged, rules);

            dropped.addAll(one.dropped());

            if (!one.outline().isEmpty()) {
                placed.add(one.outline());
            }
        }
        return new PlacedCoasts(placed, List.copyOf(dropped));
    }

    /**
     * Selection and placement over ONE run of coast marks, whichever kind of coast it came off.
     *
     * <p>The two stages meet here and nowhere else, which is what makes the order between them
     * a fact of the code rather than a convention: selection settles which stretches the border
     * visits, and only then is placement handed them.
     *
     * <p>Named on its own because two callers want it one run at a time: the silhouettes go
     * through in a batch, and a lake pairs its finished shore with its own water's edge, so
     * it has to keep hold of which run produced which. Called through the batch with a
     * one-element list, a lake would have to read the answer back out of a list that may have
     * dropped it.
     *
     * @param coast   one run of coast marks, in walk order
     * @param union   the discs to draw against
     * @param bridged the cells a laid wall attaches to, which selection never drops
     * @param rules   what selection drops, and how finely placement samples
     * @return the placed outline and the stretches selection left out of it. The outline is
     *         empty where what came out was too small to enclose anything, which is what a run
     *         that could not be built comes to
     */
    static PlacedCoast buildOneBorder(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            Set<Integer> bridged,
            BorderRules rules) {

        var visited = selectVisitedStretches(coast, union, bridged, rules);
        var outline = placeClearedOutline(coast, visited, union, rules);
        var dropped = new ArrayList<DiscUnionBoundary.CoastMark>();

        for (var index = 0; index < coast.size(); index++) {

            if (!visited.contains(index)) {
                dropped.add(coast.get(index));
            }
        }

        return new PlacedCoast(
            outline.size() >= Limits.MIN_VERTICES_TO_ENCLOSE_AREA ? outline : List.of(),
            List.copyOf(dropped));
    }

    /**
     * What the two stages made of one run of coast, and what selection left out on the way.
     *
     * @param outline the placed line, or empty where it came out too small to be a shape
     * @param dropped every stretch of this run the line was not drawn through
     */
    record PlacedCoast(
        List<Coastlines.CoastVertex> outline,
        List<DiscUnionBoundary.CoastMark> dropped) {
    }

    /**
     * What they made of a batch of them, and what was left out over all of them.
     *
     * <p>The stretches dropped are kept rather than discarded because they are the only record
     * of a decision selection otherwise makes silently. A coast that came out wrong looks the
     * same on screen whether a rule dropped too much or the walk never offered the stretch at
     * all, and those are opposite faults with opposite fixes.
     *
     * @param coasts  one closed run of points per run of connected cells, degenerate ones
     *                left out - so this does NOT line up with what went in
     * @param dropped every stretch the coast was not drawn through, over all of them
     */
    record PlacedCoasts(
        List<List<Coastlines.CoastVertex>> coasts,
        List<DiscUnionBoundary.CoastMark> dropped) {
    }

    // A cell alone in the void has no coast, so the runs that name one are not silhouettes.
    //
    // A coast is where settled space ends along a run of cells that hold something BETWEEN
    // them. A cell touching nothing, joined to nothing, holds only itself: the line traced
    // round it is the cell's own border a second time, and there is no void it encloses that
    // the cell does not already draw. Kept, it is an edge drawn twice on screen, a stretch of
    // frontage in every coast measure that no reach can ever be laid along, and a ring that
    // answers "inside the coast" for points the cell already claims.
    //
    // Read off the run itself: cells that touch, and cells a laid bridge joins, are walked
    // into ONE run - so a run naming a single circle is exactly the degenerate case, with no
    // separate test for touching or for bridges to fall out of step with the walk.
    static List<List<DiscUnionBoundary.CoastMark>> keepJoinedRuns(
            List<List<DiscUnionBoundary.CoastMark>> silhouettes) {

        var joined = new ArrayList<List<DiscUnionBoundary.CoastMark>>(silhouettes.size());

        for (var silhouette : silhouettes) {

            if (!isLoneIsland(silhouette)) {
                joined.add(silhouette);
            }
        }
        return joined;
    }

    // The other half of the same reading: which cells those dropped runs were.
    //
    // Kept because having no coast is not the same as not being there. A cell alone in the void
    // is a shape of the sector like any other - it can be reached, and a span laid to it joins
    // it to whatever it reaches - and the only thing it lacks is a line of its own to draw.
    //
    // An empty run names no cell and is not an island; it is a walk that found nothing, and
    // there is no cell to hand on.
    static List<Integer> collectLoneIslands(
            List<List<DiscUnionBoundary.CoastMark>> silhouettes) {

        var islands = new ArrayList<Integer>();

        for (var silhouette : silhouettes) {

            if (isLoneIsland(silhouette) && !silhouette.isEmpty()) {
                islands.add(silhouette.get(0).circle());
            }
        }
        return List.copyOf(islands);
    }

    // Whether a run of coast is one cell's own border and nothing else.
    private static boolean isLoneIsland(List<DiscUnionBoundary.CoastMark> silhouette) {

        if (silhouette.isEmpty()) {
            return true;
        }

        for (var mark : silhouette) {

            if (mark.circle() != silhouette.get(0).circle()) {
                return false;
            }
        }
        return true;
    }

    // The cells a laid wall attaches to. Asked of the laid chords rather than of every bridge
    // offered, because a bridge that was never drawn has no wall for the coast to cut across
    // and protecting its cells would only cost selection detail for nothing.
    static Set<Integer> findBridgedCircles(
            DiscUnion union,
            DiscUnionBoundary.Walls walls) {

        var bridged = new LinkedHashSet<Integer>();

        for (var chord : DiscUnionBoundary.findAttachableChords(union, walls)) {

            bridged.add(chord.fromCircle());
            bridged.add(chord.toCircle());
        }
        return bridged;
    }

    // The whole of the selection stage: which stretches the border visits - the ones that show
    // enough of themselves to the void, plus the ones dropping would have put a cell across the
    // jump.
    //
    // The two halves are in that order for a reason. The first is a matter of taste and reads
    // nothing but the stretch itself; the second is a matter of correctness and can only be
    // asked once there is a set of drops to test. So a rule of taste may propose any drop it
    // likes, and the repair passes are what stop a proposal stranding the line inside a cell.
    private static List<Integer> selectVisitedStretches(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            Set<Integer> bridged,
            BorderRules rules) {

        var isVisited = flagExposedStretches(coast, bridged, rules);

        for (var pass = 0; pass < REPAIR_PASSES; pass++) {

            if (!restoreBlockingStretches(coast, union, isVisited, rules)) {
                break;
            }
        }

        return collectVisitedPositions(isVisited);
    }

    // The visited stretches as positions along the walk, which is what placement indexes by.
    // The flags are how selection decides; the positions are how its answer is read.
    private static List<Integer> collectVisitedPositions(boolean[] isVisited) {

        var visited = new ArrayList<Integer>();

        for (var index = 0; index < isVisited.length; index++) {

            if (isVisited[index]) {
                visited.add(index);
            }
        }
        return visited;
    }

    // Selection's first half: one pass along the coast, visiting every stretch that faces the
    // void with enough of its own border to be worth drawing.
    //
    // Order-free, and that is the point of it: each stretch is judged against nothing but
    // itself, so the walk could start anywhere and drop the same set. Nothing accumulates
    // across the loop, which is why there is no state here to get wrong.
    private static boolean[] flagExposedStretches(
            List<DiscUnionBoundary.CoastMark> coast,
            Set<Integer> bridged,
            BorderRules rules) {

        var isVisited = new boolean[coast.size()];

        for (var index = 0; index < coast.size(); index++) {
            isVisited[index] = !isBarelyFacingTheVoid(coast.get(index), bridged, rules);
        }
        return isVisited;
    }

    // Whether a stretch offers too little of its cell's border to be worth passing through.
    //
    // A cell that peeks out by a few degrees contributes a notch the width of a rounding
    // error, and the coast is drawn all the way in and back out for it. Dropping it is what
    // the caller asked for by setting the rule above zero.
    //
    // A cell a bridge attaches to is exempt: a bridge's wall is boundary the coast has to
    // stay OUTSIDE of, so cutting the corner across one puts the coast on the wrong side of
    // a shape already drawn. That is a matter of correctness rather than of taste, and no
    // selection knob may overrule it.
    //
    // Nothing here can strand the coast inside a cell. A stretch dropped from this pass is
    // put straight back by the repair pass if the jump over it turns out to cross anything -
    // so the rule can only ever remove detail that was not load-bearing.
    private static boolean isBarelyFacingTheVoid(
            DiscUnionBoundary.CoastMark mark,
            Set<Integer> bridged,
            BorderRules rules) {

        return mark.measureShareOfCircle() < rules.minFrontageShare()
            && !bridged.contains(mark.circle());
    }

    // Selection's second half: puts back any stretch a jump turned out to cross. A jump is
    // tested against every cell, not only the two it runs between, because the cell in the way
    // is by definition one neither end knows about - and if that cell is one of the stretches
    // skipped over, visiting it is what stops the jump being made at all.
    private static boolean restoreBlockingStretches(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            boolean[] isVisited,
            BorderRules rules) {

        var visited = collectVisitedPositions(isVisited);

        if (visited.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return false;
        }

        var restored = false;

        for (var step = 0; step < visited.size(); step++) {

            var from = visited.get(step);
            var to = visited.get((step + 1) % visited.size());
            var blocker = findBlockedStretch(coast, union, isVisited, from, to, rules);

            if (blocker >= 0) {

                isVisited[blocker] = true;
                restored = true;
            }
        }
        return restored;
    }

    // Which of the stretches skipped between two visited ones to put back, or NOTHING_BLOCKING
    // when the jump is clear.
    //
    // The jump is judged first, by the one clearance test placement itself uses, and only a
    // jump that failed goes looking for something to blame. That ordering is what keeps one
    // definition of "in the way" in play: an inline scan with its own threshold answered the
    // same question a hair differently, and a method holding two definitions of one question
    // is how the next reader picks the wrong one.
    //
    // The blame falls on the skipped stretch actually across the jump where there is one, and
    // otherwise simply on the first skipped. A jump can cut into one of the two cells it runs
    // BETWEEN, which no skipped stretch can be blamed for - it happens where both ends block
    // and the common tangent has to be clamped back onto a frontage that does not reach it,
    // leaving a run tangent to nothing. Putting any skipped stretch back is still the remedy:
    // the one long jump becomes two short ones, and two neighbouring stretches can always fall
    // back on the boundary's own join between them, which cuts nothing by construction.
    private static int findBlockedStretch(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            boolean[] isVisited,
            int from,
            int to,
            BorderRules rules) {

        var jump = new StraightRuns.StraightRun(union, coast.get(from), coast.get(to));
        var edge = StraightRuns.resolveEdge(jump, rules.reachAnchor());

        if (StraightRuns.isRunClearOfEveryCell(jump, edge)) {
            return NOTHING_BLOCKING;
        }

        var firstSkipped = NOTHING_BLOCKING;

        for (var step = 1;
                step < coast.size() && !isVisited[(from + step) % coast.size()];
                step++) {

            var index = (from + step) % coast.size();

            if (firstSkipped < 0) {
                firstSkipped = index;
            }
            if (jump.isRunBetween(coast.get(index).circle())) {
                continue;
            }
            if (jump.measureIncursion(edge, coast.get(index).circle()) > DiscUnion.TOUCHING_TOLERANCE) {
                return index;
            }
        }
        return firstSkipped;
    }

    // The whole of the placement stage: the visited stretches turned into a closed run of
    // points - a fillet along each cell's own border from where the coast arrives to where it
    // leaves, and a straight reach from there to the next cell.
    //
    // Every landing here is decided by what will clear the cells, and by nothing else. Which
    // stretches arrived is already settled, so the only freedom left is where on each of them
    // the line touches - and holding the reaches outside every cell is what that freedom is
    // spent on.
    private static List<Coastlines.CoastVertex> placeClearedOutline(
            List<DiscUnionBoundary.CoastMark> coast,
            List<Integer> visited,
            DiscUnion union,
            BorderRules rules) {

        // A run of one has no reach to any other cell, so there is nothing to clamp against
        // and its whole frontage is the coast. That is a cell alone in the void, whose coast
        // is its own border - drawn over the top of it and so invisible, which is right.
        if (visited.size() == 1) {

            var only = coast.get(visited.get(0));

            return buildVerticesOnMark(
                only,
                sampleFillet(union, only, only.fromAngle(),
                only.toAngle(),
                rules));
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
                && isJoinedByWall(union, from, to, rules)
                    ? placeRunAlongWall(run, rules)
                    : new PlacedRun(
                        StraightRuns.findClearEdge(run, isAdjacent, false, rules.reachAnchor()),
                        false,
                        false));
        }

        uncrossLandings(walk, landings);

        var outline = new ArrayList<Coastlines.CoastVertex>();

        for (var step = 0; step < walk.countStretches(); step++) {

            var mark = walk.findMarkAt(step);

            outline.addAll(buildVerticesOnMark(
                mark,
                sampleFillet(
                    union,
                    mark,
                    landings.arriveAngles()[step],
                    landings.departAngles()[step],
                    rules)));
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

    // A run the walk joined by a wall's side, placed as the shore it is on treats walls. Which
    // ends are held is reported with the edge because the un-crossing may not move them: a
    // landing that is a wall's own edge, or its anchor, is where the wall put it.
    private static PlacedRun placeRunAlongWall(StraightRuns.StraightRun run, BorderRules rules) {

        if (rules.wallPlacement() == WallPlacement.HUG_SIDES) {
            return new PlacedRun(
                StraightRuns.findClearEdge(run, true, true, rules.reachAnchor()), true, true);
        }
        var isDepartureMouth = rules.walls().isPinchedOn(run.from().circle());
        var isArrivalMouth = rules.walls().isPinchedOn(run.to().circle());

        // A side with width at both ends is cut across like any other stretch: the wall sits
        // on the land side of the line and the coast rounds up over it.
        if (!isDepartureMouth && !isArrivalMouth) {
            return new PlacedRun(
                StraightRuns.findClearEdge(run, true, false, rules.reachAnchor()), false, false);
        }
        return new PlacedRun(
            StraightRuns.findEdgeThroughMouth(
                run, isDepartureMouth, isArrivalMouth, rules.reachAnchor()),
            isDepartureMouth,
            isArrivalMouth);
    }

    // Whether the walk joined two adjacent stretches by a wall's side rather than at a crossing
    // of their two cells. Told by the join's length: two cells cross at a point, so the far end
    // of one stretch and the near end of the next are one place, while a wall's side runs from
    // one cell's mouth to the other's and is at least a channel long. Nothing shorter than the
    // channel can be a wall, since a wall is what holds its two sides that far apart.
    private static boolean isJoinedByWall(
            DiscUnion union,
            DiscUnionBoundary.CoastMark from,
            DiscUnionBoundary.CoastMark to,
            BorderRules rules) {

        var channel = rules.walls().channel();

        return channel > 0
            && Points.computeDistance(
                DiscUnionBoundary.findPointOnMark(union, from, from.toAngle()),
                DiscUnionBoundary.findPointOnMark(union, to, to.fromAngle()))
                > channel;
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
                    walk.union(), walk.findMarkAt(previous), walk.findMarkAt(step)),
                new StraightRuns.EdgeAngles(
                    landings.departAngles()[previous], landings.departAngles()[step]))
            && StraightRuns.isRunClearOfEveryCell(
                new StraightRuns.StraightRun(
                    walk.union(), walk.findMarkAt(step), walk.findMarkAt(next)),
                new StraightRuns.EdgeAngles(
                    landings.arriveAngles()[step], landings.arriveAngles()[next]));
    }

    // One stretch of coast as the points the line passes through: the sampled fillet running
    // along the cell's own border, each carrying the cell it belongs to so a later pass can
    // tell which stretch a point came off without matching coordinates back to a circle.
    private static List<Coastlines.CoastVertex> buildVerticesOnMark(
            DiscUnionBoundary.CoastMark mark,
            List<double[]> points) {

        var vertices = new ArrayList<Coastlines.CoastVertex>(points.size());

        for (var point : points) {
            vertices.add(new Coastlines.CoastVertex(point, mark.circle()));
        }
        return vertices;
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
    private static List<double[]> sampleFillet(
            DiscUnion union,
            DiscUnionBoundary.CoastMark mark,
            double arriveAngle,
            double departAngle,
            BorderRules rules) {

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
}
