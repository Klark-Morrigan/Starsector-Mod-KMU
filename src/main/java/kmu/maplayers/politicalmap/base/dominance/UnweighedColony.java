package kmu.maplayers.politicalmap.base.dominance;

import kmlib.starsector.entities.EntityMapIcon;

import java.util.Optional;

/**
 * A colony the dominance pass never weighed: one hung on a system's own entity that the economy
 * does not list, and so one the weight read is handed nothing to weigh.
 *
 * <p>Vanilla builds such colonies on purpose - a real market on a real station, deliberately left
 * out of the economy - so a player can be looking straight at a station flying a faction's colours
 * that every economy-fed read reports as nobody's. What the box does with it is name it, lead the name
 * with the glyph the map drew, and say it counted for nothing - which is why those two are the whole of
 * this value, there being no arithmetic behind it to carry.
 *
 * <p>Its own type rather than a zeroed {@link MarketWeightBreakdown}, for two reasons. That record
 * is the arithmetic behind a weight, and its contract parts an absent factor from a zeroed one -
 * this colony has neither, so building one would need a base-size factor nobody may read. And the
 * separation is the guarantee: zero weight is not absence on this side, a weightless colony still
 * marking presence and painting its system unopposed, so a value that could be summed into a
 * footprint would leave the pass one forgotten branch away from painting a system for a faction the
 * mechanic never counted.
 *
 * @param marketName the colony's display name
 * @param marketIcon the glyph the sector map marks the colony's own entity with, or empty where it
 *                   carries none. An unregistered colony wants the prefix for the reason a counted one
 *                   does - it is the one thing the box and the map can share at a glance - and all the
 *                   more so here, the map's glyph being the only trace of it the player has
 */
public record UnweighedColony(
    String marketName,
    Optional<EntityMapIcon> marketIcon) {
}
