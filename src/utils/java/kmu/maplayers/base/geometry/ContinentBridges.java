package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

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
 * <p><b>Which is a statement about a coastline, not about the outside of one.</b> The lake
 * shores are coastlines of this construction as much as the outer ones are - the same line seen
 * from the water's side - and water reaching into a continent is water reaching into it whether
 * the line around it closes outward or inward. So the same pass lays both sets, and what tells
 * them apart is which shore they were anchored on and the two questions that follow from it:
 * which pairs are worth offering - one outline's cells outside, one lake's ring inside - and
 * how the reach is measured. The judging after the offer is blind to the difference, since
 * both shores are walls a span is weighed against either way.
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
 * <p>Anchored on points the traced line already carries, rather than on places computed along
 * a cell's arc. The line is sampled, so an analytically exact point on the arc would sit off
 * its chord - near the line, not on it - and a span landing between two of its points forces
 * that stretch of coast to be split there, leaving a sliver wherever it lands close to one.
 * The anchor is therefore quantised to the sampling, a fraction of a cell radius, and in
 * exchange no tolerance stands between the traced line and this. The ROUNDED line the map
 * draws is not the anchor's home: rounding cuts every corner off the vertices, so it is
 * presentation, and everything this pass measures - anchors and walls alike - lives on the
 * traced line the rounding started from.
 *
 * <p><b>The shortest such span, per pair.</b> A cell facing the void more than once offers a
 * frontage per face, so two cells can be joined several ways; the one taken is the shortest,
 * which is where the two actually face each other across the gap.
 *
 * <p><b>No span crosses one already laid.</b> Taken shortest first, each is kept only if it
 * clears what is already down, so the tighter claim on a stretch of void stands and the
 * looser gives way. What that leaves is a tree - at most one route between any two places,
 * and the water they enclose one shape with fingers - which is what the settled bridges do.
 *
 * <p>Which is why the frontage floor matters to this. Every stretch the coast passes through
 * is another frontage and another set of spans, so a coast kept at every sliver offers spans
 * too fine to read. Raising the floor coarsens the coast and the spans with it.
 */
public final class ContinentBridges {

    // The cheap pass's answer to "is this pairing acceptable": every one of them is, since
    // what that pass is for is finding the closest without weighing anything against it.
    private static final BiPredicate<double[], double[]> ACCEPTS_ANY_PAIRING =
        (start, end) -> true;

    // How far a span's width can undershoot its cells' centre distance, in cell radii: its
    // ends sit on the rims, one radius in from each centre. What the interior offer is
    // loosened by, so the width gate sees every span it should judge.
    private static final double OFFER_SLACK_RADII = 2;

    private ContinentBridges() {
    }

    /**
     * The knobs a set of spans is laid under.
     *
     * @param reachMultiple      how much water a span may cross and still be laid, in cell
     *                           radii. On the exterior shore it is read as how far apart the
     *                           two cells may sit, which across open void is the same claim
     *                           made of the centres; on the interior it caps the span itself,
     *                           since a lake's shores can face each other from far centres
     * @param coastSlack         how far off an existing wall a span may run and still count as
     *                           running along it, in map units
     * @param shouldThinFormations whether spans sharing an anchor are thinned once the laying
     *                           is settled. A rule rather than a display switch: what it
     *                           changes is which spans exist, so a report and a drawing that
     *                           disagreed about it would describe different maps
     */
    public record BridgeRules(
        double reachMultiple,
        double coastSlack,
        boolean shouldThinFormations) {
    }

