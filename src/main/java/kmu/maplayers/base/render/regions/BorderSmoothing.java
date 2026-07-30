package kmu.maplayers.base.render.regions;

import kmlib.math.geometry.PolygonSmoothing;

import kmu.maplayers.base.theme.BorderSmoothingStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The two cluster-border smoothing passes, held apart from any one build so every pass over
 * the same loops sands and rounds them identically.
 *
 * <p>Each pass takes the smoothing profile as data rather than reading the live settings where
 * it runs. That is what makes "a lone cell's outline rounds exactly like the border of the
 * cluster beside it" hold by construction: both are handed one profile, so neither can smooth
 * to numbers the other never saw - which is what a knob read in one of the two places and not
 * the other would cause the moment it gained a per-save override.
 *
 * <p>Each pass is also honest mechanism - it always does what its name says. The on/off
 * decision is the profile's own two gates, applied by {@link #smoothBorderLoops} for a caller
 * that wants the finished loops, and left to the call site for a caller that captures every
 * stage as it goes.
 */
public final class BorderSmoothing {
    private BorderSmoothing() {
    }

    /**
     * Runs both passes over already-resolved border loops in the order they must run in -
     * sanding first, so the rounding arcs meet clean geometry - each only when the profile's
     * own gate is on. The finished loops a border strokes and a fill is clipped to.
     *
     * @param loops the resolved border loops, one closed ring each
     * @param style the sector-wide smoothing profile, its two gates included
     * @return the smoothed loops; the same loops when both gates are off
     */
    public static List<List<double[]>> smoothBorderLoops(
            List<List<double[]>> loops,
            BorderSmoothingStyle style) {

        var smoothed = loops;
        if (style.shouldSandSpikes()) {
            smoothed = sandBorderSpikes(smoothed, style);
        }
        if (style.shouldRoundCorners()) {
            smoothed = roundBorderCorners(smoothed, style);
        }
        return smoothed;
    }

    /**
     * Splices out of every clean border loop the needle protrusions and inward cusps too thin
     * for rounding to fix (the arc's step-back clamps to their tiny edges), so a rounding pass
     * afterwards runs on clean geometry. Always sands - a caller staging the passes itself
     * gates this on the profile's spike-sanding switch.
     *
     * @param loops the border loops to sand
     * @param style the profile supplying the spike height and angle
     * @return the sanded loops
     */
    public static List<List<double[]>> sandBorderSpikes(
            List<List<double[]>> loops,
            BorderSmoothingStyle style) {

        return mapEachLoop(
                loops,
                loop -> PolygonSmoothing.removeSpikes(
                        loop,
                        style.spikeHeight(),
                        style.spikeAngleRadians()));
    }

    /**
     * Rounds each border loop's corners into arcs with the profile's corner shape, applied to
     * the resolved envelope rather than a self-crossing inset (a crossing would clip the arc
     * back to a sharp point). Always rounds - a caller staging the passes itself gates this on
     * the profile's corner-rounding switch.
     *
     * @param loops the border loops to round
     * @param style the profile supplying the corner shape
     * @return the rounded loops
     */
    public static List<List<double[]>> roundBorderCorners(
            List<List<double[]>> loops,
            BorderSmoothingStyle style) {

        return mapEachLoop(loops, loop -> roundLoopCorners(loop, style));
    }

    /**
     * Rounds one closed loop's corners. The single-loop entry point for ground that is already
     * one convex ring and so needs no chaining or envelope resolve - a factionless cell's lone
     * outline - so that outline reaches the same rounding as a cluster border through the same
     * profile, rather than through a second call site that names the knobs again.
     *
     * @param loop  the closed loop to round
     * @param style the profile supplying the corner shape
     * @return the rounded loop
     */
    public static List<double[]> roundLoopCorners(
            List<double[]> loop,
            BorderSmoothingStyle style) {

        return PolygonSmoothing.roundCorners(
                loop,
                style.cornerRadius(),
                style.cornerSegments(),
                style.chamferAngleRadians());
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
