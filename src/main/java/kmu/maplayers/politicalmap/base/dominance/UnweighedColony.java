package kmu.maplayers.politicalmap.base.dominance;

import kmlib.starsector.entities.EntityNameplate;

/**
 * A colony an account names but never weighed: which colony it is, and how it is shown.
 *
 * <p>Everything a weighed colony's {@link MarketWeightBreakdown} carries beyond these two is
 * arithmetic, and there is none here - the economy does not list the colony, so no term of a
 * dominance weight has anything to read. That absence is the point: an identity and a nameplate
 * cannot be summed into a footprint, so a colony reaching a box this way can never reach the pass
 * that paints the system.
 *
 * <p>The identity travels beside the nameplate for the same reason it does on a weighed colony: a
 * box saying more about a colony than the account did - how current the player's knowledge of it
 * is, say - matches this against something read elsewhere, and a display name is not an identity.
 *
 * @param marketId  which colony this is, as the economy and the sector's own records name it
 * @param nameplate how the colony is identified to a reader - its name and the glyph the sector
 *                  map marks it with
 */
public record UnweighedColony(
    String marketId,
    EntityNameplate nameplate) {
}
