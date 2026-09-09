package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The void the bridges close around, as shapes to draw.
 *
 * <p>A bridge is a line, and a line has nothing to fill. What is wanted is the space a run of
 * them shuts in, drawn to the same standard {@link VoidPockets} draws its own.
 *
 * <p><b>Only a bridge that closes a ring shuts anything in.</b> One whose two cells were
 * already reachable from each other completes a loop, and a loop has an inside; one that
 * joins two things not otherwise connected merely strings them together, and a chain of those
 * encloses no more than a single one does. Everything beyond such a chain is the unbounded
 * outside, so treating every bridge as enclosing fills the ocean in.
 *
 * <p>Those bridges are then handed to {@link DiscUnionBoundary} as chords and traced along
 * with the arcs, rather than cut into a finished outline. The cells' borders and the chords
 * are both boundary, so the shape is what walking them together closes around - and because
 * a chord meets a circle at the one angle facing the other circle, its ends are vertices of
 * that walk by construction. Cutting instead means finding where the chord went on a sampled
 * outline, and finding is what strays.
 *
 * <p>Which piece is void needs no asking either. A cycle that winds against the silhouettes
 * has void inside it, whether it was closed by a chord or by the cells alone, so the bays the
 * bridges wall off and the pockets the cells enclose come out of the same test.
 *
 * <p><b>Nothing here is offset.</b> The arcs come from a trace at the reach that leaves the
 * channel, exactly as the other construction gets its outline, and the bridge keeps the same
 * channel the cells do - one that two neighbouring pockets share, half of it each. Both are
 * the same trace at a different distance rather than a shape pushed outward afterwards, which
 * is why the corners come out where the circles actually cross instead of on a mitre.
 *
 * <p>{@link #measureWorstChordStray} is the check that governs this. A fill can sit perfectly
 * against every cell and still close on a line nowhere near its bridge, so measuring the
 * distance to the cells proves nothing about the part that can go wrong; the distance from
 * each chord end to the outline that should carry it is the number that can fail.
 */
public final class VoidBridgePockets {

    private VoidBridgePockets() {
    }

    /**
     * The bridges that close a loop, which are the only ones that shut any void in.
     *
     * <p>A bridge captures nothing on its own. It captures when its two cells could already
     * be reached from each other - through cells whose discs touch, or through bridges
     * already taken - because only then does adding it complete a ring, and only a ring has
     * an inside. A bridge that joins two things not otherwise connected just strings them
     * together, and a chain of those encloses no more than a single one does.
     *
     * <p>That is the whole of what went wrong before. Every bridge was treated as though it
     * shut something in, so the void beyond a chain of them was taken for a pocket, and the
     * unbounded outside is the largest region there is - the ocean, filled in.
     *
     * <p>Decided by walking the bridges narrowest first and asking, of each, whether its two
     * ends had already met. Narrowest first because that is the order they were chosen in and
     * the tightest ones are the most defensible rings to close.
     *
     * @param sites   the sites
     * @param bridges the bridges, as {@link VoidBridges} found them
     * @param reach   how far a cell reaches, to know which cells already touch
     * @return the bridges that complete a ring, in the order they were offered
     */
    public static List<CellGap> findCapturingBridges(
            List<double[]> sites,
            List<CellGap> bridges,
            double reach) {

        var reachedFrom = new int[sites.size()];

        for (var site = 0; site < sites.size(); site++) {
            reachedFrom[site] = site;
        }

        // Cells whose discs overlap are already one piece before any bridge is laid, so a
        // bridge between two of them closes a ring on its own.
        for (var first = 0; first < sites.size(); first++) {
            for (var second = first + 1; second < sites.size(); second++) {

                if (CellGaps.findGapBetween(sites, first, second, reach) == null) {
                    joinReach(reachedFrom, first, second);
                }
            }
        }

        var capturing = new ArrayList<CellGap>();

        for (var bridge : bridges) {

            if (findReach(reachedFrom, bridge.fromSite())
                    == findReach(reachedFrom, bridge.toSite())) {

                capturing.add(bridge);
                continue;
            }
            joinReach(reachedFrom, bridge.fromSite(), bridge.toSite());
        }
        return capturing;
    }

    /**
     * Finds what the bridges close around, shaped ready to draw.
     *
     * <p>EVERY bridge becomes a chord across the union of the cells' reach discs, and the
     * whole boundary - arcs and chords together - is walked in one pass. Every cycle that
     * comes out winding against the silhouettes has void inside it: the bays the chords wall
     * off, and the pockets the cells enclose without any help from a bridge.
     *
     * <p>Every one, rather than only the ring-closing ones, because the winding is what says
     * whether anything was shut in and it can only say so about boundary that is actually
     * there. A chain of bridges shuts in nothing, and traced it shuts in nothing - it draws a
     * dumbbell that winds like any other silhouette. But leave the chain out and lay only the
     * bridge that closes the ring, and its two ends sit on two silhouettes with nothing drawn
     * between them, so it strings THOSE together instead and the pocket never appears.
     *
     * <p>{@link #findCapturingBridges} is then the independent second opinion rather than a
     * filter: it counts the same rings by walking the graph, so its count and the number of
     * pockets that come back are two different computations of one thing.
     *
     * <p>Traced at the reach that leaves the channel, exactly as the other construction gets
     * its own outline, so nothing here is offset. The bridges keep that same channel: each
     * pocket stops half of one short of the wall, so two pockets meeting across a bridge are
     * held apart by the same gap that holds a pocket off the cells around it, and every
     * section reads as its own shape rather than as part of one mass.
     *
     * @param sites      the sites
     * @param bridges    the bridges, as {@link VoidBridges} found them
     * @param parameters the knobs the cells are built under, which are also what the arcs are
     *                   flattened onto: the bound the cells' own vertices sit at
     * @param shaping    how much of the channel each pocket takes out of its own outline.
     *                   Taken by walking the discs at a moved reach rather than by offsetting
     *                   afterwards, so at its true extent it is the discs that move
     * @return one outline per captured pocket
     */
    public static List<List<double[]>> findCapturedPockets(
            List<double[]> sites,
            List<CellGap> bridges,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        var outlines = new ArrayList<List<double[]>>();

        for (var hole : WalledVoid.traceVoidAcrossWalls(
                sites, buildBridgeWalls(bridges, parameters), parameters, shaping)) {

            outlines.add(hole.boundary());
        }
        return outlines;
    }

    /**
     * Finds what the bridges close around, leaving out the void they had no part in closing.
     *
     * <p>The same walk as {@link #findCapturedPockets}, reported to a caller that draws the
     * cell-enclosed void elsewhere. That walk hands back every hole it finds, because the
     * winding is what says whether anything was shut in and a bridge left out of the walk
     * strings two silhouettes together instead of closing a ring. But a hole the cells closed
     * unaided is not a bridge's - it is a lake or a puddle - and a construction that draws
     * those under their own switches would paint the same water twice and go on painting it
     * with those switches off.
     *
     * <p>Read off {@link VoidHole#walledBy}, which is the walk's own answer about what closed
     * each hole, rather than by matching shapes afterwards.
     *
     * @param sites      the sites
     * @param bridges    the bridges, as {@link VoidBridges} found them
     * @param parameters the knobs the cells are built under
     * @param shaping    how much of the channel each pocket takes out of its own outline
     * @return one outline per pocket a bridge helped close
     */
    public static List<List<double[]>> findBridgeWalledPockets(
            List<double[]> sites,
            List<CellGap> bridges,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        var outlines = new ArrayList<List<double[]>>();

        for (var hole : findBridgeWalledHoles(sites, bridges, parameters, shaping)) {
            outlines.add(hole.boundary());
        }
        return outlines;
    }

    /**
     * The same water as {@link #findBridgeWalledPockets}, as the walk found it rather than as
     * an outline to fill.
     *
     * <p>What a caller wants when the question is not what to draw: which cells the water runs
     * against and which bridges closed it are the walk's own record, and both are gone by the
     * time a pocket is an outline. Recovered from the outline afterwards they would be a
     * second opinion arrived at by testing points against lines - which is the finding that
     * strays, and the reason the walk records them in the first place.
     *
     * @param sites      the sites
     * @param bridges    the bridges, as {@link VoidBridges} found them
     * @param parameters the knobs the cells are built under
     * @param shaping    how much of the channel each pocket gives up against the cells
     * @return one hole per pocket a bridge helped close
     */
    public static List<VoidHole> findBridgeWalledHoles(
            List<double[]> sites,
            List<CellGap> bridges,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        // The same chord instances the walk was handed, so a hole's walls are asked about by
        // the walls themselves rather than by a set built to look like them.
        var walls = buildBridgeWalls(bridges, parameters);

        // Everything laid is also what is kept on: the bridges are the only walls here, so a
        // hole no bridge closed is one the cells closed unaided.
        return WalledVoid.traceVoidWalledBy(
            sites, walls, walls.chords(), parameters, shaping);
    }

    /**
     * The bridges that end up drawn as walls, which is fewer than are offered.
     *
     * <p>A bridge drops out when its mouth is buried inside another disc - its two cells have
     * closed over at the drawn reach and there is no gap left to wall - or when a bridge
     * taken earlier already holds that mouth, two leaving one cell within a channel of each
     * other. Worth counting rather than inferring: it is the difference between the rings the
     * graph says are there and the pockets the trace hands back.
     *
     * @param sites      the sites
     * @param bridges    the bridges, as {@link VoidBridges} found them
     * @param parameters the knobs the cells are built under
     * @return the chords actually laid, in the order the bridges were offered
     */
    public static List<DiscUnionBoundary.Chord> findLaidChords(
            List<double[]> sites,
            List<CellGap> bridges,
            SectorGeometryParameters parameters) {

        return DiscUnionBoundary.findAttachableChords(
            VoidPockets.buildDrawnUnion(sites, parameters),
            buildBridgeWalls(bridges, parameters));
    }

    /**
     * How far the worst captured outline strays from the bridge it should close on.
     *
     * <p>The check the earlier attempts lacked. Both measured how far a fill sat from the
     * CELLS, which was never what was broken - a fill can sit perfectly against every cell
     * and still close on a line nowhere near its bridge, which is what happened. This asks
     * the question that can actually fail.
     *
     * <p>Two filters, and both are needed. Only chords that were actually LAID are looked
     * for - one whose mouth is buried inside another disc, or crowded out by a chord that
     * got there first, was never traced, so demanding an outline carry it would fail the
     * construction for doing the right thing. And only chords that close a RING, because a
     * chain of them bounds a silhouette rather than a pocket and there is no fill for its
     * ends to be on.
     *
     * <p>The laid set is worked out over every bridge, exactly as the trace works it out, and
     * the ring-closing ones are picked out of THAT. Filtering the other way round would offer
     * a shorter list to the greedy pass, which could then lay a chord the trace had dropped.
     *
     * <p>Asked of a chord's better SIDE, not of both. Once a bridge keeps a channel it is two
     * lines, and only one of them has a pocket against it - the other faces the cells, whose
     * silhouette is not a fill and is not drawn. Demanding both would fail every chord by
     * exactly the width of the channel.
     *
     * @param captured   what {@link #findCapturedPockets} handed back
     * @param sites      the sites
     * @param bridges    the bridges, as {@link VoidBridges} found them
     * @param parameters the knobs the cells are built under
     * @return the largest distance from any chord end to the nearest vertex of any captured
     *         outline, which is zero when every fill closes on its own bridge
     */
    public static double measureWorstChordStray(
            List<List<double[]>> captured,
            List<double[]> sites,
            List<CellGap> bridges,
            SectorGeometryParameters parameters) {

        var union = VoidPockets.buildDrawnUnion(sites, parameters);
        var capturing = Set.copyOf(DiscUnionBoundary.buildChordsFrom(
            findCapturingBridges(sites, bridges, parameters.cellRadius())));

        var worst = 0.0;

        for (var chord : findLaidChords(sites, bridges, parameters)) {

            if (!capturing.contains(chord)) {
                continue;
            }
            var bestSide = Double.MAX_VALUE;

            for (var side : DiscUnionBoundary.findChordSides(
                    union, chord, buildBridgeWalls(bridges, parameters))) {

                bestSide = Math.min(bestSide, measureWorstStrayOnSide(captured, side));
            }
            worst = Math.max(worst, bestSide);
        }
        return worst;
    }

    // How far the further of one side's two ends sits from the outline that should carry it.
    // The further of the two rather than the nearer, because a line that landed one end right
    // and the other end nowhere has not landed.
    private static double measureWorstStrayOnSide(
            List<List<double[]>> captured,
            List<double[]> side) {

        var worst = 0.0;

        for (var end : side) {
            worst = Math.max(worst, measureDistanceToNearestVertex(captured, end));
        }
        return worst;
    }

    /**
     * The walls the bridges become.
     *
     * <p>The channel is the same border inset the cells keep, so two pockets meeting across a
     * bridge are held apart by the same gap that holds a pocket off the cells around it.
     *
     * <p>Shared rather than private because what the walk did with these walls is a question
     * asked from outside, and the walk answers about the walls it was handed - so a second
     * build of the same lines is a set of walls no verdict can be about.
     *
     * @param bridges    the bridges, as {@link VoidBridges} found them
     * @param parameters the knobs the cells are built under
     * @return the walls, at the channel a pocket keeps against them
     */
    public static DiscUnionBoundary.Walls buildBridgeWalls(
            List<CellGap> bridges,
            SectorGeometryParameters parameters) {

        return new DiscUnionBoundary.Walls(
            DiscUnionBoundary.buildChordsFrom(bridges), parameters.borderInset());
    }

    // How far a point sits from the nearest vertex of any outline. What a bridge's drawn end
    // is measured against: the fill is a run of points, so "the fill reaches this end" means
    // one of those points is at it.
    private static double measureDistanceToNearestVertex(
            List<List<double[]>> outlines,
            double[] point) {

        var nearest = Double.MAX_VALUE;

        for (var outline : outlines) {
            for (var vertex : outline) {

                nearest = Math.min(nearest, Points.computeDistance(point, vertex));
            }
        }
        return nearest == Double.MAX_VALUE ? 0 : nearest;
    }

    private static int findReach(int[] reachedFrom, int site) {

        var root = site;

        while (reachedFrom[root] != root) {
            root = reachedFrom[root];
        }

        // Flattened on the way back, so a long chain of cells is walked once rather than once
        // per question asked of it.
        var walked = site;

        while (reachedFrom[walked] != root) {

            var next = reachedFrom[walked];
            reachedFrom[walked] = root;
            walked = next;
        }
        return root;
    }

    private static void joinReach(int[] reachedFrom, int first, int second) {
        reachedFrom[findReach(reachedFrom, first)] = findReach(reachedFrom, second);
    }
}
