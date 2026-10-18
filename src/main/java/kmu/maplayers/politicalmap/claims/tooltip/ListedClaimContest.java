package kmu.maplayers.politicalmap.claims.tooltip;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;

import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.ownermap.owners.holders.alliances.BlocRelations;
import kmu.maplayers.ownermap.owners.holders.alliances.ContestSide;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A hovered system's claim contest as a box may state it: the whole read, the standings the known
 * projection leaves it free to name, the colony rule that projection was taken under, and the block
 * each of those standings is listed under.
 *
 * <p>They travel as one value because the standings are a projection of the breakdown under the
 * rule, and most lines a box draws are read against more than one of them - the claimant off the
 * breakdown, the number beside its name off the standing the projection kept, the banner over them
 * both off the rule, the block each falls in off the routing. Passed apart, one call's standings
 * could arrive beside another read's breakdown, and a box would state a claimant it had no standing
 * for - or judge a system's habitation under a rule its listing was never projected through.
 *
 * <p>Which block a standing falls in is settled once, as the contest is selected, rather than by
 * the box laying the blocks down: every block is drawn from the same pool with the claimant already
 * out of it, and the two relations are read once for all of them. A box asking per block would
 * re-derive the pool and re-read the relations each time, and could route two of its blocks against
 * two different readings of who stands with the holder.
 *
 * @param breakdown        the whole claim read of the system
 * @param colonyVisibility the colony rule the listing was projected through
 * @param listedStandings  the standings the box may name, strongest first
 * @param standingsByBlock the listed standings each block below the claim lists, strongest first;
 *                         every block is present, a block listing nobody holding an empty list
 */
record ListedClaimContest(
    SystemClaimBreakdown breakdown,
    ColonyVisibility colonyVisibility,
    List<FactionClaimStanding> listedStandings,
    Map<ClaimContestBlock, List<FactionClaimStanding>> standingsByBlock) {

    /**
     * Selects from a contest the standings the player may be shown, keeping the rule that
     * selected them, and places each in the block it is listed under.
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
            BlocRelations blocRelations) {

        var isListingUndiscoveredMarkets = colonyVisibility.shouldIncludeUndiscoveredMarkets();

        var listedStandings = breakdown
            .standings()
            .stream()
            .filter(standing -> hasListedColony(standing, isListingUndiscoveredMarkets))
            .toList();

        return new ListedClaimContest(
            breakdown,
            colonyVisibility,
            listedStandings,
            routeListedStandings(breakdown, listedStandings, blocRelations));
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
     * The standings one block below the claim lists.
     *
     * @param block the block being laid down
     * @return its standings in the order the breakdown handed them over, strongest first; empty
     *         where nobody falls in it, which is what drops its heading
     */
    List<FactionClaimStanding> selectStandingsIn(ClaimContestBlock block) {
        return standingsByBlock.get(block);
    }

    // Places every listed standing but the claimant's in the one block it is listed under.
    //
    // The axes nest. Alliance comes first: everyone standing with the holder by it is allied, of
    // either eligibility, since the heading states an alliance and the alliance is true of an
    // ineligible ally exactly as of a rival that nearly took the system. Of the rest, disposition
    // comes next, taken out of what alliance left standing against the holder and out of nothing
    // else, for the reason ContestSides states - a friendly faction is friendly of either
    // eligibility too. Eligibility parts what is left, because that is what the two remaining
    // headings say: a territorial faction holding only concealed bases is in the running by the
    // mechanic's own gate and scored nothing, which the contested block plus a nought states
    // exactly.
    //
    // The claimant is out of every block, since a faction named twice would read as holding two
    // separate presences. Dropped once here, so no routing rule added later can readmit it.
    //
    // The switch has no default arm, so a block added to the set without a placement here fails to
    // compile rather than drawing a block that never fills.
    private static Map<ClaimContestBlock, List<FactionClaimStanding>> routeListedStandings(
            SystemClaimBreakdown breakdown,
            List<FactionClaimStanding> listedStandings,
            BlocRelations blocRelations) {

        var claimantFactionId = breakdown.claimantFactionId();

        var listedRivals = listedStandings
            .stream()
            .filter(standing -> !standing.factionId().equals(claimantFactionId))
            .toList();

        // A faction is its own bloc on this layer, the fills being pinned to the claiming faction,
        // so the faction ID is the bloc ID the alliance set is read against.
        var allied = blocRelations.selectSide(
            ContestSide.ALLIED,
            claimantFactionId,
            listedRivals,
            FactionClaimStanding::factionId);

        var unallied = blocRelations.selectSide(
            ContestSide.RIVAL,
            claimantFactionId,
            listedRivals,
            FactionClaimStanding::factionId);

        // The holder's membership, which the disposition is judged against. A system nobody holds
        // names nobody to be friendly with, so the friendly block never draws over a system without
        // a holder its heading names.
        var holderMemberFactionIds = breakdown.hasClaimant()
            ? Set.of(claimantFactionId)
            : Set.<String>of();

        var friendly = new ArrayList<FactionClaimStanding>();
        var territorial = new ArrayList<FactionClaimStanding>();
        var nonTerritorial = new ArrayList<FactionClaimStanding>();

        for (var standing : unallied) {

            if (blocRelations.isFactionFriendlyWith(standing.factionId(), holderMemberFactionIds)) {
                friendly.add(standing);

            } else if (standing.isTerritorial()) {
                territorial.add(standing);

            } else {
                nonTerritorial.add(standing);
            }
        }

        var standingsByBlock = new EnumMap<ClaimContestBlock, List<FactionClaimStanding>>(
            ClaimContestBlock.class);

        for (var block : ClaimContestBlock.values()) {

            standingsByBlock.put(block, switch (block) {
                case ALLIED -> allied;
                case FRIENDLY -> List.copyOf(friendly);
                case CONTESTED -> List.copyOf(territorial);
                case NON_TERRITORIAL -> List.copyOf(nonTerritorial);
            });
        }

        return standingsByBlock;
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
