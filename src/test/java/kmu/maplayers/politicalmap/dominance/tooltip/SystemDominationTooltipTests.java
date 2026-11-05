package kmu.maplayers.politicalmap.dominance.tooltip;

import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.factions.alliances.FactionAlliances;
import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.logging.LogAppenderFake;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.content.CellTooltipMark;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.standings.GroupStanding;
import kmu.maplayers.politicalmap.dominance.standings.WeighedFactionStanding;
import kmu.maplayers.politicalmap.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.KnownMarketFootprints;
import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;
import kmu.maplayers.politicalmap.dominance.weighting.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.tooltip.ContestWording;
import kmu.maplayers.politicalmap.tooltip.PoliticalBoxSeams;
import kmu.maplayers.politicalmap.tooltip.PoliticalMapCellTooltipTests;
import kmu.settings.HiddenMarketScalingChoice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.MARKED_LABEL_RUN;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.MARK_RUN;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readOpeningWords;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readSectionOpeningWords;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readTableRow;
import static kmu.maplayers.base.visibility.colonies.FactionAllianceFixture.buildAllianceOf;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.politicalmap.dominance.tooltip.MarketWeightBreakdownFixtures.buildBaseSizeBreakdown;
import static kmu.maplayers.politicalmap.dominance.tooltip.StandingsTooltipSeamsFake.VIEW_GROUPING;
import static kmu.starsector.SectorFactionFixtures.stubDispositionToward;
import static kmu.starsector.SectorFactionFixtures.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the faction and alliance views' hover box says about a system: the groups holding it, laid
 * out flat or over their member factions as the resolver handed them over, the block each of them is
 * listed under, and the one weighing every part of the box reads.
 *
 * <p>A group is listed as the resolver shaped it, which is the shared default rather than an answer of
 * this box's own - a group made up of nothing reads as one flat line, one carrying members reads over
 * them indented. What hangs beneath each faction is {@link PassFactionAccountSourceTests}'s.
 *
 * <p>How much of that account a given detail level admits is not this box's decision at all - the
 * account is composed whole and cut where the listing is laid out, which is pinned once over the
 * shape ({@link SystemDominationTooltipShapeTests}). So is the ranking, the status line, the headings
 * and which of them a group falls under; the decree heading the box belongs to every one of the
 * layer's boxes and is pinned with the heading itself ({@link PoliticalMapCellTooltipTests}). Where a
 * colony then hangs is the shared resolution's ({@link StandingRowResolverTests}).
 *
 * <p>The non-political block is the exception among the blocks and is pinned here, because it is the
 * one whose routing turns on which faction a bloc actually is: the shape suite poses its groups as
 * bloc IDs that stand for nobody in particular, which is right for every block placed by rank or by
 * alliance and cannot state this one at all. So is the holder, which is the bloc the fill was painted
 * for rather than the one ranked first, and the present block a system with no holder lists.
 */
final class SystemDominationTooltipTests {

    private static final String SYSTEM_ID = "askonia";
    private static final String CORE_FACTION = "hegemony";
    private static final String ALLY_FACTION = "tritachyon";

    // The two the chain's remaining relation blocks are posed with: one the sector puts on good terms
    // with the holder, and one it says nothing about, which is indifference and so a rival.
    private static final String FRIENDLY_FACTION = "luddic_church";
    private static final String RIVAL_FACTION = "persean_league";

    private static final String BLOC_CREST = "graphics/rebel_pact_crest.png";
    private static final CellTooltipMark BLOC_MARK =
        CellTooltipMark.resolveMarkAsAuthored(BLOC_CREST);

    private static final String MEMBER_CREST = "graphics/hegemony_crest.png";
    private static final CellTooltipMark MEMBER_MARK =
        CellTooltipMark.resolveMarkAsAuthored(MEMBER_CREST);

    // The lines a box with no status and no decree lays out, in draw order: the heading naming who
    // holds the system, that group's own header, then its members beneath.
    private static final int GROUP_HEADER_ROW = 1;
    private static final int FIRST_MEMBER_ROW = 2;
    private static final int SECOND_MEMBER_ROW = 3;

