package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Straight runs as a graph, and the faces they turn out to enclose.
 *
 * <p><b>The faces are found by turning, not by filling.</b> At each corner the ways out are put
 * in angular order; arriving along one, the walk leaves by the next one clockwise. Following
 * that rule to a cycle traces one face, and doing it from every direction traces all of them
 * exactly once - so the pieces cost one pass over the edges rather than a sampled sweep whose
 * grid decides what is too small to notice.
 *
 * <p>Traversed this way a bounded face comes back anticlockwise and the unbounded outside comes
 * back clockwise, so a cycle's own signed area says which it is - and a graph in several pieces
 * gives one clockwise cycle per piece, all of them that same outside.
 *
 * <p><b>Its own type rather than loose arrays</b> because the walk needs the runs indexed three
 * ways at once - by corner, by direction, and paired with their own reverse - and a set of
 * parallel arrays passed between static methods is those three indexes with nothing keeping
 * them agreeing.
 *
 * <p><b>Every edge is added whole, and none is ever removed.</b> The walk's one invariant is
 * that a direction's reverse sits at the index beside it, which is what lets it turn round at a
 * corner without searching - and an edge removed part way through breaks that for every edge
 * after it. So the runs are settled as geometry BEFORE any is added here, and what is taken out
 * afterwards is marked rather than deleted.
 *
 * <p>Knows nothing of coasts, cells or continents: a run is a run and a crossing is a crossing.
 * Which construction drew which line, and what any of it means, belongs to whatever handed the
 * runs in.
 */
final class PlanarGraph {

    /**
     * One face as the walk found it, with the runs that bound it still named.
     *
     * @param edges     the directions walked round it, which is what says who its neighbours
     *                  are
     * @param boundary  its corners, in the order walked
     * @param area      signed, so anticlockwise is bounded and clockwise is the outside
     * @param perimeter how far round it is. Carried from the walk rather than measured again
     *                  from the corners, because a caller joining faces can keep it exact - two
     *                  faces joined along a shared run have the sum of their perimeters less
     *                  twice what they shared - and a second measurement would drift from that
     */
    record WalkedFace(
        List<Integer> edges,
        List<double[]> boundary,
        double area,
        double perimeter) {
    }

    /**
     * One straight run handed in to be added, and whether it may later be taken out.
     *
     * @param start       one end
     * @param end         the other
     * @param isRemovable whether a merge may take it out. A run that bounds the construction
     *                    itself is not the sort of thing a merge is entitled to remove
     * @param groupId     which run this is a stretch of, for a caller that cut one long run
     *                    into several before handing them over. Carried so that taking out a
     *                    whole run is expressible: the stretches between crossings are what
     *                    the walk needs, and the run they came from is what a reader sees
     */
    record Run(
        double[] start,
        double[] end,
        boolean isRemovable,
        int groupId) {
    }

    private final CornerIndex corners = new CornerIndex();

    // Each edge twice, once each way, with the two directions of one run adjacent - so a
    // direction's reverse is found by flipping the low bit rather than by searching.
    private final List<Integer> leavesCorner = new ArrayList<>();
    private final List<Integer> entersCorner = new ArrayList<>();
    private final List<Boolean> isRemovable = new ArrayList<>();
    private final List<Integer> groupOfEdge = new ArrayList<>();

    // Which runs have been taken out. Marked rather than removed, so every other edge keeps
    // the index its reverse is found by - which is the whole of how the walk turns a corner. A
    // dropped run is simply not offered as a way out of anywhere.
    private final List<Boolean> isDropped = new ArrayList<>();

    /**
     * Finds the corner at a place, adding it if this is the first run to reach it.
     *
     * <p>Exposed because a caller has to be able to settle its runs against the same corners
     * this will match them to: whether a run needs breaking part way along depends on which
     * corners exist, and a caller answering that against a second set of corners would break
     * its runs in places this then does not recognise.
     *
     * @param at where it is
     * @return which corner
     */
    int findOrAddCorner(double[] at) {
        return corners.findOrAddCorner(at);
    }

    /**
     * Every corner known so far.
     *
     * @return the corners, by index
     */
    List<double[]> listCorners() {
        return corners.listCorners();
    }

    /**
     * Adds a run, both ways.
     *
     * @param run the run to add. One whose ends land on the same corner is ignored: a run of no
     *            length has no direction, and the angular order the walk turns on cannot be
     *            asked of it
     */
    void addRun(Run run) {

        var from = corners.findOrAddCorner(run.start());
        var to = corners.findOrAddCorner(run.end());

        if (from == to) {
            return;
        }
        addDirection(from, to, run.isRemovable(), run.groupId());
        addDirection(to, from, run.isRemovable(), run.groupId());
    }

    /**
     * Which run a stretch came from.
     *
     * @param edge either direction of it
     * @return the group its caller gave it
     */
    int readGroupOfEdge(int edge) {
        return groupOfEdge.get(edge);
    }

