package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.systems.claims.ContestAdmission;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.PresenceOnlyClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.CellTooltipRowReads;
import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.base.visibility.ColonyKnowledge;
import kmu.maplayers.base.visibility.ColonyVisibility;
import kmu.maplayers.base.visibility.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture.buildStandingOnOneMarket;

import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.readLabelTexts;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARKED_LABEL_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARKED_QUALIFIER_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static kmu.maplayers.base.visibility.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.politicalmap.base.dominance.HolderGroupingFixture.buildAllianceOf;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the detail mode adds to the claims box: every faction it lists opened up into the colonies
 * that faction holds the system with, and each colony into the terms its claim score was summed from.
 *
 * <p>The one fact this box alone decides is that a faction is accounted for by the colonies its own
 * standing was read from - the thing the line above it cannot say, since a standing is one colony's
 * score and the faction may hold several. Where those colonies then hang, and which of them leads, is
 * the resolver's ({@link ClaimScoreRowResolverTest}); the claimant, the decree, the four headings and
 * the lines naming the factions belong to the shape both claim boxes share, and are pinned through the
 * ordinary one ({@link SystemClaimTooltipTest}).
 */
final class ExpandedSystemClaimTooltipTest {

    private static final String SYSTEM_ID = "askonia";

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    // Where the claim lands in a box over a populated system: under the heading the box opens with.
    private static final int CLAIM_ROW = 1;

    // Scores stand for market standings only, so any sizes serve.
    private static final int TOP_SCORE = 12;
    private static final int RIVAL_SCORE = 8;
    private static final int LESSER_SCORE = 3;

    // A colony holding the system for a faction that holds no other there, which is what every standing
    // posed here is built from - no case below is about the sibling term.
    private static final int NO_SIBLING_MARKETS = 0;

    // Where a market falls in the system's economy listing. No case here is about a tie, so every
    // market posed takes the head of the listing bar the second colony of the one faction holding two
    // the contest never weighed, whose account reads in listing order for want of any score to rank by.
    private static final int FIRST_LISTED = 1;
    private static final int SECOND_LISTED = 2;

    // Every market posed here is one the player has found and one the mechanic weighed. What the
    // box withholds of a market they have not found, and which listing ties it marks, are the
    // resolver's and pinned there; the cases below are about which faction gets an account at all.
    private static final boolean IS_KNOWN_TO_PLAYER = true;

    // What a colony's kind states on a line, and when it was last seen, are the resolver's and
    // pinned there - so every account here is resolved over a reading that says neither, which is
    // what an ordinary colony in plain sight reads as.

    private static final boolean IS_TERRITORIAL = true;

    // A system the contest itself settled - no decree over it - which is the state an account is
    // ordinarily resolved under and the one in which the strongest market is called out.
    private static final SystemClaimBreakdown CONTESTED_SYSTEM =
        new SystemClaimBreakdown(null, HEGEMONY, List.of());

    // That system as the box reads it: the scored contest paired with the colony rule its listing
    // was projected under. An account is handed the pair rather than the scored read alone, so a
    // case states the rule its account is resolved under here rather than through a settings seam -
    // which is the point of the pairing, an account reading the rule for itself being free to
    // withhold what the listing above it named.
    private static final SystemClaimContestTooltip.ListedClaimContest CONTESTED_CONTEST =
        SystemClaimContestTooltip.ListedClaimContest.selectFrom(
            CONTESTED_SYSTEM,
            ColonyVisibility.BASE_FOG,
            HolderGrouping.identity());

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();

    // The alliance set the box routes its blocks against, restated by the one case that is about an
    // ally and left ungrouped for every other - the state an install with nothing grouping factions
    // is permanently in, and the one the accounts below are all posed under.
    private HolderGrouping holderGrouping = HolderGrouping.identity();

    private final ExpandedSystemClaimTooltip tooltip =
        new ExpandedSystemClaimTooltip(claimBreakdownReaderFake, () -> holderGrouping);

    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private MockedStatic<SystemStatusRow> statusRowMock;
    private MockedStatic<MapVisibilityRules> visibilityRulesMock;

