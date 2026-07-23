package kmu.maplayers.politicalmap.base.dominance;

/**
 * One faction's ranked place in a single hovered system: the faction and the domination score it
 * holds there. The lower tier of the two-tier standings the cell tooltip shows - a group's member
 * factions, each with its own score, ranked beneath the group.
 *
 * <p>The score is the faction's {@link MarketFootprint#totalWeight()} in the system, so a member's
 * ranking uses the same stability-scaled dominance weight the map paints its fills by rather than a
 * second measure. Kept to plain ids and an int with no Starsector types, so the ranking is
 * unit-testable on hand-built inputs; turning the id into a crest and a display name is a later,
 * separate step's job.
 *
 * @param factionId the faction holding markets in the hovered system
 * @param score     the faction's summed domination weight in that system
 */
public record FactionStanding(String factionId, int score) {
}
