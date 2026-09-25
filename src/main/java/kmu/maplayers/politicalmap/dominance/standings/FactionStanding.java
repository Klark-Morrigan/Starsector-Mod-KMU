package kmu.maplayers.politicalmap.dominance.standings;

import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;

/**
 * One faction's ranked place in a single hovered system, of which there are exactly two kinds: a
 * presence the pass weighed, and a presence it never weighed. The lower tier of the two-tier
 * standings the cell tooltip shows - a group's member factions, each with its own score, ranked
 * beneath the group.
 *
 * <p>Sealed because the two differ in what their number means. A weighed standing's score is the
 * faction's {@link MarketFootprint#totalWeight()}, worked out from the colonies the economy lists;
 * a presence-only standing's is a nought nobody computed, the faction holding nothing the pass had
 * anything to weigh. Both are ranked, listed and counted alike, so the ranking walks one list - and
 * only a reader stating how loudly the number is to be read routes on the kind, which the seal
 * makes exhaustive.
 *
 * <p>Stated in the type rather than as a flag beside the score, so a presence-only standing carrying
 * a weight it was never given cannot be written. Kept to plain IDs and an int with no Starsector
 * types, so the ranking is plain arithmetic; turning the ID into a crest and a display name is the
 * tooltip's job.
 *
 * <p>A presence-only standing can never take a system: dominance holding is resolved off footprints
 * rather than off standings, and an unweighed colony raises none. Carrying one therefore widens what
 * a hover reports without moving any fill.
 */
public sealed interface FactionStanding
        permits WeighedFactionStanding, PresenceOnlyFactionStanding {

    /**
     * @return the ID of the faction this standing belongs to
     */
    String factionId();

    /**
     * @return the faction's summed domination weight in the hovered system
     */
    int score();
}
