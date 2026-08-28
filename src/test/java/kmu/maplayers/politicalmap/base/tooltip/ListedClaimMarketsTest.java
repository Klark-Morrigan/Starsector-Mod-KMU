package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.ContestAdmission;
import kmlib.testfixtures.starsector.systems.claims.ClaimMarketFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which markets a claim account may list, over the two grounds either of which is enough: the
 * player knows of the colony, or the contest weighed it.
 *
 * <p>The four shapes the pair produces are asserted one by one, because each is a different
 * statement about the box. A colony the player knows of is theirs to be shown whatever the mechanic
 * made of it. A colony the contest weighed is already in the numbers on screen, so its row is what
 * makes them add up. The one that fails both is the only market a row would purely disclose, and the
 * dev reveal exists to state even that.
 *
 * <p>Asserted here rather than only through the rows an account draws, because this is the rule the
 * box is held to rather than a step in drawing a list: a case reading it off row output would pass
 * on a resolver that had quietly stopped asking.
 */
final class ListedClaimMarketsTest {

    private static final String COLONY = "Kanta's Den";

    // Whether a market the contest never weighed may be listed though nobody has discovered it:
    // withheld in play, stated under the dev reveal.
    private static final boolean WITHHOLDING_UNDISCOVERED_MARKETS = false;
    private static final boolean LISTING_UNDISCOVERED_MARKETS = true;

    private static final boolean IS_KNOWN_TO_PLAYER = true;

    // The same flag false, named for the shape that reaches it. A market held in the open is unknown
    // only by being undiscovered; a concealed one stays unknown until somebody has seen it standing
    // there, whatever its entity says.
    private static final boolean IS_UNDISCOVERED_BY_PLAYER = false;
    private static final boolean IS_UNKNOWN_TO_PLAYER = false;

    @Nested
    class IsListedMarket {

        @Test
        void isListedMarketListsAMarketTheContestWeighedThoughItsColonyIsUndiscovered() {
            // The weight is on screen already - the claim, the faction's score, the difference
            // between this market's total and the terms beneath it - so the row is what accounts
            // for it.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setKnownToPlayer(IS_UNDISCOVERED_BY_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets.isListedMarket(market, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isTrue();
        }

        @Test
        void isListedMarketListsAMarketThePlayerKnowsOfThoughTheContestPassedItOver() {
            // A concealed colony somebody has seen standing there is on the map in its faction's
            // colours, so naming it tells the player nothing they cannot already see - and the row
            // is what says the contest counted it for nothing.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setAdmission(ContestAdmission.HIDDEN)
                .setKnownToPlayer(IS_KNOWN_TO_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets.isListedMarket(market, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isTrue();
        }

        @Test
        void isListedMarketWithholdsAMarketThatIsNeitherKnownNorWeighed() {
            // Both exclusions at once, which is the one shape a row would be pure disclosure of: it
            // accounts for no number on screen, and the player has nothing to recognise it by.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setAdmission(ContestAdmission.HIDDEN)
                .setKnownToPlayer(IS_UNKNOWN_TO_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets.isListedMarket(market, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isFalse();
        }

        @Test
        void isListedMarketListsTheWithheldShapeUnderTheDevReveal() {
            // The reveal is the state a player has asked to be shown everything in, so the one
            // market both grounds turn away is stated like any other.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setAdmission(ContestAdmission.HIDDEN)
                .setKnownToPlayer(IS_UNKNOWN_TO_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets.isListedMarket(market, LISTING_UNDISCOVERED_MARKETS))
                .isTrue();
        }

        @Test
        void isListedMarketListsAMarketOffTheEconomysListingThePlayerKnowsOf() {
            // The second of the two admissions that suppress scoring, asserted apart from
            // concealment because the rule reads the pair through one question and a case posing
            // only the concealed shape would not notice the other going unasked.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setAdmission(ContestAdmission.OFF_ECONOMY)
                .setKnownToPlayer(IS_KNOWN_TO_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets.isListedMarket(market, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isTrue();
        }

        @Test
        void isListedMarketWithholdsAMarketOffTheEconomysListingNobodyHasDiscovered() {
            // The same pairing on the other admission: the walk never reached it, so nothing on
            // screen is short of it, and the player has not found it either.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setAdmission(ContestAdmission.OFF_ECONOMY)
                .setKnownToPlayer(IS_UNDISCOVERED_BY_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets.isListedMarket(market, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isFalse();
        }
    }
}
