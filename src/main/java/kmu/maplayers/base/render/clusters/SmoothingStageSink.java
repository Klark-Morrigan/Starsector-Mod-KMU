package kmu.maplayers.base.render.clusters;

import java.util.List;

/**
 * Receives the loops after each smoothing pass that ran. A caller showing the stages reads them off
 * {@link BorderSmoothing#smoothBorderLoops(List, kmu.maplayers.base.theme.BorderSmoothingStyle,
 * SmoothingStageSink)}, the one place that orders and gates the passes, so what it shows is what the
 * map drew.
 *
 * <p>Told only of a pass that ran: an empty stage is how a diagnostic says that pass's gate was off.
 */
public interface SmoothingStageSink {

    /** The sink of a caller that wants only the finished loops. */
    SmoothingStageSink IGNORING_SINK = new SmoothingStageSink() {

        @Override
        public void captureDespikedStage(List<List<double[]>> loops) {
            // Nothing to keep: the caller reads the finished loops alone.
        }

        @Override
        public void captureRoundedStage(List<List<double[]>> loops) {
            // Nothing to keep: the caller reads the finished loops alone.
        }
    };

    /**
     * Receives the loops after spike sanding, when that pass ran.
     *
     * @param loops the sanded loops, each a closed ring of {@code {x, y}} vertices
     */
    void captureDespikedStage(List<List<double[]>> loops);

    /**
     * Receives the loops after corner rounding, when that pass ran.
     *
     * @param loops the rounded loops, each a closed ring of {@code {x, y}} vertices
     */
    void captureRoundedStage(List<List<double[]>> loops);
}
