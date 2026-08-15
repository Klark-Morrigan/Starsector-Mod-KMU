package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The outer edge of settled space, smoothed: one line through the middle of every cell's
 * frontage on the open void, instead of the scallop of arcs the cells actually make.
 *
 * <p>The silhouette a run of connected cells traces is a chain of circular arcs, and at the
 * scale a sector is read at that reads as a row of bites taken out of the edge rather than as
 * a coast. What the eye wants is the shape those cells collectively occupy, which is the line
 * joining the middle of each cell's frontage - so that is what is drawn.
 *
 * <p><b>Nothing is searched for.</b> The silhouettes come out of {@link DiscUnionBoundary} as
 * stretches of coast in walk order, so "the next cell along the coast" is the next element of
 * the run. There is no nearest-neighbour matching and no ordering to get wrong, and a cell
 * facing the void on two separate stretches - a strait, or the inside of a C - contributes one
 * stretch per frontage without needing a rule for it.
 *
 * <p><b>A coast may never pass through a cell.</b> Joining the middles of two frontages with a
 * straight line does exactly that wherever the coast turns inward, which is at every waist
 * between two cells - so where the line would cut a cell, the point gives way instead of the
 * line. It slides along that cell's own border to the nearest place to the middle that the
 * line can actually reach, which needs no search: seen from a point, the part of a circle a
 * straight line can touch without cutting through it is the arc facing that point, half a turn
 * wide less {@code acos(reach / distance)}. Both ends give way, the far cell blocking the
 * approach and the near cell blocking its own departure, and where both are blocked at once
 * the two settle on the common tangent.
 *
 * <p>That is why a cell contributes an arrival point and a departure point rather than one
 * point. The line coming in and the line going out can want it slid in opposite directions,
 * which is precisely what happens at the inward turns this exists to fix; joined along the
 * cell's own border, the two can disagree without the coast leaving the boundary. Where
 * nothing blocks, both land on the middle and it is one point again.
 *
 * <p><b>Why cells get skipped.</b> A run of cells packed tightly along a coast contributes a
 * stretch every few hundred units, and joining all of them reproduces the scallop at a
 * slightly smaller amplitude rather than smoothing it. Dropping the ones that fall within a
 * set distance of the last one kept is what turns the chain into a line.
 *
 * <p>Three things bound that. Skipping runs out after a set number in a row, so a dense coast
 * cannot be swallowed whole and reduced to a triangle. <b>A cell a bridge attaches to is never
 * skipped</b>, because a bridge's wall is boundary the coast has to stay outside of, and
 * cutting the corner across one puts the coast on the wrong side of a shape already drawn.
 * And a skip is provisional: a cell nothing else knows about can sit in the gap a jump opens
 * up, so every jump is tested against every cell, and whichever one blocks it is put back.
 */
final class Coastlines {

    // Each pass slides one end to the furthest the other allows, so the two close on the
    // common tangent from opposite directions. Four is past the point where the movement
    // stops being visible; the clearance check afterwards is what says whether it was enough.
    private static final int CLAMP_PASSES = 4;

    // A skip put back changes the jump either side of it, which can expose a different cell.
    // Bounded rather than run to a fixed point, because each pass keeps strictly more cells
    // and the worst case - every cell kept - is the unsmoothed coast rather than a wrong one.
    private static final int REPAIR_PASSES = 4;

    // How far inside a cell a straight run may reach and still count as touching it rather
    // than crossing it. A run begins and ends ON two borders, so without a hair of slack
    // every one of them would count itself as crossing the two cells it runs between.
    private static final double TOUCHING_TOLERANCE = 1;

    private Coastlines() {
    }

