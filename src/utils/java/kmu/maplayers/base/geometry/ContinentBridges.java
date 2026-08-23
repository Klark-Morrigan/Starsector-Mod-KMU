package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bridges laid to a sector that already carries continent coastlines, anchored where those
 * coastlines meet the cells.
 *
 * <p><b>The second pass at one shape.</b> Tracing the coast rounds a continent off once, by
 * choosing which of its cells the line runs through; what it cannot do is leave the cells
 * behind, so wherever the outline turns inward it must follow. This pass captures those turns:
 * a span laid across the mouth of an inlet takes the inlet's water inside the continent, and
 * the shape rounds up again.
 *
 * <p><b>So a span joins two cells of ONE continent.</b> Its two ends are corners of the same
 * outline, and the water it closes off is water that outline currently reaches into. A span
 * to another continent would be a different act entirely - joining two shapes rather than
 * tidying one - and nothing here is asking for that.
 *
 * <p>Which continent a cell belongs to is not worked out here. The coast walk already sorted
 * the cells into runs of touching neighbours - one run per continent - so that grouping is
 * read straight off the silhouettes it produced, and the two passes cannot come to disagree
 * about what a continent is.
 *
 * <p><b>A span ends where the coastline stops hugging a cell.</b> A coast alternates between
 * fillets, which run along a cell's own edge, and reaches, which cross void from one cell to
 * the next. So the coastline meets a cell's edge along a whole fillet, and the ends of that
 * fillet are the two places the meeting stops - the corners the eye reads as the corners of
 * the void. Those are what a span aims at. An intersection, where a coast touches a cell at a
 * single point rather than running along it, is the same thing with the fillet degenerated,
 * and needs no rule of its own.
 *
 * <p>Nothing here is measured against the drawn line to find those points. Where a fillet
 * begins and ends is a fact the walk already settled - it is where a reach departed and where
 * the next one landed - so the corners are exact, and no tolerance stands between the walk's
 * answer and this one.
 *
 * <p><b>The shortest such span, per pair.</b> A cell facing the void more than once offers a
 * corner per frontage, so two cells can be joined several ways; the one taken is the shortest,
 * which is the pair of corners actually facing each other across the gap.
 *
 * <p><b>Crossings are kept or refused, and it is a real choice.</b> Refused - which is what the
 * settled search does - the spans leave a tree, at most one route between any two places, and
 * the water they enclose is one shape with fingers. Kept, they draw a grid, and a grid divides
 * that water into pieces bounded on every side. Neither is right in general: a tree keeps the
 * lines few and the shapes large, a grid gives every piece a boundary to be judged by.
 *
 * <p><b>Drawing that grid is all this does.</b> Finding the pieces it cuts is a separate job
 * and not one {@link DiscUnionBoundary} can do: its walls run circle to circle and its walk
 * has no vocabulary for the stretch of a span between two crossings, which is precisely what
 * bounds a piece here. A trace of these spans therefore reports far fewer, larger shapes than
 * are drawn, and reading pocket sizes off one is reading a different map.
 *
 * <p>Which is why the frontage floor matters to this. Every stretch the coast passes through
 * is another pair of corners and another set of spans, so a coast kept at every sliver makes a
 * grid too fine to read. Raising the floor coarsens the coast and the grid with it.
 */
final class ContinentBridges {

    // How finely a span is walked when something has to be asked of its whole length. A
    // handful of steps catches a span clipping a cell's corner, or slipping off a wall for a
    // stretch, without either question costing a sample per map unit.
    //
    // Which steps are asked differs by question, and the two conventions are not
    // interchangeable: whether a span runs through a cell is a question about its MIDDLE,
    // since both ends sit on a cell by construction, while whether it is already walled is
    // asked of its ends as well, because a stretch left uncovered at either end is exactly
    // the gap that makes a span worth laying.
    private static final int SPAN_SAMPLES = 8;

    private ContinentBridges() {
    }

    /**
     * The knobs a grid of spans is laid under.
     *
     * @param reachMultiple     how far apart two cells may sit and still be bridged, in cell
     *                          radii
     * @param coastSlack        how far off an existing wall a span may run and still count as
     *                          running along it, in map units
     * @param isCrossingAllowed whether a span may cross one already laid. Allowed, the spans
     *                          divide the void into a grid; refused, they leave a tree, which
     *                          is what the settled bridges do
     */
    record BridgeRules(
        double reachMultiple,
        double coastSlack,
        boolean isCrossingAllowed) {
    }

