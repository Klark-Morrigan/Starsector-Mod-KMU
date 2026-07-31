package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.TooltipRow;

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
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins how the standings the map ranks become the lines the hover box draws: a lone faction reads as one
 * flat header, an alliance reads as a header over its indented members however few it has, and a system
 * that ranks empty says why rather than falling silent. Also that the ranking runs under the active
 * view's own grouping, which is what makes the numbers in the box the ones the fills were painted by.
 *
 * <p>The ranking and the row resolution behind it are stood in for, since each is pinned by its own
 * suite: what is left is the shape this class alone decides - which rows are emitted, in what order, and
 * at what tier.
 */
final class SystemDominationTooltipTest {
    private static final Color BRIGHT = new Color(200, 230, 255);
    private static final Color TEXT = Color.LIGHT_GRAY;
    private static final Color GOLD = new Color(255, 200, 100);

    private static final String BLOC_CREST = "graphics/rebel_pact_crest.png";
    private static final String MEMBER_CREST = "graphics/hegemony_crest.png";

    // The lines the box lays out, in draw order: the group's own header, then its members beneath.
    private static final int HEADER_ROW = 0;
    private static final int FIRST_MEMBER_ROW = 1;
    private static final int SECOND_MEMBER_ROW = 2;

    // A line's label is one run here - no case below writes a qualifier onto one.
    private static final int LABEL_RUN = 0;

    // The scores are only carried through to the value column, so any weights stand in; four figures,
    // so the assertions also catch the thousands grouping being dropped on the way.
    private static final int BLOC_SCORE = 1200;
    private static final int MEMBER_SCORE = 900;
    private static final int OTHER_MEMBER_SCORE = 300;

