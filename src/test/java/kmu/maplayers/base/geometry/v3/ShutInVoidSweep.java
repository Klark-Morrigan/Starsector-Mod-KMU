package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import kmu.maplayers.base.geometry.Chord;
import kmu.maplayers.base.geometry.DiscUnion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * The void a sector's cells and walls shut in, found by trying to get out of it.
 *
 * <p>An independent answer to the question the boundary walk also answers. The walk decides
 * what is enclosed by tracing a ring and seeing that it closes; this decides it by starting in
 * the water and failing to reach open sea. Two constructions that share no code can disagree,
 * and where they do the walk is the one to doubt, because getting out is the question a fill
 * actually answers - a reader sees water ringed by cells and expects it coloured in.
 *
 * <p>Walks a grid rather than solving the geometry. That makes the stride a claim in its own
 * right: a flood cannot pass through a gap narrower than one step, so too coarse a stride
 * reports water shut in by a passage it merely stepped over. Every figure this produces should
 * be confirmed at a finer stride before being believed.
 */
final class ShutInVoidSweep {

    // Room beyond the outermost cell for open sea to reach the border of the grid and be
    // recognised as open. One reach clears every disc and every wall, since both are drawn
    // from the sites; past that all the flood needs is a ring of water wide enough to walk
    // round the land to the edge, and in strides because that is what it walks in. Wider
    // margins buy nothing and cost the whole grid: the squares they add are open sea, which
    // is the one piece of water the sweep throws away.
    private static final int MARGIN_IN_STRIDES = 2;

    // The four squares a flood steps to from any one. Held rather than written into the loop,
    // where it is rebuilt for every square the flood stands on.
    private static final int[][] STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private ShutInVoidSweep() {
    }

    /**
     * Every piece of void that cannot reach the edge of the sector.
     *
     * <p>The cells come off the union rather than beside it. Handed in separately, a caller
     * could pair one set of centres with a union built from another, and the flood would then
     * be walking discs that are not the ones it measures itself against.
     *
     * @param union  the discs the cells cover, at the reach being asked about
     * @param walls  the walls laid across the void, which stop the flood as a cell does
     * @param stride how coarsely to walk, in map units
     * @return one entry per piece of shut-in void, in no particular order
     */
    static List<ShutInVoid> findShutInVoid(
            DiscUnion union,
            List<Chord> walls,
            double stride) {

        var overSites = Bounds.computeEnclosingBounds(union.sites());
        var margin = union.reach() + MARGIN_IN_STRIDES * stride;

        var walked = new Bounds(
            overSites.minX() - margin,
            overSites.minY() - margin,
            overSites.maxX() + margin,
            overSites.maxY() + margin);

        var grid = stampLand(union, walked, stride);

        return floodTheVoid(grid, WallIndex.over(grid, walls));
    }

    /**
     * Marks the squares that lie inside a cell.
     *
     * <p>Stamped disc by disc rather than asked square by square: every square a cell covers is
     * reachable from that cell's own square, which turns a sweep over every site for every
     * square into one pass over the sites.
     */
    private static FloodGrid stampLand(DiscUnion union, Bounds walked, double stride) {

        var grid = FloodGrid.over(walked, stride);

        for (var site : union.sites()) {

            var fromX = Math.max(
                0, (int) ((site[0] - union.reach() - walked.minX()) / stride));
            var toX = Math.min(grid.countAcross() - 1, (int) Math.ceil(
                (site[0] + union.reach() - walked.minX()) / stride));
            var fromY = Math.max(
                0, (int) ((site[1] - union.reach() - walked.minY()) / stride));
            var toY = Math.min(grid.countUp() - 1, (int) Math.ceil(
                (site[1] + union.reach() - walked.minY()) / stride));

            for (var x = fromX; x <= toX; x++) {
                for (var y = fromY; y <= toY; y++) {

                    if (Points.computeDistance(grid.findSquareAt(x, y), site)
                            < union.reach()) {
                        grid.markLand(x, y);
                    }
                }
            }
        }
        return grid;
    }

