package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipIndexOutcome;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.readLabelTexts;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which lines a faction's claim standing breaks down into and what hangs beneath what: its markets
 * under the faction holding them, the terms of a score under the market that scored it, and the
 * presence its several holdings earned at the foot of the list rather than on any one of them.
 *
 * <p>What a term that never arose looks like is most of what is asserted here, because it is the
 * difference between an account and a form: a lone market has no presence line and no garrison line,
 * rather than two lines insisting they counted for nothing.
 *
 * <p>The mechanic behind the numbers is KMLib's and has its own suite there, so what is left is what
 * this resolver alone decides - which market leads, when it is called out, and which terms are stated
 * where.
 */
final class ClaimScoreRowResolverTest {

    private static final String HEGEMONY = "hegemony";

    // The market every case represents its faction by, sized so a term added to it is plainly a
    // separate number rather than one that could be read out of the size.
    private static final String STRONGEST_MARKET = "Chicomoztoc";
    private static final int STRONGEST_MARKET_SIZE = 7;

    // What the presence line calls the term, spelled out so a case reads as the words a player sees.
    private static final String PRESENCE_LINE = "Same-faction market bonus";

    // Vanilla's flat garrison bonus. Stated as a literal rather than read from the mechanic, so a case
    // asserting the line shows it cannot pass by restating whatever the reader happened to hand over.
    private static final int MILITARY_BONUS = 10;

    // How many other markets a faction holds in the system. The mechanic gives every one of a
    // faction's markets the same count, so a case listing others states the matching number on the
    // standing rather than a standing that could not arise.
    private static final int NO_SIBLING_MARKETS = 0;
    private static final int ONE_SIBLING_MARKET = 1;
    private static final int TWO_SIBLING_MARKETS = 2;

    // Where each market falls in the system's economy listing - the order a tied contest is settled
    // in. The strongest market heads the listing throughout, since no case here is about a tie.
    private static final int FIRST_LISTED = 1;
    private static final int SECOND_LISTED = 2;
    private static final int THIRD_LISTED = 3;

    private static final boolean IS_TERRITORIAL = true;

    // Whether this faction is the one the contest handed the system to - the only faction whose own
    // strongest market is called out, and the answer for none of them where a decree settled it.
    private static final boolean IS_HOLDING_THE_CLAIM = true;
    private static final boolean IS_NOT_HOLDING_THE_CLAIM = false;

    // Whether a market the player has not found may be listed: withheld in play, stated in full under
    // the dev reveal.
    private static final boolean WITHHOLDING_UNFOUND_MARKETS = false;
    private static final boolean LISTING_UNFOUND_MARKETS = true;

    // Whether the player has found a market at all - the flag the withholding reads.
    private static final boolean IS_KNOWN_TO_PLAYER = true;
    private static final boolean IS_UNFOUND_BY_PLAYER = false;

