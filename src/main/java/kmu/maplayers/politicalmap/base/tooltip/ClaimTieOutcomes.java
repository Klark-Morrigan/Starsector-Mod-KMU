package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;
import kmlib.text.KmlibStrings;

import kmu.maplayers.base.tooltip.CellTooltipIndexOutcome;

/**
 * What a market's place in the economy's listing decided, judged the way the mechanic itself
 * compares: one walk over the whole system, the lead changing only on a strictly greater score, so
 * on any equal pair whatever the walk reached first stays ahead.
 *
 * <p>That walk consults the order at exactly two points, and only a tie drawn at one of them is
 * marked. Which market stands for its faction: the standing falls to the faction's strongest, and
 * on a tie to the earlier listed. And which faction claims the system: the claimant is the
 * territorial faction whose standing market carries the top score, and on a tie the one the walk
 * reached first. Equal scores anywhere else - two rivals' lesser markets, say - were never compared
 * over anything, and marking them would state a contest that did not happen.
 *
 * <p>Several kinds of market carry a score yet never compete, and none of them are marked. A hidden
 * market is skipped by the walk outright - it reaches the contest only through the sibling term - and
 * one the economy does not list is never reached at all, so neither wins nor loses however its score
 * reads. And a non-territorial faction's markets can never take the lead, so a tie between one of
 * them and the claimant decided nothing: the claimant did not out-list it, it simply had no rival
 * in it.
 *
 * <p>Under a decree the claimant contest is not judged at all - the system was settled before a
 * market was weighed - while the tie inside each faction still is: which market stands for a
 * faction is answered the same way whatever the system fell to.
 *
 * <p>What the player knows of a colony decides nothing here. A mark answers why two markets on one
 * score are ordered as they are, and both sides of a judged tie are markets the contest weighed -
 * which the list carries whatever the player has found - so the ordering a mark is about is always
 * in front of the reader.
 *
 * <p>Pure over the breakdown with no Starsector types, like the resolver that reads it.
 */
public final class ClaimTieOutcomes {

    private ClaimTieOutcomes() {
    }

    /**
     * Resolves what one market's listing place decided: nothing, or the winning or losing of one of
     * the two comparisons the mechanic settles by that place. The claimant contest outranks the
     * standing selection where a market drew both - being the faction's strongest is a smaller fact
     * than having lost the system over it.
     *
     * @param breakdown                    the whole contest, whose standings the cross-faction
     *                                     comparison runs over
     * @param standing                     the faction the market belongs to, as ranked in that
     *                                     contest
     * @param market                       one of that faction's markets
     * @param isListingUndiscoveredMarkets whether a market on an undiscovered entity may be listed
     *                                     though the contest never weighed it - handed on to the
     *                                     listing rule, which every market a tie is judged over
     *                                     passes on its own account
     * @return what the market's place decided
     */
    public static CellTooltipIndexOutcome resolveOutcome(
            SystemClaimBreakdown breakdown,
            WeighedClaimStanding standing,
            MarketClaimBreakdown market,
            boolean isListingUndiscoveredMarkets) {

        if (!market.isScoredOnItsOwnAccount()) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        if (market == standing.standingMarket()) {
            var contestOutcome = resolveClaimantContestOutcome(
                breakdown,
                standing,
                isListingUndiscoveredMarkets);

            if (contestOutcome != CellTooltipIndexOutcome.UNCONTESTED) {
                return contestOutcome;
            }
        }
        return resolveStandingSelectionOutcome(standing, market, isListingUndiscoveredMarkets);
    }