    // Floods every piece of water in turn, keeping the ones that never reach the border.
    private static List<ShutInVoid> floodTheVoid(FloodGrid grid, WallIndex walls) {

        var found = new ArrayList<ShutInVoid>();

        for (var startX = 0; startX < grid.countAcross(); startX++) {
            for (var startY = 0; startY < grid.countUp(); startY++) {

                if (!grid.isUnreachedWater(startX, startY)) {
                    continue;
                }

                var piece = floodFrom(startX, startY, grid, walls);

                if (piece != null) {
                    found.add(piece);
                }
            }
        }
        return found;
    }

    /**
     * One piece of water, walked out from a square to everything it can reach.
     *
     * <p>Null where the flood got to the border of the grid, which is water open to the sea
     * rather than shut in. Walked to the end even so, since what it reached is water no later
     * start should begin from again.
     */
    private static ShutInVoid floodFrom(
            int startX,
            int startY,
            FloodGrid grid,
            WallIndex walls) {

        var points = new ArrayList<double[]>();
        var pending = new ArrayDeque<int[]>();
        var blocking = new LinkedHashSet<Chord>();
        var escaped = false;

        grid.markReached(startX, startY);
        pending.add(new int[] {startX, startY});

        while (!pending.isEmpty()) {

            var square = pending.poll();
            var here = grid.findSquareAt(square[0], square[1]);

            points.add(here);
            escaped |= grid.isOnBorder(square[0], square[1]);

            for (var towards : STEPS) {

                var nextX = square[0] + towards[0];
                var nextY = square[1] + towards[1];

                if (!grid.isUnreachedWater(nextX, nextY)) {
                    continue;
                }

                // A step across a wall does not carry the flood: that is what a wall is.
                // Tested against the wall's SEGMENT, since a wall spans one gap and taken
                // as a line would divide the whole sector. The wall is remembered as well
                // as obeyed, because which walls shut a piece in is what says whose fill
                // the water is owed.
                var blockedBy = findBlockingChord(
                    here,
                    grid.findSquareAt(nextX, nextY),
                    walls.findWallsNear(square[0], square[1]));

                if (blockedBy != null) {
                    blocking.add(blockedBy);
                    continue;
                }
                grid.markReached(nextX, nextY);
                pending.add(new int[] {nextX, nextY});
            }
        }
        return escaped ? null : new ShutInVoid(points, grid.stride(), List.copyOf(blocking));
    }

    private static Chord findBlockingChord(
            double[] from, double[] to, List<Chord> walls) {

        for (var wall : walls) {
            if (Segments.intersectSegments(from, to, wall.findStart(), wall.findEnd()) != null) {
                return wall;
            }
        }
        return null;
    }

    /**
     * The walls each square could be stopped by, so a step tests a handful rather than all of
     * them.
     *
     * <p>A sector carries hundreds of walls and millions of squares, and the flood asks its
     * blocking question at nearly every square it steps onto - so scanning the whole wall list
     * per step is what a sweep spends its time on, not the walking. Bucketed against the same
     * squares the flood walks, since that is the only key it has to hand when it asks.
     *
     * @param wallsBySquare the walls filed against each square they could stop a step out of
     */
    private record WallIndex(Map<Long, List<Chord>> wallsBySquare) {

        // How far around a wall's own box its squares are filed. One square: a step spans
        // exactly one, so a step a wall crosses starts within a square of the crossing point,
        // and the crossing point is inside the box.
        private static final int REACHING_SQUARES = 1;

        static WallIndex over(FloodGrid grid, List<Chord> walls) {

            var wallsBySquare = new HashMap<Long, List<Chord>>();

            for (var wall : walls) {

                var start = wall.findStart();
                var end = wall.findEnd();

                var fromX = grid.findColumnAt(Math.min(start[0], end[0])) - REACHING_SQUARES;
                var toX = grid.findColumnAt(Math.max(start[0], end[0])) + REACHING_SQUARES;
                var fromY = grid.findRowAt(Math.min(start[1], end[1])) - REACHING_SQUARES;
                var toY = grid.findRowAt(Math.max(start[1], end[1])) + REACHING_SQUARES;

                for (var x = fromX; x <= toX; x++) {
                    for (var y = fromY; y <= toY; y++) {

                        wallsBySquare
                            .computeIfAbsent(keyFor(x, y), square -> new ArrayList<>())
                            .add(wall);
                    }
                }
            }
            return new WallIndex(wallsBySquare);
        }

