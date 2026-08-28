package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;

/**
 * Which of a faction's claim markets a box may name at all.
 *
 * <p>One rule, asked by everything that draws a claim account, because the two questions put to it are
 * asked about the same colonies and would be answered separately otherwise: which markets are listed,
 * and whether a finding stated about one of them has its other half on screen. Answered apart, a box
 * could withhold a colony from the list and go on marking an outcome against it.
 *
 * <p>The rule is the player's knowledge and nothing the mechanic decides. A market held in the open is
 * weighed whether or not anybody has reached it, so being weighed says nothing about being nameable -
 * which is why this is asked of the flag the walk recorded rather than of the admission beside it.
 */
final class ListedClaimMarkets {

    private ListedClaimMarkets() {
    }

    /**
     * Whether a box may name a market at all.
     *
     * @param market                  the market a line would be drawn for
     * @param isListingUnfoundMarkets whether a market the player has not found may be listed - false
     *                                is the ordinary state, true the dev reveal
     * @return true when the market may be drawn
     */
    static boolean isListedMarket(MarketClaimBreakdown market, boolean isListingUnfoundMarkets) {
        return isListingUnfoundMarkets || market.isKnownToPlayer();
    }

    /**
     * Whether a box may name the market a faction's standing rests on.
     *
     * <p>A standing the contest never weighed rests on no market at all, so nothing of it can be
     * drawn as the other half of a comparison - which is the reading a caller asking this wants, the
     * question only ever arising where one standing is being weighed against another.
     *
     * @param standing                the faction's place in the contest, of either kind
     * @param isListingUnfoundMarkets whether a market the player has not found may be listed
     * @return true when the standing rests on a market the box may draw
     */
    static boolean isStandingMarketListed(
            FactionClaimStanding standing,
            boolean isListingUnfoundMarkets) {

        return standing instanceof WeighedClaimStanding weighedStanding
            && isListedMarket(weighedStanding.standingMarket(), isListingUnfoundMarkets);
    }
}
