package kmu.maplayers.base.geometry;

/**
 * How a cluster-border trace cuts and chains its rings: the channel it insets by, and the two
 * tolerances the result is only right against at once - the gap two reports of one corner may differ
 * by and still be chained as one, and how far a sharp corner's miter may spike before it is bevelled.
 *
 * <p>One value rather than loose arguments because they are read in one place and mean nothing
 * apart - a trace given the tolerances and a stale inset traces a ring that is wrong in a way no
 * signature could catch.
 *
 * <p>The channel travels as the {@link EdgeInset} {@link CellShaper} is given, so the cells cannot be
 * cut by one rule and outlined by another, and a cluster's fill and its border stop in one place.
 *
 * @param edgeInset           which edges the channel is cut into and how far each pulls in
 * @param vertexWeldTolerance largest gap between two reports of a shared corner still welded into
 *                            one when chaining the boundary
 * @param miterSpikeLimit     a corner whose inset miter would spike past this multiple of the
 *                            channel depth is bevelled instead of pointed, so a sharp cluster corner
 *                            never shoots an inward loop
 */
public record BorderTraceStyle(
    EdgeInset edgeInset,
    double vertexWeldTolerance,
    double miterSpikeLimit) {
}