    // The cross-faction half: whether this faction's standing market tied for the system itself.
    // Judged only where the contest was what settled the system - a decree decided it before any
    // market was weighed - and only among territorial factions, the ones whose scores can take the
    // lead at all.
    private static CellTooltipIndexOutcome resolveClaimantContestOutcome(
            SystemClaimBreakdown breakdown,
            WeighedClaimStanding standing,
            boolean isListingUndiscoveredMarkets) {

        if (breakdown.isSettledByDecree()
                || !KmlibStrings.hasText(breakdown.claimantFactionId())
                || !standing.isTerritorial()) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        var claimantStanding = findStanding(breakdown, breakdown.claimantFactionId());

        if (claimantStanding == null || standing.score() != claimantStanding.score()) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        // The market that took the system is one side of every tie judged here, so nothing is marked
        // where the box does not list it. It always does - a standing rests on a market the contest
        // weighed, and the listing admits those whatever the player knows of them.
        if (!ListedClaimMarkets.isStandingMarketListed(claimantStanding, isListingUndiscoveredMarkets)) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        // A rival tied at the winning score lost the system to the listing order alone: the walk
        // reached the claimant's market first, or that rival would be the claimant.
        if (!standing.factionId().equals(breakdown.claimantFactionId())) {
            return CellTooltipIndexOutcome.LOST;
        }
        return hasTiedTerritorialRival(breakdown, standing, isListingUndiscoveredMarkets)
            ? CellTooltipIndexOutcome.WON
            : CellTooltipIndexOutcome.UNCONTESTED;
    }

    // The within-faction half: whether this market tied for the right to stand for its faction.
    // Only ties at the faction's own top score are marked - the standing selection is the one
    // outcome that order decided, and two lesser markets tied below it won and lost nothing.
    private static CellTooltipIndexOutcome resolveStandingSelectionOutcome(
            WeighedClaimStanding standing,
            MarketClaimBreakdown market,
            boolean isListingUndiscoveredMarkets) {

        if (market.computeTotalScore() != standing.score()) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        // The tie needs a competitor besides the standing market, and a market the walk passed over
        // is not it: the mechanic never compares one, so a standing tied only with such a market
        // won nothing. The listing test beside it turns nothing further away - being scored on its
        // own account is by itself enough to list a market.
        var isAnyOpenSiblingTied = standing
            .otherMarkets()
            .stream()
            .anyMatch(other -> other.isScoredOnItsOwnAccount()
                && other.computeTotalScore() == standing.score()
                && ListedClaimMarkets.isListedMarket(other, isListingUndiscoveredMarkets));

        // The standing market is the other side of every tie judged here, so a mark waits on it
        // being drawn too - and it is, for the same reason: the mechanic weighed it, which is what
        // puts a market on the list.
        if (!isAnyOpenSiblingTied
                || !ListedClaimMarkets.isListedMarket(
                    standing.standingMarket(),
                    isListingUndiscoveredMarkets)) {

            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        return market == standing.standingMarket()
            ? CellTooltipIndexOutcome.WON
            : CellTooltipIndexOutcome.LOST;
    }

    // One faction's standing in the ranked contest, or null where it took none - a decreed claimant
    // holding no colony here, say, which the caller reads as no tie to judge.
    //
    // Read across both kinds of standing, because the caller only ever reaches it for the claimant
    // of a contest no decree settled, and such a claimant scored strictly greater than nought -
    // which a presence-only standing cannot. Narrowing to the weighed kind would therefore rule
    // out nothing this call can meet.
    //
    // The standing rather than its score alone, since the caller needs both what the faction scored
    // and the market it scored that on.
    private static FactionClaimStanding findStanding(
            SystemClaimBreakdown breakdown,
            String factionId) {

        return breakdown
            .scores()
            .stream()
            .filter(standing -> standing.factionId().equals(factionId))
            .findFirst()
            .orElse(null);
    }

    // Whether any other territorial faction's standing tied the given one's score - the test that
    // parts a claimant that won a tie from one that simply had no equal.
    private static boolean hasTiedTerritorialRival(
            SystemClaimBreakdown breakdown,
            WeighedClaimStanding standing,
            boolean isListingUndiscoveredMarkets) {

        return breakdown
            .scores()
            .stream()
            .anyMatch(other -> other != standing
                && other.isTerritorial()
                && other.score() == standing.score()
                && ListedClaimMarkets.isStandingMarketListed(other, isListingUndiscoveredMarkets));
    }
}
