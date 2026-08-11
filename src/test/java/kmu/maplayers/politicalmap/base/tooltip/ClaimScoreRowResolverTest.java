package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.entities.EntityMapIcon;
import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.systems.claims.ContestAdmission;
import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipIndexOutcome;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
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
 * <p>Where a mark may appear is pinned here too, since it is a statement about which line is about a
 * thing on the map: a market's own line leads with the glyph the map marks it by, scored or not, and
 * nothing beneath it carries one. That the glyph reads in the market name's own colour rather than the
 * map's is pinned beside it - the box declining an authored shade is a decision, not an omission.
 *
 * <p>The mechanic behind the numbers is KMLib's and has its own suite there, so what is left is what
 * this resolver alone decides - which market leads, when it is called out, and which terms are stated
 * where.
 */
final class ClaimScoreRowResolverTest {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

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
    // in. The strongest market heads the listing throughout, so a tie posed against it is one it won.
    private static final int FIRST_LISTED = 1;
    private static final int SECOND_LISTED = 2;
    private static final int THIRD_LISTED = 3;

    private static final boolean IS_TERRITORIAL = true;

    // Whether a market the player has not found may be listed: withheld in play, stated in full under
    // the dev reveal.
    private static final boolean WITHHOLDING_UNFOUND_MARKETS = false;
    private static final boolean LISTING_UNFOUND_MARKETS = true;

    // Whether the player has found a market at all - the flag the withholding reads. An unfound
    // market is a hidden one, the two arms of "known" being discovery and being held in the open, so
    // a case posing one poses both.
    private static final boolean IS_KNOWN_TO_PLAYER = true;
    private static final boolean IS_UNFOUND_BY_PLAYER = false;

    // A market the sector map marks with a glyph, and one it marks with none - the two readings a
    // market line's opening run turns on. The authored colour is carried because the read hands one
    // over, not because anything below reads it: a resolved line has nowhere to put an asset colour,
    // which is the point of the mark stating that it follows its name instead.
    private static final EntityNameplate MARKED_MARKET = new EntityNameplate(
        STRONGEST_MARKET,
        Optional.of(new EntityMapIcon("graphics/warroom/icon_planet.png", new Color(120, 200, 90))));

    private static final EntityNameplate UNMARKED_MARKET =
        EntityNameplate.createUnmarkedNameplate(STRONGEST_MARKET);

