package kmu.maplayers.politicalmap.dominance.tooltip;

import kmu.maplayers.ownermap.owners.holders.alliances.ContestSide;
import kmu.maplayers.ownermap.owners.holders.alliances.ContestSides;
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
 * whether a bloc is in the running at all: everyone barred from the contest is taken out first, so a
 * bloc that takes no part in one is never named as holding a system, even where the fill is painted
 * for it. The holder is not picked here. It arrives as the bloc the fill was painted for, which the
 * dominance pass settles by tie-breaks below the combined weight the ranking orders by. A holder
 * picked off the ranking would part from the fill exactly where two blocs tie on that weight, and
 * every block below it would then be placed against a bloc the cell is not painted for.
 *
 * <p>The rest are placed either side of that holder by {@link ContestSides} - the one placement every
 * surface reporting a contest routes through - rather than by comparing blocs again here.
 * Disposition sorts inside that split and never across it, for the reason {@link ContestSides}
 * states.
 *
 * <p>A system with no holder skips the placement against one. That is a system the fill paints for
 * no contender: nobody was weighed there, or only a barred bloc was. Its contenders are then listed
 * as present and nothing more, since sides of a holder that does not exist say nothing true.
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

    private final Map<StandingBlock, List<RoutedStanding>> standingsByBlock;

    private StandingBlockRouting(Map<StandingBlock, List<RoutedStanding>> standingsByBlock) {

        this.standingsByBlock = standingsByBlock;
    }

    /**
     * Places every ranked group in the block it is listed under.
     *
     * @param rankedStandings the system's groups, strongest first
     * @param paintedBlocId   the bloc the system's fill was painted for, or null where it was painted
     *                        for nobody
     * @param rules           what the placement turns on: who is in the running, who stands with the
     *                        holder by alliance, who stands with it in disposition, and what a bloc
     *                        is made of
     * @return where each group is listed
     */
    public static StandingBlockRouting routeRankedStandings(
            List<GroupStanding> rankedStandings,
            String paintedBlocId,
            StandingBlockRules rules) {

        var candidacy = partitionByCandidacy(rankedStandings, rules);
        var holder = findPaintedContender(candidacy.contenders(), paintedBlocId);

        if (holder == null) {
            return new StandingBlockRouting(fillBlocks(
                HolderContest.NO_HOLDER,
                candidacy.contenders(),
                candidacy.barred()));
        }

        return new StandingBlockRouting(fillBlocks(
            placeAroundHolder(holder, candidacy.contenders(), rules),
            List.of(),
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

    // Everyone in the running placed by how they stand with the holder: the holder itself, then the
    // rest either side of it by alliance, and what alliance left against it sorted by disposition.
    private static HolderContest placeAroundHolder(
            GroupStanding holder,
            List<GroupStanding> contenders,
            StandingBlockRules rules) {

        var holderBlocId = holder.blocId();
        var blocRelations = rules.blocRelations();

        // Everyone in the running but the holder, still in rank order, which is the pool both blocks
        // below it are drawn from. Dropped once here rather than per block, so no routing rule can
        // readmit the holder to a block that is by definition about somebody else.
        var contestants = contenders
            .stream()
            .filter(standing -> !standing.blocId().equals(holderBlocId))
            .toList();

        // Resolved once, after the holder is known and before anybody is measured against it: every
        // disposition read in one system is against this one membership, and resolving it per
        // contestant would scan the same fold again for each of them to arrive at the same answer.
        var holderMemberFactionIds = rules.readMemberFactionIds(holderBlocId);

        var allied = blocRelations.selectSide(ContestSide.ALLIED, holderBlocId, contestants, GroupStanding::blocId);
        var rivals = RivalDispositionSplit.splitByDisposition(
            blocRelations.selectSide(ContestSide.RIVAL, holderBlocId, contestants, GroupStanding::blocId),
            holderMemberFactionIds,
            rules);

        return new HolderContest(
            routeWhole(List.of(holder)),
            routeWhole(allied),
            rivals.selectFriendlyRivals(),
            rivals.selectContestedRivals());
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
            HolderContest contest,
            List<GroupStanding> present,
            List<GroupStanding> barred) {

        var standingsByBlock = new EnumMap<StandingBlock, List<RoutedStanding>>(StandingBlock.class);

        for (var block : StandingBlock.values()) {

            standingsByBlock.put(block, switch (block) {
                case HOLDER -> contest.holderStandings();
                case ALLIED -> contest.alliedStandings();
                case FRIENDLY -> contest.friendlyRivalStandings();
                case CONTESTED -> contest.contestedRivalStandings();
                case PRESENT -> routeWhole(present);
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

    // The contender the fill was painted for, or null where the fill was painted for no contender: for
    // nobody, or for a bloc the candidacy bars, which partitionByCandidacy has already set aside.
    private static GroupStanding findPaintedContender(
            List<GroupStanding> contenders,
            String paintedBlocId) {

        return contenders
            .stream()
            .filter(standing -> standing.blocId().equals(paintedBlocId))
            .findFirst()
            .orElse(null);
    }

    // The ranked groups either side of the candidacy bar, each still strongest first.
    private record CandidacySplit(
        List<GroupStanding> contenders,
        List<GroupStanding> barred) {
    }

    // The four blocks placed by how a group stands with the holder, as one value: a system with no
    // holder fills all four from one empty answer rather than standing each up empty on its own.
    private record HolderContest(
        List<RoutedStanding> holderStandings,
        List<RoutedStanding> alliedStandings,
        List<RoutedStanding> friendlyRivalStandings,
        List<RoutedStanding> contestedRivalStandings) {

        static final HolderContest NO_HOLDER = new HolderContest(
            List.of(),
            List.of(),
            List.of(),
            List.of());
    }
}
