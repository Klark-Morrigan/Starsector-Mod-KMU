package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.BareVoidBoundary;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.VoidHole;

import java.util.ArrayList;
import java.util.List;

/**
 * The void the cells close around unaided, with no line laid across it.
 *
 * <p>The base every later layer divides. Nothing here knows what a coast is or what a span is:
 * this is the sector's void as the cells alone leave it, which is the one reading no
 * construction can disagree with because no construction has contributed to it yet.
 *
 * <p><b>Traced through the disc sweep rather than through the face walk that replaces it.</b>
 * The sweep's model is covers - a thing takes a stretch out of a circle, and the boundary is
 * the gaps between them - and a wall of no width takes out nothing, which is a contradiction it
 * carries five separate accommodations for. Handed NO walls, not one of them is reached:
 * nothing asks for a mouth, no terminal belongs to a chord, and a disc cover of no width would
 * need two sites at one point, which the sweep already refuses. So with no walls the sweep is
 * exact, and it is the soundest reading of the bare void available.
 *
 * <p>Which makes it the reference the face walk is measured against rather than something to
 * be replaced by it: a walk that hands back a different set of holes from this, over the same
 * sites at the same reach, is wrong, and there is nowhere else to learn that from.
 *
 * <p>At the cells' OWN reach, always. The reach a shape is drawn at is a presentation choice
 * made per layer; what void there is, is not.
 */
public final class BareVoid {

    private final List<VoidHole> holes;

    private BareVoid(List<VoidHole> holes) {
        this.holes = holes;
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

        return new BareVoid(BareVoidBoundary.traceBareHoles(
            new DiscUnion(sites, parameters.cellRadius()), parameters.boundSegments()));
    }

    /**
     * Every piece of void, as the closed outline of each.
     *
     * @return one ring per piece, in the order the trace found them
     */
    public List<List<double[]>> collectOutlines() {

        var outlines = new ArrayList<List<double[]>>(holes.size());

        for (var hole : holes) {
            outlines.add(hole.boundary());
        }
        return List.copyOf(outlines);
    }

    /**
     * How many pieces the cells leave.
     *
     * @return the count
     */
    public int countPieces() {
        return holes.size();
    }
}
