package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipLabelPlacement;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.SystemStandings;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how the standings the map ranks become the lines the hover box draws: the strongest group is
 * named as dominating the system and the rest as contesting it, a group made up of nothing reads as one
 * flat line while one carrying members reads over them indented, and what the system is beyond its
 * standings - dead, or held by decree - is said above the contest. Also that the ranking runs under the
 * active view's own grouping, which is what makes the numbers in the box the ones the fills were painted
 * by.
 *
 * <p>The ranking, the resolution into entries, the status line and the claim read behind it are all
 * stood in for, since each is pinned by its own suite - which group breaks down at all is the resolver's
 * decision and pinned there. What is left is the shape this class alone decides: which entries are
 * listed, in what order, and under which heading.
 */
final class SystemDominationTooltipTest {

    private static final String SYSTEM_ID = "askonia";

    private static final String CORE_FACTION = "hegemony";

    private static final String BLOC_CREST = "graphics/rebel_pact_crest.png";
    private static final String MEMBER_CREST = "graphics/hegemony_crest.png";
    private static final String RIVAL_CREST = "graphics/persean_league_crest.png";

    // The lines a box with no status and no decree lays out, in draw order: the heading naming who
    // holds the system, that group's own header, then its members beneath.
    private static final int DOMINATED_HEADING_ROW = 0;
    private static final int GROUP_HEADER_ROW = 1;
    private static final int FIRST_MEMBER_ROW = 2;
    private static final int SECOND_MEMBER_ROW = 3;

    // A line's label is one run here - no case below writes a qualifier onto one, bar the decree line,
    // which has its own suite.
    private static final int LABEL_RUN = 0;

    // The scores reach this box already worded by the resolver, so they stand in as the text they draw
    // as - what the box does with them is carry them into the value column.
    private static final String BLOC_SCORE = "1,200";
    private static final String MEMBER_SCORE = "900";
    private static final String OTHER_MEMBER_SCORE = "300";
    private static final String RIVAL_SCORE = "400";

