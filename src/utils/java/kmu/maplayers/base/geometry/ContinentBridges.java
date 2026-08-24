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
 * <p><b>A span ends on the coastline, anywhere along it.</b> A coast alternates between
 * fillets, which run along a cell's own edge, and reaches, which cross void from one cell to
 * the next. What a span needs of its ends is that they sit ON the drawn line, since a span
 * ending short of it closes nothing; a whole fillet satisfies that, not merely the two corners
 * where one begins and ends.
 *
 * <p>Aiming only at those corners is what a first pass did, and it shows: a cell offers two
 * places to leave from, so several spans leave the same one and cross the void as a fan. The
 * lines are then a picture of where anchors were allowed rather than of where the void is
 * narrow, which is the one thing they are drawn to show.
 *
 * <p><b>So a span is anchored at the point of each frontage nearest the other.</b> The
 * settled bridges reach rim to rim along the line between two cells' centres, which is the
 * same statement made where there is no coastline to sit on yet.
 *
 * <p>Anchored on points the drawn ring already carries, rather than on places computed along
 * a cell's arc. The ring is sampled, so an analytically exact point on the arc would sit off
 * the drawn chord - near the line, not on it - and a span landing between two ring points
 * forces that stretch of coast to be split there, leaving a sliver wherever it lands close to
 * one. The anchor is therefore quantised to the sampling, a fraction of a cell radius, and in
 * exchange no tolerance stands between the drawn line and this.
 *
 * <p><b>The shortest such span, per pair.</b> A cell facing the void more than once offers a
 * frontage per face, so two cells can be joined several ways; the one taken is the shortest,
 * which is where the two actually face each other across the gap.
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
public final class ContinentBridges {