    /**
     * Lays a span between every pair of cells near enough to hold void between them, anchored
     * at the corners where their coastlines stop hugging them.
     *
     * @param traced     the continent coasts, as they were traced without bridges
     * @param parameters the knobs the cells are built under
     * @param rules      the reach to offer bridges at
     * @return the spans, shortest first
     */
    static List<CellGap> findAnchoredBridges(
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters,
            BridgeRules rules) {

        var corners = collectCoastCorners(traced);

        if (corners.size() < 2) {
            return List.of();
        }

        var continentOf = mapCellsToContinents(traced);
        var union = traced.union();
        var sites = union.sites();

        // Gathered once. Every candidate pairing is checked against these, and rebuilding
        // them per candidate would be the same answer found tens of thousands of times.
        var coastWalls = collectCoastWalls(traced);
        var reach = parameters.cellRadius() * rules.reachMultiple();
        var laid = new ArrayList<CellGap>();

        // Every pair of cells on ONE continent, once. Nothing is refused here for crossing
        // anything: what the offer is depends only on the cells, and which of the offers
        // survive is settled afterwards, in one place, against one rule.
        for (var from : corners.keySet()) {
            for (var to : corners.keySet()) {

                if (from >= to
                        || !isOneContinent(continentOf, from, to)
                        || Points.computeDistance(sites.get(from), sites.get(to)) > reach) {

                    continue;
                }

                var span = findShortestSpan(
                    from,
                    to,
                    corners.get(from),
                    corners.get(to),
                    union);

                if (span != null) {
                    laid.add(span);
                }
            }
        }

        laid.sort(Comparator
            .comparingDouble(CellGap::width)
            .thenComparingInt(CellGap::fromSite)
            .thenComparingInt(CellGap::toSite));

        return List.copyOf(keepSpansWorthLaying(laid, coastWalls, rules));
    }

    /**
     * Which continent each cell belongs to.
     *
     * <p>Read off the silhouettes rather than worked out again: the coast walk already
     * separated the cells into runs of touching neighbours - one run per continent - and that
     * separation is precisely what a continent IS here. Grouping them a second way would be a
     * second answer, and the two constructions on screen would stop describing one map.
     *
     * @param traced the coast
     * @return the continent each cell sits on, by cell. A cell facing no void is absent rather
     *         than present under some sentinel, since "on no continent" and "on continent
     *         number x" are not answers to one question and a caller has to tell them apart
     */
    private static Map<Integer, Integer> mapCellsToContinents(Coastlines.TracedCoasts traced) {

        var continentOf = new LinkedHashMap<Integer, Integer>();

        for (var continent = 0; continent < traced.silhouettes().size(); continent++) {
            for (var mark : traced.silhouettes().get(continent)) {

                continentOf.put(mark.circle(), continent);
            }
        }
        return continentOf;
    }

    // Whether two cells belong to the same continent, and so to the same outline.
    //
    // A span exists to round ONE outline up, so both its ends have to be corners of that
    // outline: the water it closes off is water that outline currently runs into. Two cells
    // on different continents have no shared inlet to capture - a line between them would be
    // joining two shapes rather than tidying either.
    private static boolean isOneContinent(
            Map<Integer, Integer> continentOf,
            int from,
            int to) {

        var one = continentOf.get(from);
        var other = continentOf.get(to);

        return one != null && one.equals(other);
    }

    /**
     * Every corner a coastline leaves on a cell, gathered by the cell it sits on.
     *
     * <p>Read off the reaches rather than hunted for along the drawn line: a reach departs at
     * the end of one fillet and lands at the beginning of the next, so its two ends ARE the
     * two places a coastline stopped running along a cell. A cell facing the void on more than
     * one frontage collects a corner from each without needing a rule for it.
     *
     * @param traced the coast
     * @return each cell's corners, in the order the walk found them
     */
    private static Map<Integer, List<double[]>> collectCoastCorners(
            Coastlines.TracedCoasts traced) {

        var corners = new LinkedHashMap<Integer, List<double[]>>();

        for (var reach : Coastlines.collectStraightReaches(traced)) {

            corners
                .computeIfAbsent(reach.from().circle(), cell -> new ArrayList<>())
                .add(reach.from().point());
            corners
                .computeIfAbsent(reach.to().circle(), cell -> new ArrayList<>())
                .add(reach.to().point());
        }
        return corners;
    }