    // How the mechanic met a market. A concealed one it skips before scoring, so it competes in
    // neither comparison the listing settles and no tie it appears in is marked; one the economy
    // does not list it never reaches at all, which additionally keeps that market out of the
    // presence count. Either brings nothing to the contest.
    private static final ContestAdmission CONCEALED = new ContestAdmission(true, false);
    private static final ContestAdmission OFF_ECONOMY = new ContestAdmission(false, true);

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
            var rows = resolveContestedRows(buildStandingOverOneSibling());

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Culann", PRESENCE_LINE);
            assertThat(rows.get(0).line().qualifierText())
                .isEqualTo("claim holder");
        }

        @Test
        void resolveMarketRowsStatesWhereTheEconomyListsEachMarket() {
            // The whole of the answer to what the scores cannot settle: a tie falls to whichever
            // market the economy reached first, and nothing else in the box says which that was.
            var rows = resolveContestedRows(buildStandingOverOneSibling());

            assertThat(rows.get(0).line().indexPlace().text())
                .isEqualTo("[1]");
            assertThat(rows.get(1).line().indexPlace().text())
                .isEqualTo("[2]");
        }

        @Test
        void resolveMarketRowsLeadsAMarketWithTheGlyphTheMapMarksItBy() {
            // The reader has a list of names and a map, and the glyph is the one thing the two share
            // at a glance.
            var rows = resolveContestedRows(buildStanding(
                buildMarkedMarket(MARKED_MARKET),
                List.of()));

            assertThat(rows.get(0).line().mark().spritePath())
                .isEqualTo("graphics/warroom/icon_planet.png");
        }

        @Test
        void resolveMarketRowsLeadsAMarketTheMechanicPassedOverWithItsGlyphToo() {
            // The nought is the whole of what the contest says about such a market, and identifying it
            // is not the contest speaking - a colony the player can see on the map has to be findable
            // from the list whether or not anything weighed it.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(new MarketClaimBreakdown(
                    new EntityNameplate(
                        "Tigra City",
                        Optional.of(new EntityMapIcon(
                            "graphics/warroom/icon_planet.png",
                            new Color(120, 200, 90)))),
                    SECOND_LISTED,
                    IS_KNOWN_TO_PLAYER,
                    CONCEALED,
                    9,
                    ONE_SIBLING_MARKET,
                    OptionalInt.empty()))));

            assertThat(rows.get(1).line().valueText())
                .isEqualTo("0");
            assertThat(rows.get(1).line().mark().spritePath())
                .isEqualTo("graphics/warroom/icon_planet.png");
        }

        @Test
        void resolveMarketRowsDrawsAMarketsGlyphInTheMarketNamesOwnColour() {
            // The map's shades are authored to tell one world from another against black, and carried
            // into the box unchanged they arrive brighter than the numbers the account is about - a
            // column of coloured glyphs reads as the finding when what it is is a bullet point.
            var rows = resolveContestedRows(buildStanding(
                buildMarkedMarket(MARKED_MARKET),
                List.of()));

            assertThat(rows.get(0).line().mark().isInLineColour())
                .isTrue();
        }

        @Test
        void resolveMarketRowsOpensAMarketOnItsNameWhereTheMapMarksItWithNoGlyph() {
            // An entity carrying no authored icon hands the absence straight over, so the line is
            // built from its words rather than from an image run with nothing to load.
            var rows = resolveContestedRows(buildStanding(
                buildMarkedMarket(UNMARKED_MARKET),
                List.of()));

            assertThat(rows.get(0).line().hasMark())
                .isFalse();
        }

        @Test
        void resolveMarketRowsMarksNoLineBeneathAMarket() {
            // A size or a garrison bonus is a term of arithmetic with nothing on the map to point at,
            // so a glyph there would be standing in for a number.
            var rows = resolveContestedRows(buildStanding(
                buildMarkedMarket(MARKED_MARKET),
                List.of()));

            assertThat(rows.get(0).children())
                .isNotEmpty()
                .allSatisfy(term -> assertThat(term.line().hasMark()).isFalse());
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
                    buildMarket(
                        "Eventide",
                        STRONGEST_MARKET_SIZE,
                        TWO_SIBLING_MARKETS,
                        SECOND_LISTED))));

            assertThat(rows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.WON);
            assertThat(rows.get(1).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.LOST);
            assertThat(rows.get(2).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.LOST);
        }

        @Test
        void resolveMarketRowsMarksTheClaimantAsHavingWonATieAgainstARivalFaction() {
            // The comparison that actually settles the system, and the one a per-faction reading
            // cannot see at all: two factions' strongest markets on the same score are parted by the
            // listing alone, so the claimant's reads as having won and the rival's as having lost.
            var claimant = buildStanding(buildStrongestMarket(NO_SIBLING_MARKETS), List.of());
            var rival = buildRivalStanding(SECOND_LISTED, STRONGEST_MARKET_SIZE);
            var breakdown = new SystemClaimBreakdown(null, HEGEMONY, List.of(claimant, rival));

            var claimantRows = ClaimScoreRowResolver.resolveMarketRows(
                breakdown,
                claimant,
                WITHHOLDING_UNFOUND_MARKETS);

            var rivalRows = ClaimScoreRowResolver.resolveMarketRows(
                breakdown,
                rival,
                WITHHOLDING_UNFOUND_MARKETS);

            assertThat(claimantRows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.WON);
            assertThat(rivalRows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.LOST);
        }

        @Test
        void resolveMarketRowsLeavesAClaimantThatOutScoredEveryRivalUnmarked() {
            // Winning outright is not winning a tie. The listing decided nothing there, and a mark
            // would offer the reader a tie-break to look for that never took place.
            var claimant = buildStanding(buildStrongestMarket(NO_SIBLING_MARKETS), List.of());
            var rival = buildRivalStanding(SECOND_LISTED, STRONGEST_MARKET_SIZE - 1);
            var rows = ClaimScoreRowResolver.resolveMarketRows(
                new SystemClaimBreakdown(null, HEGEMONY, List.of(claimant, rival)),
                claimant,
                WITHHOLDING_UNFOUND_MARKETS);

            assertThat(rows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveMarketRowsLeavesATieWithANonTerritorialFactionUnmarked() {
            // A non-territorial faction's score can never take the lead, so the claimant did not
            // out-list it - there was no rival in that tie to out-list. Marking the pair would
            // invent a contest the mechanic skipped.
            var claimant = buildStanding(buildStrongestMarket(NO_SIBLING_MARKETS), List.of());
            var outsider = new FactionClaimScore(
                TRITACHYON,
                !IS_TERRITORIAL,
                buildRivalMarket(SECOND_LISTED, STRONGEST_MARKET_SIZE),
                List.of());

            var breakdown = new SystemClaimBreakdown(null, HEGEMONY, List.of(claimant, outsider));

            var claimantRows = ClaimScoreRowResolver.resolveMarketRows(
                breakdown,
                claimant,
                WITHHOLDING_UNFOUND_MARKETS);
            var outsiderRows = ClaimScoreRowResolver.resolveMarketRows(
                breakdown,
                outsider,
                WITHHOLDING_UNFOUND_MARKETS);

            assertThat(claimantRows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
            assertThat(outsiderRows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveMarketRowsJudgesNoClaimantTieOverASystemHeldByDecree() {
            // The decree settled the system, so the listing settled nothing between the two equal
            // standings beneath it - and a mark would credit the order with an outcome it never had.
            var claimant = buildStanding(buildStrongestMarket(NO_SIBLING_MARKETS), List.of());
            var rival = buildRivalStanding(SECOND_LISTED, STRONGEST_MARKET_SIZE);
            var rows = ClaimScoreRowResolver.resolveMarketRows(
                new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of(claimant, rival)),
                claimant,
                WITHHOLDING_UNFOUND_MARKETS);

            assertThat(rows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveMarketRowsLeavesAPlaceThatDecidedNothingUnmarked() {
            // Markets on different scores are told apart by the scores, so their places settled
            // nothing and a marked one would claim an outcome the numbers already gave. Asserted on
            // the markets alone: the presence line below them sits in no listing at all, which is a
            // different thing from a place that decided nothing.
            var rows = resolveContestedRows(buildStandingOverOneSibling());

            assertThat(rows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
            assertThat(rows.get(1).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveMarketRowsListsAHiddenMarketAtNought() {
            // The mechanic skips it before scoring, so it brought nothing to the contest however
            // large it is - and it is listed all the same, being one of the markets the presence
            // term counts, which a reader checking that count has to be able to see.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildFoundHiddenMarket("Tigra City", STRONGEST_MARKET_SIZE,
                    ONE_SIBLING_MARKET, SECOND_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Tigra City", PRESENCE_LINE);
            assertThat(rows.get(1).line().valueText())
                .isEqualTo("0");

            // The nought is the contest's statement about the market rather than anything it scored,
            // so it reads quiet: in the list's own colour it would pass for a score competed with.
            assertThat(rows.get(1).line().isValueUncounted())
                .isTrue();
            assertThat(rows.get(0).line().isValueUncounted())
                .isFalse();
        }

        @Test
        void resolveMarketRowsRanksAHiddenMarketBelowEveryMarketThatCompeted() {
            // The regression the contest score exists to rule out: read at the score it would have
            // carried, a large hidden base sorts above the market that actually took the system, and
            // the list stops reading in the order the mechanic settles it.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of(
                    buildFoundHiddenMarket(
                        "Tigra City",
                        STRONGEST_MARKET_SIZE + 5,
                        TWO_SIBLING_MARKETS,
                        SECOND_LISTED),
                    buildMarket("Culann", 3, TWO_SIBLING_MARKETS, THIRD_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Culann", "Tigra City", PRESENCE_LINE);
        }

        @Test
        void resolveMarketRowsBreaksAHiddenMarketDownIntoNothing() {
            // Nothing was computed for it: its size and its garrison never entered any sum, so terms
            // beneath it would invite a reader to add up to a number its line does not carry.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildFoundHiddenMarket("Tigra City", STRONGEST_MARKET_SIZE,
                    ONE_SIBLING_MARKET, SECOND_LISTED))));

            assertThat(rows.get(1).children())
                .isEmpty();
        }

        @Test
        void resolveMarketRowsLeavesATieWithAHiddenMarketUnjudged() {
            // A hidden market never competes - the mechanic skips it before scoring - so a standing
            // tied only with one won nothing, and marking it would assert a contest that did not
            // happen. It also keeps the fog honest: an unfound market is a hidden one, so a withheld
            // line can never be the missing partner of a mark the player can see.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildMarket("Kanta's Den", STRONGEST_MARKET_SIZE, ONE_SIBLING_MARKET,
                    SECOND_LISTED, IS_UNFOUND_BY_PLAYER))));

            assertThat(rows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveMarketRowsListsAMarketTheEconomyDoesNotListAtNought() {
            // Vanilla builds Galatia Academy as a real market on a real station and never registers
            // it, so the mechanic's walk never reaches it. Listed at nought states both true things
            // at once - the station is there, in a faction's colours, and it took no part.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(NO_SIBLING_MARKETS),
                List.of(buildOffEconomyMarket(
                    "Galatia Academy",
                    STRONGEST_MARKET_SIZE,
                    SECOND_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Galatia Academy");
            assertThat(rows.get(1).line().valueText())
                .isEqualTo("0");

            // Quiet for the same reason a hidden market's nought is: it is what the contest made of
            // the market rather than a score it competed with and lost on.
            assertThat(rows.get(1).line().isValueUncounted())
                .isTrue();
        }

        @Test
        void resolveMarketRowsBreaksAMarketTheEconomyDoesNotListDownIntoNothing() {
            // Nothing was computed for it, so terms beneath it would invite a reader to add up to a
            // number its own line deliberately does not carry.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(NO_SIBLING_MARKETS),
                List.of(buildOffEconomyMarket(
                    "Galatia Academy",
                    STRONGEST_MARKET_SIZE,
                    SECOND_LISTED))));

            assertThat(rows.get(1).children())
                .isEmpty();
        }

        @Test
        void resolveMarketRowsWithholdsThePresenceTermOverAListHoldingAnUncountedMarket() {
            // The count is the mechanic's, and the mechanic never saw the off-economy market. Printed
            // beneath a list carrying it, the term would read as short by exactly that market - the
            // same broken promise as printing it over a list something was withheld from.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(
                    buildMarket("Ancyra", 3, ONE_SIBLING_MARKET, SECOND_LISTED),
                    buildOffEconomyMarket("Galatia Academy", 3, THIRD_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Ancyra", "Galatia Academy");
        }

        @Test
        void resolveMarketRowsStatesTheSiblingBonusAsWorkingOverThePointsItCameTo() {
            // The line is not one of the faction's holdings but the arithmetic of a term all of them
            // share, so it reads as quietly as any other working and only the points stay a finding.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of()));

            var bonusLine = rows.get(1).line();

            assertThat(bonusLine.isAside())
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
                .allSatisfy(term -> assertThat(term.line().indexPlace()).isNull());
            assertThat(rows.get(1).line().indexPlace())
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
            var standing = buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildMarket("Kanta's Den", 3, ONE_SIBLING_MARKET, SECOND_LISTED,
                    IS_UNFOUND_BY_PLAYER)));

            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildBreakdownClaimedBy(HEGEMONY, standing),
                standing,
                LISTING_UNFOUND_MARKETS);

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Kanta's Den", PRESENCE_LINE);
        }

        @Test
        void resolveMarketRowsCallsOutNoMarketBesidesTheFactionsStrongest() {
            // A second marked line would say one system has two holders, which is exactly what a
            // contest cannot produce.
            var rows = resolveContestedRows(buildStandingOverOneSibling());

            assertThat(rows.get(1).line().qualifierText())
                .isNull();
        }

        @Test
        void resolveMarketRowsCallsOutNoMarketOfAFactionThatTookNothing() {
            // Every faction is represented by its strongest market, but only one of those won
            // anything. A rival's is called out nowhere, since the line would credit it with an
            // outcome it did not produce.
            var standing = buildStandingOverOneSibling();

            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildBreakdownClaimedBy(TRITACHYON, standing),
                standing,
                WITHHOLDING_UNFOUND_MARKETS);

            assertThat(rows.get(0).line().qualifierText())
                .isNull();
        }

        @Test
        void resolveMarketRowsCallsOutNoMarketOfASystemHeldByDecree() {
            // A decree settles the system before a market is weighed, so no market's score decided
            // anything and none is called out for it - the claimant's least of all.
            var standing = buildStandingOverOneSibling();

            var rows = ClaimScoreRowResolver.resolveMarketRows(
                new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of(standing)),
                standing,
                WITHHOLDING_UNFOUND_MARKETS);

            assertThat(rows.get(0).line().qualifierText())
                .isNull();
        }

        @Test
        void resolveMarketRowsStillLeadsWithTheStrongestMarketOfAFactionThatTookNothing() {
            // Only the call-out goes. The markets are still read strongest first, since that is the
            // order a contest is read in whether or not this faction won it.
            var standing = buildStandingOverOneSibling();

            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildBreakdownClaimedBy(TRITACHYON, standing),
                standing,
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
                    UNMARKED_MARKET,
                    FIRST_LISTED,
                    IS_KNOWN_TO_PLAYER,
                    ContestAdmission.WEIGHED,
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

    // The account over a system this faction won outright and whose markets the player has all
    // found - the ordinary state, and what every case but the rivalled, decreed and withheld ones
    // is posed over.
    private static List<CellTooltipEntry> resolveContestedRows(FactionClaimScore standing) {
        return ClaimScoreRowResolver.resolveMarketRows(
            buildBreakdownClaimedBy(HEGEMONY, standing),
            standing,
            WITHHOLDING_UNFOUND_MARKETS);
    }

    // A contest the given faction won on the scores, holding the one standing posed against it. The
    // resolver reads the claimant off the contest rather than being told, so a case stating who won
    // states it here.
    private static SystemClaimBreakdown buildBreakdownClaimedBy(
            String claimantFactionId,
            FactionClaimScore standing) {

        return new SystemClaimBreakdown(null, claimantFactionId, List.of(standing));
    }

    // A rival faction standing on one market of the given size, listed after the standing every case
    // poses first - the shape a cross-faction tie is posed with.
    private static FactionClaimScore buildRivalStanding(int listingPosition, int marketSize) {
        return new FactionClaimScore(
            TRITACHYON,
            IS_TERRITORIAL,
            buildRivalMarket(listingPosition, marketSize),
            List.of());
    }

    // The one market a rival stands on, holding nothing else in the system.
    private static MarketClaimBreakdown buildRivalMarket(int listingPosition, int marketSize) {
        return buildMarket("Eventide", marketSize, NO_SIBLING_MARKETS, listingPosition);
    }

    // The faction most cases here are posed on: its strongest market, and one weaker market listed
    // after it. Named rather than restated per case so a case states only what it varies - which is
    // the tie, the withholding or the call-out it is actually about.
    private static FactionClaimScore buildStandingOverOneSibling() {
        return buildStanding(
            buildStrongestMarket(ONE_SIBLING_MARKET),
            List.of(buildMarket("Culann", 3, ONE_SIBLING_MARKET, SECOND_LISTED)));
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

    // How the mechanic met a market the player has, or has not, found. An unfound market is a
    // concealed one - the two arms of "known" being discovery and being held in the open - so a case
    // posing one poses both, and a found market is the ordinary competitor.
    private static ContestAdmission admitAsFound(boolean isKnownToPlayer) {
        return isKnownToPlayer ? ContestAdmission.WEIGHED : CONCEALED;
    }

    // The same market, stated as one the player has or has not found - the two cases the withholding
    // turns on.
    private static MarketClaimBreakdown buildStrongestMarket(
            int siblingMarketCount,
            boolean isKnownToPlayer) {

        return new MarketClaimBreakdown(
            UNMARKED_MARKET,
            FIRST_LISTED,
            isKnownToPlayer,
            admitAsFound(isKnownToPlayer),
            STRONGEST_MARKET_SIZE,
            siblingMarketCount,
            OptionalInt.empty());
    }

    // The market a standing rests on, stated as one the sector map marks with the given glyph or with
    // none - the two readings the line's opening run turns on. A garrison, so the cases about which
    // lines carry a mark have a term line beneath the market to read.
    private static MarketClaimBreakdown buildMarkedMarket(EntityNameplate market) {
        return new MarketClaimBreakdown(
            market,
            FIRST_LISTED,
            IS_KNOWN_TO_PLAYER,
            ContestAdmission.WEIGHED,
            STRONGEST_MARKET_SIZE,
            NO_SIBLING_MARKETS,
            OptionalInt.of(MILITARY_BONUS));
    }

    // A market held out of the open that the player has nonetheless found - the one combination the
    // two flags part company on, and the only hidden market the box ever draws.
    private static MarketClaimBreakdown buildFoundHiddenMarket(
            String marketName,
            int marketSize,
            int siblingMarketCount,
            int listingPosition) {

        return new MarketClaimBreakdown(
            EntityNameplate.createUnmarkedNameplate(marketName),
            listingPosition,
            IS_KNOWN_TO_PLAYER,
            CONCEALED,
            marketSize,
            siblingMarketCount,
            OptionalInt.empty());
    }

    // A colony the economy does not list - a real market on a real entity the mechanic never reached.
    // Held in the open and found by the player, so the only reason it took no part is the one the case
    // is about.
    private static MarketClaimBreakdown buildOffEconomyMarket(
            String marketName,
            int marketSize,
            int listingPosition) {

        return new MarketClaimBreakdown(
            EntityNameplate.createUnmarkedNameplate(marketName),
            listingPosition,
            IS_KNOWN_TO_PLAYER,
            OFF_ECONOMY,
            marketSize,
            NO_SIBLING_MARKETS,
            OptionalInt.empty());
    }

    // A market every term of the score arose on, for the cases about the whole sum rather than about
    // one term of it: its own size, two others beside it, and a garrison on it.
    private static MarketClaimBreakdown buildFullyScoredMarket() {
        return new MarketClaimBreakdown(
            UNMARKED_MARKET,
            FIRST_LISTED,
            IS_KNOWN_TO_PLAYER,
            ContestAdmission.WEIGHED,
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
            EntityNameplate.createUnmarkedNameplate(marketName),
            listingPosition,
            isKnownToPlayer,
            admitAsFound(isKnownToPlayer),
            marketSize,
            siblingMarketCount,
            OptionalInt.empty());
    }
}
