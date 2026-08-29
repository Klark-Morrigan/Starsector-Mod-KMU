package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;

/**
 * What a box may show of a faction's claim markets: which of them get a row, and which of them are
 * enough on their own to name the faction at all.
 *
 * <p>The two rules live together, named apart from the account that spends them, because they are
 * statements about what the player may be shown rather than steps in drawing a list - and the box
 * states outcomes over the very colonies they decide, so a copy of either living beside those would be
 * free to disagree with it. Together rather than apart because the second is a strict tightening of
 * the first, and two types could not hold them that way.
 *
 * <p>Both admit a colony the player knows of, which is the half they share: such a colony is on the map
 * in its faction's colours already, so naming it tells them nothing they cannot see. What the two
 * differ over is how much of the contest counts as accounting for a colony they do not know of.
 *
 * <p>A <strong>row</strong> is drawn wherever the contest counted the market into a number this
 * account states - scored on its own account, or counted toward a sibling term the account goes on to
 * show. Either way the market's effect is on screen already, so the row is what makes it add up rather
 * than leaving it unexplained: a block stating three markets over two rows has a count its own list
 * contradicts. What fails this is a market that reaches no stated number - one the economy never
 * listed, and one counted only into a term the account never draws, a presence-only standing scoring
 * a nought with nothing under it. A row for either would be disclosure and nothing else.
 *
 * <p>A <strong>faction</strong> is named only where the contest weighed one of its markets. The
 * sibling term is not enough here, because it pays a faction for markets it already has rows for: a
 * faction present through concealed colonies alone is counted nowhere, scores nothing, and has no
 * number on screen its absence leaves short - so naming it would state the very presence the fog is
 * keeping back. The nesting is what keeps the pair honest: a market that only the sibling term counts
 * is drawn under a faction that some other market already put on the box.
 *
 * <p>Knowledge rather than discovery on both, since the markets these decide anything about are the
 * ones the contest passed over - a concealed base among them, which is found and still withheld until
 * somebody has seen it standing there.
 *
 * <p>Whether a listed row may state the colony's name is a separate question, settled where the row is
 * built. These settle only that the row, or the faction, is there.
 */
final class ListedClaimMarkets {

    private ListedClaimMarkets() {
    }

    /**
     * Whether a market alone is enough for a box to name the faction standing on it.
     *
     * @param market                       the market the faction would be named over
     * @param isListingUndiscoveredMarkets whether a colony the player has yet to find may be shown -
     *                                     false is the ordinary state, true the dev reveal
     * @return true when the faction may be named over this market
     */
    static boolean isFactionNamingMarket(
            MarketClaimBreakdown market,
            boolean isListingUndiscoveredMarkets) {

        return isListingUndiscoveredMarkets
            || market.isKnownToPlayer()
            || market.isScoredOnItsOwnAccount();
    }

    /**
     * Whether a box may draw a row for a market at all.
     *
     * @param market                       the market a line would be drawn for
     * @param standing                     the faction standing the row would be drawn under, whose
     *                                     account decides whether the sibling count is on screen
     * @param isListingUndiscoveredMarkets whether a colony the player has yet to find may be shown -
     *                                     false is the ordinary state, true the dev reveal
     * @return true when the market may be drawn
     */
    static boolean isListedMarket(
            MarketClaimBreakdown market,
            FactionClaimStanding standing,
            boolean isListingUndiscoveredMarkets) {

        return isListingUndiscoveredMarkets
            || market.isKnownToPlayer()
            || market.isScoredOnItsOwnAccount()
            || isMarketBehindAStatedSiblingCount(market, standing);
    }

    // Whether the market is one the account's own sibling count counted. Both halves are needed and
    // neither is the other: the count reaches every market the economy lists, and the account states
    // the count only where the contest weighed the faction - a presence-only standing scores a nought
    // with no terms under it, so a colony counted nowhere on screen would be disclosed by a row rather
    // than accounted for by one.
    private static boolean isMarketBehindAStatedSiblingCount(
            MarketClaimBreakdown market,
            FactionClaimStanding standing) {

        return market.isCountedTowardSiblings() && standing instanceof WeighedClaimStanding;
    }
}
