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
 * <p>A tie is marked only where both markets it was drawn between are ones the box may name. The
 * mark answers why two markets on one score are ordered as they are, so with one of them withheld
 * there is no ordering in front of the reader for it to answer - and what it would state instead is
 * an outcome against a colony the player has not found, which is a finding they can neither check
 * nor have been told the other half of. This is the mechanic reported faithfully and stated
 * selectively: what the walk settled does not change, only whether the box is in a position to say
 * so.
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
     * @param breakdown               the whole contest, whose standings the cross-faction comparison
     *                                runs over
     * @param standing                the faction the market belongs to, as ranked in that contest
     * @param market                  one of that faction's markets
     * @param isListingUnfoundMarkets whether a market the player has not found may be listed. Read
     *                                because a tie is marked only where both markets it was drawn
     *                                between are on screen: the mark answers why two markets on one
     *                                score are ordered as they are, and with one of them withheld
     *                                there is no ordering in front of the reader for it to answer
     * @return what the market's place decided
     */
    public static CellTooltipIndexOutcome resolveOutcome(
            SystemClaimBreakdown breakdown,
            WeighedClaimStanding standing,
            MarketClaimBreakdown market,
            boolean isListingUnfoundMarkets) {

        if (!market.isScoredOnItsOwnAccount()) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        if (market == standing.standingMarket()) {
            var contestOutcome = resolveClaimantContestOutcome(
                breakdown,
                standing,
                isListingUnfoundMarkets);

            if (contestOutcome != CellTooltipIndexOutcome.UNCONTESTED) {
                return contestOutcome;
            }
        }
        return resolveStandingSelectionOutcome(standing, market, isListingUnfoundMarkets);
    }

    // The cross-faction half: whether this faction's standing market tied for the system itself.
    // Judged only where the contest was what settled the system - a decree decided it before any
    // market was weighed - and only among territorial factions, the ones whose scores can take the
    // lead at all.
    private static CellTooltipIndexOutcome resolveClaimantContestOutcome(
            SystemClaimBreakdown breakdown,
            WeighedClaimStanding standing,
            boolean isListingUnfoundMarkets) {

        if (KmlibStrings.hasText(breakdown.overrideFactionId())
                || !KmlibStrings.hasText(breakdown.claimantFactionId())
                || !standing.isTerritorial()) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        var claimantStanding = findStanding(breakdown, breakdown.claimantFactionId());

        if (claimantStanding == null || standing.score() != claimantStanding.score()) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        // The market that took the system is one side of every tie judged here, so nothing is marked
        // where the box may not name it: a rival stated as having lost would be losing to a colony
        // the player has not found, and the claimant stated as having won would be beating one.
        if (!ListedClaimMarkets.isStandingMarketListed(claimantStanding, isListingUnfoundMarkets)) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        // A rival tied at the winning score lost the system to the listing order alone: the walk
        // reached the claimant's market first, or that rival would be the claimant.
        if (!standing.factionId().equals(breakdown.claimantFactionId())) {
            return CellTooltipIndexOutcome.LOST;
        }
        return hasTiedTerritorialRival(breakdown, standing, isListingUnfoundMarkets)
            ? CellTooltipIndexOutcome.WON
            : CellTooltipIndexOutcome.UNCONTESTED;
    }

    // The within-faction half: whether this market tied for the right to stand for its faction.
    // Only ties at the faction's own top score are marked - the standing selection is the one
    // outcome that order decided, and two lesser markets tied below it won and lost nothing.
    private static CellTooltipIndexOutcome resolveStandingSelectionOutcome(
            WeighedClaimStanding standing,
            MarketClaimBreakdown market,
            boolean isListingUnfoundMarkets) {

        if (market.computeTotalScore() != standing.score()) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        // The tie needs a competitor besides the standing market, and a market the walk passed over
        // is not it: the mechanic never compares one, so a standing tied only with such a market
        // won nothing. Nor is a market the box withholds: the tie was drawn, but a reader shown one
        // side of it has no ordering in front of them for the mark to be about.
        var isAnyOpenSiblingTied = standing
            .otherMarkets()
            .stream()
            .anyMatch(other -> other.isScoredOnItsOwnAccount()
                && other.computeTotalScore() == standing.score()
                && ListedClaimMarkets.isListedMarket(other, isListingUnfoundMarkets));

        // The standing market is the other side of every tie judged here, so a mark waits on it
        // being drawn too - a sibling stated as having lost while the market that beat it is
        // withheld would name an outcome against a colony that is nowhere on the list.
        if (!isAnyOpenSiblingTied
                || !ListedClaimMarkets.isListedMarket(
                    standing.standingMarket(),
                    isListingUnfoundMarkets)) {

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
    // and whether the market it scored it on is one the box may name.
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

    // Whether any other territorial faction's standing tied the given one's score on a market the
    // box may name - the test that parts a claimant that won a tie from one that simply had no
    // equal, and from one whose only equal is a faction the player has yet to find a colony of.
    private static boolean hasTiedTerritorialRival(
            SystemClaimBreakdown breakdown,
            WeighedClaimStanding standing,
            boolean isListingUnfoundMarkets) {

        return breakdown
            .scores()
            .stream()
            .anyMatch(other -> other != standing
                && other.isTerritorial()
                && other.score() == standing.score()
                && ListedClaimMarkets.isStandingMarketListed(other, isListingUnfoundMarkets));
    }
}
