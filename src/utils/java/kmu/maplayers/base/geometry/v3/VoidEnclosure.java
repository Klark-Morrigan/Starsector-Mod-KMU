package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.DiscUnion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Which pieces of void a sector's walls shut in, found by trying to get out of them.
 *
 * <p>Answers the same question the boundary walk answers, and answers it the way a reader
 * checks it: start in the water and see whether open sea can be reached. What that buys is
 * independence from how the walls were LAID. The walk lays each wall as a chord and gives
 * every mouth on a cell to one chord only, so where two walls meet at a point - which is
 * exactly what a shared bridge anchor is - one of them is refused and the region it would
 * have closed never appears. A flood does not care: a wall it cannot step over stops it
 * whether or not any walk was willing to lay that wall.
 *
 * <p>Walks a grid rather than solving the geometry, which makes the stride a claim in its own
 * right. A flood cannot pass a gap narrower than its own step, so too coarse a stride reports
 * water shut in behind a passage it merely stepped over - and that is the dangerous direction
 * here, since a span between two regions wrongly believed enclosed is a span something will
 * happily drop. The stride belongs well under the narrowest gap that ought to count as open.
 *
 * <p>Barriers are stamped a square thick, so a wall blocks the flood along its whole length
 * rather than only where it happens to cross a square's centre. Those squares belong to no
 * region, which puts a stride's worth of no-man's-land either side of every wall - the same
 * shape of allowance the channel already makes for a drawn pocket.
 */
public final class VoidEnclosure {

    /**
     * The answer for a point the flood reached, or one buried in land or wall: it is not held
     * by anything, so nothing that would release it can be doing harm.
     */
    public static final int OPEN_VOID = -1;

    /**
     * The answer for a point buried in a cell or in a wall's own square: not water at all, so
     * it says nothing either way about what is held. A caller probing beside a wall has to
     * tell this from {@link #OPEN_VOID} - one means "I looked and found the sea", the other
     * means "I never got out of the wall", and reading the second as the first is how a span
     * with water on both sides comes to look as though it faced open water.
     */
    public static final int INSIDE_WALL = -2;

    // Room beyond the outermost cell for open sea to reach the border of the grid and be
    // recognised as open. One reach clears every disc and every wall, since both are drawn
    // from the sites; the strides past that are what the flood needs to walk round the land.
    private static final int MARGIN_IN_STRIDES = 2;

    // The four squares a flood steps to from any one.
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    // How finely a wall is walked when stamping it, as a share of the stride. Half a square
    // at a time, so no square a wall passes through is stepped over.
    private static final double STAMPS_PER_STRIDE = 2;

    private static final byte OPEN_SQUARE = 0;
    private static final byte BLOCKED_SQUARE = 1;
    private static final byte REACHED_SQUARE = 2;

    private final WalkedGrid grid;
    private final byte[] squares;
    private final int[] regionAt;
    private final double[] areas;

    private VoidEnclosure(WalkedGrid grid, byte[] squares, int[] regionAt, double[] areas) {

        this.grid = grid;
        this.squares = squares;
        this.regionAt = regionAt;
        this.areas = areas;
    }

    /**
     * The squares the flood walked, as the four numbers that only mean anything together: a
     * square's place is read out of all of them at once, and three of the four paired with a
     * stride from somewhere else describes a grid nothing was ever walked on.
     *
     * @param walked what the grid spans, in map units
     * @param stride how far one square is across
     * @param across how many squares wide it is
     * @param up     how many squares tall
     */
    private record WalkedGrid(Bounds walked, double stride, int across, int up) {

        static WalkedGrid over(Bounds walked, double stride) {

            return new WalkedGrid(
                walked,
                stride,
                (int) Math.ceil((walked.maxX() - walked.minX()) / stride) + 1,
                (int) Math.ceil((walked.maxY() - walked.minY()) / stride) + 1);
        }

        int countSquares() {
            return across * up;
        }
    }

    /**
     * Maps every piece of void the walls hold in.
     *
     * @param union    the discs the cells cover, at the reach that defines void
     * @param barriers the walls, each as the two points of one straight run. Whatever the map
     *                 draws as a boundary belongs here - a coast's segments as much as a span
     * @param stride   how coarsely to walk, in map units
     * @return the map, ready to be asked about any point
     */
    public static VoidEnclosure mapEnclosedVoid(
            DiscUnion union,
            List<double[][]> barriers,
            double stride) {

        var overSites = Bounds.computeEnclosingBounds(union.sites());
        var margin = union.reach() + MARGIN_IN_STRIDES * stride;

        var walked = new Bounds(
            overSites.minX() - margin,
            overSites.minY() - margin,
            overSites.maxX() + margin,
            overSites.maxY() + margin);

        var grid = WalkedGrid.over(walked, stride);
        var squares = new byte[grid.countSquares()];

        stampLand(squares, union, grid);
        stampBarriers(squares, barriers, grid);
        floodFromTheBorder(squares, grid.across(), grid.up());

        return labelHeldWater(grid, squares);
    }

    /**
     * Which held piece of void a point lies in.
     *
     * @param x where to look
     * @param y the same
     * @return the piece, {@link #OPEN_VOID} where the flood reached it, or
     *         {@link #INSIDE_WALL} where a cell or a wall covers it
     */
    public int findRegionAt(double x, double y) {

        var atX = (int) Math.round((x - grid.walked().minX()) / grid.stride());
        var atY = (int) Math.round((y - grid.walked().minY()) / grid.stride());

        if (atX < 0 || atY < 0 || atX >= grid.across() || atY >= grid.up()) {
            return OPEN_VOID;
        }

        var at = atY * grid.across() + atX;

        return squares[at] == BLOCKED_SQUARE ? INSIDE_WALL : regionAt[at];
    }

