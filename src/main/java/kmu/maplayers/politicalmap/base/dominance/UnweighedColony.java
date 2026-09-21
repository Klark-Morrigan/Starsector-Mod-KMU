package kmu.maplayers.politicalmap.base.dominance;

import kmlib.starsector.entities.EntityNameplate;

import kmu.maplayers.base.visibility.colonies.ColonyKind;

/**
 * A colony an account names but never weighed: which colony it is, what kind of place it is,
 * whether it conceals itself, and how it is shown.
 *
 * <p>Everything a weighed colony's {@link MarketWeightBreakdown} carries beyond those is
 * arithmetic, and there is none here - the economy does not list the colony, so no term of a
 * dominance weight has anything to read. That absence is the point: nothing on this record can be
 * summed into a footprint, so a colony reaching a box this way can never reach the pass that paints
 * the system.
 *
 * <p>The identity travels beside the nameplate for the same reason it does on a weighed colony: a
 * box saying more about a colony than the account did - how current the player's knowledge of it
 * is, say - matches this against something read elsewhere, and a display name is not an identity.
 *
 * <p>The kind travels with it because this is the shape a collapsed colony and a derelict both
 * arrive in - each unowned, off-economy and listed at nought - and the account has no other way to
 * say that one is a place people still live and the other a wreck nobody ever lived on.
 *
 * <p>Concealment travels for the same reason it does on a weighed colony: it is a finding the line
 * calls out, and the two boxes name the same colonies of one system - so a colony the claims box
 * calls concealed and this one cannot is the disagreement a shared account exists to rule out.
 *
 * @param marketId       which colony this is, as the economy and the sector's own records name it
 * @param kind           what kind of place the colony is, read on the walk that met it so it can
 *                       only ever describe the colony named beside it
 * @param isHiddenMarket whether the colony conceals itself rather than being held in the open
 * @param nameplate      how the colony is identified to a reader - its name and the glyph the
 *                       sector map marks it with
 */
public record UnweighedColony(
    String marketId,
    ColonyKind kind,
    boolean isHiddenMarket,
    EntityNameplate nameplate) {
}
