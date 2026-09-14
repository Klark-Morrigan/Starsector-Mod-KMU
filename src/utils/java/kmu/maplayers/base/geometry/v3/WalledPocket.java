package kmu.maplayers.base.geometry.v3;

import kmu.maplayers.base.geometry.DiscUnionBoundary;

import java.util.List;

/**
 * One coast pocket and the reaches of coast that closed it.
 *
 * <p>Paired because neither means much without the other: a run of outline is only over the
 * line when it is over the line THIS pocket closes on, and a reach only accuses the pocket it
 * actually walled.
 *
 * <p>Its own type rather than one nested in the fault check, because the CONSTRUCTION builds it
 * and hands it back - a pocket paired with what walled it is what {@link CoastPockets} produces,
 * and nesting it in the check made the thing being judged named after the judging.
 *
 * @param pocket  the pocket
 * @param reaches the coast reaches it closes on
 */
public record WalledPocket(
    VoidPockets.VoidPocket pocket,
    List<DiscUnionBoundary.Chord> reaches) {
}
