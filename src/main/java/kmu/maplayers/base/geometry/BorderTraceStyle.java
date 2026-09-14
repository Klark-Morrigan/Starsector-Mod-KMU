package kmu.maplayers.base.geometry;

/**
 * How a cluster-border trace cuts and chains its rings: which edges take the channel, and the
 * three distances the result is only right against all at once - the channel each of those edges
 * insets by, the gap two reports of one corner may differ by and still be chained as one, and how
 * far a sharp corner's miter may spike before it is bevelled.
 *
 * <p>One value rather than four arguments because they are read in one place and mean nothing
 * apart - a trace given three of them and a stale fourth traces a ring that is wrong in a way no
 * signature could catch, three being doubles that compile in any order and the fourth deciding
 * whether the first of them applies at all.
 *
 * <p>The rule travels with the depth for that reason above every other. Handed in separately it
 * became possible to cut the cells by one rule and outline them by another, which is how a
 * cluster's fill and its border came to stop in two different places.
 *
 * @param insetRule           which edges the channel is cut into; the same rule
 *                            {@link CellShaper} is given, so a cluster's outline and its cells'
 *                            fills stop in one place
 * @param borderInset         inward inset applied to each ring, matching the fills' channel so the
 *                            border lands on the fill edge
 * @param vertexWeldTolerance largest gap between two reports of a shared corner still welded into
 *                            one when chaining the boundary
 * @param miterSpikeLimit     a corner whose inset miter would spike past this multiple of
 *                            {@code borderInset} is bevelled instead of pointed, so a sharp cluster
 *                            corner never shoots an inward loop
 */
public record BorderTraceStyle(
    EdgeInsetRule insetRule,
    double borderInset,
    double vertexWeldTolerance,
    double miterSpikeLimit) {
}
