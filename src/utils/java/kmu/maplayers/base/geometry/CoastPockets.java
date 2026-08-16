package kmu.maplayers.base.geometry;

import kmlib.math.geometry.DirectedLine;
import kmlib.math.geometry.Limits;

import java.util.ArrayList;
import java.util.List;

/**
 * The void a smoothed coast shuts in behind it, as pockets to draw.
 *
 * <p>The second way of arriving at a void pocket, beside {@link VoidPockets}. That one starts
 * from the cells and finds the void they closed around; this one starts from the lines the
 * smoothing drew and finds the void those lines cut off. They come back as the same
 * {@link VoidPockets.VoidPocket}, because what a pocket is - bound void, shaped, sectioned,
 * ringed by cells - has nothing to do with what closed it.
 *
 * <p><b>A reach of coast is laid as a wall, exactly as a bridge is.</b> That is the whole of
 * the method. {@link DiscUnionBoundary} already turns a wall and a set of discs into closed
 * cycles, tells a hole from a silhouette by its winding, and gives a hole at any reach asked
 * for; a coast reach is another line to hand it. Every vertex that comes back is a crossing
 * worked out in closed form - circle against circle, or circle against the wall - and the
 * inset is a fresh walk at a moved reach rather than a shape edited afterwards.
 *
 * <p>An earlier attempt built the ring here instead, by sampling the border the reach bypassed
 * and dropping the samples the channel had eaten. Dropping points from a ring does not shrink
 * it, it short-circuits it: the survivors either side of a dropped run get joined by a chord
 * straight across whatever was between them. Pinching, closing over, and a wall meeting a cell
 * at a shallow angle all came out wrong, and each would have needed its own special case.
 * Laid as a wall, none of them is a case at all.
 *
 * <p>Which holes are the coast's is read off {@link VoidHole#walledBy}. Bridges and coast
 * reaches are walls of one kind and the trace closes both in one walk, so the wall a hole
 * closes on is the only thing that distinguishes what shut it in.
 */
final class CoastPockets {

    private CoastPockets() {
    }

    /**
     * Finds every pocket the coast's straight reaches shut in.
     *
     * @param traced       the coast, as {@link Coastlines#traceSectorCoasts} handed it back
     * @param sites        the sites
     * @param bridges      the bridges, laid alongside the coast's own reaches so a pocket
     *                     here cannot claim void a bridge already holds
     * @param ownerBySite  each site's owner, index-aligned with {@code sites} and null where
     *                     the site is unowned, to decide which pockets sit inside one owner's
     *                     area rather than between owners
     * @param parameters   the knobs the cells are built under
     * @param sectionRules how long a piece of a pocket should be before it is cut into more
     *                     than one, and how narrow a crossing has to be to count as a place
     *                     to cut it
     * @return the pockets, each with a closed outline and the reaches that walled it
     */
    static List<CoastPocketFaults.WalledPocket> findCoastPockets(
            Coastlines.TracedCoasts traced,
            List<double[]> sites,
            List<CellGaps.CellGap> bridges,
            List<String> ownerBySite,
            SectorGeometryParameters parameters,
            VoidSections.SectionRules sectionRules) {

        var reaches = buildCoastWalls(traced, parameters.borderInset());

        if (reaches.isEmpty()) {
            return List.of();
        }

        // The bridges are laid alongside, though nothing here reports what they close. Void a
        // bridge already holds is that construction's, and a trace that cannot see the bridge
        // runs a coast pocket straight across it and paints the same emptiness twice.
        var laid = new ArrayList<>(DiscUnionBoundary.buildChordsFrom(bridges));
        laid.addAll(reaches);


        var walls = new DiscUnionBoundary.Walls(laid, parameters.borderInset());
        var arcSegments = parameters.measureArcSegments();

        // ONE trace, and what it hands back is what gets drawn - the same thing
        // VoidBridgePockets does with its own walls. A pocket found among the cells can
        // afford to be traced twice and the two matched up by containment, because a bridge
        // spans a real gap and is still on the boundary at a wider reach. A reach of coast is
        // tangent to the fills and is not, so the two traces lay different walls, their holes
        // do not correspond, and the match silently drops the ones that fail - which is what
        // left pockets on the map with nothing drawn in them.
        //
        // At the reach that DEFINES void, which is as wide as a coast wall survives: the
        // coast is drawn on the cells' fills, so a channel further out the discs have
        // swallowed it and the wall is dropped as buried. The channel comes out at the
        // mouths, where every wall keeps one, and off the cells by the reach itself.
        var pockets = new ArrayList<CoastPocketFaults.WalledPocket>();

        for (var hole : DiscUnionBoundary.traceHolesAcrossWalls(
                new DiscUnion(sites, parameters.measureDrawnReach()), walls, arcSegments)) {

            // Only what a COAST reach shut in. Void the cells closed unaided, and void a
            // bridge holds, are both the other construction's to report; drawing them here
            // too would paint the same emptiness twice over.
            if (java.util.Collections.disjoint(hole.walledBy(), reaches)) {
                continue;
            }
            // Its own outline, because this trace already IS the shaped one - the channel
            // came out of it at the mouths and out of the reach against the cells.
            var walling = new ArrayList<>(hole.walledBy());
            walling.retainAll(reaches);

            pockets.add(new CoastPocketFaults.WalledPocket(
                VoidPockets.shapeVoidPocket(
                    hole, List.of(hole.boundary()), ownerBySite, sites, sectionRules),
                List.copyOf(walling)));
        }
        return pockets;
    }