    /**
     * Every face the runs enclose, largest first.
     *
     * <p>Bounded faces only. The unbounded outside comes back clockwise, once per connected
     * piece of the graph, and is left out - what is wanted is the pieces, and the outside is
     * not one of them.
     *
     * @return the faces
     */
    List<WalkedFace> walkFaces() {

        var outgoingByCorner = sortOutgoingByAngle();
        var walked = new boolean[leavesCorner.size()];
        var faces = new ArrayList<WalkedFace>();

        for (var edge = 0; edge < leavesCorner.size(); edge++) {

            if (walked[edge] || isDropped.get(edge)) {
                continue;
            }

            var face = walkFaceFrom(edge, outgoingByCorner, walked);

            if (face != null && face.area() > 0) {
                faces.add(face);
            }
        }
        faces.sort(Comparator.comparingDouble(WalkedFace::area).reversed());

        return faces;
    }

    /**
     * How many directions there are, which is twice the number of runs.
     *
     * @return the count, so a caller indexing edges can size its own tables from the graph
     *         rather than from whichever edge it happened to see last
     */
    int countEdges() {
        return leavesCorner.size();
    }

    /**
     * The removable runs still standing, once each.
     *
     * <p>What is left of the walls a caller handed in as removable, after whatever has been
     * taken out. Wanted because a caller that DREW those walls has to draw what still stands
     * rather than what it originally laid: a run whose middle stretch has gone still divides
     * something at each end, and drawing it whole puts a line on screen across a piece that is
     * no longer divided there.
     *
     * @return each standing run as its two ends
     */
    List<double[][]> listStandingRemovableRuns() {

        var standing = new ArrayList<double[][]>();

        // Every other index, so each run is reported once rather than once per direction.
        for (var edge = 0; edge < leavesCorner.size(); edge += 2) {

            if (isRemovable.get(edge) && !isDropped.get(edge)) {

                standing.add(new double[][] {
                    corners.readCorner(leavesCorner.get(edge)),
                    corners.readCorner(entersCorner.get(edge))});
            }
        }
        return List.copyOf(standing);
    }

    /**
     * Whether a run may be taken out.
     *
     * @param edge either direction of it
     * @return whether it may
     */
    boolean isRemovableEdge(int edge) {
        return isRemovable.get(edge);
    }

    /**
     * Whether a run has already been taken out.
     *
     * @param edge either direction of it
     * @return whether it has
     */
    boolean isDroppedEdge(int edge) {
        return isDropped.get(edge);
    }

    /**
     * Takes a run out, both ways at once.
     *
     * <p>Both directions go together because a wall is one thing: with only one side down the
     * walk could still run along the other, and the faces either side of it would not join.
     *
     * @param edge either direction of the run
     */
    void dropEdge(int edge) {

        isDropped.set(edge, true);
        isDropped.set(edge ^ 1, true);
    }

    /**
     * Takes out any removable run left with a free end.
     *
     * <p>Joining two faces can leave a run attached at one end and open at the other - a wall
     * with the same face on both sides of it, bounding nothing. The walk still traces it, out
     * and back, so it survives as a spike on that face's outline and as a line on screen that
     * divides nothing.
     *
     * <p>Repeatedly, since taking one out can free the end of the next; and only removable
     * ones, so a run that bounds the construction itself is safe however it is left.
     */
    void dropRunsLeftDangling() {

        var dropped = true;

        while (dropped) {

            dropped = false;

            var waysOut = countWaysOutPerCorner();

            for (var edge = 0; edge < leavesCorner.size(); edge++) {

                if (!isDropped.get(edge)
                        && isRemovable.get(edge)
                        && waysOut[entersCorner.get(edge)] == 1) {

                    dropEdge(edge);
                    dropped = true;
                }
            }
        }
    }

    // One cycle, or null where the turns do not close one.
    //
    // Every edge is marked walked whether the cycle closes or not, so a run that cannot close
    // is not tried again from each of its own edges in turn.
    private WalkedFace walkFaceFrom(
            int from,
            Map<Integer, List<Integer>> outgoing,
            boolean[] walked) {

        var boundary = new ArrayList<double[]>();
        var edges = new ArrayList<Integer>();
        var perimeter = 0.0;
        var step = from;

        while (step >= 0 && !walked[step]) {

            walked[step] = true;
            boundary.add(corners.readCorner(leavesCorner.get(step)));
            edges.add(step);
            perimeter += measureEdgeLength(step);
            step = findNextAroundFace(step, outgoing);
        }

        // Closed only where the turns led back to the edge this began at. Anything else is a
        // run that walked into a cycle it is not part of, and the corners it collected are a
        // path rather than a boundary - reporting them would report a shape nothing encloses.
        return step == from
            ? new WalkedFace(
                List.copyOf(edges),
                List.copyOf(boundary),
                measureSignedArea(boundary),
                perimeter)
            : null;
    }

