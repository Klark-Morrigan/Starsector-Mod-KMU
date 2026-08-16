package kmu.maplayers.base.geometry;

import kmlib.math.geometry.DirectedLine;

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
     * @return the pockets, each with a closed outline
     */
    static List<VoidPockets.VoidPocket> findCoastPockets(
            Coastlines.TracedCoasts traced,
            List<double[]> sites,
            List<CellGaps.CellGap> bridges,
            List<String> ownerBySite,
            SectorGeometryParameters parameters,
            VoidSections.SectionRules sectionRules) {

        var reaches = buildCoastWalls(traced);

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

        // Both traces sit at or below the reach the COAST is drawn at, which is the cells'
        // filled border. A wall has to be on the boundary to be laid at all, and the coast
        // line runs along the fills - at any wider reach the discs have swallowed it, the
        // walls are dropped as buried, and the pockets either side merge into one.
        //
        // So the pocket's own extent is what the coast shut in against the fills, and what it
        // comes to once the channel is out is that retraced a channel further out. Its gap
        // against a cell is one channel rather than the two a pocket between cells shows,
        // for the same reason the coast hugs the fill in the first place: the fill has
        // already given up its own channel, and the coast is drawn on what that left.
        var extent = DiscUnionBoundary.traceHolesAcrossWalls(
            new DiscUnion(sites, parameters.measureFilledReach()), walls, arcSegments);

        var withChannel = DiscUnionBoundary.traceHolesAcrossWalls(
            new DiscUnion(sites, parameters.cellRadius()), walls, arcSegments);

        var pockets = new ArrayList<VoidPockets.VoidPocket>();

        for (var hole : extent) {

            // Only what a COAST reach shut in. Void the cells closed unaided, and void a
            // bridge holds, are both the other construction's to report; drawing them here
            // too would paint the same emptiness twice over.
            if (java.util.Collections.disjoint(hole.walledBy(), reaches)) {
                continue;
            }
            pockets.add(VoidPockets.shapeVoidPocket(
                hole,
                DiscUnionBoundary.findHolesInside(withChannel, hole),
                ownerBySite,
                sites,
                sectionRules));
        }
        return pockets;
    }

    // Every straight reach of coast, as the wall it is. A reach runs from a point on one
    // cell's border to a point on another's, so the line through those two points is the line
    // it lies on - and that is all a wall needs to be found again at any reach.
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
                walls.add(new DiscUnionBoundary.Chord(
                    from.circle(),
                    to.circle(),
                    new DirectedLine(
                        from.point()[0],
                        from.point()[1],
                        to.point()[0] - from.point()[0],
                        to.point()[1] - from.point()[1])));
            }
        }
        return walls;
    }
}