    // The pass is forwarded to the (stood-in) ranking, so its weights never reach an assertion - any
    // rules stand in where the seam demands them.
    private static final DominancePass ANY_PASS = new DominancePass(
        new DominanceRules(false,
            new BaseSizeWeighting(1.0, null, 1.0, 1.0),
            new StationWeighting(false, 1.0, 0.5, 0.5),
            new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5)),
        false,
        HolderGrouping.identity());

    // The grouping the active view answers with, and deliberately not the identity one: a grouping the
    // ranking could have reached for on its own would let a tooltip that ignored the view still pass
    // the case below. Asserted by identity, since what matters is that this instance is the one used.
    private static final HolderGrouping VIEW_GROUPING = new HolderGrouping(
        Map.of("hegemony", "rebel_pact"),
        Map.of("rebel_pact", "hegemony"),
        Map.of("rebel_pact", "Rebel Pact"));

    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    private MockedStatic<Misc> miscMock;
    private MockedStatic<PoliticalMapViewRegistry> viewRegistryMock;
    private MockedStatic<DominancePass> dominancePassMock;
    private MockedStatic<SystemStandings> standingsMock;
    private MockedStatic<StandingRowResolver> rowResolverMock;
    private MockedStatic<SystemStatusRow> statusRowMock;

    @BeforeEach
    void installColoursAndTheRankingSeams() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();
        miscMock = Mockito.mockStatic(Misc.class);
        miscMock.when(Misc::getBrightPlayerColor).thenReturn(BRIGHT);
        miscMock.when(Misc::getTextColor).thenReturn(TEXT);
        miscMock.when(Misc::getHighlightColor).thenReturn(GOLD);

        // A view is active by default, since every case but one is about what its rows become. The
        // ranking and the settings read behind it are stood in so no case depends on a live economy or
        // a live LunaLib.
        var viewMock = mock(PoliticalMapView.class);
        when(viewMock.resolveGrouping()).thenReturn(VIEW_GROUPING);
        viewRegistryMock = Mockito.mockStatic(PoliticalMapViewRegistry.class);
        viewRegistryMock.when(PoliticalMapViewRegistry::getActiveView).thenReturn(viewMock);
        dominancePassMock = Mockito.mockStatic(DominancePass.class);
        dominancePassMock.when(() -> DominancePass.readFromLunaSettings(any())).thenReturn(ANY_PASS);
        standingsMock = Mockito.mockStatic(SystemStandings.class);
        rowResolverMock = Mockito.mockStatic(StandingRowResolver.class);
        statusRowMock = Mockito.mockStatic(SystemStatusRow.class);
    }

    @AfterEach
    void clearColoursAndTheRankingSeams() {
        statusRowMock.close();
        rowResolverMock.close();
        standingsMock.close();
        dominancePassMock.close();
        viewRegistryMock.close();
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
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

            assertThat(SystemDominationTooltip.INSTANCE.buildBodyRows(sectorMock, systemMock))
                .isEmpty();
        }

        @Test
        void buildBodyRowsRanksTheSystemUnderTheActiveViewsOwnGrouping() {
            // What keeps the box honest: the tooltip ranks through the same grouping the map painted
            // its fills by, so the two can never disagree about who holds the system.
            SystemDominationTooltip.INSTANCE.buildBodyRows(sectorMock, systemMock);

            rowResolverMock.verify(() -> StandingRowResolver.resolveRows(
                same(sectorMock),
                any(),
                same(VIEW_GROUPING)));
        }

        @Test
        void buildBodyRowsDrawsALoneFactionAsOneFlatHeader() {
            // The faction view's shape: the group is the faction, so its one member would only repeat
            // the header, and the row that would carry it is never emitted.
            stubResolvedRows(createGroupRow(false, createMemberRow(MEMBER_SCORE)));

            var rows = SystemDominationTooltip.INSTANCE.buildBodyRows(sectorMock, systemMock);

            assertThat(rows)
                .hasSize(1);

            var header = readTableRow(rows, HEADER_ROW);

            assertThat(readLabelRun(header, LABEL_RUN))
                .isEqualTo(new TextSpan("Rebel Pact", BRIGHT));

            assertThat(header.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));

            assertThat(header.labelledRow().leadingRowSlot())
                .isEqualTo(new RowSlot.Image(BLOC_CREST));

            assertThat(header.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", GOLD)));
        }

        @Test
        void buildBodyRowsDrawsAnAllianceHeaderAboveItsIndentedMembers() {
            // The alliances view's shape: the bloc heads its own block and its members read as
            // belonging to it, by the indent and the plainer colour rather than by any label saying so.
            stubResolvedRows(createGroupRow(
                    true,
                    createMemberRow(MEMBER_SCORE),
                    createMemberRow(OTHER_MEMBER_SCORE)));

            var rows = SystemDominationTooltip.INSTANCE.buildBodyRows(sectorMock, systemMock);

            assertThat(rows)
                .hasSize(3);

            var firstMember = readTableRow(rows, FIRST_MEMBER_ROW);

            assertThat(readLabelRun(readTableRow(rows, HEADER_ROW), LABEL_RUN))
                .isEqualTo(new TextSpan("Rebel Pact", BRIGHT));

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

            var rows = SystemDominationTooltip.INSTANCE.buildBodyRows(sectorMock, systemMock);

            assertThat(rows)
                .hasSize(2);

            assertThat(readTableRow(rows, FIRST_MEMBER_ROW).indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void buildBodyRowsFallsBackToTheSystemStatusWhenNothingRanks() {
            // A system nobody holds is not nothing: the status line says why it holds no standing, so
            // the hover reads as landing on a real but uninhabited system.
            var statusRow = CellTooltipRows.buildStandaloneRow("Unpopulated");
            statusRowMock
                .when(() -> SystemStatusRow.resolveStatusRow(
                    same(sectorMock),
                    same(systemMock),
                    any(Boolean.class)))
                .thenReturn(Optional.of(statusRow));

            var rows = SystemDominationTooltip.INSTANCE.buildBodyRows(sectorMock, systemMock);

            assertThat(rows).containsExactly(statusRow);
        }

        @Test
        void buildBodyRowsShowsNothingWhenNothingRanksAndTheSystemHasNoStatusEither() {
            // Nothing ranked and nothing to say about the system, so the body stays empty and no box is
            // drawn - the one case where a hover over a real system shows nothing at all.
            assertThat(SystemDominationTooltip.INSTANCE.buildBodyRows(sectorMock, systemMock))
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

    // One member faction beneath a group header, told apart from its siblings by its score alone.
    private static FactionStandingRow createMemberRow(int score) {
        return new FactionStandingRow(
            "hegemony",
            "The Hegemony",
            MEMBER_CREST,
            score);
    }
}
