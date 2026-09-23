package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Segment;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The void the cells leave: everywhere no cell reaches, divided into its pieces.
 *
 * <p>The base every later layer divides. Nothing here knows what a coast is or what a span is:
 * this is the sector's void as the cells alone leave it, which is the one reading no
 * construction can disagree with because no construction has contributed to it yet.
 *
 * <p><b>Read off the cells, which already know where it is.</b> Every cell edge says what lies
 * across it, and {@link EdgeTarget#REACH_BOUND} says nothing does - the cell stopped at its own
 * reach rather than meeting a neighbour. Those edges and no others are the line between cell and
 * void, so they are the whole input, and no second construction has to rediscover them. A
 * neighbouring pair meets exactly where its two bounds cross, so the frontier closes into rings
 * at rounding rather than at a tolerance.
 *
 * <p><b>The open sea is a piece like any other.</b> It is the largest part of the void by far -
 * everything beyond the cells' reach - and it is bounded on the inside by the silhouettes the
 * frontier draws round each group of cells. A frame round the sector closes it on the outside,
 * so it comes back as a face rather than as the thing left over when the faces were counted.
 * Without it the pieces do not partition the void, which is the property the whole construction
 * rests on.
 *
 * <p>So what separates a piece of void from a cell is which side of the frontier it lies on, and
 * the walk answers that by winding: a cell is walked with its own edges, a piece of void against
 * them. Every face that is not a cell is void.
 *
 * <p>At the cells' OWN reach, always. The reach a shape is drawn at is a presentation choice
 * made per layer; what void there is, is not.
 */
public final class BareVoid {

    // What the frame is labelled with.
    //
    // Negative, so it can be no cell's index - and deliberately NOT the negative KMLib's
    // VoronoiCellBuilder.BOUND_EDGE uses, which is what a cell's own edges carry where nothing
    // lies across them. Both of those reach a piece's labels, and they say opposite things:
    // one means a cell stopped at its reach and void begins, the other means the sector did.
    // Sharing a number would let a reader take the edge of the map for somebody's shore, and
    // nothing would say otherwise.
    public static final int THE_FRAME = -2;

    // How far outside the cells the frame stands, as a share of the sector's own span. Far
    // enough that the sea has room to read as a piece rather than as a rim, and near enough
    // that the piece is about this sector rather than about the number chosen here.
    private static final double FRAME_MARGIN_SHARE = 0.05;

    private final DiscUnion union;

    private final List<Face> pieces;

    private BareVoid(DiscUnion union, List<Face> pieces) {
        this.union = union;
        this.pieces = pieces;
    }

    /**
     * Reads the void a sector's cells leave.
     *
     * @param cellEdges  every cell, as its adjacency-tagged edges
     * @param sites      the cells' own positions
     * @param parameters the knobs the cells were built under
     * @return the bare void
     */
    public static BareVoid readBareVoid(
            Map<?, List<CellEdge>> cellEdges,
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        var frontier = collectFrontier(cellEdges);
        var pieces = new ArrayList<Face>();

        for (var face : FaceWalk.walkFaces(
                List.of(), frameTheSector(frontier, sites, parameters),
                measureBoundGap(parameters))) {

            if (!face.isOuterFace() && !isLand(face, sites)) {
                pieces.add(face);
            }
        }
        return new BareVoid(DiscUnion.buildAtCellReach(sites, parameters), List.copyOf(pieces));
    }

    /**
     * Every piece of void, as the closed outline of each.
     *
     * @return one ring per piece, wound to fill, in the order the walk closed them
     */
    public List<List<double[]>> collectOutlines() {
        return pieces.stream().map(Face::boundary).toList();
    }

    /**
     * Every piece of void, with what each edge of it lies on.
     *
     * @return the pieces, in the order the walk closed them
     */
    public List<Face> collectPieces() {
        return pieces;
    }

    /**
     * How many pieces the cells leave.
     *
     * @return the count
     */
    public int countPieces() {
        return pieces.size();
    }

    /**
     * The discs the pieces were read between.
     *
     * @return the cells at their own reach
     */
    public DiscUnion union() {
        return union;
    }

    // Whether a piece is cells rather than void.
    //
    // The frontier is one line with a cell on one side and void on the other, so both are
    // faces of this walk and something has to tell them apart. Which side a piece lies on is
    // read off the direction its edges are walked in: a cell reports its own boundary with
    // itself on the left, and a bounded piece is walked with itself on the left, so the cell's
    // site is on the LEFT of a frontier edge exactly when the piece is that cell's side of it.
    //
    // Read off the winding rather than by probing a point inside the piece, because a piece
    // can be long and thin and bent round a hole, and a probe has to be placed somewhere
    // inside it before it can ask anything. The direction is already there.
    private static boolean isLand(Face face, List<double[]> sites) {

        var boundary = face.boundary();
        var labels = face.edgeLabels();

        for (var edge = 0; edge < boundary.size(); edge++) {

            // Only a cell's own border says which side of it the cell is on. The frame says
            // nothing, and neither does a line a tier laid across the void - and asking for
            // the site behind either of those reads the sites at a negative index.
            if (!EdgeLabels.isCell(labels[edge])) {
                continue;
            }

            var from = boundary.get(edge);
            var to = boundary.get((edge + 1) % boundary.size());
            var site = sites.get(labels[edge]);

            return (to[0] - from[0]) * (site[1] - from[1])
                - (to[1] - from[1]) * (site[0] - from[0]) > 0;
        }

        // No cell on its outline at all: the piece bounded only by the frame and whatever was
        // laid across it, which is the open sea in a sector whose cells are all islands.
        return false;
    }

    // Every edge a cell faces the void across, labelled with the cell it belongs to.
    private static List<LabelledWall> collectFrontier(Map<?, List<CellEdge>> cellEdges) {

        var frontier = new ArrayList<LabelledWall>();
        var cell = 0;

        for (var edges : cellEdges.values()) {

            for (var edge : edges) {
                if (edge.target() == EdgeTarget.REACH_BOUND) {
                    frontier.add(new LabelledWall(
                        new Segment(edge.x1(), edge.y1(), edge.x2(), edge.y2()), cell));
                }
            }
            cell++;
        }
        return frontier;
    }

    // The frontier with a box round the whole sector, which is what turns the open sea from
    // the space outside every face into a face of its own.
    private static List<LabelledWall> frameTheSector(
            List<LabelledWall> frontier,
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        var lines = new ArrayList<>(frontier);

        if (sites.isEmpty()) {
            return lines;
        }

        var around = Bounds.computeEnclosingBounds(sites);
        var margin = parameters.cellRadius()
            + FRAME_MARGIN_SHARE * Math.max(
                around.maxX() - around.minX(), around.maxY() - around.minY());

        var minX = around.minX() - margin;
        var minY = around.minY() - margin;
        var maxX = around.maxX() + margin;
        var maxY = around.maxY() + margin;

        lines.add(new LabelledWall(new Segment(minX, minY, maxX, minY), THE_FRAME));
        lines.add(new LabelledWall(new Segment(maxX, minY, maxX, maxY), THE_FRAME));
        lines.add(new LabelledWall(new Segment(maxX, maxY, minX, maxY), THE_FRAME));
        lines.add(new LabelledWall(new Segment(minX, maxY, minX, minY), THE_FRAME));

        return lines;
    }

    // How far two reports of one frontier corner may stand apart and still be welded.
    //
    // The bound is drawn as a polygon inscribed in the circle, so a frontier edge lies up to
    // the chord's sagitta inside the true bound. Where a corner has to be left on the chord -
    // the near-tangent case, where placing it on the bound would hand the cell a wedge nearer
    // a third site - two neighbours put it within that sagitta of each other rather than at
    // one point. So the sagitta is exactly the gap the welding has to cover, and it is derived
    // from the knobs the cells were drawn at rather than chosen.
    private static double measureBoundGap(SectorGeometryParameters parameters) {
        return parameters.measureBoundSagitta();
    }
}
