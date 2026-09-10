package kmu.maplayers.base.geometry;

/**
 * The three distances a cluster-border trace is tuned by, which travel together because a ring is
 * only right against all three at once: the channel it insets by, the gap two reports of one corner
 * may differ by and still be chained as one, and how far a sharp corner's miter may spike before it
 * is bevelled.
 *
 * <p>One value rather than three arguments because they are read in one place and mean nothing
 * apart - a trace given two of them and a stale third traces a ring that is wrong in a way no
 * signature could catch, the three being doubles that compile in any order.
 *
 * @param borderInset         inward inset applied to each ring, matching the fills' channel so the
 *                            border lands on the fill edge
 * @param vertexWeldTolerance largest gap between two reports of a shared corner still welded into
 *                            one when chaining the boundary
 * @param miterSpikeLimit     a corner whose inset miter would spike past this multiple of
 *                            {@code borderInset} is bevelled instead of pointed, so a sharp cluster
 *                            corner never shoots an inward loop
 */
public record BorderTraceTolerances(
    double borderInset,
    double vertexWeldTolerance,
    double miterSpikeLimit) {
}