    // The scores reach this box already worded by the resolver, so they stand in as the text they draw
    // as - what the box does with them is carry them into the value column.
    private static final String BLOC_SCORE = "1,200";
    private static final String ALLY_SCORE = "800";
    private static final String PLACEHOLDER_SCORE = "50";

    // What a stood-up group is weighed at, forwarded to the (stood-in) naming: the case using it is
    // about which block a group falls in, which is read off its bloc alone.
    private static final int ANY_SCORE = 0;
    private static final String MEMBER_SCORE = "900";
    private static final String OTHER_MEMBER_SCORE = "300";

    // The weighting the pass is opened under, which no case here reads past handing it on.
    private static final DominanceRules ANY_RULES = new DominanceRules(
        false,
        new BaseSizeWeighting(1.0, HiddenMarketScalingChoice.NORMAL, 2.5, 1.0),
        new StationWeighting(false, 3.0, 0.5, 0.5),
        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    // Opened over no sector, so every pass here answers the empty colony set: what these cases are
    // about is which set the box hands on and under which knobs, not what a walk would have found.
    private static final DominancePass ANY_PASS =
        DominancePass.createOver(null, ANY_RULES, UNDER_THE_FOG, VIEW_GROUPING);

    // A bloc the ranking never lists, for the case about a fill painted for one.
    private static final String UNRANKED_BLOC = "unranked_bloc";

    // What the fill was painted for in a system nobody was weighed in.
    private static final String NO_PAINTED_BLOC = null;

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();

    // The alliance set the box routes its blocks against, restated by the case about an ally and left
    // ungrouped for the rest - the state an install with nothing grouping factions is in.
    private FactionAlliances allianceSet = FactionAlliances.NONE;

    // How the rival block is headed, restated by the case about an install where no system ever
    // changes hands and left as the contest for the rest - which is what every case predating the
    // wording was written under.
    private ContestWording contestWording = ContestWording.CONTESTED;

    private final SystemDominationTooltip tooltip = new SystemDominationTooltip(
        StandingsTooltipSeamsFake.VIEW_GROUPING_SOURCE,
        new PoliticalBoxSeams(claimBreakdownReaderFake, sector -> allianceSet, () -> contestWording));

    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    // The economy read behind the account, which no unit test can drive live. CALLS_REAL_METHODS keeps
    // the weight arithmetic beside it live, since the numbers on a factor line are read through the
    // very same class.
    private MockedStatic<KnownMarketFootprints> footprintsMock;

    @BeforeEach
    void installColoursAndTheRankingSeams() {

        CellTooltipPaletteFake.installPalette();
        StandingsTooltipSeamsFake.installSeams(ANY_PASS);

        footprintsMock = Mockito.mockStatic(KnownMarketFootprints.class, Mockito.CALLS_REAL_METHODS);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);

        stubFaction(sectorMock, CORE_FACTION, "The Hegemony", MEMBER_CREST);
    }

