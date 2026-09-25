package kmu.maplayers.politicalmap.dominance.standings;

import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;

/**
 * One faction's ranked place in a single hovered system where the pass weighed its colonies: the
 * faction and the domination score it holds there.
 *
 * <p>The score is the faction's {@link MarketFootprint#totalWeight()} in the system, so a member's
 * ranking uses the same stability-scaled dominance weight the map paints its fills by rather than a
 * second measure. A nought here is one the arithmetic arrived at - a colony weighed and found to be
 * worth nothing - which is why it is a different statement from the nought
 * {@link PresenceOnlyFactionStanding} carries.
 *
 * @param factionId the faction holding weighed markets in the hovered system
 * @param score     the faction's summed domination weight in that system
 */
public record WeighedFactionStanding(
    String factionId,
    int score) implements FactionStanding {
}