    /**
     * How aggressively a coast is smoothed.
     *
     * <p>One record because the two only mean anything together: the distance decides how
     * much is dropped and the cap decides how much may be dropped at once, and a distance
     * handed round without its cap is the setting that eats a whole coastline.
     *
     * @param skipDistance        how near the last kept point a cell must be to be dropped
     * @param maxConsecutiveSkips how many may be dropped in a row before one is kept whatever
     *                            its distance, so a dense stretch still contributes a point
     */
    record SmoothingRules(
        double skipDistance,
        int maxConsecutiveSkips) {
    }

    /**
     * One point of a smoothed coast, and the cell whose border it sits on.
     *
     * <p>The cell travels with the point because the two things drawn here are not the same
     * kind of run: a step between two points on ONE cell is a fillet along that cell's own
     * border, where a step between two cells is a straight reach across open void. Only the
     * second has to clear every cell, and a check that could not tell them apart would read
     * every fillet as a coast cutting into the cell it is drawn on.
     *
     * @param point  where the coast passes
     * @param circle whose cell's border it is on
     */
    record CoastVertex(
        double[] point,
        int circle) {
    }

    /**
     * Traces the smoothed outer edge of every run of connected cells.
     *
     * <p>A run has to reach three points to be worth anything: one cell alone in the void
     * makes a circle with a single stretch of coast, and two that touch make two, neither of
     * which is a shape. Those drop out rather than being drawn as a dot or a line, which is
     * also exactly the "only cells touching or bridged to another" rule - a cell connected to
     * nothing cannot reach three.
     *
     * @param union       the discs to trace, at whatever reach the coast is drawn at
     * @param walls       the walls laid across the void, whose cells are never skipped
     * @param rules       how aggressively to smooth
     * @param arcSegments how finely a half-turn of arc is sampled
     * @return one closed run of points per run of connected cells
     */
    static List<List<CoastVertex>> traceSmoothedCoasts(
            DiscUnion union,
            DiscUnionBoundary.Walls walls,
            SmoothingRules rules,
            int arcSegments) {

        var bridged = findBridgedCircles(union, walls);
        var smoothed = new ArrayList<List<CoastVertex>>();

        for (var coast : DiscUnionBoundary.traceSilhouetteCoasts(union, walls, arcSegments)) {

            var outline = buildClearedOutline(
                keepSmoothedMarks(coast, union, bridged, rules), union, arcSegments);

            // Judged on the points drawn rather than on the cells kept, because a cell
            // contributes a whole run of border rather than one point. One cell alone in the
            // void encloses an area perfectly well - its own - and two that touch enclose the
            // pair; only a run that came out too small to be a shape at all is dropped.
            if (outline.size() >= Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                smoothed.add(outline);
            }
        }
        return smoothed;
    }

    /**
     * The ring on its own, for anything that only wants to draw or fill it.
     *
     * @param coast a smoothed coast
     * @return its points, in order
     */
    static List<double[]> collectPoints(List<CoastVertex> coast) {

        var points = new ArrayList<double[]>(coast.size());

        for (var vertex : coast) {
            points.add(vertex.point());
        }
        return points;
    }

    /**
     * How far the worst straight run of coast reaches inside a cell.
     *
     * <p>The check that governs this. A smoothed edge can look plausible and still cut a cell
     * in half - that is the failure it was built to fix - and the only way to say it has not
     * is to ask every straight reach how near it comes to every site.
     *
     * <p><b>Every site, including the two the run departs from and lands on.</b> Leaving
     * those out looks reasonable - a run touches both by construction, so they read as false
     * positives - but they are exactly the cells a run cuts through when the sliding fails,
     * and excluding them made this report zero while chain ends were being crossed end to
     * end. A run that leaves both borders outwards touches them and no more, so a real
     * incursion there is as real as any other.
     *
     * <p>Fillets are left out, because a fillet is drawn ON a cell's border and its chord
     * dips inside that cell by the sagitta of its own sampling, which is not the same thing
     * as a coast crossing one.
     *
     * @param coasts what {@link #traceSmoothedCoasts} handed back
     * @param union  the discs it was traced against
     * @return the deepest any straight run of coast reaches inside a cell, which is zero when
     *         none of them enters one
     */
    /**
     * How many straight runs of coast pass inside a cell.
     *
     * <p>The companion to {@link #measureDeepestIncursion}, which says how bad the worst one
     * is and nothing about how many there are. One deep crossing is a bug in one place; forty
     * shallow ones are a bug in the construction, and the two want different fixes.
     *
     * <p>Counted past a hair of slack, because a run begins and ends ON two borders and so
     * touches those two cells to within rounding. Anything past that is the run inside a cell
     * rather than against it.
     *
     * @param coasts what {@link #traceSmoothedCoasts} handed back
     * @param union  the discs it was traced against
     * @return how many runs go inside a cell
     */
    static int countPenetratingRuns(List<List<CoastVertex>> coasts, DiscUnion union) {
        return findPenetrations(coasts, union).size();
    }

