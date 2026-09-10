package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;

import java.util.ArrayList;
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
 * outline, and the water it closes off is water that outline currently reaches into. A span to
 * another continent is a different act entirely - joining two shapes rather than tidying one -
 * and it is laid by {@link IntercontinentalBridges}, after this, against what this left down.
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
 * cell-pair search reaches rim to rim along the line between two cells' centres, which is the
 * same statement made where there is no coastline to sit on yet.
 *
 * <p>Anchored on points the traced line already carries, rather than on places computed along
 * a cell's arc. An anchor has to sit on the drawn line and on its own cell's rim at once, and
 * the two are the same places only at the line's vertices: the drawn coast is a chord
 * approximation of the rim, so a place between two vertices lies inside the cell, and a place on
 * the rim between them lies off the drawn line. Neither is free - a foot inside its own cell
 * anchors a span the clearance test refuses, and a foot off the line is not on the thing every
 * reader measures against. The anchor is therefore quantised to the sampling, a fraction of a
 * cell radius, and in exchange no tolerance stands between the traced line and this.
 *
 * <p>The ROUNDED line the map draws is not the anchor's home either: rounding cuts every corner
 * off the vertices, so it is presentation, and everything this pass measures - anchors and walls
 * alike - lives on the traced line the rounding started from.
 *
 * <p><b>The shortest such span, per pair.</b> A cell facing the void more than once offers a
 * frontage per face, so two cells can be joined several ways; the one taken is the shortest,
 * which is where the two actually face each other across the gap.
 *
 * <p><b>And no two of them stand on one point.</b> A cell offers a stretch of coast to anchor
 * on, and where several spans settle on the same vertex of it the map shows a fan - which is
 * not a feature of the void but of where the search happened to look. Each is stepped along its
 * own frontage until it has a foot to itself, and only a crowd that cannot be separated that
 * way is thinned instead; see {@link CrowdedAnchors} and {@link SpanFormations}.
 *
 * <p><b>No span crosses one already laid.</b> Taken shortest first, each is kept only if it
 * clears what is already down, so the tighter claim on a stretch of void stands and the
 * looser gives way. What that leaves is a tree - at most one route between any two places,
 * and the water they enclose one shape with fingers - which is what the cell-pair search does.
 *
 * <p>Which is why the frontage floor matters to this. Every stretch the coast passes through
 * is another frontage and another set of spans, so a coast kept at every sliver offers spans
 * too fine to read. Raising the floor coarsens the coast and the spans with it.
 */
public final class ContinentBridges {

    // Nothing already stands on the map when these are laid: they are the first spans of the
    // construction, and the coastlines they are judged against are walls rather than spans.
    private static final List<CellGap> NOTHING_ALREADY_LAID = List.of();

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
     * @param anchorSeparation   how close two spans' feet may stand before one of them moves
     *                           along its frontage, and how much room it reaches for when it
     *                           does, in map units. One number for both, since a fan is exactly
     *                           a foot whose nearest neighbour is inside it. Non-positive leaves
     *                           every foot where the search put it
     */
    public record BridgeRules(
        double reachMultiple,
        double coastSlack,
        boolean shouldThinFormations,
        double anchorSeparation) {
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
     * @return the spans. Chosen narrowest first, which is what decides them; the widths they
     *         carry are measured after their feet have settled, so the order is the order they
     *         were judged in rather than a sort of what came out
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
        var coastWalls = AnchoredSpans.collectCoastWalls(traced);
        var reach = parameters.cellRadius() * rules.reachMultiple();
        var offerDistance = resolveOfferDistance(shore, reach, parameters.cellRadius());
        var offered = new ArrayList<CellGap>();

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

                var span = AnchoredSpans.findClosestSpan(
                    from,
                    to,
                    frontages.get(from),
                    frontages.get(to),
                    union);

                if (span != null && isSpanWithinReach(shore, span, reach)) {
                    offered.add(span);
                }
            }
        }

        var kept = AnchoredSpans.keepSpansWorthLaying(
            offered, coastWalls, NOTHING_ALREADY_LAID, rules.coastSlack());

        // Spread, then thinned, so every reader of this method's answer sees the same resolved
        // set: a formation left for a consumer to tidy is a formation two consumers tidy
        // differently. Switched off, what comes back is the laying as it stands - which is the
        // only way to see what either pass is doing, since a span each of them touches is moved
        // or gone rather than marked.
        //
        // In that order because the two answer one crowded anchor differently and moving is the
        // lesser remedy: a span given a foot of its own is still on the map, while a span
        // dropped for sharing one is a piece of void nothing holds. So the crowd is spread
        // first, and the thinning is left with the formations that could not be separated - a
        // frontage of one point, or one whose every place is taken or would put the line across
        // another span. Thinned first, spans were being dropped that had somewhere to stand.
        var spread = CrowdedAnchors.spreadCrowdedAnchors(
            kept,
            NOTHING_ALREADY_LAID,
            shore.collectFrontages(traced),
            union,
            rules.anchorSeparation());

        return List.copyOf(rules.shouldThinFormations()
            ? SpanFormations.resolveSharedAnchors(spread, traced, parameters)
            : spread);
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

        var continentOf = Coastlines.mapCellsToContinents(traced);

        // A span exists to round ONE outline up, so both its ends have to be corners of that
        // outline: the water it closes off is water that outline currently runs into. Two cells
        // on different continents have no shared inlet to capture - a line between them would
        // be joining two shapes rather than tidying either.
        return (from, to) ->
            Coastlines.compareShapesOf(continentOf, from, to).isSameShape();
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

}
