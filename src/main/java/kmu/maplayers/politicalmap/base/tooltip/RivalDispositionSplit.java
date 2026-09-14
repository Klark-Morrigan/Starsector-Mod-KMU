package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.StandingFraction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * How the blocs standing against a system's holder divide by disposition: those on good terms with it,
 * those not, and how far each of those two statements reaches over each of them.
 *
 * <p>Apart from the routing that asks for it because the two answer different questions. The routing
 * settles a chain of axes - who is in the running, who holds the system, who stands with the holder by
 * alliance - and hands what is left to this, which knows nothing about any of them and only how a bloc
 * feels about the one it was handed.
 *
 * <p>A bloc its members agree about takes one side, and a bloc they do not is placed on both, each
 * placement holding only the members on its own side. Neither statement then overreaches, because the
 * fraction on the row says exactly how far it applies; the bloc stays one named thing under both; and
 * nothing is orphaned from it, which breaking it into loose factions would do to the one grouping the
 * map paints that territory by.
 *
 * <p>What splits a bloc is a disagreement among the members standing in the hovered system, since a
 * side with nobody in it would head a row with nothing beneath it - and the box has never listed
 * anyone who is not present. Where they agree, the bloc-level answer over both whole memberships
 * places it, so a bloc whose sour member holds nothing here still contests the system.
 *
 * <p>The fractions read whole either way, and the two tiers count different things: a bloc's row
 * counts its own roster, a member's counts how much of the holder it quarrels with. Neither counts who
 * happens to stand in the hovered system, which is what makes one alliance read the same over every
 * system it holds - so the rows and the fraction above them do not add up wherever a member holds
 * nothing here, and are not meant to.
 *
 * <p>Every disposition is read through {@link kmu.maplayers.politicalmap.base.dominance.BlocFriendliness}
 * from the faction being sorted, at both tiers, so which side a faction takes and what its row counts
 * cannot be answered out of pairs read in opposite directions.
 *
 * <p>Built in one walk and held, the sides being wanted one after another by a box laying its blocks
 * down.
 */
final class RivalDispositionSplit {

    private final List<RoutedStanding> friendlyRivals = new ArrayList<>();
    private final List<RoutedStanding> contestedRivals = new ArrayList<>();

    private final Set<String> holderMemberFactionIds;
    private final StandingBlockRules rules;

    private RivalDispositionSplit(
            Set<String> holderMemberFactionIds,
            StandingBlockRules rules) {

        this.holderMemberFactionIds = holderMemberFactionIds;
        this.rules = rules;
    }

    /**
     * Divides the holder's rivals by how each is disposed toward it.
     *
     * @param rivals                 the blocs standing against the holder, in the order they ranked
     * @param holderMemberFactionIds the holder's whole membership, which every rival is measured
     *                               against - resolved once by the caller, every read in one system
     *                               being against this one set
     * @param rules                  what a bloc is made of and how warm two factions are
     * @return the two sides, each in the order its blocs ranked
     */
    static RivalDispositionSplit splitByDisposition(
            List<GroupStanding> rivals,
            Set<String> holderMemberFactionIds,
            StandingBlockRules rules) {

        var split = new RivalDispositionSplit(holderMemberFactionIds, rules);

        for (var rival : rivals) {
            split.appendRival(rival);
        }
        return split;
    }

    /**
     * The rivals on good terms with the holder.
     *
     * @return their rows, in the order their blocs ranked
     */
    List<RoutedStanding> selectFriendlyRivals() {
        return List.copyOf(friendlyRivals);
    }

    /**
     * The rest, whether indifferent or hostile.
     *
     * @return their rows, in the order their blocs ranked
     */
    List<RoutedStanding> selectContestedRivals() {
        return List.copyOf(contestedRivals);
    }

    // One bloc, placed on one side or folded onto both. The members present are sorted first, because
    // it is their disagreement that decides which of the two happens.
    private void appendRival(GroupStanding rival) {

        var friendlyMembers = new ArrayList<FactionStanding>();
        var sourMembers = new ArrayList<FactionStanding>();

        for (var member : rival.members()) {

            var members = isMemberFriendly(member) ? friendlyMembers : sourMembers;
            members.add(member);
        }
        var blocMemberFactionIds = rules.readMemberFactionIds(rival.blocId());

        if (friendlyMembers.isEmpty() || sourMembers.isEmpty()) {

            var isBlocFriendly = rules
                .friendliness()
                .areBlocsFriendly(blocMemberFactionIds, holderMemberFactionIds);

            var side = isBlocFriendly ? friendlyRivals : contestedRivals;

            side.add(routeRivalSide(rival, rival.members(), isBlocFriendly, blocMemberFactionIds));
            return;
        }
        friendlyRivals.add(routeRivalSide(rival, friendlyMembers, true, blocMemberFactionIds));
        contestedRivals.add(routeRivalSide(rival, sourMembers, false, blocMemberFactionIds));
    }

    // One bloc as a single side lists it: the bloc under its own ID, holding only the members on that
    // side, over the fractions the row and its members state.
    //
    // A bloc folded onto both sides is weighed at what the members listed under each row come to,
    // rather than stating its whole weight twice.
    private RoutedStanding routeRivalSide(
            GroupStanding rival,
            List<FactionStanding> sideMembers,
            boolean isFriendlySide,
            Set<String> blocMemberFactionIds) {

        var sideStanding = sideMembers == rival.members()
            ? rival
            : GroupStanding.sumOverMembers(rival.blocId(), sideMembers);

        return new RoutedStanding(
            sideStanding,
            resolveBlocFraction(isFriendlySide, blocMemberFactionIds),
            resolveMemberFractions(sideMembers));
    }

    // How much of a bloc's own roster the statement over its row took.
    private StandingFraction resolveBlocFraction(
            boolean isFriendlySide,
            Set<String> blocMemberFactionIds) {

        var atOddsCount = rules
            .friendliness()
            .countMembersAtOddsWith(blocMemberFactionIds, holderMemberFactionIds);

        return new StandingFraction(
            isFriendlySide ? blocMemberFactionIds.size() - atOddsCount : atOddsCount,
            blocMemberFactionIds.size());
    }

    // How much of the holder each listed member is at odds with - a different question from the bloc's
    // own, and the reason the two are carried apart. Worked out for every member rather than only for
    // those standing against the holder, a member with no quarrel simply counting nought and stating
    // nothing.
    private Map<String, StandingFraction> resolveMemberFractions(
            List<FactionStanding> sideMembers) {

        var fractionsByFactionId = new HashMap<String, StandingFraction>();

        for (var member : sideMembers) {

            fractionsByFactionId.put(
                member.factionId(),
                new StandingFraction(
                    rules.friendliness().countFactionsAtOddsWith(
                        member.factionId(),
                        holderMemberFactionIds),
                    holderMemberFactionIds.size()));
        }
        return fractionsByFactionId;
    }

    // Whether one member of a bloc is on good terms with the holder, asked as the bloc of one it would
    // be listed as. Through the same rule the whole bloc is judged by rather than by reaching for the
    // faction-level answer underneath it, so a member deciding a split is measured by exactly the test
    // its bloc is - the empty-bloc reading included.
    private boolean isMemberFriendly(FactionStanding member) {

        return rules
            .friendliness()
            .areBlocsFriendly(Set.of(member.factionId()), holderMemberFactionIds);
    }
}
