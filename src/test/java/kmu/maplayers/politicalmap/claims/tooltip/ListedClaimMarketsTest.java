package kmu.maplayers.politicalmap.claims.tooltip;

import kmlib.starsector.systems.claims.ContestAdmission;
import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.testfixtures.starsector.systems.claims.ClaimMarketFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture.buildPresenceOnlyStanding;
import static kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture.buildStandingOnOneMarket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two rules a claim account is shown through: which markets get a row, and which markets are
 * enough on their own to name the faction standing on them.
 *
 * <p>The shapes are asserted one by one, because each is a different statement about the box. A colony
 * the player knows of is theirs to be shown whatever the mechanic made of it. A colony the contest
 * counted - weighed, or merely paid for through the sibling term - is already in the numbers on screen,
 * so its row is what makes them add up. A colony off the economy's books that nobody has found reaches
 * no term at all and is the only market a row would purely disclose; the dev reveal exists to state
 * even that.
 *
 * <p>The cases where the two rules part company are the ones worth reading: a concealed colony nobody
 * has found earns a row and does not earn its faction a name. That nesting is what keeps the box
 * honest, and a rule that quietly collapsed into the other would show as a faction named over an
 * account with nothing in it, or as a market count standing over a list short of what it counted.
 *
 * <p>Asserted here rather than only through the rows an account draws, because these are the rules the
 * box is held to rather than steps in drawing a list: a case reading them off row output would pass on
 * a resolver that had quietly stopped asking.
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

    // Whose account the rows would be drawn under. One faction throughout - nothing here compares two -
    // and territorial, which no rule these cases exercise reads either way.
    private static final String FACTION_ID = "hegemony";
    private static final boolean IS_TERRITORIAL = true;

    // What the weighed account's own market came to. Any weight serves: what the cases read off this
    // standing is which kind it is, never the number.
    private static final int STANDING_SCORE = 12;

    // The account a row would be drawn under. Weighed for every case but the one about the other kind,
    // because that is the account that states a sibling count at all - and the count is what a row for
    // a market the contest never scored has to account for.
    private static final FactionClaimStanding WEIGHED_STANDING =
        buildStandingOnOneMarket(FACTION_ID, STANDING_SCORE, IS_TERRITORIAL);

    // The account that states no count: a faction present through colonies the contest never weighed
    // scores a named nought with no terms beneath it.
    private static final FactionClaimStanding PRESENCE_ONLY_STANDING =
        buildPresenceOnlyStanding(FACTION_ID, IS_TERRITORIAL);

    @Nested
    class IsFactionNamingMarket {

        @Test
        void isFactionNamingMarketNamesAFactionOverAMarketTheContestWeighed() {
            // The mechanic weighed the colony and could have handed it the system, so the faction is
            // part of what decided the contest whether or not anybody has been there.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setKnownToPlayer(IS_UNDISCOVERED_BY_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets
                .isFactionNamingMarket(market, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isTrue();
        }

        @Test
        void isFactionNamingMarketNamesAFactionOverAColonyThePlayerKnowsOf() {
            // The colony is on the map in its faction's colours, so naming the faction tells the player
            // nothing they cannot already see.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setAdmission(ContestAdmission.HIDDEN)
                .setKnownToPlayer(IS_KNOWN_TO_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets
                .isFactionNamingMarket(market, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isTrue();
        }

        @Test
        void isFactionNamingMarketLeavesOutAFactionCountedOnlyAsItsOwnSibling() {
            // Where the two rules part company, and the reason they are two. A concealed colony nobody
            // has found earns a row under a faction already on the box, the sibling term having paid
            // for it - but it puts no faction there itself: a faction present through such colonies
            // alone is counted nowhere, scores nothing, and leaves no number on screen short, so naming
            // it would state the very presence the fog is keeping back.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setAdmission(ContestAdmission.HIDDEN)
                .setKnownToPlayer(IS_UNKNOWN_TO_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets
                .isFactionNamingMarket(market, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isFalse();
        }

        @Test
        void isFactionNamingMarketNamesAFactionOverTheWithheldShapeUnderTheDevReveal() {

            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setAdmission(ContestAdmission.HIDDEN)
                .setKnownToPlayer(IS_UNKNOWN_TO_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets
                .isFactionNamingMarket(market, LISTING_UNDISCOVERED_MARKETS))
                .isTrue();
        }
    }

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

            assertThat(ListedClaimMarkets
                .isListedMarket(market, WEIGHED_STANDING, WITHHOLDING_UNDISCOVERED_MARKETS))
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

            assertThat(ListedClaimMarkets
                .isListedMarket(market, WEIGHED_STANDING, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isTrue();
        }

        @Test
        void isListedMarketListsAConcealedMarketTheSiblingTermCountsThoughNobodyKnowsOfIt() {
            // The concealed market's one reach into the contest: the sibling count walks the economy's
            // listing and counts it there, so its faction's block is paid a point for a market the block
            // would otherwise not show - a count of three standing over two rows.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setAdmission(ContestAdmission.HIDDEN)
                .setKnownToPlayer(IS_UNKNOWN_TO_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets
                .isListedMarket(market, WEIGHED_STANDING, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isTrue();
        }

        @Test
        void isListedMarketWithholdsAConcealedMarketUnderAnAccountThatStatesNoCount() {
            // The same market under the other kind of account. A presence-only standing scores a named
            // nought with no terms beneath it, so the count that pays for this colony is nowhere on
            // screen - and a row nothing shown needs is disclosure rather than accounting.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setAdmission(ContestAdmission.HIDDEN)
                .setKnownToPlayer(IS_UNKNOWN_TO_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets
                .isListedMarket(market, PRESENCE_ONLY_STANDING, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isFalse();
        }

        @Test
        void isListedMarketListsTheWithheldShapeUnderTheDevReveal() {
            // The reveal is the state a player has asked to be shown everything in, so the one
            // market both grounds turn away is stated like any other.
            var market = ClaimMarketFixture
                .startMarket(COLONY)
                .setAdmission(ContestAdmission.OFF_ECONOMY)
                .setKnownToPlayer(IS_UNKNOWN_TO_PLAYER)
                .buildMarket();

            assertThat(ListedClaimMarkets
                .isListedMarket(market, WEIGHED_STANDING, LISTING_UNDISCOVERED_MARKETS))
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

            assertThat(ListedClaimMarkets
                .isListedMarket(market, WEIGHED_STANDING, WITHHOLDING_UNDISCOVERED_MARKETS))
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

            assertThat(ListedClaimMarkets
                .isListedMarket(market, WEIGHED_STANDING, WITHHOLDING_UNDISCOVERED_MARKETS))
                .isFalse();
        }
    }
}