    @AfterEach
    void clearColoursAndTheRankingSeams() {

        footprintsMock.close();

        StandingsTooltipSeamsFake.clearSeams();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class ComposeBody {

        // The faction view's shape: a lone faction resolves to a group made up of nothing, so the box
        // lists it and nothing beneath it - and its number reads called-out like every value.
        @Test
        void drawsAGroupMadeUpOfNothingAsOneFlatLine() {

            // ARRANGE
            StandingsTooltipSeamsFake.stubGroupEntries(createLoneGroupEntry());

            var expectedMark = new ImageSpan(BLOC_CREST);
            var expectedLabel = new TextSpan("Rebel Pact", PLAYER_BRIGHT);
            var expectedValueSlot = new RowSlot.Text(new TextSpan(BLOC_SCORE, HIGHLIGHT));

            // ACT
            var actualRows = readBodyRows();
            var actualHeader = readTableRow(actualRows, GROUP_HEADER_ROW);

            // ASSERT
            assertThat(actualRows)
                .hasSize(2);
            assertThat(readLabelRun(actualHeader, MARK_RUN))
                .isEqualTo(expectedMark);
            assertThat(readLabelRun(actualHeader, MARKED_LABEL_RUN))
                .isEqualTo(expectedLabel);
            assertThat(actualHeader.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
            assertThat(actualHeader.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(actualHeader.labelledRow().trailingRowSlot())
                .isEqualTo(expectedValueSlot);
        }

        // The alliances view's shape: the bloc is listed and its members read as belonging to it, by
        // the indent and the plainer colour rather than by any label saying so. The members read
        // strongest first, as the standing ranked them and like the fills.
        @Test
        void drawsAnAllianceAboveItsIndentedMembers() {

            // ARRANGE
            StandingsTooltipSeamsFake.stubGroupEntries(createGroupEntry(
                createMemberLine(MEMBER_SCORE),
                createMemberLine(OTHER_MEMBER_SCORE)));

            var expectedGroupLabel = new TextSpan("Rebel Pact", PLAYER_BRIGHT);
            var expectedMemberLabel = new TextSpan("The Hegemony", TEXT);
            var expectedFirstValueSlot = new RowSlot.Text(new TextSpan(MEMBER_SCORE, TEXT));
            var expectedSecondValueSlot = new RowSlot.Text(new TextSpan(OTHER_MEMBER_SCORE, TEXT));

            // ACT
            var actualRows = readBodyRows();
            var actualHeader = readTableRow(actualRows, GROUP_HEADER_ROW);
            var actualFirstMember = readTableRow(actualRows, FIRST_MEMBER_ROW);
            var actualSecondMember = readTableRow(actualRows, SECOND_MEMBER_ROW);

            // ASSERT
            assertThat(actualRows)
                .hasSize(4);
            assertThat(readLabelRun(actualHeader, MARKED_LABEL_RUN))
                .isEqualTo(expectedGroupLabel);
            assertThat(readLabelRun(actualFirstMember, MARKED_LABEL_RUN))
                .isEqualTo(expectedMemberLabel);
            assertThat(actualFirstMember.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
            assertThat(actualFirstMember.labelledRow().trailingRowSlot())
                .isEqualTo(expectedFirstValueSlot);
            assertThat(actualSecondMember.labelledRow().trailingRowSlot())
                .isEqualTo(expectedSecondValueSlot);
        }

        // Vanilla hands every abandoned station and collapsed colony to the neutral placeholder, which
        // takes a footprint like anybody else. Left among the contenders it would head the box over a
        // system a real faction runs, so it is set aside before a holder is named - listed with
        // whatever it scored, and never named as holding the place.
        @Test
        void listsThePlaceholderOwnerApartFromWhoHoldsTheSystem() {

            // ARRANGE
            StandingsTooltipSeamsFake.stubRankedGroups(
                List.of(
                    createLoneGroup(CORE_FACTION),
                    createLoneGroup(Factions.NEUTRAL)),
                List.of(createLoneGroupEntry(), createPlaceholderGroupEntry()));

            var expectedOpeningWords = List.of(
                "Dominated by:",
                "Rebel Pact",
                "Non-political:",
                "Neutral");

            // ACT
            var actualOpeningWords = readBodyOpeningWords();

            // ASSERT
            assertThat(actualOpeningWords)
                .isEqualTo(expectedOpeningWords);
        }

        // The whole chain in one system, in the order a reader meets it: who holds the place, who
        // stands with it by alliance, who stands with it in disposition, who stands against it, and
        // who was never in the running. Posed here rather than with the shared shape because two of
        // the five turn on which faction a bloc actually is - the placeholder owner, and a bloc the
        // sector's own relations put on good terms with the holder.
        @Test
        void laysTheFiveBlocksDownFromTheHolderOutward() {

            // ARRANGE
            allianceSet = buildAllianceOf(CORE_FACTION, ALLY_FACTION);

            stubFaction(sectorMock, FRIENDLY_FACTION, "The Luddic Church", MEMBER_CREST);
            stubDispositionToward(sectorMock, FRIENDLY_FACTION, CORE_FACTION, RepLevel.FAVORABLE);
            stubTheWholeChain();

            var expectedOpeningWords = List.of(
                "Dominated by:",
                "Rebel Pact",
                "Allied with the system holder:",
                "Tri-Tachyon",
                "Friendly with the system holder:",
                "The Luddic Church",
                "Contested by:",
                "Persean League",
                "Non-political:",
                "Neutral");

            // ACT
            var actualOpeningWords = readBodyOpeningWords();

            // ASSERT
            assertThat(actualOpeningWords)
                .isEqualTo(expectedOpeningWords);
        }

        // A tie on combined weight: the ranking puts the core faction first by ID, while the pass broke
        // the tie on a lower level and painted the fill for the rival. The box names the painted bloc
        // as holder and measures everyone else against it.
        @Test
        void namesTheBlocTheFillWasPaintedForAsHolderWhereItRanksSecond() {

            // ARRANGE
            StandingsTooltipSeamsFake.stubRankedGroups(
                List.of(
                    createLoneGroup(CORE_FACTION),
                    createLoneGroup(RIVAL_FACTION)),
                List.of(
                    createLoneGroupEntry(),
                    createNamedGroupEntry("Persean League")));
            StandingsTooltipSeamsFake.stubPaintedBloc(RIVAL_FACTION);

            var expectedOpeningWords = List.of(
                "Dominated by:",
                "Persean League",
                "Contested by:",
                "Rebel Pact");

            // ACT
            var actualOpeningWords = readBodyOpeningWords();

            // ASSERT
            assertThat(actualOpeningWords)
                .isEqualTo(expectedOpeningWords);
        }

        // A system the fill was painted for nobody in still lists whoever is there - blocs present
        // only through colonies the economy does not list - and names none of them as holding it.
        @Test
        void listsEveryoneAsPresentWhereTheFillWasPaintedForNobody() {

            // ARRANGE
            StandingsTooltipSeamsFake.stubRankedGroups(
                List.of(
                    createLoneGroup(CORE_FACTION),
                    createLoneGroup(RIVAL_FACTION)),
                List.of(
                    createLoneGroupEntry(),
                    createNamedGroupEntry("Persean League")));
            StandingsTooltipSeamsFake.stubPaintedBloc(NO_PAINTED_BLOC);

            var expectedOpeningWords = List.of(
                "Present:",
                "Rebel Pact",
                "Persean League");

            // ACT
            var actualOpeningWords = readBodyOpeningWords();

            // ASSERT
            assertThat(actualOpeningWords)
                .isEqualTo(expectedOpeningWords);
        }

        // Under a grouping that makes every bloc a singleton there is nothing for a bloc's members to
        // disagree about, so none folds into two headings and none is stood up anew: the box names
        // exactly the groups the ranking handed it, once each. What a fold looks like when one does
        // happen is the routing's to pin.
        @Test
        void listsEachGroupOnceWhereNoBlocCanBeOfTwoMinds() {

            // ARRANGE
            allianceSet = FactionAlliances.NONE;

            stubFaction(sectorMock, FRIENDLY_FACTION, "The Luddic Church", MEMBER_CREST);
            stubDispositionToward(sectorMock, FRIENDLY_FACTION, CORE_FACTION, RepLevel.FAVORABLE);

            StandingsTooltipSeamsFake.stubRankedGroups(
                List.of(
                    createLoneGroup(CORE_FACTION),
                    createLoneGroup(FRIENDLY_FACTION),
                    createLoneGroup(RIVAL_FACTION)),
                List.of(
                    createLoneGroupEntry(),
                    createNamedGroupEntry("The Luddic Church"),
                    createNamedGroupEntry("Persean League")));

            var expectedOpeningWords = List.of(
                "Dominated by:",
                "Rebel Pact",
                "Friendly with the system holder:",
                "The Luddic Church",
                "Contested by:",
                "Persean League");

            // ACT
            var actualOpeningWords = readBodyOpeningWords();

            // ASSERT
            assertThat(actualOpeningWords)
                .isEqualTo(expectedOpeningWords);
        }

        // The five blocks of the chain, in the same system, on an install that never transfers one:
        // the bloc beside the holder is a neighbour indefinitely rather than a challenger, so that
        // heading states its presence and leaves the struggle unsaid.
        //
        // Posed over the whole chain rather than over the one block, because what the wording is not
        // allowed to reach is the point: the four headings around it name relations and a candidacy
        // that hold whether or not anybody can act on them, and a wording read by the loop that draws
        // all five could move any of them.
        //
        // Read as the same box drawn twice, once under each wording, so every line it holds is
        // compared - an entry's crest, value and indent as well as the headings.
        @Test
        void headsOnlyTheRivalsDifferentlyWhereNoSystemEverChangesHands() {

            // ARRANGE
            var rivalHeadingRow = 6;

            allianceSet = buildAllianceOf(CORE_FACTION, ALLY_FACTION);

            stubFaction(sectorMock, FRIENDLY_FACTION, "The Luddic Church", MEMBER_CREST);
            stubDispositionToward(sectorMock, FRIENDLY_FACTION, CORE_FACTION, RepLevel.FAVORABLE);
            stubTheWholeChain();

            var expectedContestedHeading = "Contested by:";
            var expectedPresentHeading = "Present:";

            // ACT
            var actualContestedRows = readBodyRows();

            contestWording = ContestWording.PRESENT;

            var actualPresentRows = readBodyRows();

            // ASSERT
            assertThat(actualPresentRows)
                .hasSameSizeAs(actualContestedRows);
            assertThat(actualPresentRows.subList(0, rivalHeadingRow))
                .isEqualTo(actualContestedRows.subList(0, rivalHeadingRow));
            assertThat(actualPresentRows.subList(rivalHeadingRow + 1, actualPresentRows.size()))
                .isEqualTo(actualContestedRows.subList(rivalHeadingRow + 1, actualContestedRows.size()));

            // The heading line itself keeps its kind and changes its words alone.
            assertThat(actualPresentRows.get(rivalHeadingRow).lineStyle())
                .isEqualTo(actualContestedRows.get(rivalHeadingRow).lineStyle());
            assertThat(readOpeningWords(actualContestedRows.get(rivalHeadingRow)))
                .isEqualTo(expectedContestedHeading);
            assertThat(readOpeningWords(actualPresentRows.get(rivalHeadingRow)))
                .isEqualTo(expectedPresentHeading);
        }

        // An empty system says the same thing at every depth: there is no more detail to be had
        // about a system nobody holds.
        @Test
        void fallsBackToTheSystemStatusWhenNothingRanks() {

            // ARRANGE
            var expectedStatusRow = StandingsTooltipSeamsFake.stubStatusRow("Unpopulated");

            StandingsTooltipSeamsFake.stubGroupEntries();
            stubBreakdowns(Map.of());

            // ACT
            var actualSections = tooltip
                .composeBody(sectorMock, systemMock, PATROL_DETAILS)
                .blocks()
                .readSections();

            // ASSERT
            assertThat(actualSections)
                .hasSize(1);
            assertThat(actualSections.get(0).readRowsInOrder())
                .containsExactly(expectedStatusRow);
        }

        // The whole point of gating the account rather than cutting it afterwards. Every read behind
        // a colony line beyond the weights the ranking needs anyway is the account's, and it is the
        // most expensive thing a hover does - so the level that draws none of those lines has to pay
        // for none of it. Read and then cut, a player leaving the box shut would be charged what
        // opening it costs.
        @Test
        void readsNoAccountOfTheSystemWhereTheLevelNamesItsHoldersAlone() {

            // ARRANGE
            StandingsTooltipSeamsFake.stubGroupEntries(createLoneGroupEntry());

            // ACT
            tooltip.composeBody(sectorMock, systemMock, FACTIONS);

            // ASSERT
            footprintsMock.verify(
                () -> KnownMarketFootprints.readUnweighedColoniesByFaction(any(), any()),
                Mockito.never());
        }

        // The ranking, the holder and the account beneath each faction explain one set of weights,
        // so they are read once for the box: the totals are folded out of the very parts the account
        // lists. Weighed for each, a detailed hover would work out every market more than once.
        @Test
        void weighsEveryMarketOnceAtTheDeepestLevel() {

            // ARRANGE
            StandingsTooltipSeamsFake.stubGroupEntries(createLoneGroupEntry());

            // ACT
            tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS);

            // ASSERT
            // Counted on the real arguments rather than on matchers, since registering a stub is
            // itself an invocation and an any()-matched count would take it for a second read.
            footprintsMock.verify(
                () -> KnownMarketFootprints.readBreakdownByFaction(
                    Colonies.NONE,
                    ANY_RULES,
                    ANY_PASS.colonyKnowledge()),
                Mockito.times(1));
            footprintsMock.verify(
                () -> KnownMarketFootprints.readByFaction(any(), any(), any()),
                Mockito.never());
        }

        // The ranking is handed footprints folded from the weights the box read rather than reading
        // its own, which is what lets the account below explain exactly the scores it ranked. One
        // planet colony of six size points, on the grid of a thousand to the point.
        @Test
        void ranksTheSystemOffTheWeightsItRead() {

            // ARRANGE
            stubBreakdowns(Map.of("hegemony", List.of(buildBaseSizeBreakdown("Jangala", 6.0))));
            StandingsTooltipSeamsFake.stubGroupEntries(createLoneGroupEntry());

            var expectedFootprints = Map.of("hegemony", new MarketFootprint(1, 6000, 6000, 6000));

            // ACT
            tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS);

            // ASSERT
            StandingsTooltipSeamsFake.verifyRankedOver(expectedFootprints);
        }

        // A fill painted for a bloc the ranking never listed means the two read the system apart,
        // which the box's own fallback - naming nobody - would hide. Said once, however many hovers
        // reach it, since the box is composed on every cell the cursor reaches.
        @Test
        void warnsOnceWhereTheFillWasPaintedForABlocTheRankingDoesNotList() {

            // ARRANGE
            StandingsTooltipSeamsFake.stubRankedGroups(
                List.of(createLoneGroup(CORE_FACTION)),
                List.of(createLoneGroupEntry()));
            StandingsTooltipSeamsFake.stubPaintedBloc(UNRANKED_BLOC);

            var expectedMessages = List.of("Domination box: the fill of system " + SYSTEM_ID
                + " was painted for bloc " + UNRANKED_BLOC
                + ", which its ranking does not list; the box names nobody as holding it");

            // ACT
            var actualLog = LogAppenderFake.captureLogOf(SystemDominationTooltip.class, () -> {

                tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS);
                tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS);
            });

            // ASSERT
            assertThat(actualLog.getMessages())
                .isEqualTo(expectedMessages);
        }

        // The ordinary shape: the bloc the fill was painted for is among the ranked groups, so there
        // is nothing to say.
        @Test
        void saysNothingWhereThePaintedBlocIsRanked() {

            // ARRANGE
            StandingsTooltipSeamsFake.stubGroupEntries(createLoneGroupEntry());

            // ACT
            var actualLog = LogAppenderFake.captureLogOf(
                SystemDominationTooltip.class,
                () -> tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS));

            // ASSERT
            assertThat(actualLog.getMessages())
                .isEmpty();
        }