    @BeforeEach
    void installStrings() {
        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearStrings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ResolveMarketRows {

        @Test
        void resolveMarketRowsLeadsWithTheFactionsStrongestMarketAndCallsTheHolderOut() {
            // The faction's number above is this one market's score, so the reader following it
            // downward has to be told which of the markets listed here it came out of - and kept from
            // reading the number as the total of the list.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildMarket("Culann", 3, ONE_SIBLING_MARKET, SECOND_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Culann", PRESENCE_LINE);
            assertThat(rows.get(0).line().qualifierText())
                .isEqualTo("claim holder");
        }

        @Test
        void resolveMarketRowsStatesWhereTheEconomyListsEachMarket() {
            // The whole of the answer to what the scores cannot settle: a tie falls to whichever
            // market the economy reached first, and nothing else in the box says which that was.
            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildStanding(
                    buildStrongestMarket(ONE_SIBLING_MARKET),
                    List.of(buildMarket("Culann", 3, ONE_SIBLING_MARKET, SECOND_LISTED))),
                IS_HOLDING_THE_CLAIM,
                WITHHOLDING_UNFOUND_MARKETS);

            assertThat(rows.get(0).line().indexText())
                .isEqualTo("[1]");
            assertThat(rows.get(1).line().indexText())
                .isEqualTo("[2]");
        }

        @Test
        void resolveMarketRowsMarksTheTieBreakerAsWonAndTheMarketsItBeatAsLost() {
            // The moment the place stops being a label: two markets equal on everything else are
            // parted by it alone, so the earlier-listed one reads as having won the tie and the rest
            // as having lost it, rather than leaving the reader to work out that the smaller wins.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of(
                    buildMarket("Culann", STRONGEST_MARKET_SIZE, TWO_SIBLING_MARKETS, THIRD_LISTED),
                    buildMarket("Eventide", STRONGEST_MARKET_SIZE, TWO_SIBLING_MARKETS,
                        SECOND_LISTED))));

            assertThat(rows.get(0).line().indexOutcome())
                .isEqualTo(CellTooltipIndexOutcome.WON);
            assertThat(rows.get(1).line().indexOutcome())
                .isEqualTo(CellTooltipIndexOutcome.LOST);
            assertThat(rows.get(2).line().indexOutcome())
                .isEqualTo(CellTooltipIndexOutcome.LOST);
        }

        @Test
        void resolveMarketRowsLeavesAPlaceThatDecidedNothingUnmarked() {
            // Markets on different scores are told apart by the scores, so their places settled
            // nothing and a marked one would claim an outcome the numbers already gave.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildMarket("Culann", 3, ONE_SIBLING_MARKET, SECOND_LISTED))));

            assertThat(rows)
                .allSatisfy(entry -> assertThat(entry.line().indexOutcome())
                    .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED));
        }

        @Test
        void resolveMarketRowsJudgesATieOverTheMarketsItActuallyDrew() {
            // The marking answers a question the drawn list raises - why does that one sit above this
            // one, when the numbers match - so a tie with a market the player cannot see is not that
            // question, and marking it would point at nothing on screen.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildMarket("Kanta's Den", STRONGEST_MARKET_SIZE, ONE_SIBLING_MARKET,
                    SECOND_LISTED, IS_UNFOUND_BY_PLAYER))));

            assertThat(rows.get(0).line().indexOutcome())
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveMarketRowsStatesTheSiblingBonusAsWorkingOverThePointsItCameTo() {
            // The line is not one of the faction's holdings but the arithmetic of a term all of them
            // share, so it reads as quietly as any other working and only the points stay a finding.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of()));

            var bonusLine = rows.get(1).line();

            assertThat(bonusLine.isWorkingOnly())
                .isTrue();
            assertThat(bonusLine.valueWorkingText())
                .isEqualTo("(3 markets) - 1 =");
            assertThat(bonusLine.valueText())
                .isEqualTo("+2");
        }

        @Test
        void resolveMarketRowsStatesNoListingPlaceOnATermLine() {
            // A term is arithmetic, not a market, so it sits in no listing and has no place to
            // state - and the presence line below the markets is the faction's rather than one of
            // them, so it has none either.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of()));

            assertThat(rows.get(0).children())
                .allSatisfy(term -> assertThat(term.line().indexText()).isNull());
            assertThat(rows.get(1).line().indexText())
                .isNull();
        }

        @Test
        void resolveMarketRowsLeavesAMarketThePlayerHasNotFoundOffTheList() {
            // Vanilla settles a claim over colonies nobody has found; repeating what it learned
            // there would tell the player a system holds something they have no way of knowing.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of(
                    buildMarket("Culann", 3, TWO_SIBLING_MARKETS, SECOND_LISTED),
                    buildMarket("Kanta's Den", 3, TWO_SIBLING_MARKETS, THIRD_LISTED,
                        IS_UNFOUND_BY_PLAYER))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Culann");
        }

        @Test
        void resolveMarketRowsWithholdsThePresenceTermOverAListSomethingWasKeptFrom() {
            // The term's whole claim on the reader is that its count can be checked against the
            // markets above it. Printed over a shortened list it would either contradict what is on
            // screen or state the very number the withholding exists to keep back.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildMarket("Kanta's Den", 3, ONE_SIBLING_MARKET, SECOND_LISTED,
                    IS_UNFOUND_BY_PLAYER))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET);
        }

        @Test
        void resolveMarketRowsListsEveryMarketUnderTheDevReveal() {
            // The reveal is the state a player has asked to be shown everything in, so the account is
            // stated in full - the withholding is about what they have found, not about the box.
            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildStanding(
                    buildStrongestMarket(ONE_SIBLING_MARKET),
                    List.of(buildMarket("Kanta's Den", 3, ONE_SIBLING_MARKET, SECOND_LISTED,
                        IS_UNFOUND_BY_PLAYER))),
                IS_HOLDING_THE_CLAIM,
                LISTING_UNFOUND_MARKETS);

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Kanta's Den", PRESENCE_LINE);
        }

        @Test
        void resolveMarketRowsCallsOutNoMarketBesidesTheFactionsStrongest() {
            // A second marked line would say one system has two holders, which is exactly what a
            // contest cannot produce.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildMarket("Culann", 3, ONE_SIBLING_MARKET, SECOND_LISTED))));

            assertThat(rows.get(1).line().qualifierText())
                .isNull();
        }

        @Test
        void resolveMarketRowsCallsOutNoMarketOfAFactionThatTookNothing() {
            // Every faction is represented by its strongest market, but only one of those won
            // anything. A rival's - or any faction's under a decree - is called out nowhere, since
            // the line would credit it with an outcome it did not produce.
            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildStanding(
                    buildStrongestMarket(ONE_SIBLING_MARKET),
                    List.of(buildMarket("Culann", 3, ONE_SIBLING_MARKET, SECOND_LISTED))),
                IS_NOT_HOLDING_THE_CLAIM,
                WITHHOLDING_UNFOUND_MARKETS);

            assertThat(rows.get(0).line().qualifierText())
                .isNull();
        }

        @Test
        void resolveMarketRowsStillLeadsWithTheStrongestMarketOfAFactionThatTookNothing() {
            // Only the call-out goes. The markets are still read strongest first, since that is the
            // order a contest is read in whether or not this faction won it.
            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildStanding(
                    buildStrongestMarket(ONE_SIBLING_MARKET),
                    List.of(buildMarket("Culann", 3, ONE_SIBLING_MARKET, SECOND_LISTED))),
                IS_NOT_HOLDING_THE_CLAIM,
                WITHHOLDING_UNFOUND_MARKETS);

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Culann", PRESENCE_LINE);
        }

        @Test
        void resolveMarketRowsRanksTheRemainingMarketsStrongestFirst() {
            // The markets read strongest first for the same reason the factions above them do: the
            // account of a standing opens on what came nearest to being it.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of(
                    buildMarket("Culann", 3, TWO_SIBLING_MARKETS, SECOND_LISTED),
                    buildMarket("Eventide", 5, TWO_SIBLING_MARKETS, THIRD_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Eventide", "Culann", PRESENCE_LINE);
        }

        @Test
        void resolveMarketRowsBreaksATieOnTheOrderTheMechanicItselfSettlesIt() {
            // Two markets of a faction can score exactly the same, and the contest parts them by the
            // earlier place in the economy's listing - so the list reads in the order the mechanic
            // would settle it rather than in one the box invented.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of(
                    buildMarket("Eventide", 4, TWO_SIBLING_MARKETS, SECOND_LISTED),
                    buildMarket("Culann", 4, TWO_SIBLING_MARKETS, THIRD_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Eventide", "Culann", PRESENCE_LINE);
        }

        @Test
        void resolveMarketRowsStatesTheMarketsWholeScoreBesideIt() {
            // The market's line carries the number the contest weighed it at - presence included, since
            // that is what the faction's line above and the map's own fill were settled by.
            var rows = resolveContestedRows(buildStanding(
                buildFullyScoredMarket(),
                List.of()));

            assertThat(rows.get(0).line().valueText())
                .isEqualTo("19");
        }

        @Test
        void resolveMarketRowsOpensAMarketOnTheSizeItsScoreStartsFrom() {
            // The size is the term the sum starts from, so it heads the terms and is stated even where
            // it is the whole of the score - a market listing no term at all would read as a number
            // with no account behind it.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(NO_SIBLING_MARKETS),
                List.of()));

            assertThat(readLabelTexts(rows.get(0).children()))
                .containsExactly("Size");
            assertThat(rows.get(0).children().get(0).line().valueText())
                .isEqualTo("7");
        }

        @Test
        void resolveMarketRowsClosesTheListWithThePresenceEveryOneOfTheMarketsEarned() {
            // The term belongs to the faction rather than to any one of its markets - the mechanic
            // gives all of them the same points for each other - so it is stated once, beneath the
            // very markets whose number the reader is meant to check the count against.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of()));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, PRESENCE_LINE);
        }

        @Test
        void resolveMarketRowsKeepsThePresenceTermOffEachMarketsOwnAccount() {
            // Repeated under every market the one term would read as several separate findings, and
            // there would be nothing beside any of them to check the count against.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of()));

            assertThat(readLabelTexts(rows.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void resolveMarketRowsStatesNoPresenceTermForAFactionHoldingTheSystemWithOneMarket() {
            // The term never arose, and a line reading "1 x 0 = 0" would invite the reader to look
            // for a market that is not there.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(NO_SIBLING_MARKETS),
                List.of()));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET);
        }

        @Test
        void resolveMarketRowsStatesTheGarrisonTermOnlyForAMilitaryMarket() {
            // The bonus is a flat constant a garrison earns, so an absent one is a market that is no
            // garrison rather than a garrison worth nothing - two different markets a "+0" would
            // print alike.
            var garrisoned = resolveContestedRows(buildStanding(
                new MarketClaimBreakdown(
                    STRONGEST_MARKET,
                    FIRST_LISTED,
                    IS_KNOWN_TO_PLAYER,
                    STRONGEST_MARKET_SIZE,
                    NO_SIBLING_MARKETS,
                    OptionalInt.of(MILITARY_BONUS)),
                List.of()));

            assertThat(readLabelTexts(garrisoned.get(0).children()))
                .containsExactly("Size", "Military");
            assertThat(garrisoned.get(0).children().get(1).line().valueText())
                .isEqualTo("+10");

            var civilian = resolveContestedRows(buildStanding(
                buildStrongestMarket(NO_SIBLING_MARKETS),
                List.of()));

            assertThat(readLabelTexts(civilian.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void resolveMarketRowsListsATermsOwnAccountNoDeeper() {
            // The claim score is one addition deep. A term breaking down further would be inventing an
            // arithmetic the mechanic does not have.
            var rows = resolveContestedRows(buildStanding(
                buildFullyScoredMarket(),
                List.of()));

            assertThat(rows.get(0).children())
                .allSatisfy(term -> assertThat(term.children()).isEmpty());
        }
    }

    // The account over a system the contest itself settled and whose markets the player has all
    // found - the ordinary state, and what every case but the decreed and withheld ones is posed
    // over.
    private static List<CellTooltipEntry> resolveContestedRows(FactionClaimScore standing) {
        return ClaimScoreRowResolver.resolveMarketRows(
            standing,
            IS_HOLDING_THE_CLAIM,
            WITHHOLDING_UNFOUND_MARKETS);
    }

    // A faction standing on the given market and holding the given others in the system. Territorial
    // throughout: which block a standing is listed under is the box's to decide, and no case here is
    // about it.
    private static FactionClaimScore buildStanding(
            MarketClaimBreakdown standingMarket,
            List<MarketClaimBreakdown> otherMarkets) {

        return new FactionClaimScore(HEGEMONY, IS_TERRITORIAL, standingMarket, otherMarkets);
    }

    // The market a faction's standing rests on, holding the stated number of others in the system and
    // no garrison - the baseline the cases above add one term at a time to.
    private static MarketClaimBreakdown buildStrongestMarket(int siblingMarketCount) {
        return buildStrongestMarket(siblingMarketCount, IS_KNOWN_TO_PLAYER);
    }

    // The same market, stated as one the player has or has not found - the two cases the withholding
    // turns on.
    private static MarketClaimBreakdown buildStrongestMarket(
            int siblingMarketCount,
            boolean isKnownToPlayer) {

        return new MarketClaimBreakdown(
            STRONGEST_MARKET,
            FIRST_LISTED,
            isKnownToPlayer,
            STRONGEST_MARKET_SIZE,
            siblingMarketCount,
            OptionalInt.empty());
    }

    // A market every term of the score arose on, for the cases about the whole sum rather than about
    // one term of it: its own size, two others beside it, and a garrison on it.
    private static MarketClaimBreakdown buildFullyScoredMarket() {
        return new MarketClaimBreakdown(
            STRONGEST_MARKET,
            FIRST_LISTED,
            IS_KNOWN_TO_PLAYER,
            STRONGEST_MARKET_SIZE,
            TWO_SIBLING_MARKETS,
            OptionalInt.of(MILITARY_BONUS));
    }

    // One of the faction's other markets, one the player has found. It carries the same sibling count
    // its standing does, since the mechanic gives every market of a faction a point for each other.
    private static MarketClaimBreakdown buildMarket(
            String marketName,
            int marketSize,
            int siblingMarketCount,
            int listingPosition) {

        return buildMarket(
            marketName,
            marketSize,
            siblingMarketCount,
            listingPosition,
            IS_KNOWN_TO_PLAYER);
    }

    // The same market, stated as one the player has or has not found.
    private static MarketClaimBreakdown buildMarket(
            String marketName,
            int marketSize,
            int siblingMarketCount,
            int listingPosition,
            boolean isKnownToPlayer) {

        return new MarketClaimBreakdown(
            marketName,
            listingPosition,
            isKnownToPlayer,
            marketSize,
            siblingMarketCount,
            OptionalInt.empty());
    }
}