    /**
     * How long one run is.
     *
     * @param edge either direction of it
     * @return its length
     */
    double measureEdgeLength(int edge) {

        return Points.computeDistance(
            corners.readCorner(leavesCorner.get(edge)),
            corners.readCorner(entersCorner.get(edge)));
    }

    // Where the walk goes on arriving along an edge: back down that edge's reverse, then round
    // to the next way out clockwise. Turning as tightly as the corner allows is what keeps the
    // walk on one face rather than cutting across it.
    private int findNextAroundFace(int arriving, Map<Integer, List<Integer>> outgoing) {

        var reverse = arriving ^ 1;
        var around = outgoing.get(entersCorner.get(arriving));
        var at = around.indexOf(reverse);

        return around.get((at - 1 + around.size()) % around.size());
    }

    // Every corner's ways out, in anticlockwise order of the direction they leave in.
    private Map<Integer, List<Integer>> sortOutgoingByAngle() {

        var outgoing = new HashMap<Integer, List<Integer>>();

        for (var edge = 0; edge < leavesCorner.size(); edge++) {

            if (!isDropped.get(edge)) {
                outgoing
                    .computeIfAbsent(leavesCorner.get(edge), corner -> new ArrayList<>())
                    .add(edge);
            }
        }

        for (var ways : outgoing.values()) {
            ways.sort(Comparator.comparingDouble(this::measureBearing));
        }
        return outgoing;
    }

    private int[] countWaysOutPerCorner() {

        var waysOut = new int[corners.listCorners().size()];

        for (var edge = 0; edge < leavesCorner.size(); edge++) {

            if (!isDropped.get(edge)) {
                waysOut[leavesCorner.get(edge)]++;
            }
        }
        return waysOut;
    }

    private double measureBearing(int edge) {

        var from = corners.readCorner(leavesCorner.get(edge));
        var to = corners.readCorner(entersCorner.get(edge));

        return Math.atan2(to[1] - from[1], to[0] - from[0]);
    }

    private void addDirection(int from, int to, boolean removable, int groupId) {

        leavesCorner.add(from);
        entersCorner.add(to);
        isRemovable.add(removable);
        groupOfEdge.add(groupId);
        isDropped.add(false);
    }

    // Anticlockwise positive, because which way a cycle turns is what says whether it encloses
    // a piece of the plane or the whole of the rest of it.
    private static double measureSignedArea(List<double[]> ring) {

        var twiceArea = 0.0;

        for (var index = 0; index < ring.size(); index++) {

            var from = ring.get(index);
            var to = ring.get((index + 1) % ring.size());

            twiceArea += from[0] * to[1] - to[0] * from[1];
        }
        return twiceArea / 2;
    }

    /**
     * Corners, once each, found by where they are.
     *
     * <p>Two runs meeting at a place have to arrive at the SAME corner for the walk to be able
     * to turn from one onto the other. Two runs that meet are routinely one place that
     * arithmetic has moved a hair apart - looked up by coordinate rather than by which run
     * named it, they come back as one corner and the turn exists.
     */
    private static final class CornerIndex {

        // Far wider than any sector is in cells of the tolerance, so two different cells
        // cannot pack to one key.
        private static final int PACKED_CELL_BITS = 32;
        private static final long PACKED_CELL_STRIDE = 1L << PACKED_CELL_BITS;

        private final List<double[]> corners = new ArrayList<>();

        // Which corners sit in each cell of a coarse grid, so finding an existing corner is a
        // look at its own neighbourhood rather than a walk down every corner found so far.
        private final Map<Long, List<Integer>> cornersByCell = new HashMap<>();

        List<double[]> listCorners() {
            return corners;
        }

        double[] readCorner(int corner) {
            return corners.get(corner);
        }

        int findOrAddCorner(double[] at) {

            var cellX = (long) Math.floor(at[0] / DiscUnion.TOUCHING_TOLERANCE);
            var cellY = (long) Math.floor(at[1] / DiscUnion.TOUCHING_TOLERANCE);

            // The neighbouring cells as well as its own, since two points a hair apart can
            // still fall either side of a cell edge.
            for (var stepX = -1; stepX <= 1; stepX++) {
                for (var stepY = -1; stepY <= 1; stepY++) {

                    var found = findCornerIn(cellX + stepX, cellY + stepY, at);

                    if (found >= 0) {
                        return found;
                    }
                }
            }

            corners.add(at);
            cornersByCell
                .computeIfAbsent(packCell(cellX, cellY), cell -> new ArrayList<>())
                .add(corners.size() - 1);

            return corners.size() - 1;
        }

        private int findCornerIn(long cellX, long cellY, double[] at) {

            for (var corner : cornersByCell.getOrDefault(packCell(cellX, cellY), List.of())) {

                if (Points.computeDistance(corners.get(corner), at) <= DiscUnion.TOUCHING_TOLERANCE) {
                    return corner;
                }
            }
            return -1;
        }

        private static long packCell(long cellX, long cellY) {
            return cellX * PACKED_CELL_STRIDE + cellY;
        }
    }
}
