package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.ContestSide;
import kmu.maplayers.politicalmap.base.dominance.ContestSides;
import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which block each of a hovered system's ranked groups is listed under, settled once for the whole
 * box.
 *
 * <p>The blocks nest as one chain of axes rather than as tests of equal standing. The outermost is
 * whether a bloc is in the running at all: everyone barred from the contest is taken out before a
 * holder is picked, so a bloc that takes no part in one is never named as holding a system whatever
 * it outranked to get there. The strongest of those left holds it, and the rest are placed either
 * side of that holder by {@link ContestSides} - the one placement every surface reporting a contest
 * routes through - rather than by comparing blocs again here.
 *
 * <p>Disposition sorts inside that split and never across it. What stands with the holder by
 * alliance is already out, so a bloc on good terms with it is taken from what alliance left standing
 * against it and from nowhere else: an ally who is merely favourable is still an ally, and one gone
 * sour is still an ally too.
 *
 * <p>A bloc is sorted whole where its members agree and broken up where they do not. Every member
 * present is then listed on its own, in whichever block its own disposition puts it, still stating
 * the bloc it belongs to - the alternative being a heading asserting a friendliness half the bloc
 * does not have, which is the fault the block exists to fix one level in. The two blocks placed by
 * membership never break up: who holds a system and who is allied to it are facts about the bloc
 * rather than about how its members feel.
 *
 * <p>Answered in one pass and held, because a box lays its blocks down one after another and asking
 * per block would re-derive the whole split each time, off inputs that could be sampled apart.
 *
 * <p>Plain data with no Starsector types: everything the placement turns on arrives as
 * {@link StandingBlockRules}, so the routing is exercised on hand-built standings.
 */
public final class StandingBlockRouting {

    // How many of those in the running the box names as holding the system: the one whose colour the
    // map fills the cell in. Everyone ranked below it either stands with it or contests it.
    private static final int HOLDING_GROUP_COUNT = 1;

    private final Map<StandingBlock, List<RoutedStanding>> standingsByBlock;

    private StandingBlockRouting(Map<StandingBlock, List<RoutedStanding>> standingsByBlock) {

        this.standingsByBlock = standingsByBlock;
    }

    /**
     * Places every ranked group in the block it is listed under.
     *
     * @param rankedStandings the system's groups, strongest first
     * @param rules           what the placement turns on: who is in the running, who stands with the
     *                        holder by alliance, who stands with it in disposition, and what a bloc
     *                        is made of
     * @return where each group is listed
     */
    public static StandingBlockRouting routeRankedStandings(
            List<GroupStanding> rankedStandings,
            StandingBlockRules rules) {

        var contenders = new ArrayList<GroupStanding>();
        var nonPolitical = new ArrayList<RoutedStanding>();

        // The outer axis, taken before anything else is decided: a bloc barred from the contest
        // cannot become the holder by ranking above everyone, and cannot be lifted into the allied
        // block by an alliance set either, both of those being questions about the contest it is
        // outside of.
        for (var standing : rankedStandings) {
            if (rules.candidacy().test(standing.blocId())) {
                contenders.add(standing);
            } else {
                nonPolitical.add(RoutedStanding.routeWhole(standing));
            }
        }
        var holder = contenders
            .stream()
            .limit(HOLDING_GROUP_COUNT)
            .toList();

        // Everyone in the running but the holder, which is the pool both blocks below it are drawn
        // from. Dropped once here rather than per block, so no routing rule can readmit the holder
        // to a block that is by definition about somebody else.
        var contestants = contenders
            .stream()
            .skip(HOLDING_GROUP_COUNT)
            .toList();

        var holderBlocId = resolveHolderBlocId(holder);
        var sides = new ContestSides(holderBlocId, rules.affiliation());

        // Resolved once, after the holder is picked and before anybody is measured against it: every
        // disposition read in one system is against this one membership, and resolving it per
        // contestant would scan the same fold again for each of them to arrive at the same answer.
        var holderMemberFactionIds = rules.readMemberFactionIds(holderBlocId);

        var rivals = sortRivalsByDisposition(
            sides.selectSide(ContestSide.RIVAL, contestants, GroupStanding::blocId),
            holderMemberFactionIds,
            rules);

        var standingsByBlock = new EnumMap<StandingBlock, List<RoutedStanding>>(StandingBlock.class);

        // Every block is filled by walking the block set itself, and what each one takes is answered
        // by a switch with no default arm. That is what makes the closed set worth being one: a block
        // added to StandingBlock and not answered for here fails to compile, where a map filled by a
        // put per block would simply have drawn a block that never filled. Adding a default arm - or
        // reaching for one to silence the error - gives that guarantee away.
        for (var block : StandingBlock.values()) {

            standingsByBlock.put(block, switch (block) {
                case HOLDER -> routeWhole(holder);
                case ALLIED -> routeWhole(
                    sides.selectSide(ContestSide.ALLIED, contestants, GroupStanding::blocId));
                case FRIENDLY -> List.copyOf(rivals.friendly());
                case CONTESTED -> List.copyOf(rivals.contested());
                case NON_POLITICAL -> List.copyOf(nonPolitical);
            });
        }
        return new StandingBlockRouting(standingsByBlock);
    }

    /**
     * The groups one block lists, in the order they ranked.
     *
     * <p>Every block is answered, the routing having filled them all; a block with nothing in it
     * comes back empty, which is what drops its heading.
     *
     * @param block the block being laid down
     * @return its groups
     */
    public List<RoutedStanding> selectStandingsIn(StandingBlock block) {

        return standingsByBlock.get(block);
    }

