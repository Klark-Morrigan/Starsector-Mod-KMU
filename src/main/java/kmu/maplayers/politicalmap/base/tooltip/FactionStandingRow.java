package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.FactionStanding;

/**
 * One faction's rendered row in a hovered system's standings breakdown: the faction's full title, its
 * crest, and the domination score it holds there. The presentation-ready counterpart of a pure {@link
 * FactionStanding}, with the id already turned into the name and crest the tooltip draws.
 *
 * <p>Plain data with no Starsector types - the crest is a sprite path, not a loaded sprite, so the
 * render layer owns the actual load. The path is null when the faction has no authored crest, which
 * the row draws around by showing its name alone; the score is carried straight through from the
 * standing, so a member's rendered rank reads off the same weight the map painted its fill by.
 *
 * @param factionId       the faction this row stands for, kept for identity
 * @param fullName        the faction's long display title, the label the row draws
 * @param crestSpritePath the faction's crest sprite path, or null when it has no authored crest
 * @param score           the faction's domination weight in the hovered system
 */
public record FactionStandingRow(
    String factionId,
    String fullName,
    String crestSpritePath,
    int score) {
}
