package kmu.maplayers.base.render.clusters.debug;

import kmlib.opengl.GlVertexRuns;

import java.util.ArrayList;
import java.util.List;

/**
 * Gathers a border-smoothing pipeline's stages as a producer runs them, and closes into the
 * {@link ClusterBorderStageOverlay} a renderer draws.
 *
 * <p>Capturing every stage means running the passes one at a time rather than asking for the
 * finished loops, so a producer emits its three sets at three separate points inside its own
 * per-cluster loop. Handing it this rather than the three lists to append into is what keeps
 * which stage a loop set belongs to a named call instead of an argument position - two lists of
 * the same type, adjacent in a parameter list, are one transposition away from an overlay whose
 * colours say a pass ran that did not.
 *
 * <p>Loops arrive as geometry and are flattened to GL runs on the way in, so the conversion
 * happens once here instead of at each capture point, and a stage cannot be captured in the
 * wrong form.
 */
public final class ClusterBorderStageCollector {
    private final List<float[]> baseRuns = new ArrayList<>();
    private final List<float[]> despikedRuns = new ArrayList<>();
    private final List<float[]> roundedRuns = new ArrayList<>();

    /**
     * Closes the capture into the overlay to draw. The collector is not spent by this - a
     * producer that keeps capturing afterwards keeps growing the lists the overlay holds.
     *
     * @return the three stages as captured, in the order their loops arrived
     */
    public ClusterBorderStageOverlay buildOverlay() {
        return new ClusterBorderStageOverlay(baseRuns, despikedRuns, roundedRuns);
    }

    /**
     * Captures the traced border before any smoothing - the stage every cluster has, since it is
     * what the two passes run on.
     *
     * @param loops the traced loops, each a closed ring of {@code {x, y}} vertices
     */
    public void captureBaseStage(List<List<double[]>> loops) {
        baseRuns.addAll(GlVertexRuns.flattenLoops(loops));
    }

    /**
     * Captures the border after spike sanding. Called only when that pass actually ran: an empty
     * despiked stage is how the overlay says the sanding gate was off, so capturing the
     * unsanded loops here would report a pass that never happened.
     *
     * @param loops the sanded loops, each a closed ring of {@code {x, y}} vertices
     */
    public void captureDespikedStage(List<List<double[]>> loops) {
        despikedRuns.addAll(GlVertexRuns.flattenLoops(loops));
    }

    /**
     * Captures the border after corner rounding, under the same rule as the despiked stage: only
     * when the rounding pass ran.
     *
     * @param loops the rounded loops, each a closed ring of {@code {x, y}} vertices
     */
    public void captureRoundedStage(List<List<double[]>> loops) {
        roundedRuns.addAll(GlVertexRuns.flattenLoops(loops));
    }
}