    /**
     * Whether anybody stands in the hovered system at all - everyone the ranking found, whether or
     * not they were in the running for it.
     *
     * <p>Read off the placement rather than off the ranking beside it, so a caller asking what the
     * box has to list and a caller laying the blocks down cannot be answered from two readings of
     * one system.
     *
     * @return true where at least one group was placed in a block
     */
    public boolean hasAnyStanding() {

        return standingsByBlock
            .values()
            .stream()
            .anyMatch(standings -> !standings.isEmpty());
    }

    // Everything standing against the holder, sorted by how it is disposed toward it - walked once,
    // so a bloc's members are measured against the holder one time however the bloc ends up listed.
    private static RivalsByDisposition sortRivalsByDisposition(
            List<GroupStanding> rivals,
            Set<String> holderMemberFactionIds,
            StandingBlockRules rules) {

        var sortedRivals = new RivalsByDisposition(new ArrayList<>(), new ArrayList<>());

        for (var rival : rivals) {
            appendRival(sortedRivals, rival, holderMemberFactionIds, rules);
        }
        return sortedRivals;
    }

    // One bloc standing against the holder, listed whole or broken up.
    //
    // The members present are sorted first, because it is their disagreement that decides which of
    // the two happens: a bloc none of them disagrees about is listed whole and a bloc they split
    // over cannot be. Where it is listed whole, the side it takes is the bloc-level answer over both
    // whole memberships and not the present members' - so a bloc whose sour member holds nothing
    // here still contests the system, which is the whole point of reading a membership rather than a
    // system.
    private static void appendRival(
            RivalsByDisposition sortedRivals,
            GroupStanding rival,
            Set<String> holderMemberFactionIds,
            StandingBlockRules rules) {

        var friendlyMembers = new ArrayList<FactionStanding>();
        var sourMembers = new ArrayList<FactionStanding>();

        for (var member : rival.members()) {

            var members = isMemberFriendly(member, holderMemberFactionIds, rules)
                ? friendlyMembers
                : sourMembers;

            members.add(member);
        }

        if (friendlyMembers.isEmpty() || sourMembers.isEmpty()) {

            var isBlocFriendly = rules.friendliness().areBlocsFriendly(
                rules.readMemberFactionIds(rival.blocId()),
                holderMemberFactionIds);

            sortedRivals
                .selectSideFor(isBlocFriendly)
                .add(RoutedStanding.routeWhole(rival));

            return;
        }
        appendDissolvedMembers(sortedRivals.friendly(), friendlyMembers, rival.blocId());
        appendDissolvedMembers(sortedRivals.contested(), sourMembers, rival.blocId());
    }

    // The members of a broken-up bloc that landed on one side, each listed as the lone faction it
    // now stands as and each stating the bloc it came out of. They arrive in the order they ranked
    // beneath that bloc and are added in it, so a block reads strongest first whichever bloc its
    // rows came from.
    private static void appendDissolvedMembers(
            List<RoutedStanding> side,
            List<FactionStanding> members,
            String allianceBlocId) {

        for (var member : members) {
            side.add(RoutedStanding.dissolveFrom(member, allianceBlocId));
        }
    }

    // Whether one member of a bloc is on good terms with the holder, asked as the bloc of one it is
    // being listed as. Through the same rule the whole bloc is judged by rather than by reaching for
    // the faction-level answer underneath it, so a member listed apart is sorted by exactly the test
    // its bloc would have been.
    private static boolean isMemberFriendly(
            FactionStanding member,
            Set<String> holderMemberFactionIds,
            StandingBlockRules rules) {

        return rules.friendliness().areBlocsFriendly(
            Set.of(member.factionId()),
            holderMemberFactionIds);
    }

    // Groups listed exactly as they ranked, which is what a block placed by membership lists: it
    // never breaks a bloc up, so no row it holds states a bloc it was taken out of.
    private static List<RoutedStanding> routeWhole(List<GroupStanding> standings) {

        return standings
            .stream()
            .map(RoutedStanding::routeWhole)
            .toList();
    }

    // The bloc every contestant is placed against, or none where nobody is in the running - read off
    // the very block that names the holder, so which group that is cannot be settled one way there
    // and another here.
    private static String resolveHolderBlocId(List<GroupStanding> holder) {

        return holder
            .stream()
            .map(GroupStanding::blocId)
            .findFirst()
            .orElse(null);
    }

    /**
     * The two sides disposition parts the holder's rivals into, gathered while they are walked.
     *
     * <p>Two named lists rather than a map keyed by block, so both sides exist by construction: a
     * map would answer null for a side nothing happened to land on, and for every block the walk has
     * no business filling at all.
     *
     * <p>Mutable and private, which a value this package handed out could not be: it lives for the
     * one walk that fills it, and the routing copies each side into the block it becomes.
     *
     * @param friendly  the rivals on good terms with the holder
     * @param contested the rest, whether indifferent or hostile
     */
    private record RivalsByDisposition(
        List<RoutedStanding> friendly,
        List<RoutedStanding> contested) {

        // The side a friendly or unfriendly answer puts a bloc on. Read here rather than at each
        // caller, so a whole bloc and a member listed on its own cannot be filed by two readings of
        // the one answer.
        private List<RoutedStanding> selectSideFor(boolean isFriendly) {
            return isFriendly ? friendly : contested;
        }
    }
}
