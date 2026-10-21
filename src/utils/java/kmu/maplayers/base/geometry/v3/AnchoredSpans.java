package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.LineCrossings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * How a span is chosen between two stretches of coast, and which of the offered spans are worth
 * laying once everything already down has had its say.
 *
 * <p>Shared because two searches ask it of the same map. One lays spans between cells of ONE
 * continent, tidying that continent's outline; the other lays them BETWEEN continents, joining
 * two outlines that the trace left apart. What differs is which pairs are offered - that is the
 * whole of the difference - and what follows the offer is one construction: the same walk over
 * the pairs, the same closest pairing between two frontages, and the same judgement against the
 * walls.
 *
 * <p>Which matters more than the lines it saves. Two copies of the judgement would let one
 * search come to accept a doubled span the other refuses, and the two sets are drawn on one map
 * to be read together - so a difference between them has to be a difference in what was
 * offered, not in what was allowed.
 */
public final class AnchoredSpans {

    // The cheap pass's answer to "is this pairing acceptable": every one of them is, since
    // what that pass is for is finding the closest without weighing anything against it.
    private static final BiPredicate<double[], double[]> ACCEPTS_ANY_PAIRING =
        (start, end) -> true;

    // Narrowest first, which is the whole selection rule: where two spans claim one stretch of
    // void, the tighter claim stands. The cells break ties, and only so that two equally narrow
    // spans are always judged in the same order and the map comes out the same on every run.
    private static final Comparator<CellGap> NARROWEST_FIRST = Comparator
        .comparingDouble(CellGap::width)
        .thenComparingInt(CellGap::fromSite)
        .thenComparingInt(CellGap::toSite);

    private AnchoredSpans() {
    }

    /**
     * Every coast of a construction as the wall it is, segment by segment.
     *
     * <p>The TRACED line, not the rounded ring the map draws. Spans anchor on the coast's
     * vertices, so a span doubling a reach lies at distance zero from the vertex line - while
     * the rounded ring cuts every corner it hangs from, and sits up to a rounding radius away
     * right at the span's ends. Judged against the rounded ring, that span reads as "off the
     * wall" at any slack below the rounding radius, and the slack knob stops meaning taste and
     * starts compensating for presentation.
     *
     * <p>The lake shores are walls of this construction too, so a span is judged against them
     * the same way.
     *
     * @param traced the coasts
     * @return every segment of every ring of it
     */
    static List<double[][]> collectCoastWalls(TracedCoasts traced) {
        return WallCoverage.collectRingWalls(traced.collectShoreOutlines());
    }

    /**
     * The closest clear span between every pair of cells the caller admits, each pair offered
     * once.
     *
     * <p>Nothing is refused here for crossing anything: what the offer is depends only on the
     * cells, and which of the offers survive is settled afterwards, in one place, against one
     * rule. So the order the pairs are walked in is free, and they are walked as a sorted index
     * from each cell to every later one.
     *
     * @param frontages      each cell's frontage points, by cell
     * @param union          the cells, whose sites set the distance and which a span must clear
     * @param offerDistance  how far apart two cells' sites may sit and still be offered a span
     * @param isPairAdmitted which pairs of cells, the lower first, the search will join at all
     * @return the spans offered, in no order a reader should rely on
     */
    static List<CellGap> offerSpans(
            Map<Integer, List<double[]>> frontages,
            DiscUnion union,
            double offerDistance,
            BiPredicate<Integer, Integer> isPairAdmitted) {

        var sites = union.sites();
        var cells = frontages.keySet().stream().sorted().toList();
        var clearance = CellGaps.createOverSites(sites, union.reach());
        var offered = new ArrayList<CellGap>();

        for (var one = 0; one < cells.size(); one++) {
            for (var other = one + 1; other < cells.size(); other++) {

                var from = cells.get(one);
                var to = cells.get(other);

                if (!isPairAdmitted.test(from, to)
                        || Points.computeDistance(sites.get(from), sites.get(to)) > offerDistance) {

                    continue;
                }

                var span = findClosestSpan(
                    from,
                    to,
                    frontages.get(from),
                    frontages.get(to),
                    clearance);

                if (span != null) {
                    offered.add(span);
                }
            }
        }

        return offered;
    }