    // No cell yet, while a walk is between one frontage and the next. Named rather than left
    // as a bare -1 because it is compared against real cell numbers.
    private static final int NO_CELL = -1;

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
    public record BridgeRules(
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
    public static List<CellGap> findAnchoredBridges(
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters,
            BridgeRules rules) {

        var frontages = gatherFrontagePoints(collectBridgeFrontages(traced));

        if (frontages.size() < 2) {
            return List.of();
        }

        var continentOf = mapCellsToContinents(traced);
        var union = traced.union();
        var sites = union.sites();

        // Gathered once. Every candidate pairing is checked against these, and rebuilding
        // them per candidate would be the same answer found tens of thousands of times.
        var coastWalls = WallCoverage.collectRingWalls(Coastlines.collectCoastRings(traced));
        var reach = parameters.cellRadius() * rules.reachMultiple();
        var laid = new ArrayList<CellGap>();

        // Every pair of cells on ONE continent, once. Nothing is refused here for crossing
        // anything: what the offer is depends only on the cells, and which of the offers
        // survive is settled afterwards, in one place, against one rule.
        for (var from : frontages.keySet()) {
            for (var to : frontages.keySet()) {

                if (from >= to
                        || !isOneContinent(continentOf, from, to)
                        || Points.computeDistance(sites.get(from), sites.get(to)) > reach) {

                    continue;
                }

                var span = findShortestSpan(
                    from,
                    to,
                    frontages.get(from),
                    frontages.get(to),
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
     * Every point the drawn coastline passes through on a cell, gathered by the cell.
     *
     * <p><b>The whole frontage, not merely its two ends.</b> A span is anchored wherever the
     * coast comes closest to the cell across the gap, and that is generally somewhere along a
     * frontage rather than at a corner of one. Offered only the corners, every span leaving a
     * cell has to start at one of two places, so several of them start at the SAME place and
     * leave in a fan - which is a picture of what the anchors allowed rather than of where the
     * void is narrow.
     *
     * <p><b>Points of the drawn ring, rather than places computed on the cell's arc.</b> Two
     * reasons, and the second is the one that matters. The ring is sampled, so a point worked
     * out on the true arc sits off the drawn chord by the sagitta - close, but not ON the line
     * the pieces are cut against. And a span landing between two ring points forces that
     * stretch of coast to be split there, which where it lands near an existing point leaves a
     * sliver of coast shorter than anything else on the map. Anchoring on a point the ring
     * already has costs a little precision - the anchor is quantised to the sampling, a
     * fraction of a cell radius - and buys exactness against the only line anything downstream
     * reads.
     *
     * @param traced the coast
     * @return each cell's own stretch of the drawn coast, in walk order. A cell the coast never
     *         runs along is absent rather than present under an empty list, since it offers
     *         nowhere to anchor at all
     */
    public static Map<Integer, List<List<double[]>>> collectBridgeFrontages(
            Coastlines.TracedCoasts traced) {

        var frontages = new LinkedHashMap<Integer, List<List<double[]>>>();

        for (var coast : traced.coasts()) {

            if (coast.isEmpty()) {
                continue;
            }

            // Walked from a place the cell changes rather than from the list's first entry,
            // so a frontage straddling the ring's own start comes back as the one run it is
            // rather than as two ending nowhere.
            var begin = findFrontageStart(coast);
            var run = new ArrayList<double[]>();
            var runCell = NO_CELL;

            for (var step = 0; step < coast.size(); step++) {

                var vertex = coast.get((begin + step) % coast.size());

                if (vertex.circle() != runCell) {

                    addFrontage(frontages, runCell, run);
                    run = new ArrayList<>();
                    runCell = vertex.circle();
                }
                run.add(vertex.point());
            }
            addFrontage(frontages, runCell, run);
        }
        return frontages;
    }

    // Where to start walking a ring so that no frontage is split across the ends of the list.
    // A ring the coast runs round without ever changing cell has no such place, and starting
    // anywhere is as good as anywhere else.
    private static int findFrontageStart(List<Coastlines.CoastVertex> coast) {

        for (var index = 0; index < coast.size(); index++) {

            var previous = coast.get((index + coast.size() - 1) % coast.size());

            if (coast.get(index).circle() != previous.circle()) {
                return index;
            }
        }
        return 0;
    }

    private static void addFrontage(
            Map<Integer, List<List<double[]>>> frontages,
            int cell,
            List<double[]> run) {

        if (cell == NO_CELL || run.isEmpty()) {
            return;
        }

        frontages
            .computeIfAbsent(cell, whichever -> new ArrayList<>())
            .add(List.copyOf(run));
    }

    // Every point of a cell's frontages together, which is what choosing an anchor asks for.
    // Which run a point came from matters to a reader looking at the map and not at all to the
    // search, since any point of any of them is somewhere a span may be anchored.
    private static Map<Integer, List<double[]>> gatherFrontagePoints(
            Map<Integer, List<List<double[]>>> frontages) {

        var points = new LinkedHashMap<Integer, List<double[]>>();

        for (var entry : frontages.entrySet()) {
            for (var run : entry.getValue()) {

                points
                    .computeIfAbsent(entry.getKey(), whichever -> new ArrayList<>())
                    .addAll(run);
            }
        }
        return points;
    }

    // The shortest span between two cells' drawn frontages that stays out of every cell, or
    // null when no pairing of them does.
    //
    // The closest approach between the two stretches of coast, near enough: every point of one
    // against every point of the other, so the pair taken is where the void between the cells
    // is actually narrowest. A cell facing the void on two frontages offers both, and the far
    // one loses on width without needing a rule to exclude it.
    //
    // The width test comes before the clearance test deliberately. Clearance walks every site,
    // so it is the expensive half, and a pairing that cannot beat the best already found needs
    // no answer to it - which is what keeps this affordable now that a frontage is a run of
    // points rather than two of them.
    //
    // Where the two frontages come closest to EACH OTHER, and if something lies across that,
    // the closest pairing of them that nothing lies across.
    //
    // Frontage against frontage rather than each end against the other cell's centre. A coast
    // runs along a median quarter of a cell's turn, so a frontage is an arc off to one side
    // rather than a whole rim: aiming at the far cell's centre then points at a part of it the
    // coast never reaches, and both ends settle away from where the two actually face each
    // other. It is the same answer for most pairs and a materially shorter span for about one
    // in eight.
    //
    // Cheap because a frontage is a few dozen points at most, so every pairing of two of them
    // is a few hundred distances. What is NOT cheap is asking what lies across a pairing -
    // that walks every site in the sector - which is why it is asked once, of the answer,
    // rather than of each pairing on the way to it.
    private static CellGap findShortestSpan(
            int fromCell,
            int toCell,
            List<double[]> fromFrontage,
            List<double[]> toFrontage,
            DiscUnion union) {

        var closest = findClosestPairing(fromCell, toCell, fromFrontage, toFrontage);

        if (closest == null || !doesRunThroughACell(closest.start(), closest.end(), union)) {
            return closest;
        }
        return findClosestClearPairing(fromCell, toCell, fromFrontage, toFrontage, union);
    }

    // The closest pairing of two frontages, taking no view on what lies across it.
    private static CellGap findClosestPairing(
            int fromCell,
            int toCell,
            List<double[]> fromFrontage,
            List<double[]> toFrontage) {

        var closestStart = (double[]) null;
        var closestEnd = (double[]) null;
        var closestSquared = Double.MAX_VALUE;

        for (var start : fromFrontage) {
            for (var end : toFrontage) {

                var squared = Points.computeDistanceSquared(start, end);

                if (squared < closestSquared) {

                    closestStart = start;
                    closestEnd = end;
                    closestSquared = squared;
                }
            }
        }

        return closestStart == null
            ? null
            : new CellGap(
                fromCell, toCell, closestStart, closestEnd, Math.sqrt(closestSquared));
    }

    // The same search with a cell in the way disqualifying a pairing, for the minority of
    // pairs whose closest pairing has one. Separate from the search above rather than a mode
    // of it, because the whole reason that one exists is to answer without paying for this.
    private static CellGap findClosestClearPairing(
            int fromCell,
            int toCell,
            List<double[]> fromFrontage,
            List<double[]> toFrontage,
            DiscUnion union) {

        var closest = (CellGap) null;

        for (var start : fromFrontage) {
            for (var end : toFrontage) {

                var squared = Points.computeDistanceSquared(start, end);

                if (closest != null && squared >= closest.width() * closest.width()) {
                    continue;
                }
                if (doesRunThroughACell(start, end, union)) {
                    continue;
                }
                closest = new CellGap(fromCell, toCell, start, end, Math.sqrt(squared));
            }
        }
        return closest;
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

            if (WallCoverage.isAlreadyWalled(span.start(), span.end(), walls, rules.coastSlack())
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
     * <p><b>Two spans that merely share an anchor do not count.</b> The shared-endpoint case
     * is a touch rather than a crossing, and counting it would knock out every span but one of
     * any set leaving one place - which is not what refusing crossings is for.
     *
     * <p>Rare now that a span anchors at whichever point of a frontage faces the other cell,
     * since two cells in different directions are faced from different points. What still
     * produces it is a frontage the drawn coast crosses in a single point: everything leaving
     * that cell has only the one place to leave from. Kept for that case rather than for the
     * fan it was written against.
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

        for (var step = 1; step < WallCoverage.LINE_SAMPLES; step++) {

            if (union.isPointInside(WallCoverage.findPointAlong(start, end, step))) {
                return true;
            }
        }
        return false;
    }
}
