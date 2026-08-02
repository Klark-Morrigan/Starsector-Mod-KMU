package kmu.maplayers.base.theme;

/**
 * The spike-sanding pass's half of the border smoothing: whether it runs, plus the shape it works
 * to - the tallest protrusion it splices out and the angle at which a protrusion counts as a
 * spike. Half of a {@link BorderSmoothingStyle}, which says why the two are held apart.
 *
 * @param shouldSandSpikes  whether the sanding pass runs at all; off leaves the traced loop's
 *                          spikes in place for the rounding pass to meet
 * @param spikeHeight       the tallest protrusion sanding splices out, in world units - a corner
 *                          further than this from its neighbour chord is real shape and stays
 * @param spikeAngleRadians the interior angle below which a corner is sharp enough to count as a
 *                          spike at all
 */
public record SpikeSandingStyle(
    boolean shouldSandSpikes,
    double spikeHeight,
    double spikeAngleRadians) {
}