    /**
     * Every straight run that passes inside a cell, and which cells each one is inside.
     *
     * <p>What a count cannot be acted on without. Nineteen crossings somewhere is a number to
     * argue with; nineteen crossings drawn on the map, each with the cell it goes through
     * marked, is a thing to look at - and looking is how the last several of these were
     * actually found.
     *
     * @param coasts what {@link #traceSmoothedCoasts} handed back
     * @param union  the discs it was traced against
     * @return one entry per offending run, in the order they are drawn
     */
    static List<Penetration> findPenetrations(
            List<List<CoastVertex>> coasts,
            DiscUnion union) {

        var found = new ArrayList<Penetration>();

        for (var coast : coasts) {
            for (var index = 0; index < coast.size(); index++) {

                var from = coast.get(index);
                var to = coast.get((index + 1) % coast.size());

                if (from.circle() == to.circle()) {
                    continue;
                }

                var pierced = findPiercedCircles(union, from, to);

                if (!pierced.isEmpty()) {

                    found.add(new Penetration(
                        from.point(),
                        to.point(),
                        pierced,
                        measureDepthInto(union, from, to, pierced.get(0))));
                }
            }
        }
        return found;
    }

    /**
     * One straight run of coast that goes inside a cell, and the cells it goes inside.
     *
     * @param from    where the run starts
     * @param to      where it ends
     * @param circles the cells it passes inside, deepest first
     * @param depth   how far inside the worst of them it reaches. Carried because the two
     *                kinds look identical to a count and are not the same failure: a run
     *                grazing a border it is already leaving from is worth a unit or two, and
     *                a run cutting a cell in half is worth hundreds
     */
    record Penetration(
        double[] from,
        double[] to,
        List<Integer> circles,
        double depth) {
    }

    private static double measureDepthInto(
            DiscUnion union,
            CoastVertex from,
            CoastVertex to,
            int circle) {

        return union.reach() - Segments.computeDistanceToPoint(
            from.point(), to.point(), union.sites().get(circle));
    }

    // Which cells one straight run is inside, worst first. All of them rather than the worst
    // alone, because a run that clips three cells and a run that buries itself in one are
    // different failures and the drawing should not make them look alike.
    private static List<Integer> findPiercedCircles(
            DiscUnion union,
            CoastVertex from,
            CoastVertex to) {

        var pierced = new ArrayList<Integer>();
        var depthByCircle = new java.util.HashMap<Integer, Double>();

        for (var site = 0; site < union.sites().size(); site++) {

            var depth = union.reach() - Segments.computeDistanceToPoint(
                from.point(), to.point(), union.sites().get(site));

            if (depth > TOUCHING_TOLERANCE) {

                pierced.add(site);
                depthByCircle.put(site, depth);
            }
        }
        pierced.sort(java.util.Comparator.comparingDouble(depthByCircle::get).reversed());

        return pierced;
    }