    // The weights are forwarded to the (stood-in) ranking, so they never reach an assertion - any rules
    // stand in where the seam demands them.
    private static final DominanceRules ANY_RULES = new DominanceRules(false,
        new BaseSizeWeighting(1.0, null, 1.0, 1.0),
        new StationWeighting(false, 1.0, 0.5, 0.5),
        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    private static final DominancePass ANY_PASS =
        new DominancePass(ANY_RULES, false, HolderGrouping.identity());

    // The grouping the active view answers with, and deliberately not the identity one: a grouping the
    // ranking could have reached for on its own would let a tooltip that ignored the view still pass
    // the case below. Asserted by identity, since what matters is that this instance is the one used.
    private static final HolderGrouping VIEW_GROUPING = new HolderGrouping(
        Map.of("hegemony", "rebel_pact"),
        Map.of("rebel_pact", "hegemony"),
        Map.of("rebel_pact", "Rebel Pact"));

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();
    private final SystemDominationTooltip tooltip =
        new SystemDominationTooltip(claimBreakdownReaderFake);

    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    private MockedStatic<PoliticalMapViewRegistry> viewRegistryMock;
    private MockedStatic<DominancePass> dominancePassMock;
    private MockedStatic<SystemStandings> standingsMock;
    private MockedStatic<StandingRowResolver> rowResolverMock;
    private MockedStatic<SystemStatusRow> statusRowMock;

    @BeforeEach
    void installColoursAndTheRankingSeams() {
        CellTooltipPaletteFake.installPalette();

        // A view is active by default, since every case but one is about what its rows become. The
        // ranking and the settings read behind it are stood in so no case depends on a live economy or
        // a live LunaLib.
        var viewMock = mock(PoliticalMapView.class);

        when(viewMock.resolveGrouping())
            .thenReturn(VIEW_GROUPING);

        viewRegistryMock = Mockito.mockStatic(PoliticalMapViewRegistry.class);
        viewRegistryMock
            .when(PoliticalMapViewRegistry::getActiveView)
            .thenReturn(viewMock);

        dominancePassMock = Mockito.mockStatic(DominancePass.class);
        dominancePassMock
            .when(() -> DominancePass.readFromLunaSettings(any()))
            .thenReturn(ANY_PASS);

        standingsMock = Mockito.mockStatic(SystemStandings.class);
        rowResolverMock = Mockito.mockStatic(StandingRowResolver.class);
        statusRowMock = Mockito.mockStatic(SystemStatusRow.class);

        // The system is populated and under no decree unless a case says otherwise, so the two lines
        // above the standings stay out of the way of the cases about the standings themselves.
        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), any(Boolean.class)))
            .thenReturn(Optional.empty());

        var coreFactionMock = mock(FactionAPI.class);

        when(coreFactionMock.getDisplayNameLong())
            .thenReturn("The Hegemony");
        when(coreFactionMock.getCrest())
            .thenReturn(MEMBER_CREST);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);
        when(sectorMock.getFaction(CORE_FACTION))
            .thenReturn(coreFactionMock);
    }

    @AfterEach
    void clearColoursAndTheRankingSeams() {
        statusRowMock.close();
        rowResolverMock.close();
        standingsMock.close();
        dominancePassMock.close();
        viewRegistryMock.close();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class BuildTitleRows {

        @Test
        void buildTitleRowsHeadsTheBoxWithTheDecree() {
            // Why the decree is a title line at all: it settles the system outright, so it is read off
            // the system name rather than found among the findings - and the box's one parting falls
            // beneath it rather than above it, which is what the two blocks being separate buys.
            stubCoreFaction(CORE_FACTION);

            assertThat(readLabelTexts(tooltip.buildTitleRows(sectorMock, systemMock)))
                .containsExactly("The Hegemony");
        }

        @Test
        void buildTitleRowsHeadsALivingSystemWithADecreeToo() {
            // A decree holds whatever it is laid over, colony or not, so the line does not hang off a
            // status line: a populated system under one is headed exactly as an empty one is.
            stubCoreFaction(CORE_FACTION);
            stubResolvedRows(createLoneGroupEntry());

            assertThat(readLabelTexts(tooltip.buildTitleRows(sectorMock, systemMock)))
                .containsExactly("The Hegemony");
        }

        @Test
        void buildTitleRowsHeadsTheBoxWithNothingForASystemUnderNoDecree() {
            // The ordinary case: no decree holds the system, so nothing heads the box - a line naming a
            // core that does not exist would read as a claim the map never painted.
            assertThat(tooltip.buildTitleRows(sectorMock, systemMock))
                .isEmpty();
        }

        @Test
        void buildTitleRowsAsksOnlyForTheDecreeRatherThanScoringEveryMarket() {
            // Why the port carries two reads at all: this box never shows a claim score, so answering
            // "is there a decree" through the full breakdown would charge the scoring of every market
            // in the system to every faction and alliance hover. The fake derives one read from the
            // other, so only a case watching the calls can hold this.
            var claimBreakdownReaderMock = mock(ClaimBreakdownReader.class);

            new SystemDominationTooltip(claimBreakdownReaderMock)
                .buildTitleRows(sectorMock, systemMock);

            verify(claimBreakdownReaderMock)
                .readCoreFactionId(systemMock);
            verify(claimBreakdownReaderMock, never())
                .readBreakdown(any());
        }

        @Test
        void buildTitleRowsHeadsTheBoxWithoutAskingTheActiveView() {
            // A decree is a fact about the system rather than about how this layer is grouping it, so
            // the line stands whether or not a view has been registered - unlike the ranking, which has
            // nothing to rank under without one.
            viewRegistryMock
                .when(PoliticalMapViewRegistry::getActiveView)
                .thenReturn(null);

            stubCoreFaction(CORE_FACTION);

            assertThat(readLabelTexts(tooltip.buildTitleRows(sectorMock, systemMock)))
                .containsExactly("The Hegemony");
        }
    }

    @Nested
    class BuildBodySections {

        @Test
        void buildBodySectionsShowsNothingWhenNoViewIsPainting() {
            // The tab is switched away from the political map, so there is no grouping to rank under.
            // An empty body is what stops the box being drawn at all, rather than one echoing the
            // system name the cursor already sits on.
            viewRegistryMock
                .when(PoliticalMapViewRegistry::getActiveView)
                .thenReturn(null);

            assertThat(tooltip.buildBodySections(sectorMock, systemMock))
                .isEmpty();
        }

        @Test
        void buildBodySectionsRanksTheSystemUnderTheActiveViewsOwnGrouping() {
            // What keeps the box honest: the tooltip ranks through the same grouping the map painted
            // its fills by, so the two can never disagree about who holds the system.
            tooltip.buildBodySections(sectorMock, systemMock);

            rowResolverMock.verify(
                () -> StandingRowResolver.resolveRows(
                    same(sectorMock),
                    any(),
                    same(VIEW_GROUPING)));
        }

        @Test
        void buildBodySectionsDrawsAGroupMadeUpOfNothingAsOneFlatLine() {
            // The faction view's shape: a lone faction resolves to a group made up of nothing, so the
            // box lists it and nothing beneath it - and its number reads called-out like every value.
            stubResolvedRows(createLoneGroupEntry());

            var rows = readBodyRows(tooltip.buildBodySections(sectorMock, systemMock));

            assertThat(rows)
                .hasSize(2);

            var header = readTableRow(rows, GROUP_HEADER_ROW);

            assertThat(readLabelRun(header, LABEL_RUN))
                .isEqualTo(new TextSpan("Rebel Pact", PLAYER_BRIGHT));

            assertThat(header.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));

            assertThat(header.labelledRow().leadingRowSlot())
                .isEqualTo(new RowSlot.Image(BLOC_CREST));

            assertThat(header.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", HIGHLIGHT)));
        }

        @Test
        void buildBodySectionsDrawsAnAllianceAboveItsIndentedMembers() {
            // The alliances view's shape: the bloc is listed and its members read as belonging to it, by
            // the indent and the plainer colour rather than by any label saying so.
            stubResolvedRows(createGroupEntry(
                    createMemberLine(MEMBER_SCORE),
                    createMemberLine(OTHER_MEMBER_SCORE)));

            var rows = readBodyRows(tooltip.buildBodySections(sectorMock, systemMock));

            assertThat(rows)
                .hasSize(4);

            var firstMember = readTableRow(rows, FIRST_MEMBER_ROW);

            assertThat(readLabelRun(readTableRow(rows, GROUP_HEADER_ROW), LABEL_RUN))
                .isEqualTo(new TextSpan("Rebel Pact", PLAYER_BRIGHT));

            assertThat(readLabelRun(firstMember, LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));

            assertThat(firstMember.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));

            assertThat(firstMember.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("900", TEXT)));

            // Ranked as the standing ranked them, so the box reads strongest first like the fills.
            assertThat(readTableRow(rows, SECOND_MEMBER_ROW).labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("300", TEXT)));
        }

        @Test
        void buildBodySectionsNamesTheStrongestGroupAsHoldingTheSystemAndTheRestAsContestingIt() {
            // The two headings are what turn a ranked list into an answer: the map fills the system in
            // the leader's colour, so the box says outright that the leader holds it and the others are
            // merely present, rather than leaving that to be read off the row order.
            stubResolvedRows(
                createLoneGroupEntry(),
                createRivalGroupEntry());

            assertThat(readBodyLabelTexts(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Contested by:",
                    "Persean League");
        }

        @Test
        void buildBodySectionsKeepsAContestingGroupsOwnCrestAndScore() {
            // A contesting group is a full standing, not a footnote to the leader's: it keeps the crest
            // and the number the map ranked it by, so the player can see how close the contest is.
            var rivalHeaderRow = 3;

            stubResolvedRows(
                createLoneGroupEntry(),
                createRivalGroupEntry());

            var rivalHeader = readTableRow(
                readBodyRows(tooltip.buildBodySections(sectorMock, systemMock)),
                rivalHeaderRow);

            assertThat(rivalHeader.labelledRow().leadingRowSlot())
                .isEqualTo(new RowSlot.Image(RIVAL_CREST));

            assertThat(rivalHeader.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("400", HIGHLIGHT)));
        }

        @Test
        void buildBodySectionsOmitsContestedWhenOneGroupHoldsTheSystemAlone() {
            // An uncontested system has to read as uncontested, and a heading standing over no groups
            // would read as a contest whose challengers failed to resolve.
            stubResolvedRows(createLoneGroupEntry());

            assertThat(readBodyLabelTexts(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly("Dominated by:", "Rebel Pact");
        }

        @Test
        void buildBodySectionsHoldsEachHeadingWithTheGroupsItNames() {
            // Each heading is a block with its own groups, so the box parts one block from the next and
            // nothing inside a block - a heading parted from its own entries would read as belonging to
            // the block above it.
            var dominatedSection = 0;
            var contestedSection = 1;

            stubResolvedRows(
                createLoneGroupEntry(),
                createRivalGroupEntry());

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(sections)
                .hasSize(2);
            assertThat(readLabelTexts(sections.get(dominatedSection).rows()))
                .containsExactly("Dominated by:", "Rebel Pact");
            assertThat(readLabelTexts(sections.get(contestedSection).rows()))
                .containsExactly("Contested by:", "Persean League");
        }

        @Test
        void buildBodySectionsDrawsHeadingsAtTheContentEdgeInGold() {
            // What the review found here: a heading laid inside the crest gutter starts where the group
            // labels below it start and so reads as indented under nothing, and drawn in their own
            // bright it is told apart from them only by lacking a crest.
            stubResolvedRows(createLoneGroupEntry());

            var heading = readTableRow(
                readBodyRows(tooltip.buildBodySections(sectorMock, systemMock)),
                DOMINATED_HEADING_ROW);

            assertThat(readLabelRun(heading, LABEL_RUN))
                .isEqualTo(new TextSpan("Dominated by:", HIGHLIGHT));

            assertThat(heading.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);

            assertThat(heading.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);

            assertThat(heading.labelledRow().trailingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
        }

        @Test
        void buildBodySectionsNamesWhatTheSystemIsBeforeWhoHoldsIt() {
            // What the system is first, then the contest over it, so the standings read as a contest
            // over a known system rather than as the whole of what the box has to say. The decree is not
            // among them:
            // it heads the box instead, which the title cases above cover.
            stubStatusRow("Decivilised");
            stubCoreFaction(CORE_FACTION);
            stubResolvedRows(createLoneGroupEntry());

            assertThat(readBodyLabelTexts(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly(
                    "Decivilised",
                    "Dominated by:",
                    "Rebel Pact");
        }

        @Test
        void buildBodySectionsFallsBackToTheSystemStatusWhenNothingRanks() {
            // A system nobody holds is not nothing: the status line says why it holds no standing, so
            // the hover reads as landing on a real but uninhabited system. It is a block of its own,
            // since what the system is answers a different question from who contests it.
            var statusRow = stubStatusRow("Unpopulated");

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(0).rows())
                .containsExactly(statusRow);
        }

        @Test
        void buildBodySectionsJudgesTheSystemEmptyUnderTheRankingsOwnReveal() {
            // The status has to admit exactly the colonies the standings were ranked through: judged
            // under the narrower filter, a system revealed only by the dev knob would be called
            // unpopulated directly above the rows scoring the faction holding it.
            dominancePassMock
                .when(() -> DominancePass.readFromLunaSettings(any()))
                .thenReturn(new DominancePass(ANY_RULES, true, HolderGrouping.identity()));

            tooltip.buildBodySections(sectorMock, systemMock);

            statusRowMock.verify(
                () -> SystemStatusRow.resolveStatusRow(sectorMock, systemMock, true));
        }

        @Test
        void buildBodySectionsShowsNothingWhenNothingRanksAndTheSystemHasNoStatusEither() {
            // Nothing ranked and nothing to say about the system, so the body stays empty and no box is
            // drawn - the one case where a hover over a real system shows nothing at all.
            assertThat(tooltip.buildBodySections(sectorMock, systemMock))
                .isEmpty();
        }
    }

    // Hands the box the group entries it is about, standing in for the ranking and the resolution that
    // would otherwise have to be driven through a live economy to produce them.
    private void stubResolvedRows(CellTooltipEntry... groupEntries) {
        rowResolverMock
            .when(() -> StandingRowResolver.resolveRows(any(), any(), any()))
            .thenReturn(List.of(groupEntries));
    }

    // Puts the system under a faction's decree, through the same single-flag read the tooltip makes -
    // the fake derives it from the breakdown, so the two can never be set to disagree.
    private void stubCoreFaction(String factionId) {
        claimBreakdownReaderFake.setBreakdown(
            SYSTEM_ID,
            new SystemClaimBreakdown(factionId, factionId, List.of()));
    }

    // Stands the status line in as present, which the (stood-in) resolver would otherwise need a live
    // economy to decide. Returns the row so a case can assert the body carries that very line.
    private TooltipRow stubStatusRow(String statusText) {

        var statusRow = CellTooltipRows.buildBannerRow(null, statusText);

        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), any(Boolean.class)))
            .thenReturn(Optional.of(statusRow));

        return statusRow;
    }

    // The body read top to bottom as the lines a player sees, headings and entries alike - the shape
    // most of these cases are about, which asserting block by block would bury. How those lines are
    // grouped is the subject of one case of its own.
    private static List<TooltipRow> readBodyRows(List<TooltipSection> sections) {
        return TooltipSection.readRowsInOrder(sections);
    }

    private static List<String> readBodyLabelTexts(List<TooltipSection> sections) {
        return readLabelTexts(readBodyRows(sections));
    }

    private static List<String> readLabelTexts(List<TooltipRow> rows) {
        return rows
            .stream()
            .map(SystemDominationTooltipTest::readOpeningWords)
            .toList();
    }

    // What a line opens with in words: its first run that carries any. Runs that hold an image rather
    // than text are stepped over, so a line led by a crest still reads as the name it goes on to say
    // rather than as a sprite path - which is what makes one list of expected lines cover a box that
    // mixes plain entries with a crested banner.
    private static String readOpeningWords(TooltipRow row) {
        return row
            .labelRuns()
            .stream()
            .filter(TextSpan.class::isInstance)
            .map(labelRun -> ((TextSpan) labelRun).text())
            .findFirst()
            .orElse("");
    }

    // Reads one body line as the table row it is. A block's lines are typed on the row supertype, since
    // a centred line is a legal shape for one, but every line these cases assert on lays into the box's
    // columns - which is where the indent and the crest and value slots live.
    private static TooltipRow.TableRow readTableRow(List<TooltipRow> rows, int rowIndex) {
        return (TooltipRow.TableRow) rows.get(rowIndex);
    }

    // One group as the resolver hands it over: a bloc carrying its crest and summed score, made up of
    // the factions in it. Whether a group is made up of anything is the resolver's decision, so a case
    // here states it by handing over the members or none.
    private static CellTooltipEntry createGroupEntry(CellTooltipEntryLine... memberLines) {
        return CellTooltipEntry
            .createEntry(CellTooltipEntryLine.createLine(BLOC_CREST, "Rebel Pact", BLOC_SCORE))
            .nesting(List.of(memberLines));
    }

    // A group that breaks down no further - what a lone faction in the faction view resolves to.
    private static CellTooltipEntry createLoneGroupEntry() {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(BLOC_CREST, "Rebel Pact", BLOC_SCORE));
    }

    // A second, lower-ranked group, named and scored apart from the leader so a case about which block
    // a group lands in cannot pass by reading the leader's line twice.
    private static CellTooltipEntry createRivalGroupEntry() {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(RIVAL_CREST, "Persean League", RIVAL_SCORE));
    }

    // One member faction beneath a bloc, told apart from its siblings by its score alone.
    private static CellTooltipEntryLine createMemberLine(String scoreText) {
        return CellTooltipEntryLine.createLine(MEMBER_CREST, "The Hegemony", scoreText);
    }
}
