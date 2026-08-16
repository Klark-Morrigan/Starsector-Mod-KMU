package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ranks the factions holding markets in one hovered system into the two-tier standings the cell
 * tooltip shows, grouped by the active view.
 *
 * <p>Turns a single hovered system into the breakdown the tooltip lists, reusing the map's own
 * dominance weighting so the numbers match the fills and its own {@link HolderGrouping} so a
 * group's aggregate is exactly the territory that view paints as one bloc. Each faction's
 * {@link MarketFootprint#totalWeight()} folds into its group; groups rank by their summed score and
 * a group's members rank by their own, ties breaking by id at both tiers so the ordering is total.
 *
 * <p>Keying the fold on the grouping rather than hardcoding "faction" is what makes the alliances
 * tooltip fall out of the same read plus one grouped sum: the faction view is the identity grouping,
 * where every group is a singleton and renders flat, and the alliances view is a bloc grouping,
 * where a group is a bloc with its members nested. One code path serves both.
 *
 * <p>The grouped ranking is a pure rule over hand-built footprints and a grouping, kept free of the
 * live economy so it is exercised directly on hand-built inputs; the live entry only reads the one
 * hovered system's footprints ({@link KnownMarketFootprints}) before handing them to that rule.
 */
public final class SystemStandings {

    // Groups rank descending by their summed score, a tie falling to the lowest bloc id so the order
    // is total and never depends on the economy walk order the footprints arrive in.
    private static final Comparator<GroupStanding> GROUP_ORDER =
        Comparator
            .comparingInt(GroupStanding::aggregateScore)
            .reversed()
            .thenComparing(GroupStanding::blocId);

    // A group's members rank descending by their own score, a tie falling to the lowest faction id
    // on the same total-order rule as the groups above them.
    private static final Comparator<FactionStanding> MEMBER_ORDER =
        Comparator
            .comparingInt(FactionStanding::score)
            .reversed()
            .thenComparing(FactionStanding::factionId);

    private SystemStandings() {
    }

    /**
     * Ranks the one hovered system's factions into two-tier standings under a pass's rule, dev
     * reveal, and grouping: reads each faction's footprint in the system, then folds and ranks it
     * through the pure rule below. The live entry the tooltip resolves a hover through.
     *
     * @param system the hovered system whose markets are ranked
     * @param pass   the weighting rule, dev reveal, grouping and colony walk this ranking resolves
     *               under, sampled once by the caller so the whole ranking uses one set of knobs
     * @return the system's groups ranked descending by summed score, each with its members ranked
     *         within; empty when the system holds no known owned market
     */
    public static List<GroupStanding> rankByDominationScore(
            StarSystemAPI system,
            DominancePass pass) {
        return rankByDominationScore(
            pass.readFootprintsByFaction(system),
            pass.grouping());
    }

    /**
     * Folds a system's per-faction footprints into two-tier standings under a grouping: each faction
     * becomes a member of its group, groups rank by their summed score, and each group's members
     * rank within it, ties breaking by id at both tiers. The pure rule the live entry delegates to,
     * so it is exercised on hand-built footprints and a hand-built grouping.
     *
     * @param footprintByFactionId each faction's footprint in the hovered system; an empty map means
     *                             the system holds no known owned market
     * @param grouping             the active view's grouping folding factions into groups; the
     *                             identity grouping yields one singleton group per faction
     * @return the groups ranked descending by summed score, each with its members ranked within;
     *         empty when the footprint map is empty
     */
    public static List<GroupStanding> rankByDominationScore(
            Map<String, MarketFootprint> footprintByFactionId,
            HolderGrouping grouping) {

        var membersByBlocId = collectMembersByBloc(footprintByFactionId, grouping);
        var groups = new ArrayList<GroupStanding>(membersByBlocId.size());

        for (var entry : membersByBlocId.entrySet()) {
            groups.add(rankGroup(entry.getKey(), entry.getValue()));
        }
        groups.sort(GROUP_ORDER);
        return List.copyOf(groups);
    }

    // Buckets each faction's footprint under its group's bloc id, so an alliance's members land in
    // one bucket while every other faction is its own. First-seen bloc order is kept here only for a
    // stable build; the caller sorts the groups into ranked order regardless.
    private static Map<String, List<FactionStanding>> collectMembersByBloc(
            Map<String, MarketFootprint> footprintByFactionId,
            HolderGrouping grouping) {

        var membersByBlocId = new LinkedHashMap<String, List<FactionStanding>>();
        for (var entry : footprintByFactionId.entrySet()) {
            var blocId = grouping.resolveBlocId(entry.getKey());
            
            membersByBlocId
                .computeIfAbsent(blocId, id -> new ArrayList<>())
                .add(new FactionStanding(entry.getKey(), entry.getValue().totalWeight()));
        }
        return membersByBlocId;
    }

    // Ranks one group's members and sums their scores into the group's aggregate, so a group's own
    // ranking key is exactly the total of the members it lists.
    private static GroupStanding rankGroup(String blocId, List<FactionStanding> members) {
        members.sort(MEMBER_ORDER);
        var aggregateScore = 0;
        for (var member : members) {
            aggregateScore += member.score();
        }
        return new GroupStanding(blocId, aggregateScore, members);
    }
}
