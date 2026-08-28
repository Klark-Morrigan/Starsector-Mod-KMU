package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

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
    // recognised as open. Any water this far out is past every disc and every wall, so it
    // needs only enough space to be walked to the edge.
    private static final double MARGIN_IN_CELL_RADII = 3;

    private ShutInVoidSweep() {
    }

    /**
     * Every piece of void that cannot reach the edge of the sector.
     *
     * @param sites  the cell centres
     * @param union  the discs the cells cover, at the reach being asked about
     * @param walls  the walls laid across the void, which stop the flood as a cell does
     * @param stride how coarsely to walk, in map units
     * @return one entry per piece of shut-in void, in no particular order
     */
    static List<ShutInVoid> findShutInVoid(
            List<double[]> sites,
            DiscUnion union,
            List<DiscUnionBoundary.Chord> walls,
            double stride) {

        var leastX = Double.MAX_VALUE;
        var leastY = Double.MAX_VALUE;
        var mostX = -Double.MAX_VALUE;
        var mostY = -Double.MAX_VALUE;

        for (var site : sites) {
            leastX = Math.min(leastX, site[0]);
            leastY = Math.min(leastY, site[1]);
            mostX = Math.max(mostX, site[0]);
            mostY = Math.max(mostY, site[1]);
        }

        var margin = union.reach() + MARGIN_IN_CELL_RADII * union.reach();

        return floodTheVoid(
            stampLand(sites, union, leastX - margin, leastY - margin,
                mostX + margin, mostY + margin, stride),
            walls,
            leastX - margin,
            leastY - margin,
            stride);
    }

    /**
     * Marks the squares that lie inside a cell.
     *
     * <p>Stamped disc by disc rather than asked square by square: every square a cell covers is
     * reachable from that cell's own square, which turns a sweep over every site for every
     * square into one pass over the sites.
     */
    private static boolean[][] stampLand(
            List<double[]> sites,
            DiscUnion union,
            double leastX,
            double leastY,
            double mostX,
            double mostY,
            double stride) {

        var across = (int) Math.ceil((mostX - leastX) / stride) + 1;
        var up = (int) Math.ceil((mostY - leastY) / stride) + 1;
        var isLand = new boolean[across][up];

        for (var site : sites) {

            var fromX = Math.max(0, (int) ((site[0] - union.reach() - leastX) / stride));
            var toX = Math.min(across - 1, (int) Math.ceil(
                (site[0] + union.reach() - leastX) / stride));
            var fromY = Math.max(0, (int) ((site[1] - union.reach() - leastY) / stride));
            var toY = Math.min(up - 1, (int) Math.ceil(
                (site[1] + union.reach() - leastY) / stride));

            for (var x = fromX; x <= toX; x++) {
                for (var y = fromY; y <= toY; y++) {

                    var here = new double[] {leastX + x * stride, leastY + y * stride};

                    if (Points.computeDistance(here, site) < union.reach()) {
                        isLand[x][y] = true;
                    }
                }
            }
        }
        return isLand;
    }

    // Floods every piece of water in turn, keeping the ones that never reach the border.
    private static List<ShutInVoid> floodTheVoid(
            boolean[][] isLand,
            List<DiscUnionBoundary.Chord> walls,
            double leastX,
            double leastY,
            double stride) {

        var across = isLand.length;
        var up = isLand[0].length;
        var seen = new boolean[across][up];
        var found = new ArrayList<ShutInVoid>();

        for (var startX = 0; startX < across; startX++) {
            for (var startY = 0; startY < up; startY++) {

                if (isLand[startX][startY] || seen[startX][startY]) {
                    continue;
                }

                var points = new ArrayList<double[]>();
                var pending = new ArrayDeque<int[]>();
                var blocking = new LinkedHashSet<DiscUnionBoundary.Chord>();
                var escaped = false;

                seen[startX][startY] = true;
                pending.add(new int[] {startX, startY});

                while (!pending.isEmpty()) {

                    var square = pending.poll();
                    var here = new double[] {
                        leastX + square[0] * stride, leastY + square[1] * stride};

                    points.add(here);

                    if (square[0] == 0 || square[1] == 0
                            || square[0] == across - 1 || square[1] == up - 1) {
                        escaped = true;
                    }

                    for (var towards : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {

                        var nextX = square[0] + towards[0];
                        var nextY = square[1] + towards[1];

                        if (nextX < 0 || nextY < 0 || nextX >= across || nextY >= up) {
                            continue;
                        }
                        if (seen[nextX][nextY] || isLand[nextX][nextY]) {
                            continue;
                        }

                        var there = new double[] {
                            leastX + nextX * stride, leastY + nextY * stride};

                        // A step across a wall does not carry the flood: that is what a wall
                        // is. Tested against the wall's SEGMENT, since a wall spans one gap
                        // and taken as a line would divide the whole sector. The wall is
                        // remembered as well as obeyed, because which walls shut a piece in
                        // is what says whose fill the water is owed.
                        var blockedBy = findBlockingChord(here, there, walls);

                        if (blockedBy != null) {
                            blocking.add(blockedBy);
                            continue;
                        }
                        seen[nextX][nextY] = true;
                        pending.add(new int[] {nextX, nextY});
                    }
                }

                if (!escaped) {
                    found.add(new ShutInVoid(points, stride, List.copyOf(blocking)));
                }
            }
        }
        return found;
    }

    private static DiscUnionBoundary.Chord findBlockingChord(
            double[] from, double[] to, List<DiscUnionBoundary.Chord> walls) {

        for (var wall : walls) {
            if (Segments.intersectSegments(from, to, wall.findStart(), wall.findEnd()) != null) {
                return wall;
            }
        }
        return null;
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
        List<DiscUnionBoundary.Chord> walledBy) {

        double measureArea() {
            return points.size() * stride * stride;
        }

        /** The cells this water lies against, which is what names it in a failure. */
        List<String> nameRingingCells(
                List<double[]> sites, DiscUnion union, List<String> systemIdBySite) {

            var ringing = new LinkedHashSet<String>();

            for (var point : points) {
                for (var site = 0; site < sites.size(); site++) {

                    if (Points.computeDistance(point, sites.get(site))
                            < union.reach() + stride) {
                        ringing.add(systemIdBySite.get(site));
                    }
                }
            }
            return List.copyOf(ringing);
        }
    }
}
