package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.GroupStanding;

import java.util.List;

/**
 * One group's rendered row block in a hovered system's standings breakdown: the group header - its
 * display name, crest, and summed score - above the member factions that make it up. The
 * presentation-ready counterpart of a pure {@link GroupStanding}, with the group and its members
 * already resolved into the names and crests the tooltip draws.
 *
 * <p>The nested shape carries the two-tier ranking through to the render layer without it knowing
 * which view produced the rows: a faction-view group is a singleton whose header reuses its one
 * member's name and crest and renders flat, while an alliances-view group is a bloc whose header
 * carries the alliance's own name and crest above its indented members. Plain data with no Starsector
 * types - the crest is a sprite path the render layer loads - so a header with no authored crest
 * leaves the path null and draws its name alone.
 *
 * @param groupId         the group's bloc id - a faction id in the faction view, an alliance bloc id
 *                        in the alliances view
 * @param displayName     the group header's label - a lone faction's title, or an alliance's name
 * @param crestSpritePath the header's crest sprite path, or null when the header faction or bloc has
 *                        no authored crest
 * @param aggregateScore  the summed domination score of the members, the group's own ranking key
 * @param members         the member factions' rows, ranked as the standing ranked them; a
 *                        single-element list in the faction view
 */
public record StandingGroupRow(
        String groupId,
        String displayName,
        String crestSpritePath,
        int aggregateScore,
        List<FactionStandingRow> members) {

    public StandingGroupRow {
        members = List.copyOf(members);
    }
}
