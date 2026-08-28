package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.MarketClaimBreakdown;

/**
 * Which of a faction's claim markets a box may list at all.
 *
 * <p>One rule, named apart from the account that spends it, because it is a statement about what the
 * player may be shown rather than a step in drawing a list - and the box states outcomes over the
 * very colonies it decides, so a second copy of it living beside those would be free to disagree.
 *
 * <p>Two grounds, either of them enough: the player knows of the colony, or the contest weighed it. A
 * market the contest weighed is already showing its effect in numbers on screen - the claim itself, the
 * faction's score, the difference between a listed market's total and the terms stated beneath it - so
 * a row for it makes that effect accountable instead of leaving it unexplained. A market the contest
 * never weighed leaks nothing that needs accounting for, so one the player does not know of stays off
 * the list altogether.
 *
 * <p>Knowledge rather than discovery, since the markets this arm decides anything about are the ones
 * the contest passed over - a concealed base among them, which is found and still withheld until
 * somebody has seen it standing there. A weighed market is admitted by the other arm whatever this one
 * says, so the two never disagree about a colony either could answer for.
 *
 * <p>Whether a listed row may state the colony's name is a separate question, settled where the row is
 * built. This one settles only that the row is there.
 */
final class ListedClaimMarkets {

    private ListedClaimMarkets() {
    }

    /**
     * Whether a box may list a market at all.
     *
     * @param market                       the market a line would be drawn for
     * @param isListingUndiscoveredMarkets whether a market on an undiscovered entity may be listed
     *                                     though the contest never weighed it - false is the
     *                                     ordinary state, true the dev reveal
     * @return true when the market may be drawn
     */
    static boolean isListedMarket(
            MarketClaimBreakdown market,
            boolean isListingUndiscoveredMarkets) {

        return isListingUndiscoveredMarkets
            || market.isKnownToPlayer()
            || market.isScoredOnItsOwnAccount();
    }
}
