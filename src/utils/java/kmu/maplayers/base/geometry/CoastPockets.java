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
 * {@link VoidPockets.VoidPocket}, because what a pocket is - bound void, shaped and
 * ringed by cells - has nothing to do with what closed it.
 *
 * <p><b>A reach of coast is laid as a wall, exactly as a bridge is.</b> That is the whole of
 * the method. {@link DiscUnionBoundary} already turns a wall and a set of discs into closed
 * cycles, tells a hole from a silhouette by its winding, and gives a hole at any reach asked
 * for; a coast reach is another line to hand it. Every vertex that comes back is a crossing
 * worked out in closed form - circle against circle, or circle against the wall - and the
 * inset is a fresh walk at a moved reach rather than a shape edited afterwards. Against the
 * COAST too: a wall holds its own two sides half a channel off its line, so what the trace
 * hands back is already inset from the reach that closed it. Nothing is cut, and
 * {@link CoastPocketFaults} measures rather than corrects.
 *
 * <p>Which holes are the coast's is read off {@link VoidHole#walledBy}, through
 * {@link WalledVoid}. Bridges and coast reaches are walls of one kind and the trace closes both
 * in one walk, so the wall a hole closes on is the only thing that distinguishes what shut it
 * in.
 */
public final class CoastPockets {

    private CoastPockets() {
    }

    /**
     * Finds every pocket the coast's straight reaches shut in.
     *
     * @param traced       the coast, as it was traced,
     *                     which carries the sites everything here is measured against and the
     *                     bridges it was walled by
     * @param ownerBySite  each site's owner, index-aligned with the coast's own sites and
     *                     null where the site is unowned, to decide which pockets sit inside
     *                     one owner's area rather than between owners
     * @param rules        the knobs to build them under. At its true extent the trace
     *                     itself moves to the cell radius, since a coast pocket takes the
     *                     channel from the discs it is walked against rather than from a
     *                     reach applied afterwards
     * @return the pockets, each with a closed outline and the reaches that walled it
     */
    public static List<WalledPocket> findCoastPockets(
            Coastlines.TracedCoasts traced,
            List<String> ownerBySite,
            VoidPockets.PocketRules rules) {

        var parameters = rules.parameters();

        // The sites come off the coast rather than beside it. Handed in separately, a caller
        // can pair one construction's sites with another's coast and still compile, and every
        // shape built here would then be measured against discs the coast never saw.
        var sites = traced.union().sites();

        var reaches = buildCoastWalls(traced);

        if (reaches.isEmpty()) {
            return List.of();
        }
        // The channel comes from the knobs rather than off the coast's own walls. A coast
        // traced WITHOUT bridges has no walls to read one from - its channel is zero, which
        // is legal only while there are no chords - and the reaches laid here are chords. So
        // reading it off the trace worked for as long as every coast happened to carry
        // bridges, and refused outright for the one that does not.
        //
        // The right source either way: how far a wall holds its two sides back from its line
        // is a property of how walls are DRAWN, and has nothing to do with whether the trace
        // that produced the coast had bridges to lay.
        var walls = layCoastWalls(traced, reaches, parameters.borderInset());

        // ONE trace, and what it hands back is what gets drawn - the same thing
        // VoidBridgePockets does with its own walls. A pocket found among the cells can
        // afford to be traced twice and the two matched up by containment, because a bridge
        // spans a real gap and is still on the boundary at a wider reach. A reach of coast is
        // tangent to the fills and is not, so the two traces lay different walls, their holes
        // do not correspond, and the match silently drops the ones that fail.
        var pockets = new ArrayList<WalledPocket>();

        // Only what the coast's own reaches closed. A hole a bridge holds is the other
        // construction's to report, and one the cells closed unaided is neither's - so the
        // bridges are laid, to divide the void, and not kept on.
        for (var hole : WalledVoid.traceVoidWalledBy(
                sites, walls, reaches, parameters, rules.shaping())) {

            // Which of them, asked again of the hole that survived. The walk's own record, so
            // this cannot disagree with the answer that kept it.
            var walling = WalledVoid.findClosingWalls(hole, reaches);

            // What the trace hands back is what gets drawn. Nothing is cut afterwards.
            //
            // The channel against the reach is not something to correct for: the wall holds
            // its two sides half a channel off its own line, so the outline comes back inset
            // already, and the report's closest-approach reads the channel exactly at both
            // shapings with no cut in the way.
            //
            // A cut against the reach's own half-planes was worse than nothing. A reach is a
            // SEGMENT, and a pocket walled by one reach and a bridge runs past that reach's
            // ends by however far the bridge takes it - which is not out at sea, since there
            // is no coast out there to be seaward of. Held to the slab between one reach's
            // ends, such a pocket was clipped to nothing: a hole found, and a fill missing on
            // the very map that exists to show it.
            //
            // What guards the rule the cut was meant to enforce is a measure, not a clip: no
            // run of any outline lies outside the coast AT ITS OWN REACH, on either fixture, at
            // either shaping. The reach is half the statement - a pocket a channel out is
            // walked against discs a channel wider, and those close straits the cells' own
            // reach leaves open.
            var outline = hole.boundary();

            // A pocket with nothing to draw is still a pocket - it keeps its extent, its span
            // and the cells around it. That is what an empty outline already means here, and
            // dropping the pocket instead would hide the one thing worth knowing: that the
            // channel closed it over.
            pockets.add(new WalledPocket(
                VoidPockets.shapeVoidPocket(
                    hole,
                    outline.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA
                        ? List.of()
                        : List.of(outline),
                    VoidPockets.resolveAbsorbingOwner(hole.ringing(), ownerBySite)),
                walling));
        }
        return pockets;
    }