    // Every straight reach of coast, as the wall it is. A reach runs from a point on one
    // cell's border to a point on another's, so the line through those two points is the line
    // it lies on - and that is all a wall needs to be found again at any reach.
    //
    // Taken from the coast on the cells' TRUE borders, not the drawn one. The drawn coast
    // hugs the fills, a channel inside those borders, so its reaches cut into both cells they
    // run between rather than touching them - and a wall laid on that line closes the wrong
    // shape by a channel everywhere.
    //
    // Fillets are not reaches. A fillet runs along one cell's own border from where the coast
    // arrived to where it leaves, so both its ends sit on the same circle and there is no
    // second circle for a wall to run to; it also shuts nothing in, being boundary already.
    private static List<DiscUnionBoundary.Chord> buildCoastWalls(
            Coastlines.TracedCoasts traced,
            double inset) {

        var walls = new ArrayList<DiscUnionBoundary.Chord>();

        for (var coast : traced.onBorders()) {
            for (var index = 0; index < coast.size(); index++) {

                var from = coast.get(index);
                var to = coast.get((index + 1) % coast.size());

                if (from.circle() == to.circle()) {
                    continue;
                }
                var alongX = to.point()[0] - from.point()[0];
                var alongY = to.point()[1] - from.point()[1];
                var length = Math.hypot(alongX, alongY);

                if (length < Limits.MIN_EDGE_LENGTH) {
                    continue;
                }

                // Pulled in by the channel, towards the cells the reach runs between. The
                // reach is bedrock - it is where the coast IS - and a fill has to hold back
                // from bedrock the same way it holds back from a cell. The cells' own side of
                // that is the reach this is traced at; this is the line's side of it.
                var inX = alongY / length * inset;
                var inY = -alongX / length * inset;

                var centre = traced.union().sites().get(from.circle());
                var side = (centre[0] - from.point()[0]) * inX
                    + (centre[1] - from.point()[1]) * inY < 0 ? -1 : 1;

                walls.add(new DiscUnionBoundary.Chord(
                    from.circle(),
                    to.circle(),
                    new DirectedLine(
                        from.point()[0] + inX * side,
                        from.point()[1] + inY * side,
                        alongX,
                        alongY)));
            }
        }
        return walls;
    }
}
