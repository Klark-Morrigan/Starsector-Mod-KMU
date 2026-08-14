package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.List;

/**
 * The void a run of bridges closes around, as shapes to draw.
 *
 * <p>Bridges say which void two cells hold between them, but a bridge is a line and a line
 * has nothing to fill. What is wanted is the space a connected run of them shuts in - and at
 * the same quality as {@link VoidPockets} draws its own, which means the same construction
 * rather than an imitation of it.
 *
 * <p><b>The reach is what makes that possible.</b> A pocket in {@code VoidPockets} is not a
 * shape that gets offset; it is defined outright as the points further than some reach from
 * every site, so its outline at any other reach is another trace rather than an offset of the
 * first. That is where its exactness comes from: every corner is where two circles actually
 * cross, and nothing is ever mitred to a guess.
 *
 * <p>A bridged pocket can be defined the same way, because a bridge is only ever a gap that
 * is nearly closed. A corridor of width {@code w} shuts the moment every cell reaches
 * {@code w / 2} further, so the void a run of bridges captures is exactly a hole in the union
 * of the discs at that grown reach. Which is what the four-reach range was always saying: at
 * four reaches apart a whole further cell fits the gap, so half its width is what closing the
 * gap costs.
 *
 * <p>So there is no new geometry here and no offsetting anywhere. A captured pocket is traced
 * at the grown reach, its channel is taken out by tracing again at the grown reach plus the
 * channel, and both are the shared arc walk. Everything that makes the first construction
 * exact holds here for the same reasons.
 *
 * <p><b>This does not work, and the reason is the growth itself.</b> The construction was
 * argued for on the grounds that the shrink it costs would read as deliberate. On screen it
 * does not: the growth is half a bridge's width, which runs to thousands of units against a
 * channel of a hundred and fifty, so every fill sits back from the cells by roughly twelve
 * times the gap the rest of the map uses and reads as a hole in the drawing rather than as a
 * pocket. No sweep or threshold fixes that - the shift is the method.
 *
 * <p>A second fault compounds it. A hole is kept when a bridge midpoint lies inside it, but at
 * the larger growths separate pockets merge into single vast regions that still contain a
 * midpoint, so those are captured too - and the dedupe that was meant to drop a pocket met
 * twice compares a hole's mean boundary point, which for a star-shaped pocket falls outside
 * the pocket, so the same region survives at several growths and is painted over itself.
 *
 * <p>What it has to be instead is the mixed offset: arcs re-reached to {@code r + channel} and
 * the bridge chords shifted perpendicular into the pocket by the channel, with each corner
 * taken as the intersection of the two offset edges meeting there. Arc-to-line and
 * line-to-line intersections are both closed form, so that stays exact and never mitres a
 * corner it cannot see - it keeps the guarantee this shortcut was trying to borrow.
 *
 * <p><b>How far to grow is swept rather than chosen.</b> The first attempt grew by the
 * widest bridge in each connected run of them, on the reasoning that only bridges sharing a
 * pocket bear on each other. That was wrong by a mile: bridges join nearly every cell on the
 * map into one run, so the widest bridge anywhere set the growth everywhere, and a growth of
 * almost a whole reach merged the sector into four pockets.
 *
 * <p>So the growth is swept from nothing up to half the widest bridge, and each pocket is
 * kept from the first step that closes it. That is the least-grown version of it, which is
 * the largest and the truest - a pocket shut by a narrow corridor keeps almost all of its
 * void, and only one shut by a wide one pays for it. Holes shrink as the growth rises, so a
 * pocket met again at a later step sits inside the one already kept and is dropped.
 */
final class VoidBridgePockets {

    // Half, because closing a corridor means each of the two cells either side of it reaching
    // half the distance across.
    private static final double CELLS_PER_GAP = 2;

    // How many growths to try between nothing and enough to close the widest bridge. Enough
    // that a pocket shut by a narrow corridor is not charged for a wide one elsewhere, few
    // enough that the whole sweep stays quick enough to sit behind a slider.
    private static final int GROWTH_STEPS = 8;

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

    /**
     * Finds what the bridges close around, shaped ready to draw.
     *
     * @param sites       the sites
     * @param bridges     the bridges, as {@link VoidBridges} found them
     * @param parameters  the knobs the cells are built under, for the reach the growth starts
     *                    from and the channel every fill gives up
     * @param arcSegments how finely a half-turn of arc is sampled
     * @return one outline per captured pocket, at the reach that leaves the channel, so a
     *         caller can fill them beside the cells with nothing touching
     */
    static List<List<double[]>> findCapturedPockets(
            List<double[]> sites,
            List<CellGaps.CellGap> bridges,
            SectorGeometryParameters parameters,
            int arcSegments) {

        var captured = new ArrayList<List<double[]>>();
        var widest = measureWidestBridge(bridges);

        for (var step = 1; step <= GROWTH_STEPS; step++) {

            var grown = parameters.cellRadius()
                + widest / CELLS_PER_GAP * step / GROWTH_STEPS;

            var atGrown = DiscUnionBoundary.traceHolesAtReach(sites, grown, arcSegments);

            var withChannel = DiscUnionBoundary.traceHolesAtReach(
                sites,
                grown + parameters.borderInset(),
                arcSegments);

            for (var hole : atGrown) {

                // Only holes a bridge shut, and only the first time each is met. Growing the
                // reach closes every corridor narrower than the growth whether a bridge
                // claimed it or not, and a pocket already kept from an earlier step is met
                // again here as a smaller version of itself.
                if (doesAnyBridgeCross(hole, bridges) && !isAlreadyCaptured(captured, hole)) {

                    captured.addAll(DiscUnionBoundary.findHolesInside(withChannel, hole));
                }
            }
        }
        return captured;
    }

    private static double measureWidestBridge(List<CellGaps.CellGap> bridges) {

        var widest = 0.0;

        for (var bridge : bridges) {
            widest = Math.max(widest, bridge.width());
        }
        return widest;
    }

    // Whether anything already kept holds this hole's middle. A pocket met at a later, larger
    // growth is the same pocket shrunk, so it lands inside the version already taken.
    private static boolean isAlreadyCaptured(
            List<List<double[]>> captured,
            VoidHole hole) {

        var middle = Points.computeMean(hole.boundary());

        for (var outline : captured) {

            if (PolygonRegions.isPointInsideRing(outline, middle[0], middle[1])) {
                return true;
            }
        }
        return false;
    }

    // Whether a bridge shut this hole, asked by whether one lies in it. A bridge that closed
    // a hole runs across the mouth it closed, so its midpoint sits inside.
    private static boolean doesAnyBridgeCross(VoidHole hole, List<CellGaps.CellGap> bridges) {

        for (var bridge : bridges) {

            var middleX = (bridge.start()[0] + bridge.end()[0]) / 2;
            var middleY = (bridge.start()[1] + bridge.end()[1]) / 2;

            if (PolygonRegions.isPointInsideRing(hole.boundary(), middleX, middleY)) {
                return true;
            }
        }
        return false;
    }
}
