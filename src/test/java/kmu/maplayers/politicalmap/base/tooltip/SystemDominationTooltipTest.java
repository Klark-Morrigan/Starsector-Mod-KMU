package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.TooltipRow;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

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
 * named as dominating the system and the rest as contesting it, a lone faction reads as one flat header
 * while an alliance reads as a header over its indented members however few it has, and what the system
 * is beyond its standings - dead, or held by decree - is said above the contest. Also that the ranking
 * runs under the active view's own grouping, which is what makes the numbers in the box the ones the
 * fills were painted by.
 *
 * <p>The ranking, the row resolution, the status line and the claim read behind it are all stood in
 * for, since each is pinned by its own suite: what is left is the shape this class alone decides - which
 * rows are emitted, in what order, and at what tier.
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

    // The scores are only carried through to the value column, so any weights stand in; four figures,
    // so the assertions also catch the thousands grouping being dropped on the way.
    private static final int BLOC_SCORE = 1200;
    private static final int MEMBER_SCORE = 900;
    private static final int OTHER_MEMBER_SCORE = 300;
    private static final int RIVAL_SCORE = 400;

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
            stubResolvedRows(createGroupRow(false, createMemberRow(MEMBER_SCORE)));

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
    class BuildBodyRows {

        @Test
        void buildBodyRowsShowsNothingWhenNoViewIsPainting() {
            // The tab is switched away from the political map, so there is no grouping to rank under.
            // An empty body is what stops the box being drawn at all, rather than one echoing the
            // system name the cursor already sits on.
            viewRegistryMock
                .when(PoliticalMapViewRegistry::getActiveView)
                .thenReturn(null);

            assertThat(tooltip.buildBodyRows(sectorMock, systemMock))
                .isEmpty();
        }

        @Test
        void buildBodyRowsRanksTheSystemUnderTheActiveViewsOwnGrouping() {
            // What keeps the box honest: the tooltip ranks through the same grouping the map painted
            // its fills by, so the two can never disagree about who holds the system.
            tooltip.buildBodyRows(sectorMock, systemMock);

            rowResolverMock.verify(
                () -> StandingRowResolver.resolveRows(
                    same(sectorMock),
                    any(),
                    same(VIEW_GROUPING)));
        }

        @Test
        void buildBodyRowsDrawsALoneFactionAsOneFlatHeader() {
            // The faction view's shape: the group is the faction, so its one member would only repeat
            // the header, and the row that would carry it is never emitted.
            stubResolvedRows(createGroupRow(false, createMemberRow(MEMBER_SCORE)));

            var rows = tooltip.buildBodyRows(sectorMock, systemMock);

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
        void buildBodyRowsDrawsAnAllianceHeaderAboveItsIndentedMembers() {
            // The alliances view's shape: the bloc heads its own block and its members read as
            // belonging to it, by the indent and the plainer colour rather than by any label saying so.
            stubResolvedRows(createGroupRow(
                    true,
                    createMemberRow(MEMBER_SCORE),
                    createMemberRow(OTHER_MEMBER_SCORE)));

            var rows = tooltip.buildBodyRows(sectorMock, systemMock);

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
        void buildBodyRowsNestsAOneMemberAllianceRatherThanCollapsingIt() {
            // The regression this guards: branching on the member count instead of the nests-members
            // flag would silently flatten a one-member alliance into a lone-faction line, so the same
            // bloc would read as two different things depending on how many members it happens to hold.
            stubResolvedRows(createGroupRow(true, createMemberRow(MEMBER_SCORE)));

            var rows = tooltip.buildBodyRows(sectorMock, systemMock);

            assertThat(rows)
                .hasSize(3);

            assertThat(readTableRow(rows, FIRST_MEMBER_ROW).indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void buildBodyRowsNamesTheStrongestGroupAsHoldingTheSystemAndTheRestAsContestingIt() {
            // The two headings are what turn a ranked list into an answer: the map fills the system in
            // the leader's colour, so the box says outright that the leader holds it and the others are
            // merely present, rather than leaving that to be read off the row order.
            stubResolvedRows(
                createGroupRow(false, createMemberRow(MEMBER_SCORE)),
                createRivalGroupRow());

            assertThat(readLabelTexts(tooltip.buildBodyRows(sectorMock, systemMock)))
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Contested by:",
                    "Persean League");
        }

        @Test
        void buildBodyRowsKeepsAContestingGroupsOwnCrestAndScore() {
            // A contesting group is a full standing, not a footnote to the leader's: it keeps the crest
            // and the number the map ranked it by, so the player can see how close the contest is.
            var rivalHeaderRow = 3;

            stubResolvedRows(
                createGroupRow(false, createMemberRow(MEMBER_SCORE)),
                createRivalGroupRow());

            var rivalHeader = readTableRow(tooltip.buildBodyRows(sectorMock, systemMock),
                rivalHeaderRow);

            assertThat(rivalHeader.labelledRow().leadingRowSlot())
                .isEqualTo(new RowSlot.Image(RIVAL_CREST));

            assertThat(rivalHeader.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("400", HIGHLIGHT)));
        }

        @Test
        void buildBodyRowsOmitsContestedWhenOneGroupHoldsTheSystemAlone() {
            // An uncontested system has to read as uncontested, and a heading standing over no groups
            // would read as a contest whose challengers failed to resolve.
            stubResolvedRows(createGroupRow(false, createMemberRow(MEMBER_SCORE)));

            assertThat(readLabelTexts(tooltip.buildBodyRows(sectorMock, systemMock)))
                .containsExactly("Dominated by:", "Rebel Pact");
        }

        @Test
        void buildBodyRowsOpensEverySectionSoItsBlockIsPartedFromTheOneAbove() {
            // Only the headings take the break, so the box reads as blocks: an entry taking one would
            // part it from the heading it belongs to.
            var contestedHeadingRow = 2;

            stubResolvedRows(
                createGroupRow(false, createMemberRow(MEMBER_SCORE)),
                createRivalGroupRow());

            var rows = tooltip.buildBodyRows(sectorMock, systemMock);

            assertThat(rows.get(DOMINATED_HEADING_ROW).hasSectionBreak())
                .isTrue();
            assertThat(rows.get(contestedHeadingRow).hasSectionBreak())
                .isTrue();
            assertThat(rows.get(GROUP_HEADER_ROW).hasSectionBreak())
                .isFalse();
        }

        @Test
        void buildBodyRowsDrawsHeadingsFlushAndCrestless() {
            // A heading names a block rather than sitting in it, so it opens flush at the box's edge
            // with no crest of its own and no number - the shape that tells it apart from the group
            // rows beneath it, which carry both.
            stubResolvedRows(createGroupRow(false, createMemberRow(MEMBER_SCORE)));

            var heading = readTableRow(
                tooltip.buildBodyRows(sectorMock, systemMock),
                DOMINATED_HEADING_ROW);

            assertThat(readLabelRun(heading, LABEL_RUN))
                .isEqualTo(new TextSpan("Dominated by:", PLAYER_BRIGHT));

            assertThat(heading.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));

            assertThat(heading.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);

            assertThat(heading.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(HIGHLIGHT)));
        }

        @Test
        void buildBodyRowsNamesWhatTheSystemIsBeforeWhoHoldsIt() {
            // What the system is first, then the contest over it, so the standings read as a contest
            // over a known system rather than as the whole of what the box has to say. The decree is not
            // among them:
            // it heads the box instead, which the title cases above cover.
            stubStatusRow("Decivilised");
            stubCoreFaction(CORE_FACTION);
            stubResolvedRows(createGroupRow(false, createMemberRow(MEMBER_SCORE)));

            assertThat(readLabelTexts(tooltip.buildBodyRows(sectorMock, systemMock)))
                .containsExactly(
                    "Decivilised",
                    "Dominated by:",
                    "Rebel Pact");
        }

        @Test
        void buildBodyRowsFallsBackToTheSystemStatusWhenNothingRanks() {
            // A system nobody holds is not nothing: the status line says why it holds no standing, so
            // the hover reads as landing on a real but uninhabited system.
            var statusRow = stubStatusRow("Unpopulated");

            assertThat(tooltip.buildBodyRows(sectorMock, systemMock))
                .containsExactly(statusRow);
        }

        @Test
        void buildBodyRowsJudgesTheSystemEmptyUnderTheRankingsOwnReveal() {
            // The status has to admit exactly the colonies the standings were ranked through: judged
            // under the narrower filter, a system revealed only by the dev knob would be called
            // unpopulated directly above the rows scoring the faction holding it.
            dominancePassMock
                .when(() -> DominancePass.readFromLunaSettings(any()))
                .thenReturn(new DominancePass(ANY_RULES, true, HolderGrouping.identity()));

            tooltip.buildBodyRows(sectorMock, systemMock);

            statusRowMock.verify(
                () -> SystemStatusRow.resolveStatusRow(sectorMock, systemMock, true));
        }

        @Test
        void buildBodyRowsShowsNothingWhenNothingRanksAndTheSystemHasNoStatusEither() {
            // Nothing ranked and nothing to say about the system, so the body stays empty and no box is
            // drawn - the one case where a hover over a real system shows nothing at all.
            assertThat(tooltip.buildBodyRows(sectorMock, systemMock))
                .isEmpty();
        }
    }

    // Hands the flattening the group rows it is about, standing in for the ranking and the resolution
    // that would otherwise have to be driven through a live economy to produce them.
    private void stubResolvedRows(StandingGroupRow... groupRows) {
        rowResolverMock
            .when(() -> StandingRowResolver.resolveRows(any(), any(), any()))
            .thenReturn(List.of(groupRows));
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

    // The box read top to bottom as the words a player sees, headings and entries alike - the shape
    // most of these cases are about, which asserting row by row would bury.
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

    // Reads one body line as the table row it is. The body is typed on the row supertype, since a
    // centred line is a legal shape for one, but every line these cases assert on lays into the box's
    // columns - which is where the indent and the crest and value slots live.
    private static TooltipRow.TableRow readTableRow(List<TooltipRow> rows, int rowIndex) {
        return (TooltipRow.TableRow) rows.get(rowIndex);
    }

    // One group as the resolver hands it over: a bloc header carrying its crest and summed score, over
    // the members that make it up. The nesting flag is the group's kind, which is what the flattening
    // branches on.
    private static StandingGroupRow createGroupRow(
            boolean shouldNestMembers,
            FactionStandingRow... members) {
                
        return new StandingGroupRow(
            "rebel_pact",
            "Rebel Pact",
            BLOC_CREST,
            BLOC_SCORE,
            shouldNestMembers,
            List.of(members));
    }

    // A second, lower-ranked group, named and scored apart from the leader so a case about which block
    // a group lands in cannot pass by reading the leader's row twice.
    private static StandingGroupRow createRivalGroupRow() {
        return new StandingGroupRow(
            "persean_league",
            "Persean League",
            RIVAL_CREST,
            RIVAL_SCORE,
            false,
            List.of(createMemberRow(RIVAL_SCORE)));
    }

    // One member faction beneath a group header, told apart from its siblings by its score alone.
    private static FactionStandingRow createMemberRow(int score) {
        return new FactionStandingRow(
            "hegemony",
            "The Hegemony",
            MEMBER_CREST,
            score);
    }
}