    static double measureDeepestIncursion(List<List<CoastVertex>> coasts, DiscUnion union) {

        var deepest = 0.0;

        for (var coast : coasts) {
            for (var index = 0; index < coast.size(); index++) {

                var from = coast.get(index);
                var to = coast.get((index + 1) % coast.size());

                if (from.circle() == to.circle()) {
                    continue;
                }

                for (var site = 0; site < union.sites().size(); site++) {

                    deepest = Math.max(deepest, union.reach() - Segments.computeDistanceToPoint(
                        from.point(), to.point(), union.sites().get(site)));
                }
            }
        }
        return deepest;
    }

    /**
     * How many stretches of coast the cells actually make, before any are skipped.
     *
     * <p>The other half of the count that says whether the smoothing is doing anything: a
     * kept count on its own cannot tell a coast that was already smooth from one the skip
     * rules refused to touch.
     *
     * @param union       the discs to trace
     * @param walls       the walls laid across the void
     * @param arcSegments how finely a half-turn of arc is sampled
     * @return how many stretches there are in total, across every run
     */
    static int countCoastMarks(DiscUnion union, DiscUnionBoundary.Walls walls, int arcSegments) {

        var marks = 0;

        for (var coast : DiscUnionBoundary.traceSilhouetteCoasts(union, walls, arcSegments)) {
            marks += coast.size();
        }
        return marks;
    }

    // The cells a laid wall attaches to. Asked of the laid chords rather than of every bridge
    // offered, because a bridge that was never drawn has no wall for the coast to cut across
    // and protecting its cells would only cost smoothing for nothing.
    private static Set<Integer> findBridgedCircles(
            DiscUnion union,
            DiscUnionBoundary.Walls walls) {

        var bridged = new LinkedHashSet<Integer>();

        for (var chord : DiscUnionBoundary.findAttachableChords(union, walls)) {

            bridged.add(chord.fromCircle());
            bridged.add(chord.toCircle());
        }
        return bridged;
    }

    // Which stretches the coast is drawn through: the ones too far from their neighbour to be
    // dropped, plus the ones dropping would have put a cell across the jump.
    private static List<DiscUnionBoundary.CoastMark> keepSmoothedMarks(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            Set<Integer> bridged,
            SmoothingRules rules) {

        var isKept = markSpacedOutStretches(coast, union, bridged, rules);

        for (var pass = 0; pass < REPAIR_PASSES; pass++) {

            if (!restoreBlockingStretches(coast, union, isKept)) {
                break;
            }
        }

        var kept = new ArrayList<DiscUnionBoundary.CoastMark>();

        for (var index = 0; index < coast.size(); index++) {

            if (isKept[index]) {
                kept.add(coast.get(index));
            }
        }
        return kept;
    }

    // One pass along the coast, dropping stretches that sit too near the last one kept. The
    // first is always kept, because there is nothing yet for it to be near.
    private static boolean[] markSpacedOutStretches(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            Set<Integer> bridged,
            SmoothingRules rules) {

        var isKept = new boolean[coast.size()];
        var lastKept = (double[]) null;
        var skippedInARow = 0;

        for (var index = 0; index < coast.size(); index++) {

            var mark = coast.get(index);
            var middle = findMidpoint(union, mark);

            if (lastKept != null
                    && !bridged.contains(mark.circle())
                    && skippedInARow < rules.maxConsecutiveSkips()
                    && Points.computeDistance(lastKept, middle) < rules.skipDistance()) {

                skippedInARow++;
                continue;
            }
            isKept[index] = true;
            lastKept = middle;
            skippedInARow = 0;
        }
        return isKept;
    }

    // Puts back any stretch a jump turned out to cross. A jump is tested against every cell,
    // not only the two it runs between, because the cell in the way is by definition one
    // neither end knows about - and if that cell is one of the stretches skipped over, keeping
    // it is what stops the jump being made at all.
    private static boolean restoreBlockingStretches(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            boolean[] isKept) {

        var kept = new ArrayList<Integer>();

        for (var index = 0; index < coast.size(); index++) {

            if (isKept[index]) {
                kept.add(index);
            }
        }

        if (kept.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return false;
        }

        var restored = false;

        for (var step = 0; step < kept.size(); step++) {

            var from = kept.get(step);
            var to = kept.get((step + 1) % kept.size());
            var blocker = findBlockedStretch(coast, union, isKept, from, to);

            if (blocker >= 0) {

                isKept[blocker] = true;
                restored = true;
            }
        }
        return restored;
    }

