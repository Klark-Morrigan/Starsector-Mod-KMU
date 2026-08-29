package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.BlocAffiliation;
import kmu.maplayers.politicalmap.base.dominance.ContestSide;
import kmu.maplayers.politicalmap.base.dominance.ContestSides;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Which block each of a hovered system's ranked groups is listed under, settled once for the whole
 * box.
 *
 * <p>The blocks nest as one chain of axes rather than as four tests of equal standing. The outermost
 * is whether a bloc is in the running at all: everyone barred from the contest is taken out before a
 * holder is picked, so the placeholder owner is never named as holding a system whatever it outranked
 * to get there. The strongest of those left holds it, and the rest are placed either side of that
 * holder by {@link ContestSides} - the one placement every surface reporting a contest routes
 * through - rather than by comparing blocs again here.
 *
 * <p>Answered in one pass and held, because a box lays its blocks down one after another and asking
 * per block would re-derive the whole split each time, off inputs that could be sampled apart.
 *
 * <p>Plain data with no Starsector types: what bars a bloc arrives as a predicate over bloc ids and
 * the alliance set as an affiliation, so the routing is exercised on hand-built standings.
 */
public final class StandingBlockRouting {

    // How many of those in the running the box names as holding the system: the one whose colour the
    // map fills the cell in. Everyone ranked below it either stands with it or contests it.
    private static final int HOLDING_GROUP_COUNT = 1;

    private final Map<StandingBlock, List<GroupStanding>> standingsByBlock;

    private StandingBlockRouting(Map<StandingBlock, List<GroupStanding>> standingsByBlock) {

        this.standingsByBlock = standingsByBlock;
    }

    /**
     * Places every ranked group in the block it is listed under.
     *
     * @param rankedStandings the system's groups, strongest first
     * @param blocCandidacy   which blocs take part in the contest at all; the rest are set aside as
     *                        non-political before a holder is picked
     * @param affiliation     the alliance set the holder's own allies are lifted out by, which is
     *                        {@link BlocAffiliation#NONE} wherever nothing groups factions
     * @return where each group is listed
     */
    public static StandingBlockRouting routeRankedStandings(
            List<GroupStanding> rankedStandings,
            Predicate<String> blocCandidacy,
            BlocAffiliation affiliation) {

        var contenders = new ArrayList<GroupStanding>();
        var nonPolitical = new ArrayList<GroupStanding>();

        // The outer axis, taken before anything else is decided: a bloc barred from the contest
        // cannot become the holder by ranking above everyone, and cannot be lifted into the allied
        // block by an alliance set either, both of those being questions about the contest it is
        // outside of.
        for (var standing : rankedStandings) {
            if (blocCandidacy.test(standing.blocId())) {
                contenders.add(standing);
            } else {
                nonPolitical.add(standing);
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

        var sides = new ContestSides(resolveHolderBlocId(holder), affiliation);
        var standingsByBlock = new EnumMap<StandingBlock, List<GroupStanding>>(StandingBlock.class);

        standingsByBlock.put(
            StandingBlock.HOLDER,
            holder);

        standingsByBlock.put(
            StandingBlock.ALLIED,
            sides.selectSide(ContestSide.ALLIED, contestants, GroupStanding::blocId));

        standingsByBlock.put(
            StandingBlock.CONTESTED,
            sides.selectSide(ContestSide.RIVAL, contestants, GroupStanding::blocId));

        standingsByBlock.put(
            StandingBlock.NON_POLITICAL,
            List.copyOf(nonPolitical));

        return new StandingBlockRouting(standingsByBlock);
    }

    /**
     * The groups one block lists, in the order they ranked.
     *
     * @param block the block being laid down
     * @return its groups; empty where nothing routed there, which is what drops its heading
     */
    public List<GroupStanding> selectStandingsIn(StandingBlock block) {

        return standingsByBlock.getOrDefault(block, List.of());
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
}
