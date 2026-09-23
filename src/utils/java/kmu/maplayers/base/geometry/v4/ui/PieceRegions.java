package kmu.maplayers.base.geometry.v4.ui;

import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.RingRegion;

import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.geometry.v4.Face;
import kmu.maplayers.base.geometry.v4.PieceShaper;
import kmu.maplayers.base.render.clusters.BorderSmoothing;
import kmu.maplayers.base.theme.BorderSmoothingStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Pieces of void as regions that can actually be filled.
 *
 * <p>Everything between a piece and a fill, which is more than the inset. The inset alone is
 * not drawable: a piece of void is all necks, and a miter of anything that pinches to a neck
 * crosses itself there, so a ring straight off the shaping fills to something other than its
 * outline and strokes a line through its own interior. What makes it drawable is the same
 * cleanup a cluster border gets, through the same passes and the same profile - which is also
 * what makes the two sides of one channel round alike.
 *
 * <p>The order is the one thing here, and each step is the reason the next can run.
 *
 * <ol>
 *   <li>Shape the piece, which is where a piece too narrow for the channel folds over.</li>
 *   <li>Drop it while its raw ring is still at hand to say so. A resolve handed a folded
 *       ring alone takes that ring's winding for the plane's and draws it as a fill.</li>
 *   <li>Resolve, smooth, resolve again.</li>
 *   <li>Group the loops back into bodies and holes, because the resolve may split a piece at
 *       a neck and hand back the two pieces it really is.</li>
 * </ol>
 *
 * <p>Apart from the overlay that paints them so the sequence can be tested. It is the kind of
 * pipeline that fails silently - a dropped step leaves a picture that is wrong rather than
 * absent - and a private method inside a Swing overlay is reachable only by looking at it.
 */
public final class PieceRegions {

    private PieceRegions() {
    }

    /**
     * Shapes every piece and cleans it up into the regions to fill.
     *
     * @param pieces          the pieces of void, as the walk closed them
     * @param edgeInset       which edges take the channel and how deep it is; under
     *                        {@link kmu.maplayers.base.geometry.EdgeInsetRule#NOWHERE} what is
     *                        shaped is the partition itself, which still wants the resolve
     * @param miterSpikeLimit multiple of the inset past which a sharp corner bevels
     * @param smoothing       the sector-wide profile, the same one the cluster borders take
     * @return one region per body left, each with its own holes; a piece the inset folded over
     *         contributes none
     */
    public static List<RingRegion> collectDrawableRegions(
            List<Face> pieces,
            EdgeInset edgeInset,
            double miterSpikeLimit,
            BorderSmoothingStyle smoothing) {

        var regions = new ArrayList<RingRegion>();

        for (var piece : pieces) {

            var shaped = PieceShaper.shapePiece(piece, edgeInset, miterSpikeLimit);

            if (shaped.outerRing().isEmpty()) {
                continue;
            }
            regions.addAll(PolygonRegions.groupRingsIntoRegions(
                BorderSmoothing.resolveSmoothedBorderLoops(shaped.toRings(), smoothing)));
        }
        return List.copyOf(regions);
    }
}
