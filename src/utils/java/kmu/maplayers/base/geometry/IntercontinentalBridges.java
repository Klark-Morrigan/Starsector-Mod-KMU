package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spans laid between the continents: the links that make a sector of shapes the trace left
 * apart.
 *
 * <p><b>The other half of the laying, and the opposite act.</b> {@link ContinentBridges} tidies
 * one continent - a span across the mouth of an inlet takes that water inside the outline, and
 * the shape rounds up. These join two outlines that have nothing to do with each other, over
 * void that belongs to neither. Nothing is being rounded up here; what is being decided is
 * whether two shapes are near enough to read as one sector rather than as two.
 *
 * <p>Which is why it is a class rather than a third case inside the other. What differs is the
 * one question of which pairs are offered - the closest pairing between two frontages, and the
 * judgement against the walls, are {@link AnchoredSpans}' and are shared - and a pass whose
 * whole content is "who may be joined to whom" is better read as its own answer than as a
 * branch inside the answer that says the opposite.
 *
 * <p><b>Range-based, the way the settled bridges are.</b> Two cells hold the void between them
 * when they sit near enough to trap it, measured centre to centre; further apart than that and
 * what lies between is open void that happens to be between them. That is the settled search's
 * claim about the sector, made here where the coasts have already taken some of the void.
 *
 * <p><b>Anchored on bridgeable frontage, and nowhere else.</b> The settled search runs rim to
 * rim, which it can because it is laid before any line exists. Here the coastlines are already
 * drawn, and a span ending anywhere but ON one closes nothing - so a link leaves and lands where
 * the exterior coast runs along a cell, exactly as an inlet span does. The rest of a cell's
 * border either faces land or faces water some other line has closed.
 *
 * <p><b>Against what is already down, never over it.</b> The inlet spans were laid first and are
 * on the map; a link is offered across void some of them have already claimed. So they are
 * walls here - a link that merely doubles one is refused, and a link that crosses one is
 * refused - which is also what keeps a link from leaving a shore that faces water rather than
 * the open void: reaching that water means crossing the span that captured it.
 *
 * <p>And a pair those spans already join is not offered at all. Whether it is worth joining two
 * cells is a question about the cells, so it is settled where the pairs are chosen rather than
 * left for the wall test to answer about a line.
 *
 * <p><b>Nor onto a foot one of them is standing on.</b> A link settling on the vertex an inlet
 * span already anchors at draws a fan neither search could see, since each was laid without the
 * other; worse, the boundary walk gives a cell's mouth to one wall only, so one of the two goes
 * unlaid and the water it would have closed never appears. The link is the one that steps
 * along its frontage, the inlet spans having claimed their feet first - see
 * {@link CrowdedAnchors}.
 *
 * <p><b>Not thinned.</b> The formation pass drops spans that share an anchor and hold no water
 * back, which is right for a set tidying one outline and wrong for a set of links: what a link
 * holds is not water but the sector together, and two links leaving one crowded anchor are two
 * routes rather than a fan over one bay. It is also a rule about spans that share an anchor,
 * and a link shares its anchors with the inlet spans already down - so a thinning of this set
 * alone would judge a formation with half of it missing.
 */
public final class IntercontinentalBridges {

    private IntercontinentalBridges() {
    }

    /**
     * Lays a span between every pair of cells on DIFFERENT continents that are near enough to
     * hold the void between them, anchored where each continent's outer coast runs along its
     * cell.
     *
     * @param traced     the continent coasts, as they were traced without bridges
     * @param laid       the spans already down on this trace, which are judged against and
     *                   whose pairs are not offered again
     * @param parameters the knobs the cells are built under
     * @param rules      the knobs the spans are laid under. The thinning is not read: see the
     *                   class note
     * @return the links, narrowest first
     */
    public static List<CellGap> findIntercontinentalBridges(
            Coastlines.TracedCoasts traced,
            List<CellGap> laid,
            SectorGeometryParameters parameters,
            ContinentBridges.BridgeRules rules) {

        // The exterior shore's, because that is the coastline that faces the void between the
        // continents. A lake shore faces water one continent has closed around, and a link
        // anchored there would leave from inside the very shape it is meant to reach out of.
        var runs = CoastFrontages.Shore.EXTERIOR.collectFrontages(traced);
        var frontages = CoastFrontages.gatherFrontagePoints(runs);

        if (frontages.size() < 2) {
            return List.of();
        }

        var continentOf = Coastlines.mapCellsToContinents(traced);
        var joined = gatherJoinedPairs(laid);
        var union = traced.union();
        var sites = union.sites();

        // Read as how far apart the two cells may sit, which across open void is the same claim
        // made of the centres - the settled search's reading, and the one the exterior spans are
        // offered under.
        var reach = parameters.cellRadius() * rules.reachMultiple();
        var offered = new ArrayList<CellGap>();

        // Every pair the gates admit, once. Nothing is refused here for crossing anything: what
        // the offer is depends only on the cells, and which of the offers survive is settled
        // afterwards, in one place, against one rule.
        for (var from : frontages.keySet()) {
            for (var to : frontages.keySet()) {

                if (from >= to
                        || !isTwoContinents(continentOf, from, to)
                        || joined.contains(SpannedCells.buildFromCells(from, to))
                        || Points.computeDistance(sites.get(from), sites.get(to)) > reach) {

                    continue;
                }

                var span = AnchoredSpans.findClosestSpan(
                    from,
                    to,
                    frontages.get(from),
                    frontages.get(to),
                    union);

                if (span != null) {
                    offered.add(span);
                }
            }
        }

        var kept = AnchoredSpans.keepSpansWorthLaying(
            offered, AnchoredSpans.collectCoastWalls(traced), laid, rules.coastSlack());

        // Spread against the spans already down as well as against each other, which is the
        // whole point of doing it here: a link and the inlet span it fans with were laid by two
        // searches, so neither of them could see the crowd on its own. The inlet spans claimed
        // their feet first, so a link is what steps aside - which is right, since the spans it
        // is stepping around were settled before it was offered.
        return List.copyOf(CrowdedAnchors.spreadCrowdedAnchors(
            kept, laid, runs, union, rules.anchorSeparation()));
    }

    // Whether two cells sit on continents that are not the same one, which is the whole of what
    // makes a span between them a link.
    //
    // A cell on no continent joins nothing. The silhouettes name only the cells the outer walk
    // touched, so a cell facing nothing but a lake is absent from them - and it has no frontage
    // on the void to reach out over in the first place.
    private static boolean isTwoContinents(
            Map<Integer, Integer> continentOf,
            int from,
            int to) {

        var one = continentOf.get(from);
        var other = continentOf.get(to);

        return one != null && other != null && !one.equals(other);
    }

    // The cell pairs something has already joined, so that no second line is offered between
    // two cells a line already runs between.
    private static Set<SpannedCells> gatherJoinedPairs(List<CellGap> laid) {

        var joined = new LinkedHashSet<SpannedCells>();

        for (var span : laid) {
            joined.add(SpannedCells.buildFromCells(span.fromSite(), span.toSite()));
        }
        return joined;
    }
}
