package kmu.maplayers.base.render.clusters;

import kmlib.math.geometry.CornerRounding;
import kmlib.math.geometry.PolygonSmoothing;
import kmlib.opengl.PolygonTessellator;

import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.SpikeSandingStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * What stands between an inset ring and a drawable one: the two smoothing passes, and the
 * envelope resolve either side of them. Held apart from any one build so every pass over the
 * same loops cleans, sands and rounds them identically.
 *
 * <p>Each pass takes its own half of the smoothing profile as data rather than reading the live
 * settings where it runs. That is what makes "a lone cell's outline rounds exactly like the
 * border of the cluster beside it" hold by construction: both are handed one profile, so neither
 * can smooth to numbers the other never saw - which is what a knob read in one of the two places
 * and not the other would cause the moment it gained a per-save override. Taking only its own
 * half is the other half of that: sanding has no corner radius in scope and rounding no spike
 * angle, so neither pass can be handed the other's numbers at all.
 *
 * <p>Each pass is also honest mechanism - it always does what its name says. The on/off
 * decision is each half's own gate, applied by {@link #smoothBorderLoops} for a caller
 * that wants the finished loops, and left to the call site for a caller that captures every
 * stage as it goes.
 */
public final class BorderSmoothing {
    private BorderSmoothing() {
    }

    /**
     * Runs both passes over already-resolved border loops in the order they must run in -
     * sanding first, so the rounding arcs meet clean geometry - each only when its own half's
     * gate is on. The finished loops a border strokes and a fill is clipped to.
     *
     * @param loops the resolved border loops, one closed ring each
     * @param style the sector-wide smoothing profile, both halves' gates included
     * @return the smoothed loops; the same loops when both gates are off
     */
    public static List<List<double[]>> smoothBorderLoops(
            List<List<double[]>> loops,
            BorderSmoothingStyle style) {

        var smoothed = loops;
        if (style.spikeSanding().shouldSandSpikes()) {
            smoothed = sandBorderSpikes(smoothed, style.spikeSanding());
        }
        if (style.cornerRounding().shouldRoundCorners()) {
            smoothed = roundBorderCorners(smoothed, style.cornerRounding());
        }
        return smoothed;
    }

    /**
     * Takes inset rings all the way to the loops a border strokes: resolved to their clean
     * outer envelope, smoothed, and resolved again.
     *
     * <p>The whole of what stands between a miter inset and something drawable, in one place,
     * because every body shaped by a per-edge inset needs all of it and needs it in this order.
     * A miter inset of anything that pinches to a neck crosses itself there, and a ring narrower
     * than twice its channel folds right over; the positive-winding resolve drops the reversed
     * sub-loop of the first and the whole of the second. Smoothing has to come after that, since
     * an arc rounded onto a crossing is clipped back to a sharp point by it.
     *
     * <p>Resolved a second time after smoothing, because rounding a corner can push one arc
     * through another and open a crossing the first resolve could not have seen. That also
     * leaves every loop in the winding convention a grouping reads - outer counter-clockwise,
     * hole clockwise - which a smoothed-but-unresolved loop is not guaranteed to be.
     *
     * @param insetRings the rings as the inset left them, crossings and all
     * @param style      the sector-wide smoothing profile, both halves' gates included
     * @return the finished loops, wound outer counter-clockwise and hole clockwise
     */
    public static List<List<double[]>> resolveSmoothedBorderLoops(
            List<List<double[]>> insetRings,
            BorderSmoothingStyle style) {

        return PolygonTessellator.tessellateToBoundaryLoops(
            smoothBorderLoops(
                PolygonTessellator.tessellateToBoundaryLoops(insetRings),
                style));
    }

    /**
     * Splices out of every clean border loop the needle protrusions and inward cusps too thin
     * for rounding to fix (the arc's step-back clamps to their tiny edges), so a rounding pass
     * afterwards runs on clean geometry. Always sands - a caller staging the passes itself
     * gates this on the style's own spike-sanding switch.
     *
     * @param loops the border loops to sand
     * @param style the sanding half of the profile, supplying the spike height and angle
     * @return the sanded loops
     */
    public static List<List<double[]>> sandBorderSpikes(
            List<List<double[]>> loops,
            SpikeSandingStyle style) {

        return mapEachLoop(
            loops,
            loop -> PolygonSmoothing.removeSpikes(
                loop,
                style.spikeHeight(),
                style.spikeAngleRadians()));
    }

    /**
     * Rounds each border loop's corners into arcs with the style's corner shape, applied to
     * the resolved envelope rather than a self-crossing inset (a crossing would clip the arc
     * back to a sharp point). Always rounds - a caller staging the passes itself gates this on
     * the style's own corner-rounding switch.
     *
     * @param loops the border loops to round
     * @param style the rounding half of the profile, supplying the corner shape
     * @return the rounded loops
     */
    public static List<List<double[]>> roundBorderCorners(
            List<List<double[]>> loops,
            CornerRoundingStyle style) {

        return mapEachLoop(loops, loop -> roundLoopCorners(loop, style));
    }

    /**
     * Rounds one closed loop's corners. The single-loop entry point for an outline already
     * one convex ring and so needs no chaining or envelope resolve - an unowned cell's lone
     * outline - so that outline reaches the same rounding as a cluster border through the same
     * profile, rather than through a second call site that names the knobs again.
     *
     * @param loop  the closed loop to round
     * @param style the rounding half of the profile, supplying the corner shape
     * @return the rounded loop
     */
    public static List<double[]> roundLoopCorners(
            List<double[]> loop,
            CornerRoundingStyle style) {

        return PolygonSmoothing.roundCorners(
            loop,
            new CornerRounding(
                style.cornerRadius(),
                style.cornerSegments(),
                style.chamferAngleRadians(),
                style.roundBelowAngleRadians()));
    }

    // Applies one pass to every loop, collecting the results. Both passes are per-loop and
    // order-preserving, so the walk is stated once here rather than once in each of them.
    private static List<List<double[]>> mapEachLoop(
            List<List<double[]>> loops,
            UnaryOperator<List<double[]>> pass) {

        var passed = new ArrayList<List<double[]>>(loops.size());
        for (var loop : loops) {
            passed.add(pass.apply(loop));
        }
        return passed;
    }
}