    /**
     * The coast's reaches laid alongside the bridges the coast itself was walled by, at the
     * channel it was walled at.
     *
     * <p>The bridges have to be there, though nothing here reports what they close: void a
     * bridge already holds is that construction's, and a trace that cannot see the bridge runs
     * a coast pocket straight across it and paints the same emptiness twice. Taken from the
     * coast rather than found again, because a second search is a second answer, and a reach
     * was walked round the bridges the first one found.
     *
     * <p>At the coast's channel for the same reason: a pocket has to give up against a reach
     * exactly what the coast gave up against a bridge.
     *
     * <p>Shared rather than private because a reader asking what the walk did has to ask it of
     * the wall set the walk was given. Laid again elsewhere, the same lines in another order
     * can have a different one of them crowded out of a mouth - so the answer would be about a
     * map nobody drew.
     *
     * @param traced  the coast, which carries the bridges it was walled by
     * @param reaches its own straight reaches, as the walls they are offered as
     * @param channel how far each wall holds its two sides off its own line
     * @return the two sets laid together, at the coast's channel
     */
    static DiscUnionBoundary.Walls layCoastWalls(
            Coastlines.TracedCoasts traced,
            List<DiscUnionBoundary.Chord> reaches,
            double channel) {

        var laid = new ArrayList<>(traced.walls().chords());
        laid.addAll(reaches);

        return new DiscUnionBoundary.Walls(laid, channel);
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
    public static List<String> markEverySiteUnowned(List<double[]> sites) {

        var unowned = new ArrayList<String>(sites.size());

        for (var site = 0; site < sites.size(); site++) {
            unowned.add(null);
        }
        return unowned;
    }

    /**
     * The coast's straight reaches as the walls they are laid as.
     *
     * <p>A reach runs from a point on one cell's border to a point on another's, so the line
     * through those two points is the line it lies on - and that is all a wall needs to be
     * found again at any reach.
     *
     * <p>There is only one coast to take them from, and that is the point: the line the map
     * draws and the line a pocket insets from are the same line, so a fill cannot come to sit
     * outside the border that defines it.
     *
     * <p><b>Fillets are not reaches.</b> A fillet runs along one cell's own border from where
     * the coast arrived to where it leaves, so both its ends sit on the same circle and there
     * is no second circle for a wall to run to; it also shuts nothing in, being boundary
     * already.
     *
     * <p>Shared rather than private because more than one reader wants the same line - what a
     * pocket is bounded by, and what a check asks about - and a second construction of it is
     * a second answer about where the coast runs.
     *
     * @param traced the coast
     * @return one wall per reach, in walk order
     */
    static List<DiscUnionBoundary.Chord> buildCoastWalls(
            Coastlines.TracedCoasts traced) {

        return buildReachWalls(
            Coastlines.collectStraightReaches(traced),
            DiscUnionBoundary.WallKind.COAST_REACH);
    }

    /**
     * Every lake shore's straight reaches as the walls they are laid as.
     *
     * <p>The same line for the same reason as the outer coast's: a lake's pockets are the water
     * inside its shore, and a fill inset from any other line can come to sit outside the border
     * that defines it - or inside the band the border concedes to the cells, which is the same
     * water painted twice.
     *
     * <p>At the channel the knobs state rather than one read off the trace, since a shore
     * carries no walls of its own to read one from.
     *
     * @param traced  the coast, whose lakes carry the shores
     * @param channel the width below which a step between two cells is a handover rather than
     *                a reach
     * @return one wall per reach over every lake, in walk order
     */
    static List<DiscUnionBoundary.Chord> buildLakeShoreWalls(
            Coastlines.TracedCoasts traced,
            double channel) {

        var shores = new ArrayList<Coastlines.Coast>(traced.lakes().size());

        for (var lake : traced.lakes()) {
            shores.add(lake.shore());
        }
        return buildReachWalls(
            Coastlines.collectStraightReaches(shores, channel),
            DiscUnionBoundary.WallKind.LAKE_SHORE);
    }

    // One wall per reach, on the reach's own line. Shared by both kinds of shore because a
    // reach is placed the same way whichever side the water is on; what the kind carries is
    // which side that is.
    private static List<DiscUnionBoundary.Chord> buildReachWalls(
            List<Coastlines.CoastReach> reaches,
            DiscUnionBoundary.WallKind kind) {

        var walls = new ArrayList<DiscUnionBoundary.Chord>();

        for (var reach : reaches) {

            var from = reach.from();
            var to = reach.to();

            var alongX = to.point()[0] - from.point()[0];
            var alongY = to.point()[1] - from.point()[1];

            if (Math.hypot(alongX, alongY) < Limits.MIN_EDGE_LENGTH) {
                continue;
            }

            // The reach's own line, unshifted. A wall already holds each side back by the
            // channel it keeps - that IS the inset against the coast - so nudging the line
            // over as well insets twice, and getting the direction of that nudge wrong
            // cancels the channel instead: the pocket lands exactly on the coast, which is a
            // fill touching the border that defines it.
            //
            // Leaving it out also makes the wall line the COAST line, so anything asking how
            // far a pocket sits off its reach is asking how far it sits off the coast.
            walls.add(new DiscUnionBoundary.Chord(
                from.circle(),
                to.circle(),
                new DirectedLine(from.point()[0], from.point()[1], alongX, alongY),
                kind));
        }
        return walls;
    }
}