    /**
     * Lays a span between every pair of cells near enough to hold water between them, anchored
     * where the named shore runs along each.
     *
     * @param traced     the continent coasts, as they were traced without bridges
     * @param shore      which of the two coastlines the spans are anchored on, which is the
     *                   whole of what tells one set from the other
     * @param parameters the knobs the cells are built under
     * @param rules      the knobs the spans are laid under
     * @return the spans, shortest first
     */
    public static List<CellGap> findAnchoredBridges(
            Coastlines.TracedCoasts traced,
            CoastFrontages.Shore shore,
            SectorGeometryParameters parameters,
            BridgeRules rules) {

        var frontages = CoastFrontages.gatherFrontagePoints(shore.collectFrontages(traced));

        if (frontages.size() < 2) {
            return List.of();
        }

        // Which pairs may be bridged at all is the shore's question, answered before the loop
        // so the loop asks it of a settled rule. The exterior asks "one outline?" - a span
        // tidies one continent's shape, and the silhouettes name that continent's cells. The
        // interior asks "one water?" - a lake bridge crosses one lake, and the lake's own ring
        // names its cells. The silhouettes CANNOT answer for the interior: they name only the
        // cells the outer walk touched, and a cell can ring a lake without ever touching the
        // outer coast - so on the silhouettes such a cell is on no continent at all, and every
        // span it could host is refused before anything else looks at it.
        var isPairBridgeable = resolvePairGate(shore, traced);
        var union = traced.union();
        var sites = union.sites();

        // Gathered once. Every candidate pairing is checked against these, and rebuilding
        // them per candidate would be the same answer found tens of thousands of times.
        //
        // The TRACED line, not the rounded ring the map draws. Spans anchor on the coast's
        // vertices, so a span doubling a reach lies at distance zero from the vertex line -
        // while the rounded ring cuts every corner it hangs from, and sits up to a rounding
        // radius away right at the span's ends. Judged against the rounded ring, that span
        // reads as "off the wall" at any slack below the rounding radius, and the slack knob
        // stops meaning taste and starts compensating for presentation.
        //
        // The lake shores are walls of this construction too, so a span is judged against
        // them the same way.
        var coastWalls = WallCoverage.collectRingWalls(collectTracedRings(traced));
        var reach = parameters.cellRadius() * rules.reachMultiple();
        var offerDistance = resolveOfferDistance(shore, reach, parameters.cellRadius());
        var laid = new ArrayList<CellGap>();

        // Every pair the gate admits, once. Nothing is refused here for crossing anything:
        // what the offer is depends only on the cells, and which of the offers survive is
        // settled afterwards, in one place, against one rule.
        for (var from : frontages.keySet()) {
            for (var to : frontages.keySet()) {

                if (from >= to
                        || !isPairBridgeable.test(from, to)
                        || Points.computeDistance(sites.get(from), sites.get(to))
                            > offerDistance) {

                    continue;
                }

                var span = findShortestSpan(
                    from,
                    to,
                    frontages.get(from),
                    frontages.get(to),
                    union);

                if (span != null && isSpanWithinReach(shore, span, reach)) {
                    laid.add(span);
                }
            }
        }

        laid.sort(Comparator
            .comparingDouble(CellGap::width)
            .thenComparingInt(CellGap::fromSite)
            .thenComparingInt(CellGap::toSite));

        var kept = keepSpansWorthLaying(laid, coastWalls, rules);

        // Thinned as the last act, so every reader of this method's answer sees the same
        // resolved set: a formation left for a consumer to tidy is a formation two consumers
        // tidy differently. Switched off, what comes back is the laying as it stands - which
        // is the only way to see what the thinning is doing, since the spans it drops are
        // gone rather than marked.
        return List.copyOf(rules.shouldThinFormations()
            ? SpanFormations.resolveSharedAnchors(kept, traced, parameters)
            : kept);
    }

    // Every coast of the construction as its traced vertex ring - the line the anchors live
    // on, and so the one line "along the wall" can be measured against without the rounding
    // opening a gap between the two.
    private static List<List<double[]>> collectTracedRings(Coastlines.TracedCoasts traced) {

        var rings = new ArrayList<List<double[]>>();

        for (var coast : traced.coasts()) {
            rings.add(Coastlines.collectPoints(coast.vertices()));
        }
        for (var lake : traced.lakes()) {
            rings.add(Coastlines.collectPoints(lake.shore().vertices()));
        }
        return rings;
    }

    // The shore's own answer to which pairs are worth offering a span, built once so the offer
    // loop asks a settled rule. Chosen here rather than put on the shore itself, because what a
    // continent is and what a lake's ring is are this construction's readings of the trace -
    // the shore only names which of the two questions applies.
    private static BiPredicate<Integer, Integer> resolvePairGate(
            CoastFrontages.Shore shore,
            Coastlines.TracedCoasts traced) {

        if (shore == CoastFrontages.Shore.INTERIOR) {

            var lakesOf = mapCellsToLakes(traced);

            return (from, to) -> isSharingALake(lakesOf, from, to);
        }

        var continentOf = mapCellsToContinents(traced);

        return (from, to) -> isOneContinent(continentOf, from, to);
    }

    // How far apart two cells' centres may sit before a pair is not worth scanning, as the
    // shore reads the reach - beside the pair gate rather than on the shore, for its reason:
    // how a reach is measured is this construction's reading, and the shore only names which
    // reading applies.
    //
    // The exterior reads the reach as the gate itself: its water is unbounded, so how far
    // apart the cells sit is the only measure of separation there is, and that is the settled
    // meaning of the knob. The interior loosens the offer by the slack and gates the span
    // instead, through isSpanWithinReach: its water is bounded and it is the WATER that is
    // bridged - two cells facing each other across a wide lake have near shores and far
    // centres, and gated at the centres they are never offered at all.
    private static double resolveOfferDistance(
            CoastFrontages.Shore shore,
            double reach,
            double cellRadius) {

        return shore == CoastFrontages.Shore.INTERIOR
            ? reach + OFFER_SLACK_RADII * cellRadius
            : reach;
    }

    // The second half of the interior's offer gate: the width the loosened centre distance
    // exists to let through. Always within reach on the exterior, whose gate was the centre
    // distance itself.
    private static boolean isSpanWithinReach(
            CoastFrontages.Shore shore,
            CellGap span,
            double reach) {

        return shore == CoastFrontages.Shore.EXTERIOR || span.width() <= reach;
    }

