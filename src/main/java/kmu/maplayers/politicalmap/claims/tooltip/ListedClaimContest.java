package kmu.maplayers.politicalmap.claims.tooltip;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.ownermap.holding.BlocAffiliation;
import kmu.maplayers.ownermap.holding.BlocFriendliness;
import kmu.maplayers.ownermap.holding.ContestSide;
import kmu.maplayers.ownermap.holding.ContestSides;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A hovered system's claim contest as a box may state it: the whole read, the standings the known
 * projection leaves it free to name, the colony rule that projection was taken under, and the two
 * relations to the claim holder the blocks are routed against.
 *
 * <p>They travel as one value because the standings are a projection of the breakdown under the
 * rule, and most lines a box draws are read against more than one of them - the claimant off the
 * breakdown, the number beside its name off the standing the projection kept, the banner over them
 * both off the rule, the block each falls in off the affiliation and the friendliness. Passed
 * apart, one call's standings could arrive beside another read's breakdown, and a box would state a
 * claimant it had no standing for - or judge a system's habitation under a rule its listing was
 * never projected through, or route two of its blocks against two different readings of who stands
 * with the holder.
 *
 * <p>Which block a standing falls in is answered here rather than by the box laying the blocks
 * down: every one of them is drawn from the same pool with the claimant already out of it, and a
 * box asking per block would re-derive that pool each time.
 */
