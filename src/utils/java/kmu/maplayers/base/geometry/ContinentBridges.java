package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import java.util.ArrayList;
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
 * <p><b>Crossings are kept.</b> The settled search drops any span that crosses one already
 * laid, which leaves a tree - at most one route between any two places. Kept, they divide the
 * void they cross into a grid instead, which is the point: a piece of open sea bounded on four
 * sides is a pocket, where the same sea under a tree of spans is one shape with fingers.
 *
 * <p>Which is why the frontage floor matters to this. Every stretch the coast passes through
 * is another pair of corners and another set of spans, so a coast kept at every sliver makes a
 * grid too fine to read. Raising the floor coarsens the coast and the grid with it.
 */
final class ContinentBridges {

    // How many places along a span are asked whether they are inside a cell. The two ends sit
    // ON a cell by construction, so only the middle is in question; a handful of samples
    // catches a span clipping a corner without crossing the middle of anything.
    private static final int SPAN_SAMPLES = 8;

    private ContinentBridges() {
    }

    /**
     * The knobs a grid of spans is laid under.
     *
     * @param reachMultiple how far apart two cells may sit and still be bridged, in cell radii
     * @param coastSlack    how far off an existing wall a span may run and still count as
     *                      running along it, in map units
     */
    record BridgeRules(
        double reachMultiple,
        double coastSlack) {
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
        var tolerance = rules.coastSlack();
        var reach = parameters.cellRadius() * rules.reachMultiple();
        var laid = new ArrayList<CellGap>();

        // Every pair of cells on ONE continent, once. No pair is refused for crossing another
        // - that is what leaves a grid rather than a tree - so nothing here depends on the
        // order they are tried in.
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

        laid.sort(java.util.Comparator
            .comparingDouble(CellGap::width)
            .thenComparingInt(CellGap::fromSite)
            .thenComparingInt(CellGap::toSite));

        return List.copyOf(keepOneSpanPerLine(laid, coastWalls, tolerance));
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
     * @return the continent each cell sits on, by cell, and -1 for a cell facing no void
     */
    static Map<Integer, Integer> mapCellsToContinents(Coastlines.TracedCoasts traced) {

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
    static Map<Integer, List<double[]>> collectCoastCorners(Coastlines.TracedCoasts traced) {

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

            var along = (double) step / SPAN_SAMPLES;
            var point = new double[] {
                start[0] + (end[0] - start[0]) * along,
                start[1] + (end[1] - start[1]) * along};

            if (!isPointWalled(point, walls, tolerance)) {
                return false;
            }
        }
        return true;
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
     * which is the one covering ground a shorter span already walls - gives way. Taking them
     * in a settled order is also what makes the answer the same on every run.
     *
     * <p>Asked once per span rather than of every pairing offered. A cell pair whose closest
     * corners turn out to be already walled therefore drops out rather than falling back on a
     * wider pairing of the same two cells - the cost of asking the question of every pairing
     * was the whole pass taking most of a second, which on a knob that redraws while it is
     * dragged is worse than the pairing occasionally not being reconsidered.
     *
     * @param spans      the spans, already sorted shortest first
     * @param coastWalls the coastline, which is walled before any span is laid
     * @param tolerance  how far off a wall a span may run and still count as along it
     * @return one span per line actually walled
     */
    private static List<CellGap> keepOneSpanPerLine(
            List<CellGap> spans,
            List<double[][]> coastWalls,
            double tolerance) {

        var kept = new ArrayList<CellGap>(spans.size());

        // Opened with the coastline, so a span is judged against every wall on the map at
        // once. Held apart, a span covered half by the coast and half by another span passes
        // both tests and fails the only one that matters.
        var walls = new ArrayList<>(coastWalls);

        for (var span : spans) {

            if (isAlreadyWalled(span.start(), span.end(), walls, tolerance)) {
                continue;
            }
            kept.add(span);
            walls.add(new double[][] {span.start(), span.end()});
        }
        return kept;
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

            var along = (double) step / SPAN_SAMPLES;
            var point = new double[] {
                start[0] + (end[0] - start[0]) * along,
                start[1] + (end[1] - start[1]) * along};

            if (union.isPointInside(point)) {
                return true;
            }
        }
        return false;
    }
}
