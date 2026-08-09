package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
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
 * over anything, and marking them would assert a contest that did not happen.
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
     * @param breakdown the whole contest, whose standings the cross-faction comparison runs over
     * @param standing  the faction the market belongs to, as ranked in that contest
     * @param market    one of that faction's markets
     * @return what the market's place decided
     */
    public static CellTooltipIndexOutcome resolveOutcome(
            SystemClaimBreakdown breakdown,
            FactionClaimScore standing,
            MarketClaimBreakdown market) {

        if (!market.isScoredOnItsOwnAccount()) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        if (market == standing.standingMarket()) {
            var contestOutcome = resolveClaimantContestOutcome(breakdown, standing);

            if (contestOutcome != CellTooltipIndexOutcome.UNCONTESTED) {
                return contestOutcome;
            }
        }
        return resolveStandingSelectionOutcome(standing, market);
    }

    // The cross-faction half: whether this faction's standing market tied for the system itself.
    // Judged only where the contest was what settled the system - a decree decided it before any
    // market was weighed - and only among territorial factions, the ones whose scores can take the
    // lead at all.
    private static CellTooltipIndexOutcome resolveClaimantContestOutcome(
            SystemClaimBreakdown breakdown,
            FactionClaimScore standing) {

        if (KmlibStrings.hasText(breakdown.overrideFactionId())
                || !KmlibStrings.hasText(breakdown.claimantFactionId())
                || !standing.isTerritorial()) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        var claimantScore = findStandingScore(breakdown, breakdown.claimantFactionId());

        if (claimantScore == null || standing.score() != claimantScore) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        // A rival tied at the winning score lost the system to the listing order alone: the walk
        // reached the claimant's market first, or that rival would be the claimant.
        if (!standing.factionId().equals(breakdown.claimantFactionId())) {
            return CellTooltipIndexOutcome.LOST;
        }
        return hasTiedTerritorialRival(breakdown, standing)
            ? CellTooltipIndexOutcome.WON
            : CellTooltipIndexOutcome.UNCONTESTED;
    }

    // The within-faction half: whether this market tied for the right to stand for its faction.
    // Only ties at the faction's own top score are marked - the standing selection is the one
    // outcome that order decided, and two lesser markets tied below it won and lost nothing.
    private static CellTooltipIndexOutcome resolveStandingSelectionOutcome(
            FactionClaimScore standing,
            MarketClaimBreakdown market) {

        if (market.computeTotalScore() != standing.score()) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        // The tie needs a competitor besides the standing market, and a market the walk passed over
        // is not it: the mechanic never compares one, so a standing tied only with such a market
        // won nothing.
        var isAnyOpenSiblingTied = standing
            .otherMarkets()
            .stream()
            .anyMatch(other -> other.isScoredOnItsOwnAccount()
                && other.computeTotalScore() == standing.score());

        if (!isAnyOpenSiblingTied) {
            return CellTooltipIndexOutcome.UNCONTESTED;
        }
        return market == standing.standingMarket()
            ? CellTooltipIndexOutcome.WON
            : CellTooltipIndexOutcome.LOST;
    }

    // One faction's score in the ranked standings, or null where it took none - a decreed claimant
    // holding no colony here, say, which the caller reads as no tie to judge.
    private static Integer findStandingScore(SystemClaimBreakdown breakdown, String factionId) {
        return breakdown
            .scores()
            .stream()
            .filter(score -> score.factionId().equals(factionId))
            .findFirst()
            .map(FactionClaimScore::score)
            .orElse(null);
    }

    // Whether any other territorial faction's standing tied the given one's score - the test that
    // parts a claimant that won a tie from one that simply had no equal.
    private static boolean hasTiedTerritorialRival(
            SystemClaimBreakdown breakdown,
            FactionClaimScore standing) {

        return breakdown
            .scores()
            .stream()
            .anyMatch(other -> other != standing
                && other.isTerritorial()
                && other.score() == standing.score());
    }
}