    // Which of the stretches skipped between two kept ones has a cell across the jump, or -1
    // when nothing does. The first one found rather than the worst: putting any of them back
    // shortens the jump, and the next pass asks again about what is left.
    private static int findBlockedStretch(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            boolean[] isKept,
            int from,
            int to) {

        var edge = resolveEdge(union, coast.get(from), coast.get(to));

        var departure = findPointAt(union, coast.get(from), edge.departAngle());
        var arrival = findPointAt(union, coast.get(to), edge.arriveAngle());

        for (var step = 1; step < coast.size(); step++) {

            var index = (from + step) % coast.size();

            if (isKept[index]) {
                return -1;
            }

            var mark = coast.get(index);

            if (mark.circle() == coast.get(from).circle()
                    || mark.circle() == coast.get(to).circle()) {
                continue;
            }
            if (Segments.computeDistanceToPoint(
                    departure, arrival, union.sites().get(mark.circle())) < union.reach()) {

                return index;
            }
        }
        return -1;
    }

    // The kept stretches turned into a closed run of points: a fillet along each cell's own
    // border from where the coast arrives to where it leaves, and a straight reach from there
    // to the next cell.
    private static List<CoastVertex> buildClearedOutline(
            List<DiscUnionBoundary.CoastMark> kept,
            DiscUnion union,
            int arcSegments) {

        // A run of one has no reach to any other cell, so there is nothing to clamp against
        // and its whole frontage is the coast. That is a cell alone in the void, whose coast
        // is its own border - drawn over the top of it and so invisible, which is right.
        if (kept.size() == 1) {

            var only = kept.get(0);
            return buildVertices(
                only,
                sampleFillet(union, only, only.fromAngle(), only.toAngle(), arcSegments));
        }

        var arriveAngles = new double[kept.size()];
        var departAngles = new double[kept.size()];

        for (var index = 0; index < kept.size(); index++) {

            var next = (index + 1) % kept.size();
            var edge = resolveEdge(union, kept.get(index), kept.get(next));

            departAngles[index] = edge.departAngle();
            arriveAngles[next] = edge.arriveAngle();
        }

        var outline = new ArrayList<CoastVertex>();

        for (var index = 0; index < kept.size(); index++) {

            var mark = kept.get(index);

            outline.addAll(buildVertices(
                mark,
                sampleFillet(
                    union, mark, arriveAngles[index], departAngles[index], arcSegments)));
        }
        return outline;
    }

