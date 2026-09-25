package kmu.maplayers.politicalmap.dominance.tooltip;

import kmu.maplayers.ownermap.holding.ContestSide;
import kmu.maplayers.ownermap.holding.ContestSides;
import kmu.maplayers.politicalmap.dominance.standings.GroupStanding;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

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
 * <p>How that side then divides - which blocs are on good terms with the holder, which are not, and
 * which are of two minds and are listed under both headings - is {@link RivalDispositionSplit}'s, so
 * this class states the chain of axes and nothing about how a bloc feels. The two blocks placed by
 * membership are handed to neither: who holds a system and who is allied to it are facts about the
 * bloc rather than about how its members feel, so those two never split.
 *
 * <p>Answered in one pass and held, because a box lays its blocks down one after another and asking
 * per block would re-derive the whole split each time, off inputs that could be sampled apart.
 *
 * <p>Plain data with no Starsector types: everything the placement turns on arrives as
 * {@link StandingBlockRules}, so the routing reads nothing live.
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

        var candidacy = partitionByCandidacy(rankedStandings, rules);
        var holder = candidacy
            .contenders()
            .stream()
            .limit(HOLDING_GROUP_COUNT)
            .toList();

        // Everyone in the running but the holder, which is the pool both blocks below it are drawn
        // from. Dropped once here rather than per block, so no routing rule can readmit the holder
        // to a block that is by definition about somebody else.
        var contestants = candidacy
            .contenders()
            .stream()
            .skip(HOLDING_GROUP_COUNT)
            .toList();

        var holderBlocId = resolveHolderBlocId(holder);
        var sides = new ContestSides(holderBlocId, rules.affiliation());

        // Resolved once, after the holder is picked and before anybody is measured against it: every
        // disposition read in one system is against this one membership, and resolving it per
        // contestant would scan the same fold again for each of them to arrive at the same answer.
        var holderMemberFactionIds = rules.readMemberFactionIds(holderBlocId);

        var rivals = RivalDispositionSplit.splitByDisposition(
            sides.selectSide(ContestSide.RIVAL, contestants, GroupStanding::blocId),
            holderMemberFactionIds,
            rules);

        return new StandingBlockRouting(fillBlocks(
            holder,
            sides.selectSide(ContestSide.ALLIED, contestants, GroupStanding::blocId),
            rivals,
            candidacy.barred()));
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

    // The outer axis, taken before anything else is decided: a bloc barred from the contest cannot
    // become the holder by ranking above everyone, and cannot be lifted into the allied block by an
    // alliance set either, both of those being questions about the contest it is outside of. Split
    // out so the routing reads the two sides by name; both keep the ranked order.
    private static CandidacySplit partitionByCandidacy(
            List<GroupStanding> rankedStandings,
            StandingBlockRules rules) {

        var contenders = new ArrayList<GroupStanding>();
        var barred = new ArrayList<GroupStanding>();

        for (var standing : rankedStandings) {
            if (rules.candidacy().test(standing.blocId())) {
                contenders.add(standing);
            } else {
                barred.add(standing);
            }
        }
        return new CandidacySplit(contenders, barred);
    }

    // Every block filled from the groups already placed, split from the placement so the routing
    // decides who goes where and this only lays the answers into the one map.
    //
    // Every block is filled by walking the block set itself, and what each one takes is answered by
    // a switch with no default arm. That is what makes the closed set worth being one: a block added
    // to StandingBlock and not answered for here fails to compile, where a map filled by a put per
    // block would simply have drawn a block that never filled. Adding a default arm - or reaching for
    // one to silence the error - gives that guarantee away.
    private static Map<StandingBlock, List<RoutedStanding>> fillBlocks(
            List<GroupStanding> holder,
            List<GroupStanding> allied,
            RivalDispositionSplit rivals,
            List<GroupStanding> barred) {

        var standingsByBlock = new EnumMap<StandingBlock, List<RoutedStanding>>(StandingBlock.class);

        for (var block : StandingBlock.values()) {

            standingsByBlock.put(block, switch (block) {
                case HOLDER -> routeWhole(holder);
                case ALLIED -> routeWhole(allied);
                case FRIENDLY -> rivals.selectFriendlyRivals();
                case CONTESTED -> rivals.selectContestedRivals();
                case NON_POLITICAL -> routeWhole(barred);
            });
        }
        return standingsByBlock;
    }

    // Groups listed exactly as they ranked, which is what a block placed by membership lists: such a
    // block is true of every group whole, so no row it holds states a fraction of anything.
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

    // The ranked groups either side of the candidacy bar, each still strongest first.
    private record CandidacySplit(
        List<GroupStanding> contenders,
        List<GroupStanding> barred) {
    }
}
