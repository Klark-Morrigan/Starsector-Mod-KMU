package kmu.maplayers.politicalmap.claims.tooltip;

import kmlib.starsector.systems.claims.FactionClaimStanding;

import kmu.maplayers.politicalmap.dominance.tooltip.StandingBlock;
import kmu.maplayers.politicalmap.tooltip.ContestBlockHeading;
import kmu.maplayers.politicalmap.tooltip.ContestWording;
import kmu.util.KmuStringKeys;

import java.util.List;
import java.util.function.Function;

/**
 * The blocks a hovered system's claim contest lists its factions under, beneath the claim itself and
 * in the order a box lays them down: who stands with the claimant by alliance, who stands with it in
 * disposition, who could have taken the system, and who was never in the running at all.
 *
 * <p>A closed set for the reason {@link StandingBlock} is one - a block declared here is a block the
 * box lays down, and the order they read in is this declaration order rather than a sequence of calls
 * that could drift from it. The two sets stay apart because the blocks are not the same blocks: these
 * name factions by how they stand to a claim and end on eligibility, while those name blocs by how
 * they stand to a system's holder and end on candidacy.
 *
 * <p>Each block carries the three things that make it itself: the heading it draws under, which of
 * the contest's standings it takes, and whether its lines have to say which of them could have taken
 * the system. The third follows from the first - a block headed by an eligibility needs no line
 * repeating it, while the two headed by a relation take both kinds and can tell them apart nowhere
 * else - so the pairing is declared here rather than decided again wherever the lines are built.
 *
 * <p>The claim block is deliberately not among them. It names the claimant rather than selecting over
 * the standings, and what it lists turns on what the banner above it said, so it is appended on its
 * own before this set is walked.
 */
enum ClaimContestBlock implements ContestBlockHeading {

    /** Everyone present standing with the claim holder by alliance, of either eligibility. */
    ALLIED(
        wording -> KmuStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_ALLIED_WITH_HOLDER,
        ListedClaimContest::selectAlliedStandings,
        ClaimContestBlock.IS_STATING_ELIGIBILITY_ON_LINE),

    /**
     * Everyone present on good terms with the claim holder without standing in its alliance, of
     * either eligibility. Inside the allied block rather than beside it: an ally who is merely
     * favourable is still an ally, so disposition sorts only what alliance left standing against the
     * holder.
     */
    FRIENDLY(
        wording -> KmuStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_FRIENDLY_WITH_CLAIM_HOLDER,
        ListedClaimContest::selectFriendlyStandings,
        ClaimContestBlock.IS_STATING_ELIGIBILITY_ON_LINE),

    /**
     * The factions that could have taken the system and did not - or that merely hold colonies
     * beside the claimant, on an install where no claim can ever be overturned. The one block whose
     * heading the install decides, which is {@link ContestWording}'s answer rather than this
     * declaration's.
     */
    CONTESTED(
        ContestWording::resolveHeadingKey,
        contest -> contest.selectRivalStandings(FactionClaimStanding::isTerritorial),
        ClaimContestBlock.IS_ELIGIBILITY_LEFT_TO_THE_HEADING),

    /**
     * The factions the claim mechanic could never have handed the system to, whatever they hold
     * there. Listed with whatever the contest weighed them at, and never named as claiming it.
     */
    NON_TERRITORIAL(
        wording -> KmuStringKeys.POLITICAL_MAP_TOOLTIP_SECTION_NON_TERRITORIAL,
        contest -> contest.selectRivalStandings(standing -> !standing.isTerritorial()),
        ClaimContestBlock.IS_ELIGIBILITY_LEFT_TO_THE_HEADING);

    // Whether the block's lines have to say which of the factions listed could have taken the system.
    // A relation block's heading states how a faction stands to the holder and no eligibility, so both
    // kinds sit under it and the line is the only place left to tell them apart; the other two are
    // headed by the eligibility itself, where a qualifier would state one fact twice in two rows.
    private static final boolean IS_STATING_ELIGIBILITY_ON_LINE = true;
    private static final boolean IS_ELIGIBILITY_LEFT_TO_THE_HEADING = false;

    private final Function<ContestWording, String> headingKeySource;
    private final Function<ListedClaimContest, List<FactionClaimStanding>> standingsSource;
    private final boolean isStatingEligibilityOnLine;

    ClaimContestBlock(
            Function<ContestWording, String> headingKeySource,
            Function<ListedClaimContest, List<FactionClaimStanding>> standingsSource,
            boolean isStatingEligibilityOnLine) {

        this.headingKeySource = headingKeySource;
        this.standingsSource = standingsSource;
        this.isStatingEligibilityOnLine = isStatingEligibilityOnLine;
    }

    @Override
    public String resolveHeadingKey(ContestWording contestWording) {
        return headingKeySource.apply(contestWording);
    }

    /**
     * Whether a line under this block states that its faction could never have taken the system.
     *
     * @return true where the heading leaves that unsaid, so the line has to say it
     */
    boolean isStatingEligibilityOnLine() {
        return isStatingEligibilityOnLine;
    }

    /**
     * The standings this block lists, out of the one contest the whole box was read from.
     *
     * @param contest the hovered system's contest as the box may state it
     * @return the standings in the order the breakdown handed them over, strongest first
     */
    List<FactionClaimStanding> selectStandings(ListedClaimContest contest) {
        return standingsSource.apply(contest);
    }
}
