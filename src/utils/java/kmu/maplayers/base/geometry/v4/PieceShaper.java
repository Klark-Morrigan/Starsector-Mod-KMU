package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.PolygonOffsets;
import kmlib.math.geometry.RingRegion;

import kmu.maplayers.base.geometry.EdgeInsetRule;

import java.util.ArrayList;
import java.util.List;

/**
 * A piece of void with the cosmetic channel taken off it, or drawn as it truly is.
 *
 * <p>The counterpart of {@link kmu.maplayers.base.geometry.CellShaper} for pieces rather than
 * cells, and it asks the same question the same way: the shaper says what an edge faces, and
 * {@link EdgeInsetRule} says what that earns it. One rule over both is what makes the seam
 * between a cell and the void beside it one channel at one depth, rather than two readings that
 * happen to agree until one of them is changed.
 *
 * <p><b>The partition is not touched.</b> The inset is a later pass over pieces that already
 * exist, so a reader switching it on and off is looking at one division drawn two ways rather
 * than at two divisions. Under {@link EdgeInsetRule#NOWHERE} what comes back IS the piece, which
 * is what lets the true geometry be judged before anything cosmetic is laid over it.
 *
 * <p><b>What an edge faces is the label it already carries.</b> A piece's edge lies on a cell,
 * on a wall some tier laid, or on the frame - and only the frame is nothing: it is the edge of
 * the sector rather than the edge of anything, so a piece running up to it has nothing to stand
 * off from. That is why the test is against the frame rather than for a list of the things that
 * are something: a tier adding a wall of its own gets the right answer without amending this.
 *
 * <p><b>Holes take the same positive depth the outline does.</b> A hole is wound against its
 * piece, so shifting its edges along the inward normal of a counter-clockwise ring carries them
 * away from the hole's own interior - the hole grows and the piece pulls back from it. One sign
 * serves both rather than an outline case and a hole case that could drift apart.
 *
 * <p>Which is also why a hole comes back with its corners bevelled where an outline's are
 * mitred, at the same depth and with nothing sharp in either. Growing a hole is an outward
 * offset, and a corner offset outward is an arc rather than a point; the bevel stands in for
 * that arc, where a miter would run the corner out past the offset and cut through the piece
 * the hole belongs to.
 *
 * <p><b>A piece narrower than two channels is gone, not thin.</b> The miter does not shrink
 * such a piece to a sliver; it folds the ring over, and the folded ring winds the wrong way.
 * That is judged here against the raw ring, by the same test a cluster border is judged by,
 * because it is the last moment the raw ring is at hand - and a resolve handed the folded ring
 * alone would take its winding for the plane's and draw it as a fill.
 *
 * <p>What comes back is still the raw miter, crossings included: a piece of void is all necks,
 * and a miter of anything that pinches to a neck crosses itself there. Resolving those is the
 * drawing's job, done with the same passes and the same profile a cluster border gets, so that
 * the two sides of one channel are cleaned up alike.
 */
public final class PieceShaper {

    // What a piece the inset consumed comes back as: no rings at all, rather than an outline
    // folded over and its holes beside it.
    private static final RingRegion NOTHING_LEFT_TO_DRAW = new RingRegion(List.of(), List.of());

    private PieceShaper() {
    }

    /**
     * Shapes one piece into the rings to fill.
     *
     * @param piece           the piece, its edges labelled with what they lie on
     * @param insetRule       which of those edges take the channel
     * @param borderInset     how deep the channel is
     * @param miterSpikeLimit multiple of the inset past which a sharp corner bevels rather than
     *                        spiking, a piece being as sharp as the cells that left it
     * @return the outline and the holes, each shaped; a piece the inset folded over comes back
     *         with no rings at all
     */
    public static RingRegion shapePiece(
            Face piece,
            EdgeInsetRule insetRule,
            double borderInset,
            double miterSpikeLimit) {

        var outline = shapeRing(
            new LabelledRing(piece.boundary(), piece.edgeLabels()),
            insetRule,
            borderInset,
            miterSpikeLimit);

        // Holes and all. A hole handed on beside a folded outline would be taken for the
        // fill by whatever resolves the rings next, so the whole piece goes, not the outline.
        if (PolygonOffsets.hasInsetCollapsed(piece.boundary(), outline)) {
            return NOTHING_LEFT_TO_DRAW;
        }
        var holes = new ArrayList<List<double[]>>(piece.holes().size());

        for (var hole : piece.holes()) {
            holes.add(shapeRing(hole, insetRule, borderInset, miterSpikeLimit));
        }
        return new RingRegion(outline, List.copyOf(holes));
    }

    // One ring shifted edge by edge, by the miter path rather than the half-plane clip: a clip
    // only ever removes area and wants a convex polygon, and a piece of void is neither.
    private static List<double[]> shapeRing(
            LabelledRing ring,
            EdgeInsetRule insetRule,
            double borderInset,
            double miterSpikeLimit) {

        var labels = ring.edgeLabels();
        var depths = new double[labels.length];

        for (var edge = 0; edge < labels.length; edge++) {
            depths[edge] = insetRule.resolveInsetOf(isAgainstSomething(labels[edge]), borderInset);
        }
        return PolygonOffsets.insetPolygonByMiter(ring.vertices(), depths, miterSpikeLimit);
    }

    // Whether there is anything across this edge to stand off from.
    private static boolean isAgainstSomething(int label) {
        return label != BareVoid.THE_FRAME;
    }
}