    private static List<CoastVertex> buildVertices(
            DiscUnionBoundary.CoastMark mark,
            List<double[]> points) {

        var vertices = new ArrayList<CoastVertex>(points.size());

        for (var point : points) {
            vertices.add(new CoastVertex(point, mark.circle()));
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
    // Both ends are already clamped into that frontage, so the arrival cannot sit past the
    // departure unless the cell's two neighbours pulled each beyond the other. There is no
    // run of border between them then, and the point they are least far from is the only
    // answer left.
    private static List<double[]> sampleFillet(
            DiscUnion union,
            DiscUnionBoundary.CoastMark mark,
            double arriveAngle,
            double departAngle,
            int arcSegments) {

        var sweep = departAngle - arriveAngle;

        if (sweep <= 0) {
            return List.of(findPointAt(union, mark, (arriveAngle + departAngle) / 2));
        }

        var steps = Math.max(1, (int) Math.ceil(arcSegments * sweep / Angles.HALF_TURN));
        var points = new ArrayList<double[]>(steps + 1);

        for (var step = 0; step <= steps; step++) {
            points.add(findPointAt(union, mark, arriveAngle + sweep * step / steps));
        }
        return points;
    }

    // Where one straight reach of coast leaves one cell and lands on the next. Each end is
    // slid to the nearest place to its own middle that the other end can see, and each pass
    // moves one of them to the furthest the other now allows - so two ends that both block
    // settle towards the common tangent instead of one of them winning outright.
    //
    // The passes leave one end a step stale, though: the arrival was worked out against the
    // departure BEFORE the departure last moved. Where that still clears both cells it is the
    // answer, and where it does not the two are put straight onto the tangent, which is the
    // configuration they were converging on and is exact rather than approached.
    private static EdgeAngles resolveEdge(
            DiscUnion union,
            DiscUnionBoundary.CoastMark from,
            DiscUnionBoundary.CoastMark to) {

        var departAngle = from.midAngle();
        var arriveAngle = to.midAngle();

        for (var pass = 0; pass < CLAMP_PASSES; pass++) {

            arriveAngle = findReachableAngle(union, to, findPointAt(union, from, departAngle));
            departAngle = findReachableAngle(union, from, findPointAt(union, to, arriveAngle));
        }

        if (isRunClear(union, from, departAngle, to, arriveAngle)) {
            return new EdgeAngles(departAngle, arriveAngle);
        }

        // Equal reaches, so the outer tangent runs parallel to the line joining the two sites
        // and meets both circles square to it - one angle, the same on each.
        var fromCentre = union.sites().get(from.circle());
        var toCentre = union.sites().get(to.circle());

        var tangent = Math.atan2(
            -(toCentre[0] - fromCentre[0]),
            toCentre[1] - fromCentre[1]);

        return new EdgeAngles(
            clampIntoFrontage(from, tangent),
            clampIntoFrontage(to, tangent));
    }

    // Whether a straight reach leaves both cells without cutting into either. Asked of the
    // direction rather than of the distance: the run begins and ends ON the two borders, so
    // what decides it is whether it sets off outwards from each - which is the sign of the
    // turn between the run and the outward direction at the point it starts from.
    private static boolean isRunClear(
            DiscUnion union,
            DiscUnionBoundary.CoastMark from,
            double departAngle,
            DiscUnionBoundary.CoastMark to,
            double arriveAngle) {

        var departure = findPointAt(union, from, departAngle);
        var arrival = findPointAt(union, to, arriveAngle);

        return isLeavingOutwards(union, from.circle(), departure, arrival)
            && isLeavingOutwards(union, to.circle(), arrival, departure);
    }

    private static boolean isLeavingOutwards(
            DiscUnion union,
            int circle,
            double[] leaving,
            double[] towards) {

        var centre = union.sites().get(circle);

        return (towards[0] - leaving[0]) * (leaving[0] - centre[0])
            + (towards[1] - leaving[1]) * (leaving[1] - centre[1]) >= 0;
    }

    private static double clampIntoFrontage(DiscUnionBoundary.CoastMark mark, double angle) {

        return Angles.clampInto(
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

        return Angles.clampInto(
            mark.midAngle(),
            Math.max(mark.fromAngle(), facing - reachable),
            Math.min(mark.toAngle(), facing + reachable));
    }

    private static double[] findMidpoint(DiscUnion union, DiscUnionBoundary.CoastMark mark) {
        return findPointAt(union, mark, mark.midAngle());
    }

    private static double[] findPointAt(
            DiscUnion union,
            DiscUnionBoundary.CoastMark mark,
            double angle) {

        return DiscUnionBoundary.findPointOnCircle(
            union.sites().get(mark.circle()), union.reach(), angle);
    }

    /**
     * Where one straight reach of coast leaves one cell and where it lands on the next.
     *
     * @param departAngle where on the near cell's border it leaves
     * @param arriveAngle where on the far cell's border it lands
     */
    private record EdgeAngles(
        double departAngle,
        double arriveAngle) {
    }
}