    // The shortest span between two cells' corners that stays out of every cell, or null when
    // no pairing of them does.
    //
    // Shortest rather than first found, because a cell with two frontages offers corners on
    // opposite sides of itself, and the span wanted is between the two that face each other.
    private static CellGap findShortestSpan(
            int fromCell,
            int toCell,
            List<double[]> fromCorners,
            List<double[]> toCorners,
            DiscUnion union) {

        var shortest = (CellGap) null;

        for (var start : fromCorners) {
            for (var end : toCorners) {

                var width = Points.computeDistance(start, end);

                if (shortest != null && width >= shortest.width()) {
                    continue;
                }
                if (doesRunThroughACell(start, end, union)) {
                    continue;
                }
                shortest = new CellGap(fromCell, toCell, start, end, width);
            }
        }
        return shortest;
    }

    /**
     * Whether every part of a line is already walled by something.
     *
     * <p>A span laid where walls already run closes nothing: the void either side of it was
     * divided before it arrived, by the coastline or by a span laid earlier. Drawn, it is one
     * line under another.
     *
     * <p><b>Covered piecewise, not matched to one wall.</b> The line that has to go is rarely
     * a copy of any single wall - it is a long span whose first half runs beside a shorter
     * span and whose second half runs beside the coast, so no one wall covers it and every
     * one of them covers some. Asking each wall in turn whether IT covers the whole span
     * misses exactly that, which is why this asks the question the other way round: walk the
     * span, and let any wall answer for the stretch under it.
     *
     * <p>Its ends prove nothing on their own. Corners are where walls meet, so almost every
     * span begins and ends on one; a span that leaves a corner and strikes out across open
     * void is the ordinary case, and only one that never leaves the walls is a doubling.
     *
     * @param start     one corner
     * @param end       the other
     * @param walls     the walls already down, as pairs of endpoints
     * @param tolerance how far off a wall a place may be and still count as walled
     * @return whether the whole line is already walled
     */
    private static boolean isAlreadyWalled(
            double[] start,
            double[] end,
            List<double[][]> walls,
            double tolerance) {

        for (var step = 0; step <= SPAN_SAMPLES; step++) {

            if (!isPointWalled(findPointAlong(start, end, step), walls, tolerance)) {
                return false;
            }
        }
        return true;
    }

    // One of the places along a span the sampling asks about, by step rather than by fraction
    // so that both questions step the same way and cannot come to disagree about where the
    // middle of a span is.
    private static double[] findPointAlong(double[] start, double[] end, int step) {

        var along = (double) step / SPAN_SAMPLES;

        return new double[] {
            start[0] + (end[0] - start[0]) * along,
            start[1] + (end[1] - start[1]) * along};
    }

    private static boolean isPointWalled(
            double[] point,
            List<double[][]> walls,
            double tolerance) {

        for (var wall : walls) {

            if (Segments.computeDistanceToPoint(wall[0], wall[1], point) <= tolerance) {
                return true;
            }
        }
        return false;
    }

    /**
     * The drawn coastline as the wall it is, segment by segment.
     *
     * <p>The whole line rather than its straight reaches alone. A span can run beside a
     * fillet as readily as beside a reach - the coast is one line to the eye and one wall to
     * the void, and which of its parts a span happens to shadow is not a distinction anything
     * downstream makes.
     *
     * @param traced the coast
     * @return every segment of every coast ring
     */
    private static List<double[][]> collectCoastWalls(Coastlines.TracedCoasts traced) {

        var walls = new ArrayList<double[][]>();

        for (var ring : Coastlines.collectCoastRings(traced)) {
            for (var index = 0; index < ring.size(); index++) {

                walls.add(new double[][] {
                    ring.get(index),
                    ring.get((index + 1) % ring.size())});
            }
        }
        return walls;
    }