    // Which lakes each cell rings, off the lakes' own rings. A set per cell, because a cell
    // between two lakes rings both and may be bridged over either.
    private static Map<Integer, Set<Integer>> mapCellsToLakes(Coastlines.TracedCoasts traced) {

        var lakesOf = new LinkedHashMap<Integer, Set<Integer>>();

        for (var lake = 0; lake < traced.lakes().size(); lake++) {
            for (var cell : traced.lakes().get(lake).ringCells()) {

                lakesOf.computeIfAbsent(cell, key -> new LinkedHashSet<>()).add(lake);
            }
        }
        return lakesOf;
    }

    // Whether two cells ring one lake, which is what makes a span between them a bridge over
    // that lake rather than a line across whatever land or foreign water lies between.
    private static boolean isSharingALake(
            Map<Integer, Set<Integer>> lakesOf,
            int from,
            int to) {

        var one = lakesOf.get(from);
        var other = lakesOf.get(to);

        if (one == null || other == null) {
            return false;
        }

        for (var lake : one) {

            if (other.contains(lake)) {
                return true;
            }
        }
        return false;
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
     * @return the continent each cell sits on, by cell. A cell the outer walk never touched is
     *         absent rather than present under some sentinel - which includes every cell that
     *         faces only a lake, and is why the interior shore never asks this question
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

    // Where the two frontages come closest to each other with nothing lying across the line.
    //
    // Frontage against frontage rather than each end against the other cell's centre. A coast
    // runs along a median quarter of a cell's turn, so a frontage is an arc off to one side
    // rather than a whole rim: aiming at the far cell's centre then points at a part of it the
    // coast never reaches, and both ends settle away from where the two actually face each
    // other. It is the same answer for most pairs and a materially shorter span for about one
    // in eight.
    //
    // Found in two passes, and the second is usually not run. The first asks only which
    // pairing is closest, which costs a subtraction each; if nothing lies across that one, it
    // is the answer. Only where something does is the search run again with clearance
    // disqualifying a pairing - and clearance is the expensive question, since it weighs the
    // line against every site in the sector.
    //
    // Merging the two into a single loop that tests clearance on each improvement costs about
    // a third of the pass: a scan improves on its best many times over a few hundred pairings,
    // and all but the last of those improvements is thrown away.
    private static CellGap findShortestSpan(
            int fromCell,
            int toCell,
            List<double[]> fromFrontage,
            List<double[]> toFrontage,
            DiscUnion union) {

        var closest = findClosestPairing(
            fromCell, toCell, fromFrontage, toFrontage, ACCEPTS_ANY_PAIRING);

        if (closest == null
                || CellGaps.isLineClearOfCells(
                    closest.start(), closest.end(), union.sites(), union.reach())) {

            return closest;
        }

        return findClosestPairing(
            fromCell,
            toCell,
            fromFrontage,
            toFrontage,
            (start, end) ->
                CellGaps.isLineClearOfCells(start, end, union.sites(), union.reach()));
    }

    // The closest pairing of two frontages that the caller will accept.
    //
    // Compared squared and rooted once, since all any pairing is asked is which of two is
    // nearer. What makes a pairing acceptable is the caller's, so that the cheap pass and the
    // careful one are one search asked two questions rather than two copies of one loop.
    private static CellGap findClosestPairing(
            int fromCell,
            int toCell,
            List<double[]> fromFrontage,
            List<double[]> toFrontage,
            BiPredicate<double[], double[]> isAcceptable) {

        var closest = (CellGap) null;

        for (var start : fromFrontage) {
            for (var end : toFrontage) {

                var squared = Points.computeDistanceSquared(start, end);

                if (closest != null && squared >= closest.width() * closest.width()) {
                    continue;
                }
                if (!isAcceptable.test(start, end)) {
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
     * <p><b>And the spans that cross one already laid.</b> Two spans over the same stretch of
     * void are two claims on it; taken shortest first, keeping each that clears what is
     * already kept leaves the tighter claim standing and costs one loss per crossing. What
     * that leaves is a tree - at most one route between any two places - which is what the
     * settled bridges do.
     *
     * <p>Refused on both shores alike. Letting them cross over water the cells already ring
     * looks like it should buy subdivision, since every span across such water closes a loop -
     * but the boundary walk gives a cell's mouth to one wall only, so the extra spans crowd
     * each other out of their anchors and fewer of them are attached than before. Measured on
     * both fixtures it costs pockets rather than winning them.
     *
     * @param spans      the spans, already sorted shortest first
     * @param coastWalls the coastline, which is walled before any span is laid
     * @param rules      the slack to judge doubling at
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
                    || doesCrossAnyKept(span, kept)) {

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
     * fan it was written against - and the fans and chains it lets through are
     * {@link SpanFormations}' business, thinned after the laying is settled.
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
}
