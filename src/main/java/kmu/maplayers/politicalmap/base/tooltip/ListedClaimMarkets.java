package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;

/**
 * Which of a faction's claim markets a box may list at all.
 *
 * <p>One rule, asked by everything that draws a claim account, because the two questions put to it are
 * asked about the same colonies and would be answered separately otherwise: which markets are listed,
 * and whether a finding stated about one of them has its other half on screen. Answered apart, a box
 * could withhold a colony from the list and go on marking an outcome against it.
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

    /**
     * Whether a box may name the market a faction's standing rests on.
     *
     * <p>A standing the contest never weighed rests on no market at all, so nothing of it can be
     * drawn as the other half of a comparison - which is the reading a caller asking this wants, the
     * question only ever arising where one standing is being weighed against another.
     *
     * @param standing                     the faction's place in the contest, of either kind
     * @param isListingUndiscoveredMarkets whether a market on an undiscovered entity may be listed
     * @return true when the standing rests on a market the box may draw
     */
    static boolean isStandingMarketListed(
            FactionClaimStanding standing,
            boolean isListingUndiscoveredMarkets) {

        return standing instanceof WeighedClaimStanding weighedStanding
            && isListedMarket(weighedStanding.standingMarket(), isListingUndiscoveredMarkets);
    }
}