    /**
     * How much water a held piece holds.
     *
     * @param region the piece
     * @return its area in map units, or zero for {@link #OPEN_VOID}
     */
    public double measureRegionArea(int region) {
        return region < 0 ? 0 : areas[region];
    }

    /**
     * How coarsely this was walked, for a caller stepping off a wall to find the water beside
     * it: anything closer than a stride may still be the wall's own square.
     *
     * @return the stride, in map units
     */
    public double getStride() {
        return grid.stride();
    }

    /**
     * How many held pieces there are, which is what a caller sizes its own bookkeeping by.
     *
     * @return the count
     */
    public int countRegions() {
        return areas.length;
    }

    // Every square whose centre a cell covers, disc by disc rather than square by square:
    // every square a cell covers is reachable from that cell's own square, which turns a
    // sweep over every site for every square into one pass over the sites.
    private static void stampLand(byte[] squares, DiscUnion union, WalkedGrid grid) {

        var walked = grid.walked();
        var stride = grid.stride();

        for (var site : union.sites()) {

            var fromX = Math.max(0, (int) ((site[0] - union.reach() - walked.minX()) / stride));
            var toX = Math.min(grid.across() - 1, (int) Math.ceil(
                (site[0] + union.reach() - walked.minX()) / stride));
            var fromY = Math.max(0, (int) ((site[1] - union.reach() - walked.minY()) / stride));
            var toY = Math.min(grid.up() - 1, (int) Math.ceil(
                (site[1] + union.reach() - walked.minY()) / stride));

            for (var x = fromX; x <= toX; x++) {
                for (var y = fromY; y <= toY; y++) {

                    var atX = walked.minX() + x * stride;
                    var atY = walked.minY() + y * stride;

                    if (Points.computeDistance(new double[] {atX, atY}, site) < union.reach()) {
                        squares[y * grid.across() + x] = BLOCKED_SQUARE;
                    }
                }
            }
        }
    }

    // Every square a wall runs through, walked along the wall rather than tested per square.
    private static void stampBarriers(
            byte[] squares,
            List<double[][]> barriers,
            WalkedGrid grid) {

        var walked = grid.walked();
        var stride = grid.stride();

        for (var barrier : barriers) {

            var alongX = barrier[1][0] - barrier[0][0];
            var alongY = barrier[1][1] - barrier[0][1];
            var steps = (int) Math.ceil(
                Math.hypot(alongX, alongY) / stride * STAMPS_PER_STRIDE) + 1;

            for (var step = 0; step <= steps; step++) {

                var atX = (int) Math.round(
                    (barrier[0][0] + alongX * step / steps - walked.minX()) / stride);
                var atY = (int) Math.round(
                    (barrier[0][1] + alongY * step / steps - walked.minY()) / stride);

                if (atX >= 0 && atY >= 0 && atX < grid.across() && atY < grid.up()) {
                    squares[atY * grid.across() + atX] = BLOCKED_SQUARE;
                }
            }
        }
    }

    // Open sea, taken from the border inward. Whatever it fails to reach is held.
    private static void floodFromTheBorder(byte[] squares, int across, int up) {

        var pending = new ArrayDeque<Integer>();

        for (var x = 0; x < across; x++) {

            offer(squares, pending, x, 0, across);
            offer(squares, pending, x, up - 1, across);
        }
        for (var y = 0; y < up; y++) {

            offer(squares, pending, 0, y, across);
            offer(squares, pending, across - 1, y, across);
        }

        while (!pending.isEmpty()) {

            var at = pending.poll();

            for (var step : STEPS) {

                offer(squares, pending, at % across + step[0], at / across + step[1], across);
            }
        }
    }

    private static void offer(
            byte[] squares,
            ArrayDeque<Integer> pending,
            int x,
            int y,
            int across) {

        if (x < 0 || y < 0 || x >= across || y * across + x >= squares.length) {
            return;
        }

        var at = y * across + x;

        if (squares[at] != OPEN_SQUARE) {
            return;
        }
        squares[at] = REACHED_SQUARE;
        pending.add(at);
    }

    // What the flood never reached, gathered into pieces and measured.
    private static VoidEnclosure labelHeldWater(WalkedGrid grid, byte[] squares) {

        var across = grid.across();
        var up = grid.up();
        var regionAt = new int[grid.countSquares()];
        var counts = new ArrayList<Integer>();

        Arrays.fill(regionAt, OPEN_VOID);

        for (var start = 0; start < squares.length; start++) {

            if (squares[start] != OPEN_SQUARE || regionAt[start] != OPEN_VOID) {
                continue;
            }

            var region = counts.size();
            var held = 0;
            var pending = new ArrayDeque<Integer>();

            regionAt[start] = region;
            pending.add(start);

            while (!pending.isEmpty()) {

                var at = pending.poll();

                held++;

                for (var step : STEPS) {

                    var x = at % across + step[0];
                    var y = at / across + step[1];

                    if (x < 0 || y < 0 || x >= across || y >= up) {
                        continue;
                    }

                    var next = y * across + x;

                    if (squares[next] == OPEN_SQUARE && regionAt[next] == OPEN_VOID) {

                        regionAt[next] = region;
                        pending.add(next);
                    }
                }
            }
            counts.add(held);
        }

        var areas = new double[counts.size()];

        for (var region = 0; region < counts.size(); region++) {
            areas[region] = counts.get(region) * grid.stride() * grid.stride();
        }

        return new VoidEnclosure(grid, squares, regionAt, areas);
    }
}