    @BeforeEach
    void installStringsColoursAndTheSystemStatusSeam() {

        StarsectorSettingsFake.installSettings();
        CellTooltipPaletteFake.installPalette();

        // The status line has its own suite and would otherwise demand a live economy here, so it is
        // stood in as absent - a populated system - for every case but the one about it.
        statusRowMock = Mockito.mockStatic(SystemStatusRow.class);
        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any()))
            .thenReturn(Optional.empty());

        // The reveal is a live LunaLib read, unreachable from the test JVM; stood in as off, the state
        // every case here is posed under.
        visibilityRulesMock = Mockito.mockStatic(MapVisibilityRules.class);
        visibilityRulesMock
            .when(MapVisibilityRules::readFromLunaSettings)
            .thenReturn(MapVisibilityRules.BASE);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);

        stubFaction(sectorMock, HEGEMONY, "The Hegemony", "graphics/hegemony_crest.png");
        stubFaction(sectorMock, TRITACHYON, "Tri-Tachyon", null);
    }

    @AfterEach
    void clearStringsColoursAndTheSystemStatusSeam() {

        visibilityRulesMock.close();
        statusRowMock.close();

        CellTooltipPaletteFake.clearPalette();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ResolveAccountEntries {

        @Test
        void resolveAccountEntriesAccountsForAFactionWithTheMarketsItHolds() {
            // The point of the mode, and the one thing the faction's line cannot state: its number is
            // one market's score, so the markets it was read from are what its account lists.
            var standing = new WeighedClaimStanding(
                HEGEMONY,
                IS_TERRITORIAL,
                buildMarket("Chicomoztoc", TOP_SCORE),
                List.of(buildMarket("Culann", LESSER_SCORE)));

            assertThat(readLabelTexts(
                    tooltip.resolveAccountEntries(
                        CONTESTED_CONTEST,
                        standing,
                        SystemColonyReading.NONE)))
                .containsExactly("Chicomoztoc", "Culann");
        }

        @Test
        void resolveAccountEntriesBreaksEachMarketDownIntoItsTerms() {
            // A market's own line is a sum too, so the account goes one level further: the terms that
            // built its score hang beneath it rather than the number being left to be taken on trust.
            var entries = tooltip.resolveAccountEntries(
                CONTESTED_CONTEST,
                buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                SystemColonyReading.NONE);

            assertThat(readLabelTexts(entries.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void resolveAccountEntriesCallsOutTheMarketTheClaimantTookTheSystemWith() {
            // The box's half of the rule: it reads who took the system off the very contest it is
            // drawing, so the call-out lands on the one market in the whole box that won anything.
            var entries = tooltip.resolveAccountEntries(
                CONTESTED_CONTEST,
                buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                SystemColonyReading.NONE);

            assertThat(entries.get(0).line().qualifierText())
                .isEqualTo("claim holder");
        }

        @Test
        void resolveAccountEntriesCallsOutNoMarketOfAFactionThatTookNothing() {
            // A rival is represented by its own strongest market too, but that market took nothing -
            // called out, it would read as a second holder of a system that can only have one.
            var entries = tooltip.resolveAccountEntries(
                CONTESTED_CONTEST,
                buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL),
                SystemColonyReading.NONE);

            assertThat(entries.get(0).line().qualifierText())
                .isNull();
        }

        @Test
        void resolveAccountEntriesAccountsForAFactionTheContestNeverWeighed() {
            // The case the widening exists for: such a faction's line is a nought and nothing else,
            // so its colonies are the whole of what the detail mode has to add about it - and they
            // are what the player is looking at on the map.
            var standing = new PresenceOnlyClaimStanding(
                TRITACHYON,
                IS_TERRITORIAL,
                List.of(
                    buildConcealedMarket("Kanta's Den", FIRST_LISTED),
                    buildConcealedMarket("Chalcedon", SECOND_LISTED)));

            assertThat(readLabelTexts(
                    tooltip.resolveAccountEntries(
                        CONTESTED_CONTEST,
                        standing,
                        SystemColonyReading.NONE)))
                .containsExactly("Kanta's Den", "Chalcedon");
        }

        @Test
        void resolveAccountEntriesWithholdsUnderTheRuleTheContestWasProjectedUnder() {
            // The account draws under the rule that selected the listing above it, not under one it
            // reads for itself. Posed as the two disagreeing: the contest carries the reveal, while
            // the live settings seam answers with it off. The unfound market has to be listed - read
            // afresh here, the account would withhold the very colony the listing named, and would
            // be free to answer two factions of one box differently besides.
            var contest = SystemClaimContestTooltip.ListedClaimContest.selectFrom(
                CONTESTED_SYSTEM,
                UNDER_THE_REVEAL,
                HolderGrouping.identity());

            var standing = new WeighedClaimStanding(
                HEGEMONY,
                IS_TERRITORIAL,
                buildMarket("Chicomoztoc", TOP_SCORE),
                List.of(buildUnfoundMarket("Culann", LESSER_SCORE)));

            assertThat(readLabelTexts(
                    tooltip.resolveAccountEntries(contest, standing, SystemColonyReading.NONE)))
                .containsExactly("Chicomoztoc", "Culann");
        }

        @Test
        void resolveAccountEntriesCallsOutNoMarketOfASystemHeldByDecree() {
            // A decree took the system before any market was weighed, so no market's score decided
            // anything and none is called out for it - the claimant's least of all.
            var entries = tooltip.resolveAccountEntries(
                SystemClaimContestTooltip.ListedClaimContest.selectFrom(
                    new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of()),
                    ColonyVisibility.BASE_FOG,
                    HolderGrouping.identity()),
                buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                SystemColonyReading.NONE);

            assertThat(entries.get(0).line().qualifierText())
                .isNull();
        }
    }

    @Nested
    class BuildBodySections {

        @Test
        void buildBodySectionsHangsEachFactionsColoniesBeneathItsOwnLine() {
            // Every block the box has takes the account, claimant and rival alike - and a colony reads
            // under the faction that holds it rather than under whichever line came before it.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            assertThat(readOpeningWordsInOrder(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Standing Colony",
                    "Size",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Standing Colony",
                    "Size");
        }

        @Test
        void buildBodySectionsHangsAnAlliedFactionsColoniesBeneathItsLineInTheAlliedBlock() {
            // The routing is the shared shape's and pinned there; what this case is about is that the
            // detail follows a faction into the block the relation put it in. An ally accounted for
            // only under `Contested by:` would be an account of a line the box no longer draws.
            holderGrouping = buildAllianceOf(HEGEMONY, TRITACHYON);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            assertThat(readOpeningWordsInOrder(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Standing Colony",
                    "Size",
                    "Allied with the claim holder:",
                    "Tri-Tachyon",
                    "Standing Colony",
                    "Size");
        }

        @Test
        void buildBodySectionsHangsTheColoniesOfAFactionTheContestNeverWeighedBeneathItsOwnLine() {
            // The whole point of the widening, read as the box draws it: the faction's line states a
            // nought and its colonies hang under that line rather than under the claimant's above.
            // They break down no further, nothing having been computed for them - which is what
            // parts such an account from the weighed one two lines above it.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    new PresenceOnlyClaimStanding(
                        TRITACHYON,
                        IS_TERRITORIAL,
                        List.of(buildConcealedMarket("Kanta's Den", FIRST_LISTED))))));

            assertThat(readOpeningWordsInOrder(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Standing Colony",
                    "Size",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Kanta's Den");
        }

        @Test
        void buildBodySectionsKeepsTheDecreeMarkerOnTheClaimLine() {
            // The decree is what took the system, and it is no less true of the box the player asked
            // for detail from - a marker present in one mode and gone in the other would read as the
            // detail having disproved it.
            stubBreakdown(new SystemClaimBreakdown(
                HEGEMONY,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL))));

            var claimRow = readTableRow(tooltip.buildBodySections(sectorMock, systemMock), CLAIM_ROW);

            assertThat(readLabelTextRun(claimRow, MARKED_LABEL_RUN).text())
                .isEqualTo("The Hegemony");
            assertThat(readLabelRun(claimRow, MARKED_QUALIFIER_RUN))
                .isEqualTo(new TextSpan("(core)", HIGHLIGHT));
        }

        @Test
        void buildBodySectionsAccountsForNothingWhereADecreedClaimantHoldsNoColonyHere() {
            // A decree needs no colony behind it, so the claimant is named with nothing hung beneath
            // it rather than with a heading over an account it never earned.
            stubBreakdown(new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of()));

            assertThat(readOpeningWordsInOrder(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly("Claim:", "The Hegemony");
        }

        @Test
        void buildBodySectionsReadsTheSystemOnceHoweverManyFactionsTheContestHolds() {
            // The kinds and the last-seen remarks a listing carries are read from one walk of the
            // system, made for the box rather than for a line. Resolved where an account is built,
            // they would walk the system once for every faction listed - and the walk is the most
            // expensive thing a hover does.
            // The walk reaches the system's own entities only once it has an economy to tell a
            // listed market from an unlisted one, so the case stands one up holding nothing: what
            // it is about is how many times the system is read, not what the read finds.
            var economyMock = mock(EconomyAPI.class);

            when(economyMock.getMarkets(systemMock))
                .thenReturn(List.of());
            when(sectorMock.getEconomy())
                .thenReturn(economyMock);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            tooltip.buildBodySections(sectorMock, systemMock);

            verify(systemMock, times(1)).getAllEntities();
        }

        @Test
        void buildBodySectionsReadsTheColoniesOffTheWalkTheStatusRowWasJudgedFrom() {
            // The one walk the box makes has to answer everything below it. Opened again for the
            // account, the kinds and the dates would come off a second reading of the system - so
            // the banner could call a system empty while the lines beneath it named a colony that
            // arrived between the two.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL))));

            var statusRowColonies = ArgumentCaptor.forClass(Colonies.class);
            var statusRowKnowledge = ArgumentCaptor.forClass(ColonyKnowledge.class);
            var readingColonies = ArgumentCaptor.forClass(Colonies.class);
            var readingKnowledge = ArgumentCaptor.forClass(ColonyKnowledge.class);

            try (var readingMock = Mockito.mockStatic(
                    SystemColonyReading.class,
                    Mockito.CALLS_REAL_METHODS)) {

                tooltip.buildBodySections(sectorMock, systemMock);

                statusRowMock.verify(() -> SystemStatusRow.resolveStatusRow(
                    statusRowColonies.capture(),
                    statusRowKnowledge.capture()));

                readingMock.verify(() -> SystemColonyReading.readColoniesIn(
                    any(),
                    any(),
                    readingColonies.capture(),
                    readingKnowledge.capture()));
            }
            assertThat(readingColonies.getValue())
                .isSameAs(statusRowColonies.getValue());
            assertThat(readingKnowledge.getValue())
                .isSameAs(statusRowKnowledge.getValue());
        }

        @Test
        void buildBodySectionsFallsBackToTheSystemStatusWhenNobodyIsPresent() {
            // An empty system says the same thing in either mode: there is no more detail to be had
            // about a system nobody has scored in.
            var statusRow = stubSystemHoldingNobody();

            stubBreakdown(SystemClaimBreakdown.NONE);

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(0).readRowsInOrder())
                .containsExactly(statusRow);
        }
    }

    // Hands the box the contest it is about, standing in for the market walk that would otherwise have
    // to be driven through a live economy to produce it.
    private void stubBreakdown(SystemClaimBreakdown breakdown) {
        claimBreakdownReaderFake.setBreakdown(SYSTEM_ID, breakdown);
    }

    // Puts the hovered system among the ones holding nobody, the state the status seam answers with a
    // banner. Returned so the case about it can assert on the very row it stubbed.
    private TooltipRow.CentredRow stubSystemHoldingNobody() {

        var statusRow = CellTooltipRows.buildBannerRow(null, "Unpopulated");

        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any()))
            .thenReturn(Optional.of(statusRow));

        return statusRow;
    }

    // The box read top to bottom as the words a player sees, headings, factions, colonies and terms
    // alike - the shape every case about the nesting is asserted on, since which line hangs under which
    // is exactly what the reading order states.
    private static List<String> readOpeningWordsInOrder(List<TooltipSection> sections) {
        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .map(CellTooltipRowReads::readOpeningWords)
            .toList();
    }

    // Reads one body line as the table row it is, which is where the value and the marker runs live.
    private static TooltipRow.TableRow readTableRow(List<TooltipSection> sections, int rowIndex) {
        return (TooltipRow.TableRow) TooltipSection
            .readRowsInOrder(sections)
            .get(rowIndex);
    }

    // A colony held in concealment and found all the same - the shape a faction the contest never
    // weighed is present through, and the one the map draws in that faction's colours. Sized like
    // any other, the size going nowhere: nothing was computed for it.
    //
    // Concealment rather than an absence from the economy's listing, arbitrarily: the two suppress
    // scoring identically and no case here is about which of them did it.
    private static MarketClaimBreakdown buildConcealedMarket(String marketName, int listingPosition) {
        return new MarketClaimBreakdown(
            EntityNameplate.createUnmarkedNameplate(marketName),
            nameMarketId(marketName),
            listingPosition,
            IS_KNOWN_TO_PLAYER,
            ContestAdmission.HIDDEN,
            LESSER_SCORE,
            NO_SIBLING_MARKETS,
            OptionalInt.empty());
    }

    // The id the walk that met a colony recorded for it, derived from its name so a case naming a
    // market on the list has one identity for it throughout.
    private static String nameMarketId(String marketName) {
        return marketName.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
    }

    // A market scoring its size alone, for a standing a case states by the markets under it rather
    // than by the arithmetic inside one. Every one of them heads the listing, since no case here is
    // about a tie or where the economy put anything.
    // A market of the same shape the player has yet to find, for a case about which rule decides
    // whether it is listed at all.
    private static MarketClaimBreakdown buildUnfoundMarket(String marketName, int marketSize) {

        var isKnownToPlayer = false;

        return new MarketClaimBreakdown(
            EntityNameplate.createUnmarkedNameplate(marketName),
            nameMarketId(marketName),
            SECOND_LISTED,
            isKnownToPlayer,
            ContestAdmission.WEIGHED,
            marketSize,
            NO_SIBLING_MARKETS,
            OptionalInt.empty());
    }

    private static MarketClaimBreakdown buildMarket(String marketName, int marketSize) {
        // Marked with no glyph: whether a market line leads with one is the resolver's and pinned
        // there, and a mark on every line would run through the reading-order assertions these cases
        // are actually about.
        return new MarketClaimBreakdown(
            EntityNameplate.createUnmarkedNameplate(marketName),
            nameMarketId(marketName),
            FIRST_LISTED,
            IS_KNOWN_TO_PLAYER,
            ContestAdmission.WEIGHED,
            marketSize,
            NO_SIBLING_MARKETS,
            OptionalInt.empty());
    }
}