    /**
     * Drops the spans that run along a line another span already covers.
     *
     * <p>Shortest first, so where two overlap the one kept is the tighter, and the longer -
     * which is the one running where a shorter span already walls - gives way. Taking them in
     * a settled order is also what makes the answer the same on every run.
     *
     * <p>Asked once per span rather than of every pairing offered, which is a deliberate trade
     * against completeness: a cell pair whose closest corners turn out to be already walled
     * drops out here rather than falling back on a wider pairing of the same two cells. Asking
     * it of every pairing costs the pass most of a second, and these knobs redraw while they
     * are dragged, so a pairing occasionally left unreconsidered is the cheaper loss.
     *
     * <p><b>And, where crossings are refused, the spans that cross one already laid.</b> Two
     * spans over the same stretch of void are two claims on it; taken shortest first, keeping
     * each that clears what is already kept leaves the tighter claim standing and costs one
     * loss per crossing. What that leaves is a tree - at most one route between any two places
     * - where allowing them leaves a grid. Which of the two is wanted is a question about the
     * shape of the pockets, not about any one span, so it is asked of the rules.
     *
     * @param spans      the spans, already sorted shortest first
     * @param coastWalls the coastline, which is walled before any span is laid
     * @param rules      the slack to judge doubling at, and whether crossings are allowed
     * @return the spans worth laying
     */
    private static List<CellGap> keepSpansWorthLaying(
            List<CellGap> spans,
            List<double[][]> coastWalls,
            BridgeRules rules) {

        var kept = new ArrayList<CellGap>(spans.size());

        // Opened with the coastline, so a span is judged against every wall on the map at
        // once. Held apart, a span covered half by the coast and half by another span passes
        // both tests and fails the only one that matters.
        var walls = new ArrayList<>(coastWalls);

        for (var span : spans) {

            if (isAlreadyWalled(span.start(), span.end(), walls, rules.coastSlack())
                    || !rules.isCrossingAllowed() && doesCrossAnyKept(span, kept)) {

                continue;
            }
            kept.add(span);
            walls.add(new double[][] {span.start(), span.end()});
        }
        return kept;
    }

    /**
     * Whether a span crosses one already kept.
     *
     * <p><b>Two spans that merely share an anchor do not count.</b> A span here is anchored on
     * a coastline corner, and several of them leave the same corner - that is what a fan across
     * a bay IS. The shared-endpoint case is a touch rather than a crossing, and counting it
     * would have every fan knock all but one of itself out, which is not what refusing
     * crossings is for.
     *
     * <p>The settled bridges need no such exemption because they run rim to rim: two leaving
     * one cell start at two different points on its edge and only register when they genuinely
     * cross. Anchoring on corners is what makes this a case at all.
     *
     * @param span the span being offered
     * @param kept the spans already laid
     * @return whether it crosses one of them
     */
    private static boolean doesCrossAnyKept(CellGap span, List<CellGap> kept) {

        for (var held : kept) {

            if (!isSharingAnAnchor(span, held)
                    && Segments.intersectSegments(
                        span.start(), span.end(), held.start(), held.end()) != null) {

                return true;
            }
        }
        return false;
    }

    private static boolean isSharingAnAnchor(CellGap span, CellGap held) {

        return isSamePlace(span.start(), held.start())
            || isSamePlace(span.start(), held.end())
            || isSamePlace(span.end(), held.start())
            || isSamePlace(span.end(), held.end());
    }

    private static boolean isSamePlace(double[] one, double[] other) {
        return Points.computeDistance(one, other) <= DiscUnion.TOUCHING_TOLERANCE;
    }

    // Whether a span passes through a cell rather than across the void between them.
    //
    // The two ends are not asked. A corner sits exactly ON the reach that decides what is
    // void, which is the one place "inside a cell" has no answer floating point can be
    // trusted to give twice. What a span crosses is settled by its middle.
    private static boolean doesRunThroughACell(
            double[] start,
            double[] end,
            DiscUnion union) {

        for (var step = 1; step < SPAN_SAMPLES; step++) {

            if (union.isPointInside(findPointAlong(start, end, step))) {
                return true;
            }
        }
        return false;
    }
}