        // A fill painted for nobody is the ordinary shape of a system nobody weighed, not a fault.
        @Test
        void saysNothingWhereTheFillWasPaintedForNobody() {

            // ARRANGE
            StandingsTooltipSeamsFake.stubRankedGroups(
                List.of(createLoneGroup(CORE_FACTION)),
                List.of(createLoneGroupEntry()));
            StandingsTooltipSeamsFake.stubPaintedBloc(NO_PAINTED_BLOC);

            // ACT
            var actualLog = LogAppenderFake.captureLogOf(
                SystemDominationTooltip.class,
                () -> tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS));

            // ASSERT
            assertThat(actualLog.getMessages())
                .isEmpty();
        }
    }

    @Nested
    class ResolveDeepestAccountLevel {

        // This box is the one the levels were named after: colonies under a faction, factors under a
        // colony, and the patrol tiers under the patrol factor. It is what makes the deepest level
        // worth declaring at all - the claim box beside it stops a tier higher.
        @Test
        void fillsOutTheWholeCycle() {

            // ACT
            var actualDeepestLevel = tooltip.resolveDeepestAccountLevel();

            // ASSERT
            assertThat(actualDeepestLevel)
                .isEqualTo(PATROL_DETAILS);
        }
    }

    // The body read top to bottom as the lines a player sees, which is the shape these cases are about.
    private List<TooltipRow> readBodyRows() {

        return TooltipSection.readRowsInOrder(
            tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections());
    }

    // The words each block opens on, top to bottom: a heading, then the first line of every entry
    // beneath it. What the cases about which block a group lands in read.
    private List<String> readBodyOpeningWords() {

        return readSectionOpeningWords(
            tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections());
    }

    // The five blocks of the chain in one system, ranked core faction first, each group named apart
    // so a case reading the box top to bottom tells the blocks apart by the one line each holds.
    // Which block each lands in is the alliance set and the dispositions the case stands up.
    private static void stubTheWholeChain() {

        StandingsTooltipSeamsFake.stubRankedGroups(
            List.of(
                createLoneGroup(CORE_FACTION),
                createLoneGroup(ALLY_FACTION),
                createLoneGroup(FRIENDLY_FACTION),
                createLoneGroup(RIVAL_FACTION),
                createLoneGroup(Factions.NEUTRAL)),
            List.of(
                createLoneGroupEntry(),
                createAlliedGroupEntry(),
                createNamedGroupEntry("The Luddic Church"),
                createNamedGroupEntry("Persean League"),
                createPlaceholderGroupEntry()));
    }

    // Stands the economy read in as the colonies each faction holds in the system, so no case needs a
    // live economy to produce parts for the box to open up.
    private void stubBreakdowns(Map<String, List<MarketWeightBreakdown>> breakdownsByFactionId) {

        footprintsMock
            .when(() -> KnownMarketFootprints.readBreakdownByFaction(
                any(),
                any(),
                any()))
            .thenReturn(breakdownsByFactionId);
    }

    // One group as the resolver hands it over: a bloc carrying its crest and summed score, gathering
    // the factions in it as its peers, since a bloc and its members answer who holds the system at two
    // granularities rather than one accounting for the other. Whether a group gathers anything is the
    // resolver's decision, so a case here states it by handing over the members or none. A member
    // faction breaks down no further, so each is entered as an entry carrying nothing.
    private static CellTooltipEntry createGroupEntry(CellTooltipEntryLine... memberLines) {

        return CellTooltipEntry
            .createEntry(CellTooltipEntryLine.createLine(BLOC_MARK, "Rebel Pact", BLOC_SCORE))
            .grouping(Arrays
                .stream(memberLines)
                .map(CellTooltipEntry::createEntry)
                .toList());
    }

    // A group that breaks down no further - what a lone faction in the faction view resolves to.
    private static CellTooltipEntry createLoneGroupEntry() {

        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(BLOC_MARK, "Rebel Pact", BLOC_SCORE));
    }

    // A second group named apart from the leader, for the case about the block an alliance routes it
    // into: its line is never read, only the heading it ends up under.
    private static CellTooltipEntry createAlliedGroupEntry() {

        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(MEMBER_MARK, "Tri-Tachyon", ALLY_SCORE));
    }

    // Any further group, named apart from the rest so a case reading the box top to bottom tells the
    // blocks apart by the one line each of them holds.
    private static CellTooltipEntry createNamedGroupEntry(String name) {

        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(MEMBER_MARK, name, ALLY_SCORE));
    }

    // The placeholder owner as the resolver hands it over, named and scored apart from the leader so
    // the case about the block it lands in cannot pass by reading the leader's line twice.
    private static CellTooltipEntry createPlaceholderGroupEntry() {

        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(MEMBER_MARK, "Neutral", PLACEHOLDER_SCORE));
    }

    // One member faction beneath a bloc, told apart from its siblings by its score alone.
    private static CellTooltipEntryLine createMemberLine(String scoreText) {

        return CellTooltipEntryLine.createLine(MEMBER_MARK, "The Hegemony", scoreText);
    }

    // A group standing in the system through a lone member of its own ID, for a case about where the
    // group is listed rather than what it is made up of.
    private static GroupStanding createLoneGroup(String blocId) {
        return new GroupStanding(blocId, ANY_SCORE, List.of(new WeighedFactionStanding(blocId, ANY_SCORE)));
    }
}
