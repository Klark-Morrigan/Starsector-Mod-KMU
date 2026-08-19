package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ranks the factions present in one hovered system into the two-tier standings the cell tooltip
 * shows, grouped by the active view.
 *
 * <p>Turns a single hovered system into the breakdown the tooltip lists, reusing the map's own
 * dominance weighting so the numbers match the fills and its own {@link HolderGrouping} so a
 * group's aggregate is exactly the territory that view paints as one bloc. Each faction's
 * {@link MarketFootprint#totalWeight()} folds into its group; groups rank by their summed score and
 * a group's members rank by their own, ties breaking by id at both tiers so the ordering is total.
 *
 * <p>Presence is what puts a faction on the list, not weight - so the list is built from who is in
 * the system, with the weighed factions among them, rather than from the weighing plus whatever it
 * missed. A faction holding nothing in the system but a colony the economy does not list raises no
 * footprint - every term of a dominance weight being economy-fed - and takes a
 * {@link PresenceOnlyFactionStanding} at nought rather than going unlisted, so the box over a cell
 * names every faction the band inside it draws a run for. Such a standing settles at the foot of
 * its group on the score it carries, and cannot move a fill: holding is resolved off the
 * footprints, which it never enters.
 *
 * <p>Keying the fold on the grouping rather than hardcoding "faction" is what makes the alliances
 * tooltip fall out of the same read plus one grouped sum: the faction view is the identity grouping,
 * where every group is a singleton and renders flat, and the alliances view is a bloc grouping,
 * where a group is a bloc with its members nested. One code path serves both.
 *
 * <p>The grouped ranking is a pure rule over hand-built inputs and a grouping, kept free of the
 * live economy so it is exercised directly on those inputs; the live entry only reads the one
 * hovered system's footprints ({@link KnownMarketFootprints}) and the factions present in it
 * ({@link HolderPass#readKnownColonyFactionIds}) before handing them to that rule.
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
     * reveal, and grouping: reads who is present in the system beside each faction's footprint in
     * it, then folds and ranks both through the pure rule below. The live entry the tooltip
     * resolves a hover through.
     *
     * <p>Both reads come off the pass's one walk of the system, so who is present and what was
     * weighed for them are two readings of one set of colonies rather than two walks that can part
     * company.
     *
     * @param system the hovered system whose colonies are ranked
     * @param pass   the weighting rule, dev reveal, grouping and colony walk this ranking resolves
     *               under, sampled once by the caller so the whole ranking uses one set of knobs
     * @return the system's groups ranked descending by summed score, each with its members ranked
     *         within; empty when the player knows of no colony there
     */
    public static List<GroupStanding> rankByDominationScore(
            StarSystemAPI system,
            DominancePass pass) {
        return rankByDominationScore(
            pass.readFootprintsByFaction(system),
            pass.readKnownColonyFactionIds(system),
            pass.grouping());
    }

    /**
     * Folds a system's per-faction footprints into two-tier standings under a grouping: each faction
     * becomes a member of its group, groups rank by their summed score, and each group's members
     * rank within it, ties breaking by id at both tiers. The pure rule the live entry delegates to,
     * so it is exercised on hand-built footprints and a hand-built grouping.
     *
     * @param footprintByFactionId each faction's footprint in the hovered system; an empty map means
     *                             the pass weighed no colony there
     * @param presentFactionIds    everyone holding a colony in the system, which the footprints are
     *                             a subset of - a faction here with no footprint takes a
     *                             presence-only standing at nought
     * @param grouping             the active view's grouping folding factions into groups; the
     *                             identity grouping yields one singleton group per faction
     * @return the groups ranked descending by summed score, each with its members ranked within;
     *         empty when nobody is present
     */
    public static List<GroupStanding> rankByDominationScore(
            Map<String, MarketFootprint> footprintByFactionId,
            Set<String> presentFactionIds,
            HolderGrouping grouping) {

        var membersByBlocId = collectMembersByBloc(
            footprintByFactionId,
            presentFactionIds,
            grouping);
        var groups = new ArrayList<GroupStanding>(membersByBlocId.size());

        for (var entry : membersByBlocId.entrySet()) {
            groups.add(rankGroup(entry.getKey(), entry.getValue()));
        }
        groups.sort(GROUP_ORDER);
        return List.copyOf(groups);
    }

    // Buckets each faction's standing under its group's bloc id, so an alliance's members land in
    // one bucket while every other faction is its own. First-seen bloc order is kept here only for a
    // stable build; the caller sorts the groups into ranked order regardless.
    //
    // The weighed factions are folded first, so a faction the pass did weigh keeps the standing its
    // arithmetic earned and is passed over on the presence walk. Taking presence as the wider set
    // rather than as the leftovers of the weighing is what makes that safe: whoever is in the
    // system reaches a standing of one kind or the other, whatever the weighed side goes on to
    // exclude.
    private static Map<String, List<FactionStanding>> collectMembersByBloc(
            Map<String, MarketFootprint> footprintByFactionId,
            Set<String> presentFactionIds,
            HolderGrouping grouping) {

        var membersByBlocId = new LinkedHashMap<String, List<FactionStanding>>();

        for (var entry : footprintByFactionId.entrySet()) {
            addMember(
                membersByBlocId,
                grouping,
                new WeighedFactionStanding(entry.getKey(), entry.getValue().totalWeight()));
        }
        for (var factionId : presentFactionIds) {
            if (!footprintByFactionId.containsKey(factionId)) {
                addMember(membersByBlocId, grouping, new PresenceOnlyFactionStanding(factionId));
            }
        }
        return membersByBlocId;
    }

    // Files one standing under the bloc its faction folds into, the two kinds being bucketed the
    // same way: what a standing is made of is the ranking's business and never the grouping's.
    //
    // A faction the grouping can name no bloc for is left out, on the same rule the per-bloc folds
    // beside this one apply: a nameless key would travel on as a bloc, and the box would then be
    // asked to draw a line for one - which it cannot name, and which either tie-break below would
    // fault on the moment a second group stood beside it.
    private static void addMember(
            Map<String, List<FactionStanding>> membersByBlocId,
            HolderGrouping grouping,
            FactionStanding standing) {

        var blocId = grouping.resolveBlocId(standing.factionId());

        if (blocId != null) {
            membersByBlocId
                .computeIfAbsent(blocId, id -> new ArrayList<>())
                .add(standing);
        }
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
