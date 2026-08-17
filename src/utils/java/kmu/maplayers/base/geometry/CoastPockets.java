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
 * inset against the cells is a fresh walk at a moved reach rather than a shape edited
 * afterwards. Against the COAST it is a cut, because a reach is a line and a line does
 * not move when a reach does - see {@link CoastPocketFaults}.
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
     * @param traced       the coast, as {@link Coastlines#traceSectorCoasts} handed it back,
     *                     which carries the sites everything here is measured against and the
     *                     bridges it was walled by
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
            List<String> ownerBySite,
            SectorGeometryParameters parameters,
            VoidSections.SectionRules sectionRules) {

        // The sites come off the coast rather than beside it. Handed in separately, a caller
        // can pair one construction's sites with another's coast and still compile, and every
        // shape built here would then be measured against discs the coast never saw.
        var sites = traced.union().sites();

        var reaches = buildCoastWalls(traced);

        if (reaches.isEmpty()) {
            return List.of();
        }

        // The coast's own bridges are laid alongside its reaches, though nothing here reports
        // what they close. Void a bridge already holds is that construction's, and a trace
        // that cannot see the bridge runs a coast pocket straight across it and paints the
        // same emptiness twice.
        //
        // Taken from the coast rather than found again. A second search is a second answer,
        // and a reach was walked round bridges that the walk it is laid beside cannot see.
        var laid = new ArrayList<>(traced.walls().chords());
        laid.addAll(reaches);

        // At the channel the coast was walled at, for the same reason: the pocket has to
        // give up against a reach exactly what the coast gave up against a bridge.
        var channel = traced.walls().channel();
        var walls = new DiscUnionBoundary.Walls(laid, channel);
        var arcSegments = parameters.measureArcSegments();

        // ONE trace, and what it hands back is what gets drawn - the same thing
        // VoidBridgePockets does with its own walls. A pocket found among the cells can
        // afford to be traced twice and the two matched up by containment, because a bridge
        // spans a real gap and is still on the boundary at a wider reach. A reach of coast is
        // tangent to the fills and is not, so the two traces lay different walls, their holes
        // do not correspond, and the match silently drops the ones that fail - which is what
        // left pockets on the map with nothing drawn in them.
        //
        // A reach is drawn on the cells' own border while the trace runs a channel outside
        // it, so a reach cuts into each of its cells rather than touching them. That is what
        // opens its mouths at all, and also what buries the ones whose cells have grown over
        // where the coast left them.
        var pockets = new ArrayList<CoastPocketFaults.WalledPocket>();

        var union = VoidPockets.buildDrawnUnion(sites, parameters);

        for (var hole : DiscUnionBoundary.traceHolesAcrossWalls(union, walls, arcSegments)) {

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

            // Held to the landward side of every reach that closed it, before the fill is
            // measured from it. A reach has open sea beyond it, so an outline that has
            // strayed there is claiming void that is not the pocket's - and where the wall
            // ended up is the result of a chain of angles, while the side of the line it has
            // to stay on is one fact that holds whatever the wall did.
            var legal = CoastPocketFaults.cutToLandward(
                hole.boundary(), walling, sites, channel);

            // A pocket the cut leaves nothing of is still a pocket - it keeps its extent,
            // its span and the cells around it, and only loses what there was to draw. That
            // is exactly what the primitive already says an empty outline means, and dropping
            // it instead would hide the one thing worth knowing: that the cut emptied it.
            pockets.add(new CoastPocketFaults.WalledPocket(
                VoidPockets.shapeVoidPocket(
                    hole,
                    legal.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA
                        ? List.of()
                        : List.of(legal),
                    VoidPockets.resolveAbsorbingOwner(hole.ringing(), ownerBySite),
                    sites,
                    sectionRules),
                List.copyOf(walling)));
        }
        return pockets;
    }

    /**
     * Every site taken as unowned, for a caller asking about the shapes rather than the map.
     *
     * <p>Whether one owner rings a pocket decides if it is pushed out to meet that owner's
     * fills instead of holding back a channel, so a report or a drawing meant to show the
     * GEOMETRY has to say no-one owns anything or its shapes move with a colouring.
     *
     * @param sites the sites
     * @return one null per site
     */
    static List<String> markEverySiteUnowned(List<double[]> sites) {

        var unowned = new ArrayList<String>(sites.size());

        for (var site = 0; site < sites.size(); site++) {
            unowned.add(null);
        }
        return unowned;
    }

    // Every straight reach of coast, as the wall it is. A reach runs from a point on one
    // cell's border to a point on another's, so the line through those two points is the line
    // it lies on - and that is all a wall needs to be found again at any reach.
    //
    // There is only one coast to take them from, and that is the point: the line the map
    // draws and the line a pocket insets from are the same line, so a fill cannot come to sit
    // outside the border that defines it.
    //
    // Fillets are not reaches. A fillet runs along one cell's own border from where the coast
    // arrived to where it leaves, so both its ends sit on the same circle and there is no
    // second circle for a wall to run to; it also shuts nothing in, being boundary already.
    private static List<DiscUnionBoundary.Chord> buildCoastWalls(
            Coastlines.TracedCoasts traced) {

        var walls = new ArrayList<DiscUnionBoundary.Chord>();

        for (var coast : traced.coasts()) {
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

                // The reach's own line, unshifted. A wall already holds each side back by
                // the channel it keeps - that IS the inset against the coast - so nudging the
                // line over as well insets twice, and getting the direction of that nudge
                // wrong cancels the channel instead: the pocket lands exactly on the coast,
                // which is a fill touching the border that defines it.
                //
                // Leaving it out also makes the wall line the COAST line, so anything asking
                // how far a pocket sits off its reach is asking how far it sits off the coast.
                // Shifted, that question had a different answer from the one worth knowing,
                // and reported a healthy channel while the fill sat on the line.
                walls.add(new DiscUnionBoundary.Chord(
                    from.circle(),
                    to.circle(),
                    new DirectedLine(
                        from.point()[0], from.point()[1], alongX, alongY)));
            }
        }
        return walls;
    }
}