    /**
     * Drops the spans that run along a line something already covers, and those that cross one
     * already laid.
     *
     * <p>Narrowest first, so where two overlap the one kept is the tighter, and the longer -
     * which is the one running where a shorter span already walls - gives way. Sorted here
     * rather than by the caller, because the order is not a preference a search brings along: it
     * IS the selection rule, and a set judged in some other order is a different map.
     *
     * <p>Asked once per span rather than of every pairing offered, which is a deliberate trade
     * against completeness: a cell pair whose closest corners turn out to be already walled
     * drops out here rather than falling back on a wider pairing of the same two cells. Asking
     * it of every pairing costs the pass most of a second, and these knobs redraw while they
     * are dragged, so a pairing occasionally left unreconsidered is the cheaper loss.
     *
     * <p><b>Two spans over the same stretch of void are two claims on it.</b> Taken narrowest
     * first, keeping each that clears what is already kept leaves the tighter claim standing and
     * costs one loss per crossing. What that leaves is a tree - at most one route between any
     * two places - which is what the cell-pair search does.
     *
     * <p>Refused between shores as within one. Letting them cross over water the cells already
     * ring looks like it should buy subdivision, since every span across such water closes a
     * loop - but the boundary walk gives a cell's mouth to one wall only, so the extra spans
     * crowd each other out of their anchors and fewer of them are attached than before. Measured
     * on both fixtures it costs pockets rather than winning them.
     *
     * <p><b>What is already standing is judged against, and never returned.</b> A search run
     * after another one is offered spans over void the first search has already claimed, and a
     * set that ignored those would double lines that are on the map - so the standing spans are
     * walls here like any other. They come back out of the answer because they are not this
     * search's to hand over: a caller that got them back would draw them twice, in the colour of
     * whichever search returned them last.
     *
     * @param offered    the spans this search found, in any order
     * @param coastWalls the coastlines, which are walled before any span is laid
     * @param standing   the spans already laid over this map by anything else
     * @param coastSlack how far off a wall a span may run and still count as running along it
     * @return the spans worth laying, narrowest first, none of them standing spans
     */
    static List<CellGap> keepSpansWorthLaying(
            List<CellGap> offered,
            List<double[][]> coastWalls,
            List<CellGap> standing,
            double coastSlack) {

        if (offered.isEmpty()) {
            return List.of();
        }

        var narrowestFirst = new ArrayList<>(offered);

        narrowestFirst.sort(NARROWEST_FIRST);

        // Opened with the coastline and with whatever is already laid, so a span is judged
        // against every wall on the map at once. Held apart, a span covered half by the coast
        // and half by another span passes both tests and fails the only one that matters.
        var walls = WallCoverage.createWithin(
            measureExtent(coastWalls, standing, offered),
            coastWalls.size() + standing.size() + offered.size(),
            coastSlack);

        var laid = new ArrayList<>(standing);

        for (var wall : coastWalls) {
            walls.fileWall(wall[0], wall[1]);
        }

        for (var span : standing) {
            walls.fileWall(span.start(), span.end());
        }

        var kept = new ArrayList<CellGap>(narrowestFirst.size());

        for (var span : narrowestFirst) {

            if (walls.isAlreadyWalled(span.start(), span.end())
                    || doesCrossAnySpan(span.start(), span.end(), laid)) {

                continue;
            }

            kept.add(span);
            laid.add(span);
            walls.fileWall(span.start(), span.end());
        }

        return kept;
    }

    /**
     * Whether a line crosses a span already down.
     *
     * <p><b>Two spans that merely share an anchor do not count.</b> The shared-endpoint case is
     * a touch rather than a crossing, and counting it would knock out every span but one of any
     * set leaving one place - which is not what refusing crossings is for.
     *
     * <p>Rare, because a span anchors at whichever point of a frontage faces the other cell,
     * and two cells in different directions are faced from different points. What still
     * produces it is a frontage the drawn coast crosses in a single point: everything leaving
     * that cell has only the one place to leave from.
     *
     * <p>Asked of two loose points rather than of a span, because it is asked twice over: of a
     * span being offered, and of the line a span WOULD have if one of its feet were moved. The
     * second has no span to be asked about until the answer is known.
     *
     * @param start one end of the line
     * @param end   the other
     * @param laid  the spans already down
     * @return whether it crosses one of them
     */
    static boolean doesCrossAnySpan(double[] start, double[] end, List<CellGap> laid) {

        for (var held : laid) {

            if (LineCrossings.isCrossing(
                    start, end, held.start(), held.end(), LineCrossings.SharedAnchor.IS_TOUCH)) {

                return true;
            }
        }

        return false;
    }

    // Where two frontages come closest to each other with nothing lying across the line.
    //
    // Frontage against frontage rather than each end against the other cell's centre. A coast
    // runs along a median quarter of a cell's turn, so a frontage is an arc off to one side
    // rather than a whole rim: aiming at the far cell's centre then points at a part of it the
    // coast never reaches, and both ends settle away from where the two actually face each
    // other. It is the same answer for most pairs and a materially shorter span for about one
    // in eight.
    //
    // Found in two passes, and the second is usually not run. The first asks only which pairing
    // is closest, which costs a subtraction each; if nothing lies across that one, it is the
    // answer. Only where something does is the search run again with clearance disqualifying a
    // pairing - and clearance is the expensive question, since it weighs the line against the
    // sites near it.
    //
    // Merging the two into a single loop that tests clearance on each improvement costs about a
    // third of the pass: a scan improves on its best many times over a few hundred pairings,
    // and all but the last of those improvements is thrown away.
    private static CellGap findClosestSpan(
            int fromCell,
            int toCell,
            List<double[]> fromFrontage,
            List<double[]> toFrontage,
            CellGaps clearance) {

        var closest = findClosestPairing(
            fromCell,
            toCell,
            fromFrontage,
            toFrontage,
            ACCEPTS_ANY_PAIRING);

        if (closest == null || clearance.isLineClearOfCells(closest.start(), closest.end())) {
            return closest;
        }

        return findClosestPairing(
            fromCell,
            toCell,
            fromFrontage,
            toFrontage,
            clearance::isLineClearOfCells);
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

    // The box every wall the laying will ever file fits inside, which sizes the grid they are
    // filed on: the coast, what already stands, and every span on offer.
    private static Bounds measureExtent(
            List<double[][]> coastWalls,
            List<CellGap> standing,
            List<CellGap> offered) {

        var ends = new ArrayList<double[]>();

        for (var wall : coastWalls) {

            ends.add(wall[0]);
            ends.add(wall[1]);
        }

        for (var span : standing) {

            ends.add(span.start());
            ends.add(span.end());
        }

        for (var span : offered) {

            ends.add(span.start());
            ends.add(span.end());
        }

        return Bounds.computeEnclosingBounds(ends);
    }
}
