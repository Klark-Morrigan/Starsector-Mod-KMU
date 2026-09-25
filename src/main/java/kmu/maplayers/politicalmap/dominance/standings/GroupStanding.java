package kmu.maplayers.politicalmap.dominance.standings;

import java.util.List;

/**
 * One group's ranked place in a single hovered system: the group's summed domination score and the
 * member factions that make it up, ranked beneath it. The upper tier of the two-tier standings the
 * cell tooltip shows.
 *
 * <p>A group is whatever the active view groups holders by - a lone faction in the faction view,
 * an alliance in the alliances view - so the aggregate score is exactly the territory that view
 * paints as one bloc. In the faction view a group is a singleton: its one member is the group
 * itself, and the tooltip renders it flat. In the alliances view a group is a bloc whose summed
 * score sits above its member factions.
 *
 * <p>The aggregate is the sum of the members' scores, so a group's ranking against another group
 * reads off the same weights its members are ranked by. Kept to plain IDs and ints with no
 * Starsector types, so the ranking is plain arithmetic.
 *
 * @param blocId         the group's bloc ID - a faction ID in the faction view, an alliance bloc ID
 *                       in the alliances view
 * @param aggregateScore the sum of the members' domination scores, the group's own ranking key
 * @param members        the member factions holding markets in the hovered system, ranked
 *                       descending by their own score; a single-element list in the faction view
 */
public record GroupStanding(
    String blocId,
    int aggregateScore,
    List<FactionStanding> members) {

    public GroupStanding {
        members = List.copyOf(members);
    }

    /**
     * Builds a group over the members it lists, summing their scores into its aggregate.
     *
     * <p>The one place that sum is worked out, since it is the whole of what the aggregate means: a
     * caller adding the scores up itself states the same rule a second time, and two statements of it
     * are two chances for a group's ranking key to stop being the total of what it lists.
     *
     * @param blocId  the group's bloc ID
     * @param members the member factions the group is listed over, in the order they are listed
     * @return the group, weighed at what its members come to between them
     */
    public static GroupStanding sumOverMembers(String blocId, List<FactionStanding> members) {

        var aggregateScore = 0;

        for (var member : members) {
            aggregateScore += member.score();
        }
        return new GroupStanding(blocId, aggregateScore, members);
    }

    /**
     * Whether the pass weighed anything at all for this group - one member with a footprint is
     * enough, the aggregate then being a sum somebody worked out.
     *
     * <p>Read off the members rather than carried beside the aggregate, so what the group's number
     * means and what it is the sum of cannot come to disagree. A group with none is a bloc present
     * through unregistered colonies alone: its nought is the box's statement about it rather than a
     * weight it competed with, which is the one thing a reader drawing that number has to know.
     *
     * @return true where at least one member's score was weighed
     */
    public boolean hasWeighedMember() {

        for (var member : members) {
            if (member instanceof WeighedFactionStanding) {
                return true;
            }
        }
        return false;
    }
}
