package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.List;

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
 * channel, exactly as the other construction gets its outline. The chord takes no channel of
 * its own: a channel separates a fill from what lies across it, and what lies across a bridge
 * is the same void rather than another fill, so insetting there would open a gap against
 * nothing.
 *
 * <p>{@link #measureWorstChordStray} is the check that governs this. A fill can sit perfectly
 * against every cell and still close on a line nowhere near its bridge, so measuring the
 * distance to the cells proves nothing about the part that can go wrong; the distance from
 * each chord end to the outline that should carry it is the number that can fail.
 */
final class VoidBridgePockets {

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
    static List<CellGaps.CellGap> findCapturingBridges(
            List<double[]> sites,
            List<CellGaps.CellGap> bridges,
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

        var capturing = new ArrayList<CellGaps.CellGap>();

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
     * its own outline, so nothing here is offset. The chord takes no channel of its own,
     * because a channel separates a fill from what lies across it and what lies across a
     * bridge is the same void, not another fill. Insetting there would open a gap against
     * nothing.
     *
     * @param sites       the sites
     * @param bridges     the bridges, as {@link VoidBridges} found them
     * @param parameters  the knobs the cells are built under
     * @param arcSegments how finely a half-turn of arc is sampled
     * @return one outline per captured pocket, at the reach that leaves the channel
     */
    static List<List<double[]>> findCapturedPockets(
            List<double[]> sites,
            List<CellGaps.CellGap> bridges,
            SectorGeometryParameters parameters,
            int arcSegments) {

        var outlines = new ArrayList<List<double[]>>();

        for (var hole : DiscUnionBoundary.traceHolesAcrossChords(
                sites,
                parameters.measureDrawnReach(),
                arcSegments,
                buildChords(bridges))) {

            outlines.add(hole.boundary());
        }
        return outlines;
    }

    /**
     * How far the worst captured outline strays from the bridge it should close on.
     *
     * <p>The check the earlier attempts lacked. Both measured how far a fill sat from the
     * CELLS, which was never what was broken - a fill can sit perfectly against every cell
     * and still close on a line nowhere near its bridge, which is what happened. This asks
     * the question that can actually fail.
     *
     * <p>Only the chords that could be laid are looked for. One whose end is buried inside
     * another disc is not on the boundary and was never traced, so demanding an outline
     * carry it would fail the construction for doing the right thing.
     *
     * @param captured   what {@link #findCapturedPockets} handed back
     * @param sites      the sites
     * @param bridges    the bridges, as {@link VoidBridges} found them
     * @param parameters the knobs the cells are built under
     * @return the largest distance from any chord end to the nearest vertex of any captured
     *         outline, which is zero when every fill closes on its own bridge
     */
    static double measureWorstChordStray(
            List<List<double[]>> captured,
            List<double[]> sites,
            List<CellGaps.CellGap> bridges,
            SectorGeometryParameters parameters) {

        var drawnReach = parameters.measureDrawnReach();
        var worst = 0.0;

        for (var chord : DiscUnionBoundary.findAttachableChords(
                sites,
                drawnReach,
                buildChords(findCapturingBridges(sites, bridges, parameters.cellRadius())))) {

            for (var end : List.of(
                    DiscUnionBoundary.findChordEnd(
                        sites, drawnReach, chord.fromCircle(), chord.toCircle()),
                    DiscUnionBoundary.findChordEnd(
                        sites, drawnReach, chord.toCircle(), chord.fromCircle()))) {

                worst = Math.max(worst, measureDistanceToNearestVertex(captured, end));
            }
        }
        return worst;
    }

    // The bridges as the chords they become on the boundary: the pair of cells and nothing
    // else, because a chord's ends are fixed by which circles it runs between.
    private static List<DiscUnionBoundary.Chord> buildChords(List<CellGaps.CellGap> bridges) {

        var chords = new ArrayList<DiscUnionBoundary.Chord>();

        for (var bridge : bridges) {
            chords.add(new DiscUnionBoundary.Chord(bridge.fromSite(), bridge.toSite()));
        }
        return chords;
    }

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