record ListedClaimContest(
    SystemClaimBreakdown breakdown,
    ColonyVisibility colonyVisibility,
    List<FactionClaimStanding> listedStandings,
    BlocAffiliation affiliation,
    BlocFriendliness friendliness) {

    /**
     * Selects from a contest the standings the player may be shown, keeping the rule that
     * selected them and the two relations they are placed under.
     *
     * <p>The listing rule the market lines beneath a faction are drawn through, asked of the
     * standing as a whole rather than line by line: a faction is kept where a box may draw a
     * row for at least one of its colonies. A faction whose every colony the rule withholds
     * would otherwise be named over an account with nothing in it, which is precisely the
     * reading that tells the player what the fog is keeping back.
     *
     * <p>A weighed standing therefore keeps its place however little of the system the player
     * has explored. The mechanic scores colonies nobody has discovered and can hand one of them
     * the system, so a box explaining the mechanic states the faction and redacts the row
     * beneath it - what the fog takes is the colony's identity, not the fact that somebody is
     * there. A presence-only standing whose every colony is unknown is still dropped: the
     * contest never weighed it, so no number on screen is short of it and there is nothing but
     * a name to state.
     */
    static ListedClaimContest selectFrom(
            SystemClaimBreakdown breakdown,
            ColonyVisibility colonyVisibility,
            BlocAffiliation affiliation,
            BlocFriendliness friendliness) {

        var isListingUndiscoveredMarkets = colonyVisibility.shouldIncludeUndiscoveredMarkets();

        return new ListedClaimContest(
            breakdown,
            colonyVisibility,
            breakdown
                .scores()
                .stream()
                .filter(standing -> hasListedColony(standing, isListingUndiscoveredMarkets))
                .toList(),
            affiliation,
            friendliness);
    }

    /** Whether anything survived the projection, which is what a box has to state at all. */
    boolean hasListedStanding() {
        return !listedStandings.isEmpty();
    }

    /**
     * One faction's place in the contest as a box may state it, or none where the faction
     * holds nothing the listing keeps - a decree over a system its holder has no colony in, or
     * one whose every colony there is concealed or unlisted and unseen.
     */
    Optional<FactionClaimStanding> findStanding(String factionId) {
        return listedStandings
            .stream()
            .filter(standing -> standing.factionId().equals(factionId))
            .findFirst();
    }

    /**
     * The standings shown under the allied block: everyone present who stands with the claim
     * holder, of either eligibility.
     *
     * <p>Both kinds, because the heading states an alliance and the alliance is true of an
     * ineligible ally exactly as it is of a rival that nearly took the system. Which of the two
     * a line is stays sayable on the line itself, so nothing is lost by not splitting them.
     */
    List<FactionClaimStanding> selectAlliedStandings() {
        return selectStandingsOn(ContestSide.ALLIED);
    }

    /**
     * The standings shown under the friendly block: everyone present who stands against the
     * claim holder by alliance and yet on good terms with it, of either eligibility.
     *
     * <p>Taken out of what the alliance set left standing against the holder and out of nothing
     * else, alliance being the outer axis: an ally who is merely favourable is still an ally,
     * and one gone sour is still an ally too.
     */
    List<FactionClaimStanding> selectFriendlyStandings() {
        return selectStandingsOn(ContestSide.RIVAL)
            .stream()
            .filter(this::isFriendlyWithClaimHolder)
            .toList();
    }

    /**
     * The standings shown under a block other than the claim and the two relation blocks:
     * everyone present that neither relation took, narrowed to the eligibility that block is
     * about. The claimant is dropped from every block, since a faction named twice would read as
     * holding two separate presences.
     *
     * <p>Narrowed on eligibility rather than on which kind of standing the contest gave a
     * faction, because that is what the two headings actually say. A territorial faction
     * holding only concealed bases is in the running by the mechanic's own gate and scored
     * nothing in this system, which is what the rival block plus a nought states exactly -
     * while sorting it by record kind would file it beside a Remnant station's owner, which
     * is ineligible where it is not.
     */
    List<FactionClaimStanding> selectRivalStandings(
            Predicate<FactionClaimStanding> isWantedKind) {

        return selectStandingsOn(ContestSide.RIVAL)
            .stream()
            .filter(standing -> !isFriendlyWithClaimHolder(standing))
            .filter(isWantedKind)
            .toList();
    }

    // Whether one faction stands on good terms with whoever holds the claim, asked as the two
    // blocs of one they are on this layer: the fills are pinned per claiming faction, so a
    // standing is never an alliance and the bloc-level rule degenerates to the single pair it is
    // composed of. Going through that rule all the same, rather than reaching past it to the
    // faction-level answer, so a faction is sorted here by exactly the test the standings box
    // sorts a lone faction by.
    //
    // A system nobody holds names nobody to be friendly with, which the rule answers no for over
    // the empty side - so the block never draws over a system without a holder its heading names.
    private boolean isFriendlyWithClaimHolder(FactionClaimStanding standing) {

        var claimantFactionId = breakdown.claimantFactionId();

        return friendliness.areBlocsFriendly(
            Set.of(standing.factionId()),
            KmlibStrings.hasText(claimantFactionId)
                ? Set.of(claimantFactionId)
                : Set.of());
    }

    // Everyone a box may name but the claim holder itself, which is the pool every block below
    // the claim is drawn from. Dropped once here rather than per block, so no routing rule added
    // later can readmit the claimant to a block that is by definition about somebody else.
    private List<FactionClaimStanding> selectListedRivals() {
        return listedStandings
            .stream()
            .filter(standing -> !standing.factionId().equals(breakdown.claimantFactionId()))
            .toList();
    }

    // The factions on one side of the contest below the claim, before the disposition or the
    // eligibility a block may narrow them by. Placed by the shared split rather than by comparing
    // blocs here, so this layer files a faction by the same rule the standings box and the bands
    // beneath both do.
    //
    // A faction is its own bloc on this layer, the fills being pinned to the claiming faction,
    // so the faction ID is the bloc ID the alliance set is read against.
    private List<FactionClaimStanding> selectStandingsOn(ContestSide side) {
        return new ContestSides(breakdown.claimantFactionId(), affiliation)
            .selectSide(side, selectListedRivals(), FactionClaimStanding::factionId);
    }

    // Whether any colony a faction's standing rests on is one a box may name the faction over.
    // Asked through the listing rules themselves rather than restated here, a box stating
    // outcomes over the very colonies they decide: a copy of the rule beside this one would be
    // free to disagree, and a faction listed over rows all withheld - or dropped over rows it
    // could have drawn - is exactly what that disagreement would look like.
    //
    // The tighter of the two rules, not the one the rows are drawn by. A market that only the
    // sibling term counts earns a row under a faction already on a box; it does not put one
    // there, a faction present through concealed colonies alone having no number on screen that
    // its absence leaves short.
    private static boolean hasListedColony(
            FactionClaimStanding standing,
            boolean isListingUndiscoveredMarkets) {

        return standing
            .readHeldMarkets()
            .stream()
            .anyMatch(market ->
                ListedClaimMarkets.isFactionNamingMarket(market, isListingUndiscoveredMarkets));
    }
}
