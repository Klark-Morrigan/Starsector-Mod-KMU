package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.BareVoidBoundary;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.VoidHole;

import java.util.ArrayList;
import java.util.List;

/**
 * The void the cells close around unaided, with no line laid across it: v4's base partition.
 *
 * <p>The base every later layer divides. Nothing here knows what a coast is or what a span is:
 * this is the sector's void as the cells alone leave it, which is the one reading no
 * construction can disagree with because no construction has contributed to it yet.
 *
 * <p><b>Read as faces of the one walk, over rings the disc sweep traced.</b> The sweep's model
 * is covers - a thing takes a stretch out of a circle, and the boundary is the gaps between
 * them - and a wall of no width takes out nothing, which is a contradiction it carries five
 * separate accommodations for. Handed NO walls, not one of them is reached, so the sweep is
 * exact here and its rings are the cells' own boundary. The walk closes those rings into faces
 * with nothing laid across them, so at this tier a face is a hole and nothing more. What the
 * walk adds is that every later line is laid into the SAME faces, rather than traced by a
 * second construction that then has to be reconciled with the first.
 *
 * <p>Which is also what makes the sweep the reference rather than the thing replaced: a walk
 * that hands back a different set of pieces from the sweep's holes, over the same sites at the
 * same reach, is wrong, and there is nowhere else to learn that from.
 *
 * <p>Only the bounded faces are kept. Every ring stands alone, so the walk closes an outside
 * for each of them, and none of those is the open sea: the sea is what the cells do not
 * enclose, and no ring bounds it.
 *
 * <p>At the cells' OWN reach, always. The reach a shape is drawn at is a presentation choice
 * made per layer; what void there is, is not.
 */
public final class BareVoid {

    // How far two reports of one corner may stand apart and still be welded into one. NOT the
    // cells' weld tolerance: that one is sized to the sagitta between two neighbouring arcs, a
    // hundred units at the shipped knobs, and the sweep's rings carry corners a few units apart
    // where two arcs cross at a shallow angle - so welding at it swallows whole pieces. Every
    // corner here comes out of one trace, so the only gap to absorb is rounding, and this is
    // rounding with room to spare.
    private static final double SAME_CORNER = 1e-3;

    private final List<Face> pieces;

    private BareVoid(List<Face> pieces) {
        this.pieces = pieces;
    }

    /**
     * Reads the void a sector's cells close around.
     *
     * @param sites      the cells' own positions
     * @param parameters the knobs the cells are built under, for the reach they stand at and
     *                   the bound every arc is flattened onto
     * @return the bare void
     */
    public static BareVoid readBareVoid(
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        var holes = BareVoidBoundary.traceBareHoles(
            new DiscUnion(sites, parameters.cellRadius()), parameters.boundSegments());

        var pieces = new ArrayList<Face>(holes.size());

        for (var face : FaceWalk.walkFaces(collectRings(holes), List.of(), SAME_CORNER)) {
            if (!face.isOuterFace()) {
                pieces.add(face);
            }
        }
        return new BareVoid(List.copyOf(pieces));
    }

    /**
     * Every piece of void, as the closed outline of each.
     *
     * @return one ring per piece, wound to fill, in the order the walk closed them
     */
    public List<List<double[]>> collectOutlines() {

        var outlines = new ArrayList<List<double[]>>(pieces.size());

        for (var piece : pieces) {
            outlines.add(piece.boundary());
        }
        return List.copyOf(outlines);
    }

    /**
     * How many pieces the cells leave.
     *
     * @return the count
     */
    public int countPieces() {
        return pieces.size();
    }

    private static List<List<double[]>> collectRings(List<VoidHole> holes) {

        var rings = new ArrayList<List<double[]>>(holes.size());

        for (var hole : holes) {
            rings.add(hole.boundary());
        }
        return rings;
    }
}
