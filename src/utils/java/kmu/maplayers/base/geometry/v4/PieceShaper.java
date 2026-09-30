package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.PolygonOffsets;
import kmlib.math.geometry.RingRegion;

import kmu.maplayers.base.geometry.EdgeInset;
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
 * alone would take its winding for the plane's and draw it as a fill. A piece narrower than two
 * channels only at its ends is not gone: its end folds are spliced out and the body between
 * them is judged on its own.
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

    // What the fold splicer takes to compare every pair of edges rather than a window.
    private static final int EVERY_PAIR_OF_EDGES = 0;

    // How far apart along the ring the two sides of a corner's fold can be, in edges: the
    // corner's own two, its bevel, and the few short edges a shore's corner leaves beside a
    // junction. Wide enough for those, and a fraction of any piece's ring.
    private static final int NEARBY_EDGES = 12;

    private PieceShaper() {
    }

    /**
     * Shapes one piece into the rings to fill.
     *
     * @param piece           the piece, its edges labelled with what they lie on
     * @param edgeInset       which of those edges take the channel and how deep it is
     * @param miterSpikeLimit multiple of the inset past which a sharp corner bevels rather than
     *                        spiking, a piece being as sharp as the cells that left it
     * @return the outline and the holes, each shaped; a piece the inset folded over comes back
     *         with no rings at all
     */
    public static RingRegion shapePiece(
            Face piece,
            EdgeInset edgeInset,
            double miterSpikeLimit) {

        // The piece itself, corner for corner. Run through the miter at no depth it comes
        // back a corner doubled at every reflex turn and a unit off at the rest, which is
        // near enough to draw and not the piece - and under this rule the piece is what is
        // being looked at.
        if (edgeInset.rule() == EdgeInsetRule.NOWHERE) {

            return new RingRegion(
                piece.boundary(),
                piece.holes().stream().map(LabelledRing::vertices).toList());
        }

        // The folds the miter leaves at a corner too tight for the channel are spliced out
        // here rather than left to the resolve, which fills a reversed loop as a fill of its
        // own: a speck beside every junction. Such a fold is local - its two sides are a few
        // corners apart along the ring - so a short window finds it at a cost the sea's
        // thousands of corners can bear.
        var outline = PolygonOffsets.removeReversedLoops(
            shapeRing(piece.outline(), edgeInset, miterSpikeLimit), true, NEARBY_EDGES);

        // Holes and all. A hole handed on beside a folded outline would be taken for the
        // fill by whatever resolves the rings next, so the whole piece goes, not the outline.
        if (PolygonOffsets.hasInsetCollapsed(piece.boundary(), outline)) {

            // Folded, but perhaps only at its ends. A bay behind a reach is a lens: as wide
            // as it likes in the middle and a few units across where the reach meets the
            // shore, so the miter folds both ends into loops that run out to the spike limit
            // and outweigh the body between them. Read as one signed area that is a collapse;
            // read with the folds spliced out it is a body with its ends gone, which is what
            // a channel does to a lens. An end fold's two sides can be many corners apart
            // where the end runs along the shore, so every pair of edges is compared - asked
            // only of the few rings the first reading rejected, never of the sea.
            outline = PolygonOffsets.removeReversedLoops(outline, true, EVERY_PAIR_OF_EDGES);

            if (PolygonOffsets.hasInsetCollapsed(piece.boundary(), outline)) {
                return NOTHING_LEFT_TO_DRAW;
            }
        }
        var holes = new ArrayList<List<double[]>>(piece.holes().size());

        for (var hole : piece.holes()) {
            holes.add(shapeRing(hole, edgeInset, miterSpikeLimit));
        }
        return new RingRegion(outline, List.copyOf(holes));
    }

    // One ring shifted edge by edge, by the miter path rather than the half-plane clip: a clip
    // only ever removes area and wants a convex polygon, and a piece of void is neither.
    private static List<double[]> shapeRing(
            LabelledRing ring,
            EdgeInset edgeInset,
            double miterSpikeLimit) {

        var labels = ring.edgeLabels();
        var depths = new double[labels.length];

        for (var edge = 0; edge < labels.length; edge++) {
            depths[edge] = edgeInset.resolveInsetOf(isAgainstSomething(labels[edge]));
        }
        return PolygonOffsets.insetPolygonByMiter(ring.vertices(), depths, miterSpikeLimit);
    }

    // Whether there is anything across this edge to stand off from.
    private static boolean isAgainstSomething(int label) {
        return label != VoidPartition.THE_FRAME;
    }
}
