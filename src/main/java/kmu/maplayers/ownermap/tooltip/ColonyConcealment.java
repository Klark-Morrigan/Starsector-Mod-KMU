package kmu.maplayers.ownermap.tooltip;

/**
 * The three ways one colony may be out of plain view, which a box weighs together because they only
 * mean anything together.
 *
 * <p>A value rather than three booleans on the facts beside it, because adjacent booleans are
 * transposable without failing, and these three are the transposition that would go furthest
 * unnoticed - all say something about being out of sight, so a line swapping two of them still
 * reads plausibly and simply calls out the wrong word.
 *
 * <p>They travel together rather than being asked apart because no one of them settles anything on
 * its own. Being undiscovered displaces concealing oneself, and concealing oneself only speaks
 * where the sector is not openly pointing at the place - so a reader holding one of the three and
 * not the others can state a finding the other two would have withdrawn.
 *
 * <p>Nothing here is derived. Each is a separate fact about the colony, read from a separate place -
 * the entity's own flag, the market's, and an identity a composition root supplies - and the rules
 * that play them against each other belong to whatever is choosing words, not to the carrier.
 *
 * @param isDiscoveredByPlayer whether the player has discovered the colony's entity. Whether an
 *                             undiscovered colony reaches a list at all is the listing box's own
 *                             question; this decides the word once it is on one
 * @param isHiddenMarket       whether the colony conceals itself rather than being held in the open
 * @param isOpenlyKnownMarket  whether that concealment is public knowledge - a landmark keeping no
 *                             comm directory rather than a base hiding from anyone. Its own fact
 *                             beside the concealment rather than a correction to it, the colony
 *                             being concealed in every sense a visibility rule cares about
 */
public record ColonyConcealment(
    boolean isDiscoveredByPlayer,
    boolean isHiddenMarket,
    boolean isOpenlyKnownMarket) {
}
