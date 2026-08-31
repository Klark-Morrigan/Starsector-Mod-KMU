package kmu.maplayers.politicalmap.base.tooltip;

import kmlib.starsector.entities.EntityMapIcon;
import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.systems.claims.ContestAdmission;
import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.PresenceOnlyClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;
import kmlib.testfixtures.starsector.systems.claims.ClaimMarketFixture;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipIndexOutcome;
import kmu.maplayers.base.tooltip.CellTooltipQualifier;
import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.base.visibility.colonies.ColonyDiscoveryLookup;
import kmu.maplayers.base.visibility.colonies.ColonyKind;
import kmu.maplayers.base.visibility.colonies.ColonyKindLookup;
import kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyLookup;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static kmlib.testfixtures.starsector.systems.claims.ClaimMarketFixture.nameMarketId;

import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.NO_NAME_STATED;
import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.readLabelTexts;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.politicalmap.base.tooltip.SystemColonyReadingFixture.LAST_SEEN;
import static kmu.maplayers.politicalmap.base.tooltip.SystemColonyReadingFixture.buildReadingRemarkingOn;
import static kmu.maplayers.politicalmap.base.tooltip.SystemColonyReadingFixture.buildReadingWithOpenlyKnown;
import static kmu.maplayers.politicalmap.base.tooltip.SystemColonyReadingFixture.buildReadingWithUndiscovered;

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
 * <p>A faction the contest never weighed is pinned as the shape with the two closing statements taken
 * out: no colony called out as having taken the system, and no presence term at the foot. Both would
 * be arithmetic that never happened, and both are the kind of line a form prints and an account does
 * not.
 *
 * <p>Where a mark may appear is pinned here too, since it is a statement about which line is about a
 * thing on the map: a market's own line leads with the glyph the map marks it by, scored or not, and
 * nothing beneath it carries one. That the glyph reads in the market name's own colour rather than the
 * map's is pinned beside it - the box declining an authored shade is a decision, not an omission.
 *
 * <p>Where a remark about how old the box's news of a market is may appear is pinned the same way -
 * on a market's own line whatever the contest made of it, and on nothing beneath one. When such a
 * remark is due at all is the notes' own question and is pinned by {@link ColonyObservationNotesTest}.
 *
 * <p>What a row the box may not name looks like is {@link RedactedMarketLinesTest}'s. What is pinned
 * here is what the account does with such a row: which markets get one, the listing place and the tie
 * outcome it keeps, the empty value column and the one case that fills it, and the terms it declines to
 * break into. Each of those is posed over a walk of the system that agrees with the contest about the
 * colony being undiscovered, the two being separate reads that only ever part company in a fixture.
 *
 * <p>What a market's line calls out about the place is pinned here only as far as this resolver
 * decides it: which facts it hands over - the claim it took, the admission the contest met it
 * through, and what the box's walk of the system says about it. Which words those come to, in which
 * order, is the shared read's own question and is pinned by {@link ColonyQualifierTest}.
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

    // The colony the box may not name, in the cases about a row it draws for one. A name of its own
    // rather than the stock second market the cases about concealment reach for, so a reader meeting
    // both in one file is not left to wonder whether the sameness meant anything.
    private static final String UNDISCOVERED_MARKET = "Fikenhild";

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

    // Whether a market the contest never weighed may be listed though nobody has discovered it:
    // withheld in play, stated under the dev reveal. One the contest did weigh is listed either way,
    // its weight being in the numbers on screen already.
    private static final boolean WITHHOLDING_UNDISCOVERED_MARKETS = false;
    private static final boolean LISTING_UNDISCOVERED_MARKETS = true;

    // Whether the player knows of a market at all - the flag the withholding reads. Independent of
    // whether the market is held in the open: the mechanic settles a contest over colonies nobody
    // has reached, so an open market that is also undiscovered is the ordinary shape rather than a
    // corner, and a case wanting a concealed market poses the concealment itself.
    private static final boolean IS_KNOWN_TO_PLAYER = true;

    // The same flag false, named for the two shapes that reach it. An open market is unknown only
    // by being undiscovered; a concealed one stays unknown until somebody has seen it standing
    // there, whatever its entity says - so a case posing one of them says which it means.
    private static final boolean IS_UNDISCOVERED_BY_PLAYER = false;
    private static final boolean IS_UNKNOWN_TO_PLAYER = false;

    // Every market an ordinary colony somebody is looking at, so no line is qualified and none
    // carries a date. Both halves of what the box's walk of the system adds to a row, stated once
    // for every case about the arithmetic rather than varied: which kinds resolve is
    // ColonyKindLookupTest's question and what makes a remark due is ColonyObservationNotesTest's.
    private static final SystemColonyReading NOTHING_BEYOND_THE_SCORE = SystemColonyReading.NONE;

    // The world people left, as the box's own walk of the system classified it.
    private static final SystemColonyReading TIBICENA_IS_A_DEAD_WORLD = new SystemColonyReading(
        new ColonyKindLookup(Map.of("tibicena", ColonyKind.UNGOVERNED_COLONY)),
        ColonyDiscoveryLookup.NONE,
        OpenlyKnownColonyLookup.NONE,
        ColonyObservationNotes.NONE);

    // A market the sector map marks with a glyph, and one it marks with none - the two readings a
    // market line's opening run turns on. The authored colour is carried because the read hands one
    // over, not because anything below reads it: a resolved line has nowhere to put an asset colour,
    // which is the point of the mark stating that it follows its name instead.
    private static final EntityNameplate MARKED_MARKET = new EntityNameplate(
        STRONGEST_MARKET,
        Optional.of(new EntityMapIcon("graphics/warroom/icon_planet.png", new Color(120, 200, 90))));

    private static final EntityNameplate UNMARKED_MARKET =
        EntityNameplate.createUnmarkedNameplate(STRONGEST_MARKET);

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
            assertThat(rows.get(0).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("claim holder"));
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
                List.of(ClaimMarketFixture
                    .startMarket("Tigra City")
                    .setNameplate(new EntityNameplate(
                        "Tigra City",
                        Optional.of(new EntityMapIcon(
                            "graphics/warroom/icon_planet.png",
                            new Color(120, 200, 90)))))
                    .setListingPosition(SECOND_LISTED)
                    .setAdmission(ContestAdmission.HIDDEN)
                    .setMarketSize(9)
                    .setSiblingMarketCount(ONE_SIBLING_MARKET)
                    .buildMarket())));

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
                NOTHING_BEYOND_THE_SCORE,
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

            var rivalRows = ClaimScoreRowResolver.resolveMarketRows(
                breakdown,
                rival,
                NOTHING_BEYOND_THE_SCORE,
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

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
                NOTHING_BEYOND_THE_SCORE,
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

            assertThat(rows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveMarketRowsLeavesATieWithANonTerritorialFactionUnmarked() {
            // A non-territorial faction's score can never take the lead, so the claimant did not
            // out-list it - there was no rival in that tie to out-list. Marking the pair would
            // invent a contest the mechanic skipped.
            var claimant = buildStanding(buildStrongestMarket(NO_SIBLING_MARKETS), List.of());
            var outsider = new WeighedClaimStanding(
                TRITACHYON,
                !IS_TERRITORIAL,
                buildRivalMarket(SECOND_LISTED, STRONGEST_MARKET_SIZE),
                List.of());

            var breakdown = new SystemClaimBreakdown(null, HEGEMONY, List.of(claimant, outsider));

            var claimantRows = ClaimScoreRowResolver.resolveMarketRows(
                breakdown,
                claimant,
                NOTHING_BEYOND_THE_SCORE,
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);
            var outsiderRows = ClaimScoreRowResolver.resolveMarketRows(
                breakdown,
                outsider,
                NOTHING_BEYOND_THE_SCORE,
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

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
                NOTHING_BEYOND_THE_SCORE,
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

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
            // happen.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildFoundHiddenMarket("Kanta's Den", STRONGEST_MARKET_SIZE,
                    ONE_SIBLING_MARKET, SECOND_LISTED))));

            assertThat(rows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.UNCONTESTED);
        }

        @Test
        void resolveMarketRowsListsAMarketTheEconomyDoesNotListAtNought() {
            // Vanilla leaves a real market on a real station unregistered, so the mechanic's walk
            // never reaches it. Listed at nought states both true things at once - the station is
            // there, in a faction's colours, and it took no part.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(NO_SIBLING_MARKETS),
                List.of(buildOffEconomyMarket(
                    "Kirov Reserve",
                    STRONGEST_MARKET_SIZE,
                    SECOND_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Kirov Reserve");
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
                    "Kirov Reserve",
                    STRONGEST_MARKET_SIZE,
                    SECOND_LISTED))));

            assertThat(rows.get(1).children())
                .isEmpty();
        }

        @Test
        void resolveMarketRowsWithholdsThePresenceTermOverAListHoldingAnUncountedMarket() {
            // The count is the mechanic's, and the mechanic never saw the off-economy market. Printed
            // beneath a list carrying it, the term would read as short by exactly that market - a
            // count the reader can see is contradicted, which is the one way it may not part company
            // with the list.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(
                    buildMarket("Ancyra", 3, ONE_SIBLING_MARKET, SECOND_LISTED),
                    buildOffEconomyMarket("Kirov Reserve", 3, THIRD_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Ancyra", "Kirov Reserve");
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
        void resolveMarketRowsLeavesAnUnlistedMarketUnknownToThePlayerOffTheList() {
            // Both exclusions against it at once: the economy never listed it, so the mechanic's walk
            // reached it for neither a score nor the sibling count and no number on screen needs it to
            // add up, and the player has not found it, so there is nothing they could already know. A
            // row would be disclosure and nothing else.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of(
                    buildMarket("Culann", 3, TWO_SIBLING_MARKETS, SECOND_LISTED),
                    buildUnknownUnlistedMarket("Kanta's Den", 3, TWO_SIBLING_MARKETS,
                        THIRD_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Culann", PRESENCE_LINE);
        }

        @Test
        void resolveMarketRowsBlocksOutAConcealedMarketTheSiblingCountPaidFor() {
            // The count is the row's warrant. The faction was paid a point for this colony, so the
            // block states three markets while its own rows would show two - and a count contradicted
            // by the list beneath it is the one thing the account must not do. The name is what the
            // row gives up instead.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(TWO_SIBLING_MARKETS),
                List.of(
                    buildMarket("Culann", 3, TWO_SIBLING_MARKETS, SECOND_LISTED),
                    buildUnknownHiddenMarket("Kanta's Den", 3, TWO_SIBLING_MARKETS, THIRD_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Culann", NO_NAME_STATED, PRESENCE_LINE);
            assertThat(rows.get(2).line().hasRedactedName())
                .isTrue();

            // And says why it was passed over, as loudly as any other line says it. The word is the
            // one thing separating this row from the weighed kind's, both being blocked out - so a row
            // that lost it would read as a market the contest weighed and declined to explain.
            assertThat(rows.get(2).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("hidden"));
        }

        @Test
        void resolveMarketRowsCallsABlockedOutConcealedMarketUndiscoveredWhereItsEntityIsToo() {
            // The displacement the qualifier makes, on a row that now exists to carry it: a concealed
            // colony on an entity nobody has found is undiscovered first and concealed second, since a
            // reader who has not found the place has no use for being told what is hidden on it.
            var standing = buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildUnknownHiddenMarket("Kanta's Den", 3, ONE_SIBLING_MARKET,
                    SECOND_LISTED)));

            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildBreakdownClaimedBy(HEGEMONY, standing),
                standing,
                buildReadingWithUndiscovered(nameMarketId("Kanta's Den")),
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

            assertThat(rows.get(1).line().hasRedactedName())
                .isTrue();
            assertThat(rows.get(1).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("undiscovered"));
        }

        @Test
        void resolveMarketRowsStatesTheQuietNoughtOfABlockedOutMarketTheContestPassedOver() {
            // The nought is the contest's own statement that the colony counted for nothing, which is
            // no part of what the row withholds - and an empty column beside a blocked-out name would
            // read as a figure kept back rather than as one there was never any of.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildUnknownHiddenMarket("Kanta's Den", 3, ONE_SIBLING_MARKET,
                    SECOND_LISTED))));

            assertThat(rows.get(1).line().valueText())
                .isEqualTo("0");
            assertThat(rows.get(1).line().isValueUncounted())
                .isTrue();
        }

        @Test
        void resolveMarketRowsListsAMarketTheContestWeighedThoughItsColonyIsUndiscovered() {
            // The market's weight is already in the numbers on screen - the faction's score, and the
            // presence its siblings were each given - so the row is what makes them accountable. It is
            // the name alone that is kept back, the row standing in its place.
            var rows = resolveRowsOverAnUndiscoveredMarket();

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, NO_NAME_STATED, PRESENCE_LINE);
            assertThat(rows.get(1).line().hasRedactedName())
                .isTrue();
        }

        @Test
        void resolveMarketRowsStillCallsAMarketItWillNotNameUndiscovered() {
            // The whole shape of the row at once, which is the only reading that pins the word and the
            // blocked-out name as one answer: what the fog takes is the colony's identity, not the fact
            // that the box could not find it - so the finding is stated as loudly as on any other line.
            var rows = resolveRowsOverAnUndiscoveredMarket();

            assertThat(rows.get(1).line().hasRedactedName())
                .isTrue();
            assertThat(rows.get(1).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("undiscovered"));
        }

        @Test
        void resolveMarketRowsStatesWhereTheEconomyListsAnUndiscoveredMarket() {
            // The place identifies the market rather than describing the colony, so it survives the
            // withholding of the name.
            var rows = resolveRowsOverAnUndiscoveredMarket();

            assertThat(rows.get(1).line().indexPlace().text())
                .isEqualTo("[2]");
        }

        @Test
        void resolveMarketRowsMarksATieAnUndiscoveredMarketWonOrLost() {
            // The reason the place has to survive: a tie is settled by the listing alone, and no other
            // number on screen accounts for it - so a blocked-out row that lost one says so, or the
            // reader is left with two equal scores and no explanation of which took the system.
            var rows = resolveContestedRows(
                buildStanding(
                    buildStrongestMarket(ONE_SIBLING_MARKET),
                    List.of(buildMarket(
                        UNDISCOVERED_MARKET,
                        STRONGEST_MARKET_SIZE,
                        ONE_SIBLING_MARKET,
                        SECOND_LISTED,
                        IS_UNDISCOVERED_BY_PLAYER))),
                buildReadingWithUndiscovered(nameMarketId(UNDISCOVERED_MARKET)));

            assertThat(rows.get(0).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.WON);
            assertThat(rows.get(1).line().indexPlace().outcome())
                .isEqualTo(CellTooltipIndexOutcome.LOST);
        }

        @Test
        void resolveMarketRowsStatesNoScoreForAnUndiscoveredMarketBesidesTheStanding() {
            // The column stands empty rather than carrying the figure. The row is still ranked on the
            // real score - between the two markets it falls between here - so the lines either side of
            // it bound what it came to: the box declines to state the number, and does not go on to
            // pretend the contest ran in some other order.
            var rows = resolveContestedRows(
                buildStanding(
                    buildStrongestMarket(TWO_SIBLING_MARKETS),
                    List.of(
                        buildMarket("Eventide", 3, TWO_SIBLING_MARKETS, SECOND_LISTED),
                        buildMarket(UNDISCOVERED_MARKET, 5, TWO_SIBLING_MARKETS, THIRD_LISTED,
                            IS_UNDISCOVERED_BY_PLAYER))),
                buildReadingWithUndiscovered(nameMarketId(UNDISCOVERED_MARKET)));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, NO_NAME_STATED, "Eventide", PRESENCE_LINE);
            assertThat(rows.get(1).line().valueText())
                .isEqualTo(CellTooltipRows.NO_SCORE);
        }

        @Test
        void resolveMarketRowsBreaksAnUndiscoveredMarketDownIntoNothing() {
            // A size and a garrison are the colony itself described term by term, which is the account
            // the row exists not to give. Unlike a market the mechanic passed over, the terms were
            // computed here - they are being withheld rather than absent.
            var rows = resolveRowsOverAnUndiscoveredMarket();

            assertThat(rows.get(1).children())
                .isEmpty();
        }

        @Test
        void resolveMarketRowsStatesTheScoreOfAnUndiscoveredMarketAFactionStandsOn() {
            // The one figure a blocked-out row carries, because the faction's own line above already
            // states it: withheld here it would hide nothing, while leaving the block's arithmetic
            // unaccountable.
            var rows = resolveRowsOverAnUndiscoveredStandingMarket();

            assertThat(rows.get(0).line().valueText())
                .isEqualTo("8");
            assertThat(rows.get(0).line().isValueUncounted())
                .isFalse();
        }

        @Test
        void resolveMarketRowsCallsOutAnUndiscoveredMarketThatTookTheSystem() {
            // The two findings a blocked-out row can carry at once, and the shape the fog makes
            // reachable: a faction can take a system on a colony nobody has found, and the map is
            // already painting that system in its colours - so the claim is stated on the very line
            // that declines to name the place, rather than the box dropping one to withhold the other.
            var rows = resolveRowsOverAnUndiscoveredStandingMarket();

            assertThat(rows.get(0).line().hasRedactedName())
                .isTrue();
            assertThat(rows.get(0).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("claim holder, undiscovered"));
        }

        @Test
        void resolveMarketRowsStatesThePresenceTermOverAListSomethingWasKeptFrom() {
            // The count runs ahead of the shortened list, which is what the reader is owed rather
            // than what must be kept from them: the market on screen carries a score its own terms
            // fall short of, so the difference is already stated and the term is the only thing that
            // names it. Withheld, the account simply does not add up.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildUnknownUnlistedMarket("Kanta's Den", 3, ONE_SIBLING_MARKET,
                    SECOND_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, PRESENCE_LINE);
            assertThat(rows.get(1).line().valueWorkingText())
                .isEqualTo("(2 markets) - 1 =");
        }

        @Test
        void resolveMarketRowsListsTheStandingMarketOnAnUndiscoveredColony() {
            // The mechanic weighs colonies nobody has reached, so the very market a faction stands on
            // can be one the player has not found - and it is the market the faction's own line states
            // the score of. Left off, that number would head an account with nothing in it that comes
            // to the number. What the row gives up is the name, which is what the block below it is
            // pinned on.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET, IS_UNDISCOVERED_BY_PLAYER),
                List.of(buildMarket("Culann", 4, ONE_SIBLING_MARKET, SECOND_LISTED))));

            assertThat(readLabelTexts(rows))
                .containsExactly(NO_NAME_STATED, "Culann", PRESENCE_LINE);
            assertThat(rows.get(0).line().hasRedactedName())
                .isTrue();
        }

        @Test
        void resolveMarketRowsListsEveryMarketUnderTheDevReveal() {
            // The reveal is the state a player has asked to be shown everything in, so even the one
            // market no number on screen needs - off the economy's books and undiscovered at once -
            // takes its place in the account, and takes it under its own name.
            //
            // Posed as known as well as revealed, which is how the reveal arrives here: the setting
            // reaches the row walk as a flag and the colonies as knowledge, both off the same knob, so
            // a fixture raising one and leaving the other would pose a state play cannot produce.
            var standing = buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildOffEconomyMarket("Kanta's Den", 3, SECOND_LISTED)));

            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildBreakdownClaimedBy(HEGEMONY, standing),
                standing,
                NOTHING_BEYOND_THE_SCORE,
                LISTING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

            // No presence term beneath them, and that is the reveal's own doing rather than a second
            // rule: the market it admitted is one the count never reached, so the term would read as
            // short by a market sitting in plain sight two lines above it.
            assertThat(readLabelTexts(rows))
                .containsExactly(STRONGEST_MARKET, "Kanta's Den");
        }

        @Test
        void resolveMarketRowsCallsOutAnUndiscoveredMarket() {
            // Posed in play, that being where such a market is now listed: the contest weighed it.
            // The word comes off the box's own walk of the system rather than off the contest - no
            // claim row carries the entity's flag, which is nowhere in the arithmetic.
            var standing = buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildMarket("Kanta's Den", 3, ONE_SIBLING_MARKET, SECOND_LISTED,
                    IS_UNDISCOVERED_BY_PLAYER)));

            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildBreakdownClaimedBy(HEGEMONY, standing),
                standing,
                buildReadingWithUndiscovered("kanta's_den"),
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

            assertThat(rows.get(1).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("undiscovered"));
        }

        @Test
        void resolveMarketRowsCallsOutNoMarketBesidesTheFactionsStrongest() {
            // A second marked line would say one system has two holders, which is exactly what a
            // contest cannot produce.
            var rows = resolveContestedRows(buildStandingOverOneSibling());

            assertThat(rows.get(1).line().qualifier())
                .isNull();
        }

        @Test
        void resolveMarketRowsCallsADeadWorldOutBesideItsNought() {
            // The one thing this box says about a colony the mechanic passed over, and the pair is
            // what makes it necessary: a ruin and a concealed base both arrive at nought, and only
            // the ruin is a fact about the world rather than about the contest.
            var rows = resolveContestedRows(
                buildStanding(
                    buildStrongestMarket(NO_SIBLING_MARKETS),
                    List.of(buildOffEconomyMarket(
                        "Tibicena",
                        STRONGEST_MARKET_SIZE,
                        SECOND_LISTED))),
                TIBICENA_IS_A_DEAD_WORLD);

            assertThat(rows.get(1).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("decivilised"));
        }

        @Test
        void resolveMarketRowsCallsAnOrdinaryOffEconomyColonyUnlisted() {
            // The same line for a colony that is merely unregistered: nothing about the place is a
            // finding, so what is left is why the contest never met it.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(NO_SIBLING_MARKETS),
                List.of(buildOffEconomyMarket(
                    "Kirov Reserve",
                    STRONGEST_MARKET_SIZE,
                    SECOND_LISTED))));

            assertThat(rows.get(1).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("unlisted"));
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
                NOTHING_BEYOND_THE_SCORE,
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

            assertThat(rows.get(0).line().qualifier())
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
                NOTHING_BEYOND_THE_SCORE,
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

            assertThat(rows.get(0).line().qualifier())
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
                NOTHING_BEYOND_THE_SCORE,
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

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
                ClaimMarketFixture
                    .startMarket(STRONGEST_MARKET)
                    .setNameplate(UNMARKED_MARKET)
                    .setListingPosition(FIRST_LISTED)
                    .setMarketSize(STRONGEST_MARKET_SIZE)
                    .setMilitaryBonus(MILITARY_BONUS)
                    .buildMarket(),
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
        void resolveMarketRowsListsAPresenceOnlyFactionsColoniesAtNoughtInListingOrder() {
            // A faction the contest never weighed has no score to rank its colonies by, so the
            // account reads in the order the system's listing reaches them - the one order the walk
            // ever imposed - and every line carries the nought the contest weighed it at.
            var rows = resolvePresenceOnlyRows(
                buildFoundHiddenMarket("Kanta's Den", 6, NO_SIBLING_MARKETS, SECOND_LISTED),
                buildOffEconomyMarket("Kirov Reserve", 4, THIRD_LISTED));

            assertThat(readLabelTexts(rows))
                .containsExactly("Kanta's Den", "Kirov Reserve");
            assertThat(rows)
                .allSatisfy(row -> assertThat(row.line().valueText()).isEqualTo("0"));
        }

        @Test
        void resolveMarketRowsNamesNoColonyOfAPresenceOnlyFactionAsTheHolder() {
            // Nothing behind such a standing took the system: it scores nought, and the lead changes
            // only on a score strictly greater than nought. A call-out here would name a holder the
            // contest never produced.
            //
            // The line still says how the colony is out of plain view, that being a finding about
            // the place rather than about the contest - so the claim is asserted absent from what
            // the line calls out rather than the whole slot asserted empty.
            var rows = resolvePresenceOnlyRows(
                buildFoundHiddenMarket("Kanta's Den", 6, NO_SIBLING_MARKETS, SECOND_LISTED));

            assertThat(rows.get(0).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("hidden"));
        }

        @Test
        void resolveMarketRowsCallsAColonyTheSectorOpenlyPointsAtUnlistedRatherThanHidden() {
            // Galatia Academy: concealed, unregistered, and a place the tutorial sends the player
            // to. What is left once the concealment is excused is the fallback, which is the
            // separation the word was wanted for - the box says the economy does not carry the
            // Academy rather than that the Academy is hiding.
            var rows = resolvePresenceOnlyRows(
                buildReadingWithOpenlyKnown(ClaimMarketFixture.nameMarketId("Galatia Academy")),
                buildFoundHiddenOffEconomyMarket("Galatia Academy"));

            assertThat(rows.get(0).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("unlisted"));
        }

        @Test
        void resolveMarketRowsCallsAColonyOfTheSameShapeNobodyVouchesForHidden() {
            // The other half of that pair, differing in nothing the breakdown carries: a concealed
            // colony the economy also drops goes on reading as concealed, which is the case the
            // excusing must not reach.
            var rows = resolvePresenceOnlyRows(
                buildFoundHiddenOffEconomyMarket("Daybreak"));

            assertThat(rows.get(0).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("hidden"));
        }

        @Test
        void resolveMarketRowsClosesAPresenceOnlyAccountWithNoPresenceTerm() {
            // The term is arithmetic of a score, and no score was computed for this faction at all -
            // so a line stating one would account for a sum that never happened. The colonies carry
            // sibling counts all the same, being what the mechanic recorded on the way past them.
            var rows = resolvePresenceOnlyRows(
                buildFoundHiddenMarket("Kanta's Den", 6, TWO_SIBLING_MARKETS, SECOND_LISTED),
                buildFoundHiddenMarket("Chalcedon", 4, TWO_SIBLING_MARKETS, THIRD_LISTED));

            assertThat(readLabelTexts(rows))
                .containsExactly("Kanta's Den", "Chalcedon");
        }

        @Test
        void resolveMarketRowsBreaksAPresenceOnlyFactionsColonyDownIntoNothing() {
            // Nothing was computed for it, so there are no terms to state - the same sentence a
            // weighed faction's passed-over market speaks, and for the same reason.
            var rows = resolvePresenceOnlyRows(
                buildFoundHiddenMarket("Kanta's Den", 6, NO_SIBLING_MARKETS, SECOND_LISTED));

            assertThat(rows.get(0).children())
                .isEmpty();
        }

        @Test
        void resolveMarketRowsWithholdsAPresenceOnlyFactionsUnknownColony() {
            // The sibling count is what earns a concealed colony a row, and this kind of account states
            // none: a faction the contest never weighed scores a named nought with no terms beneath it,
            // so nothing on screen is short of the colony and a row would only disclose it.
            //
            // The same market under a weighed standing is listed, which is the pairing that makes this
            // the account's decision rather than the market's.
            var standing = buildPresenceOnlyStanding(
                buildFoundHiddenMarket("Kanta's Den", 6, ONE_SIBLING_MARKET, SECOND_LISTED),
                buildUnknownHiddenMarket("Chalcedon", 4, ONE_SIBLING_MARKET, THIRD_LISTED));

            assertThat(readLabelTexts(ClaimScoreRowResolver.resolveMarketRows(
                    buildBreakdownClaimedBy(HEGEMONY, standing),
                    standing,
                    NOTHING_BEYOND_THE_SCORE,
                    WITHHOLDING_UNDISCOVERED_MARKETS,
                    PATROL_DETAILS)))
                .containsExactly("Kanta's Den");
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

        @Test
        void resolveMarketRowsRemarksHowOldTheNewsOfAConcealedColonyIs() {
            // The kind the remark matters most for: a base held in concealment is on the list on
            // the strength of somebody having seen it, and the nought beside it says nothing else.
            var standing = buildPresenceOnlyStanding(
                buildFoundHiddenMarket("Kanta's Den", 6, NO_SIBLING_MARKETS, SECOND_LISTED));

            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildBreakdownClaimedBy(TRITACHYON, standing),
                standing,
                buildReadingRemarkingOn("kanta's_den"),
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

            assertThat(rows.get(0).line().noteText())
                .isEqualTo(LAST_SEEN);
        }

        @Test
        void resolveMarketRowsRemarksHowOldTheNewsOfAnOffEconomyColonyIs() {
            // The other shape the contest never weighed, answering on the same terms: the mechanic's
            // walk never reached it, so when it was last seen is all the account has left to add.
            var standing = buildPresenceOnlyStanding(
                buildOffEconomyMarket("Kirov Reserve", 4, THIRD_LISTED));

            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildBreakdownClaimedBy(TRITACHYON, standing),
                standing,
                buildReadingRemarkingOn(ClaimMarketFixture.nameMarketId("Kirov Reserve")),
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

            assertThat(rows.get(0).line().noteText())
                .isEqualTo(LAST_SEEN);
        }

        @Test
        void resolveMarketRowsRemarksHowOldTheNewsOfAWeighedColonyIs() {
            // How current the box's news of a colony is has nothing to do with whether the mechanic
            // weighed it, so a scored market carries the remark on the very same terms.
            var standing = buildStanding(buildStrongestMarket(NO_SIBLING_MARKETS), List.of());

            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildBreakdownClaimedBy(HEGEMONY, standing),
                standing,
                buildReadingRemarkingOn(ClaimMarketFixture.nameMarketId(STRONGEST_MARKET)),
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

            assertThat(rows.get(0).line().noteText())
                .isEqualTo(LAST_SEEN);
        }

        @Test
        void resolveMarketRowsRemarksNothingBeneathARemarkedColony() {
            // A size or a garrison is arithmetic over the market's own line, so a date there would
            // answer for the line above it twice.
            var standing = buildStanding(buildFullyScoredMarket(), List.of());

            var rows = ClaimScoreRowResolver.resolveMarketRows(
                buildBreakdownClaimedBy(HEGEMONY, standing),
                standing,
                buildReadingRemarkingOn(ClaimMarketFixture.nameMarketId(STRONGEST_MARKET)),
                WITHHOLDING_UNDISCOVERED_MARKETS,
                PATROL_DETAILS);

            assertThat(rows.get(0).children())
                .allSatisfy(term -> assertThat(term.line().noteText()).isNull());
        }

        @Test
        void resolveMarketRowsRemarksNothingOnAColonyBeingLookedAtNow() {
            // The ordinary case: in sight, the name stands alone.
            var rows = resolveContestedRows(buildStanding(
                buildStrongestMarket(NO_SIBLING_MARKETS),
                List.of()));

            assertThat(rows.get(0).line().noteText())
                .isNull();
        }
    }

    // The account over a system this faction won outright and whose markets the player has all
    // found - the ordinary state, and what every case but the rivalled, decreed and withheld ones
    // is posed over.
    private static List<CellTooltipEntry> resolveContestedRows(WeighedClaimStanding standing) {
        return resolveContestedRows(standing, NOTHING_BEYOND_THE_SCORE);
    }

    // The same, for a case that is about what a colony's kind states on the line naming it - the
    // kinds come off the box's own walk of the system, which is a separate read from the contest.
    private static List<CellTooltipEntry> resolveContestedRows(
            WeighedClaimStanding standing,
            SystemColonyReading colonyReading) {

        return ClaimScoreRowResolver.resolveMarketRows(
            buildBreakdownClaimedBy(HEGEMONY, standing),
            standing,
            colonyReading,
            WITHHOLDING_UNDISCOVERED_MARKETS,
            PATROL_DETAILS);
    }

    // A contest the given faction won on the scores, holding the one standing posed against it. The
    // resolver reads the claimant off the contest rather than being told, so a case stating who won
    // states it here.
    private static SystemClaimBreakdown buildBreakdownClaimedBy(
            String claimantFactionId,
            FactionClaimStanding standing) {

        return new SystemClaimBreakdown(null, claimantFactionId, List.of(standing));
    }

    // The account of a faction the contest never weighed, over a system another faction took. Posed
    // as the claimant's rival throughout: what the cases are about is that nothing behind such a
    // standing is called out or summed, which a system it could be mistaken for having won would
    // leave unsaid.
    private static List<CellTooltipEntry> resolvePresenceOnlyRows(
            MarketClaimBreakdown... unweighedMarkets) {

        return resolvePresenceOnlyRows(NOTHING_BEYOND_THE_SCORE, unweighedMarkets);
    }

    // The same rows under a stated walk of the system, for a case whose finding is one no breakdown
    // carries.
    private static List<CellTooltipEntry> resolvePresenceOnlyRows(
            SystemColonyReading colonyReading,
            MarketClaimBreakdown... unweighedMarkets) {

        var standing = buildPresenceOnlyStanding(unweighedMarkets);

        return ClaimScoreRowResolver.resolveMarketRows(
            buildBreakdownClaimedBy(TRITACHYON, standing),
            standing,
            colonyReading,
            WITHHOLDING_UNDISCOVERED_MARKETS,
            PATROL_DETAILS);
    }

    // A faction present in the system through the given colonies alone, none of which the mechanic
    // weighed. Territorial like every standing posed here, which nothing in this resolver reads.
    private static PresenceOnlyClaimStanding buildPresenceOnlyStanding(
            MarketClaimBreakdown... unweighedMarkets) {

        return new PresenceOnlyClaimStanding(HEGEMONY, IS_TERRITORIAL, List.of(unweighedMarkets));
    }

    // A colony off the economy's listing that the player knows nothing of either - the one shape the
    // withholding keeps off the list, the mechanic's walk never having reached it for even the sibling
    // count to pay for.
    private static MarketClaimBreakdown buildUnknownUnlistedMarket(
            String marketName,
            int marketSize,
            int siblingMarketCount,
            int listingPosition) {

        return ClaimMarketFixture
            .startMarket(marketName)
            .setListingPosition(listingPosition)
            .setKnownToPlayer(IS_UNKNOWN_TO_PLAYER)
            .setAdmission(ContestAdmission.OFF_ECONOMY)
            .setMarketSize(marketSize)
            .setSiblingMarketCount(siblingMarketCount)
            .buildMarket();
    }

    // A concealed colony the player knows nothing of either - the two arms of "known" both against
    // it. Listed all the same, the sibling count reaching it, and listed with its name blocked out.
    private static MarketClaimBreakdown buildUnknownHiddenMarket(
            String marketName,
            int marketSize,
            int siblingMarketCount,
            int listingPosition) {

        return ClaimMarketFixture
            .startMarket(marketName)
            .setListingPosition(listingPosition)
            .setKnownToPlayer(IS_UNKNOWN_TO_PLAYER)
            .setAdmission(ContestAdmission.HIDDEN)
            .setMarketSize(marketSize)
            .setSiblingMarketCount(siblingMarketCount)
            .buildMarket();
    }

    // A rival faction standing on one market of the given size, listed after the standing every case
    // poses first - the shape a cross-faction tie is posed with.
    private static WeighedClaimStanding buildRivalStanding(int listingPosition, int marketSize) {
        return new WeighedClaimStanding(
            TRITACHYON,
            IS_TERRITORIAL,
            buildRivalMarket(listingPosition, marketSize),
            List.of());
    }

    // The one market a rival stands on, holding nothing else in the system.
    private static MarketClaimBreakdown buildRivalMarket(int listingPosition, int marketSize) {
        return buildMarket("Eventide", marketSize, NO_SIBLING_MARKETS, listingPosition);
    }

    // The account the cases about a blocked-out row read: the faction's strongest market in plain
    // sight, and a weaker one the contest weighed on a colony nobody has found.
    //
    // The walk of the system is stated to match, and that is the point of naming this rather than
    // posing it per case. Whether the box may name a market and whether it calls the colony
    // undiscovered are two separate reads - the contest's own flag against the box's walk - so a
    // fixture setting one and leaving the other at its resting state poses a world play cannot
    // produce, and every case built on it would be asserting over that world.
    private static List<CellTooltipEntry> resolveRowsOverAnUndiscoveredMarket() {
        return resolveContestedRows(
            buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET),
                List.of(buildMarket(UNDISCOVERED_MARKET, 3, ONE_SIBLING_MARKET, SECOND_LISTED,
                    IS_UNDISCOVERED_BY_PLAYER))),
            buildReadingWithUndiscovered(nameMarketId(UNDISCOVERED_MARKET)));
    }

    // The same, with the undiscovered colony as the very market the faction stands on - the shape the
    // claimant's own account takes when the contest hands it a system on a colony nobody has found.
    private static List<CellTooltipEntry> resolveRowsOverAnUndiscoveredStandingMarket() {
        return resolveContestedRows(
            buildStanding(
                buildStrongestMarket(ONE_SIBLING_MARKET, IS_UNDISCOVERED_BY_PLAYER),
                List.of(buildMarket("Culann", 4, ONE_SIBLING_MARKET, SECOND_LISTED))),
            buildReadingWithUndiscovered(nameMarketId(STRONGEST_MARKET)));
    }

    // The faction most cases here are posed on: its strongest market, and one weaker market listed
    // after it. Named rather than restated per case so a case states only what it varies - which is
    // the tie, the withholding or the call-out it is actually about.
    private static WeighedClaimStanding buildStandingOverOneSibling() {
        return buildStanding(
            buildStrongestMarket(ONE_SIBLING_MARKET),
            List.of(buildMarket("Culann", 3, ONE_SIBLING_MARKET, SECOND_LISTED)));
    }

    // A faction standing on the given market and holding the given others in the system. Territorial
    // throughout: which block a standing is listed under is the box's to decide, and no case here is
    // about it.
    private static WeighedClaimStanding buildStanding(
            MarketClaimBreakdown standingMarket,
            List<MarketClaimBreakdown> otherMarkets) {

        return new WeighedClaimStanding(HEGEMONY, IS_TERRITORIAL, standingMarket, otherMarkets);
    }

    // The market a faction's standing rests on, holding the stated number of others in the system and
    // no garrison - the baseline the cases above add one term at a time to.
    private static MarketClaimBreakdown buildStrongestMarket(int siblingMarketCount) {
        return buildStrongestMarket(siblingMarketCount, IS_KNOWN_TO_PLAYER);
    }

    // The same market, stated as one the player knows of or does not - the two cases the withholding
    // turns on. Held in the open either way, a market carrying a standing being one the mechanic
    // weighed, so the second reading is a faction standing on a colony nobody has discovered.
    private static MarketClaimBreakdown buildStrongestMarket(
            int siblingMarketCount,
            boolean isKnownToPlayer) {

        return ClaimMarketFixture
            .startMarket(STRONGEST_MARKET)
            .setNameplate(UNMARKED_MARKET)
            .setListingPosition(FIRST_LISTED)
            .setKnownToPlayer(isKnownToPlayer)
            .setMarketSize(STRONGEST_MARKET_SIZE)
            .setSiblingMarketCount(siblingMarketCount)
            .buildMarket();
    }

    // The market a standing rests on, stated as one the sector map marks with the given glyph or with
    // none - the two readings the line's opening run turns on. A garrison, so the cases about which
    // lines carry a mark have a term line beneath the market to read.
    private static MarketClaimBreakdown buildMarkedMarket(EntityNameplate market) {
        return ClaimMarketFixture
            .startMarket(market.displayName())
            .setNameplate(market)
            .setListingPosition(FIRST_LISTED)
            .setMarketSize(STRONGEST_MARKET_SIZE)
            .setMilitaryBonus(MILITARY_BONUS)
            .buildMarket();
    }

    // A market held out of the open that the player has nonetheless found - the one combination the
    // two flags part company on, and the concealed shape the economy still lists.
    private static MarketClaimBreakdown buildFoundHiddenMarket(
            String marketName,
            int marketSize,
            int siblingMarketCount,
            int listingPosition) {

        return buildFoundUnscoredMarket(
            marketName,
            marketSize,
            siblingMarketCount,
            listingPosition,
            ContestAdmission.HIDDEN);
    }

    // The Academy's own shape: concealed and unregistered at once, which is the one market whose two
    // exclusions both hold - and so the one matching none of the named admissions. Nothing on it
    // parts it from a concealed base the economy also drops, which is what the landmark reading
    // exists to tell apart.
    private static MarketClaimBreakdown buildFoundHiddenOffEconomyMarket(String marketName) {
        return buildFoundUnscoredMarket(
            marketName,
            STRONGEST_MARKET_SIZE,
            NO_SIBLING_MARKETS,
            FIRST_LISTED,
            new ContestAdmission(true, true));
    }

    // The shape every market the mechanic passed over shares: found by the player, named, and
    // carrying no military bonus, with only the admission saying why it was never scored. Written
    // once so a second excluded shape is one delegation rather than a second copy of the record's
    // eight arguments.
    private static MarketClaimBreakdown buildFoundUnscoredMarket(
            String marketName,
            int marketSize,
            int siblingMarketCount,
            int listingPosition,
            ContestAdmission admission) {

        return ClaimMarketFixture
            .startMarket(marketName)
            .setListingPosition(listingPosition)
            .setKnownToPlayer(IS_KNOWN_TO_PLAYER)
            .setAdmission(admission)
            .setMarketSize(marketSize)
            .setSiblingMarketCount(siblingMarketCount)
            .buildMarket();
    }

    // A colony the economy does not list - a real market on a real entity the mechanic never reached.
    // Held in the open and found by the player, so the only reason it took no part is the one the case
    // is about. What kind of place it is comes off the box's walk rather than off this, so one shape
    // serves the cases about a bare unregistered colony and the cases about a collapsed one alike.
    private static MarketClaimBreakdown buildOffEconomyMarket(
            String marketName,
            int marketSize,
            int listingPosition) {

        return buildFoundUnscoredMarket(
            marketName,
            marketSize,
            NO_SIBLING_MARKETS,
            listingPosition,
            ContestAdmission.OFF_ECONOMY);
    }

    // A market every term of the score arose on, for the cases about the whole sum rather than about
    // one term of it: its own size, two others beside it, and a garrison on it.
    private static MarketClaimBreakdown buildFullyScoredMarket() {
        return ClaimMarketFixture
            .startMarket(STRONGEST_MARKET)
            .setNameplate(UNMARKED_MARKET)
            .setListingPosition(FIRST_LISTED)
            .setMarketSize(STRONGEST_MARKET_SIZE)
            .setSiblingMarketCount(TWO_SIBLING_MARKETS)
            .setMilitaryBonus(MILITARY_BONUS)
            .buildMarket();
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

    // The same market, stated as one the player has or has not found. Held in the open either way,
    // the two being independent - a case wanting concealment as well poses it with the hidden
    // builders, so nothing here quietly answers a second question on the fog's behalf.
    private static MarketClaimBreakdown buildMarket(
            String marketName,
            int marketSize,
            int siblingMarketCount,
            int listingPosition,
            boolean isKnownToPlayer) {

        return ClaimMarketFixture
            .startMarket(marketName)
            .setListingPosition(listingPosition)
            .setKnownToPlayer(isKnownToPlayer)
            .setMarketSize(marketSize)
            .setSiblingMarketCount(siblingMarketCount)
            .buildMarket();
    }
}