        // One key per square, packed rather than boxed as a pair: the flood asks for a square's
        // walls millions of times, and a key object per ask costs more than the scan saved.
        private static long keyFor(int x, int y) {
            return ((long) x << Integer.SIZE) | Integer.toUnsignedLong(y);
        }

        List<Chord> findWallsNear(int x, int y) {
            return wallsBySquare.getOrDefault(keyFor(x, y), List.of());
        }
    }

    /**
     * The grid a flood walks: which squares are land, which have been reached already, and
     * where each of them sits in the world.
     *
     * <p>One value rather than a boolean array, a second boolean array, a box and a stride
     * carried alongside each other. The four are only meaningful together - a square index
     * means nothing without the box and the stride that place it - and separately they made
     * a seven-argument stamp and a five-argument flood that could be handed a grid measured
     * against one box and points against another.
     *
     * @param isLand  whether each square lies inside a cell
     * @param reached whether the flood has already stood on each square
     * @param walked  the box the grid covers
     * @param stride  how far apart the squares sit, in map units
     */
    private record FloodGrid(
        boolean[][] isLand,
        boolean[][] reached,
        Bounds walked,
        double stride) {

        static FloodGrid over(Bounds walked, double stride) {

            var across = (int) Math.ceil((walked.maxX() - walked.minX()) / stride) + 1;
            var up = (int) Math.ceil((walked.maxY() - walked.minY()) / stride) + 1;

            return new FloodGrid(
                new boolean[across][up], new boolean[across][up], walked, stride);
        }

        int countAcross() {
            return isLand.length;
        }

        int countUp() {
            return isLand[0].length;
        }

        void markLand(int x, int y) {
            isLand[x][y] = true;
        }

        void markReached(int x, int y) {
            reached[x][y] = true;
        }

        // Water the flood may still step onto: on the grid at all, not inside a cell, and not
        // already stood on. One question rather than three tests spelled out at each caller,
        // since a step that skipped any of them would walk off the array or loop forever.
        boolean isUnreachedWater(int x, int y) {

            return x >= 0 && y >= 0 && x < countAcross() && y < countUp()
                && !isLand[x][y]
                && !reached[x][y];
        }

        // The border is where water is open to the sea, since the box is drawn well clear of
        // every cell.
        boolean isOnBorder(int x, int y) {
            return x == 0 || y == 0 || x == countAcross() - 1 || y == countUp() - 1;
        }

        // Where one square sits in the world. Named because the stamping, the flood and the
        // points a piece of void is made of all have to agree about it exactly.
        double[] findSquareAt(int x, int y) {
            return new double[] {walked.minX() + x * stride, walked.minY() + y * stride};
        }

        // The other direction: which column and row a place in the world falls in. Rounded
        // down rather than to the nearest, so that a point and the square it is filed under
        // are never more than one stride apart - which is what lets a box grown by a single
        // square hold every step that crosses what the box was drawn around.
        int findColumnAt(double x) {
            return (int) Math.floor((x - walked.minX()) / stride);
        }

        int findRowAt(double y) {
            return (int) Math.floor((y - walked.minY()) / stride);
        }
    }

    /**
     * One piece of shut-in void, as the squares the flood stood on.
     *
     * @param points   the centre of every square the flood reached
     * @param stride   how far apart those squares sit
     * @param walledBy the walls that stopped a step of the flood - empty where the cells
     *                 enclose the water on their own, which is what decides whose fill it
     *                 is owed
     */
    record ShutInVoid(
        List<double[]> points,
        double stride,
        List<Chord> walledBy) {

        double measureArea() {
            return points.size() * stride * stride;
        }

        /** The cells this water lies against, which is what names it in a failure. */
        List<String> nameRingingCells(DiscUnion union, List<String> systemIdBySite) {

            var ringing = new LinkedHashSet<String>();

            for (var point : points) {
                for (var site = 0; site < union.sites().size(); site++) {

                    if (Points.computeDistance(point, union.sites().get(site))
                            < union.reach() + stride) {
                        ringing.add(systemIdBySite.get(site));
                    }
                }
            }
            return List.copyOf(ringing);
        }
    }
}
