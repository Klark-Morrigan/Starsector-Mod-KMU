package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.TooltipRow;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the shape a claim contest is read in: the claim is always stated, the rivals who could have
 * taken the system and the ones who never could are told apart into their own blocks, and a system
 * held by decree says so on the claim line without losing the market standings behind it.
 *
 * <p>The breakdown itself is stood in for through the reader seam - it has its own suite in KMLib -
 * so what is left is the part this class alone decides: which lines are emitted, under which heading,
 * and in what order.
 */
final class SystemClaimTooltipTest {

    private static final Color BRIGHT = new Color(200, 230, 255);
    private static final Color TEXT = Color.LIGHT_GRAY;
    private static final Color GOLD = new Color(255, 200, 100);

    private static final String SYSTEM_ID = "askonia";

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    private static final String PIRATES = "pirates";

    private static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";

    // A line's label is one run, except the claim line of a decreed system, which continues into the
    // marker calling the decree out.
    private static final int LABEL_RUN = 0;
    private static final int MARKER_RUN = 1;

    // The two lines every box opens with, whatever the contest below them holds.
    private static final int CLAIM_HEADING_ROW = 0;
    private static final int CLAIM_ROW = 1;

    // Scores stand for market standings only, so any weights serve; four figures on the top one, so a
    // dropped thousands separator fails the assertion rather than passing unnoticed.
    private static final int TOP_SCORE = 1200;
    private static final int RIVAL_SCORE = 8;
    private static final int OUTSIDER_SCORE = 3;

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();
    private final SystemClaimTooltip tooltip = new SystemClaimTooltip(claimBreakdownReaderFake);

    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private MockedStatic<Misc> miscMock;
    private MockedStatic<SystemStatusRow> statusRowMock;

    @BeforeEach
    void installColoursAndTheSystemStatusSeam() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();

        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getBrightPlayerColor)
            .thenReturn(BRIGHT);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(TEXT);
        miscMock
            .when(Misc::getHighlightColor)
            .thenReturn(GOLD);

        // The status line has its own suite and would otherwise demand a live economy here, so it is
        // stood in as absent - a populated system - for every case but the one about it.
        statusRowMock = Mockito.mockStatic(SystemStatusRow.class);
        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), anyBoolean()))
            .thenReturn(Optional.empty());

        // Each faction is built before the sector is told about it: stubbing one mock inside another
        // stub's argument leaves Mockito mid-stubbing and fails the whole fixture.
        var hegemonyMock = factionNamed("The Hegemony", HEGEMONY_CREST);
        var tritachyonMock = factionNamed("Tri-Tachyon", null);
        var piratesMock = factionNamed("Pirates", null);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);
        when(sectorMock.getFaction(HEGEMONY))
            .thenReturn(hegemonyMock);
        when(sectorMock.getFaction(TRITACHYON))
            .thenReturn(tritachyonMock);
        when(sectorMock.getFaction(PIRATES))
            .thenReturn(piratesMock);
    }

    @AfterEach
    void clearColoursAndTheSystemStatusSeam() {
        statusRowMock.close();
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class BuildBodyRows {

        @Test
        void buildBodyRowsNamesTheClaimantWithItsCrestAndScoreUnderTheClaimHeading() {

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(new FactionClaimScore(HEGEMONY, TOP_SCORE, true))));

            var rows = tooltip.buildBodyRows(sectorMock, systemMock);

            assertThat(readLabelText(rows, CLAIM_HEADING_ROW))
                .isEqualTo("Claim:");

            var claimRow = readTableRow(rows, CLAIM_ROW);

            assertThat(readLabelRun(claimRow, LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));
            assertThat(claimRow.labelledRow().leadingRowSlot())
                .isEqualTo(new RowSlot.Image(HEGEMONY_CREST));
            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", TEXT)));
        }

        @Test
        void buildBodyRowsSortsTheRivalsIntoContestedAndNonTerritorialBlocks() {
            // The two kinds of presence answer different questions - who nearly took the system, and
            // who is merely there - so they are told apart by the heading they sit under rather than
            // by a note on a line.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    new FactionClaimScore(HEGEMONY, TOP_SCORE, true),
                    new FactionClaimScore(TRITACHYON, RIVAL_SCORE, true),
                    new FactionClaimScore(PIRATES, OUTSIDER_SCORE, false))));

            var rows = tooltip.buildBodyRows(sectorMock, systemMock);

            assertThat(readLabelTexts(rows))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Non-territorial:",
                    "Pirates");
        }

        @Test
        void buildBodyRowsOpensEverySectionSoItsBlockIsPartedFromTheOneAbove() {
            // Only the headings take the break, so the box reads as blocks: an entry taking one would
            // part it from the heading it belongs to.
            var secondHeadingRow = 2;

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    new FactionClaimScore(HEGEMONY, TOP_SCORE, true),
                    new FactionClaimScore(PIRATES, OUTSIDER_SCORE, false))));

            var rows = tooltip.buildBodyRows(sectorMock, systemMock);

            assertThat(rows.get(CLAIM_HEADING_ROW).hasSectionBreak())
                .isTrue();
            assertThat(rows.get(secondHeadingRow).hasSectionBreak())
                .isTrue();
            assertThat(rows.get(CLAIM_ROW).hasSectionBreak())
                .isFalse();
        }

        @Test
        void buildBodyRowsOmitsContestedWhenTheClaimantIsTheOnlyTerritorialFaction() {
            // An uncontested claim has to read as uncontested, and a heading standing over no lines
            // would read as a contest whose rivals failed to resolve.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(new FactionClaimScore(HEGEMONY, TOP_SCORE, true))));

            assertThat(readLabelTexts(tooltip.buildBodyRows(sectorMock, systemMock)))
                .containsExactly("Claim:", "The Hegemony");
        }

        @Test
        void buildBodyRowsMarksACoreClaimAndKeepsItsMarketScore() {
            // The decree is what took the system, so it is called out in the highlight colour on the
            // claim line itself - while the number beside it stays the faction's market standing,
            // which the decree does not erase.
            stubBreakdown(new SystemClaimBreakdown(
                HEGEMONY,
                HEGEMONY,
                List.of(new FactionClaimScore(HEGEMONY, TOP_SCORE, true))));

            var claimRow = readTableRow(tooltip.buildBodyRows(sectorMock, systemMock), CLAIM_ROW);

            assertThat(readLabelRun(claimRow, LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));
            assertThat(readLabelRun(claimRow, MARKER_RUN))
                .isEqualTo(new TextSpan("(core)", GOLD));
            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", TEXT)));
        }

        @Test
        void buildBodyRowsDropsTheDisplacedTopScorerIntoContested() {
            // The regression this guards: a decree must not collapse the box to one line. The faction
            // that would have claimed by score is simply not the claimant, so it reads as contesting -
            // which is what shows the player a core imposed over a stronger presence.
            stubBreakdown(new SystemClaimBreakdown(
                PIRATES,
                PIRATES,
                List.of(
                    new FactionClaimScore(HEGEMONY, TOP_SCORE, true),
                    new FactionClaimScore(PIRATES, OUTSIDER_SCORE, false))));

            var rows = tooltip.buildBodyRows(sectorMock, systemMock);

            assertThat(readLabelTexts(rows))
                .containsExactly(
                    "Claim:",
                    "Pirates",
                    "Contested by:",
                    "The Hegemony");

            var displacedScorerRow = 3;

            assertThat(readTableRow(rows, displacedScorerRow).labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", TEXT)));
        }

        @Test
        void buildBodyRowsShowsNoScoreForACoreFactionHoldingNoMarketThere() {
            // A decree needs no colony behind it, so the claimant is named with the value column left
            // blank rather than with a nought it never scored.
            stubBreakdown(new SystemClaimBreakdown(
                HEGEMONY,
                HEGEMONY,
                List.of(new FactionClaimScore(TRITACHYON, RIVAL_SCORE, true))));

            var claimRow = readTableRow(tooltip.buildBodyRows(sectorMock, systemMock), CLAIM_ROW);

            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(TEXT)));
        }

        @Test
        void buildBodyRowsStatesTheClaimAsNoneWhenNobodyCanTakeTheSystem() {
            // A faction present but barred from claiming leaves the system unclaimed, which the box has
            // to say outright - the claim heading over nothing would read as a failure to resolve one.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                null,
                List.of(new FactionClaimScore(PIRATES, OUTSIDER_SCORE, false))));

            var rows = tooltip.buildBodyRows(sectorMock, systemMock);

            assertThat(readLabelTexts(rows))
                .containsExactly(
                    "Claim:",
                    "None",
                    "Non-territorial:",
                    "Pirates");

            assertThat(readTableRow(rows, CLAIM_ROW).labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
        }

        @Test
        void buildBodyRowsStatesTheClaimEvenForASystemNobodyIsPresentIn() {
            // The claim section is unconditional: a hover over dead ground still answers the question
            // the layer poses, rather than drawing a box the player has to interpret the absence of.
            stubBreakdown(SystemClaimBreakdown.NONE);

            assertThat(readLabelTexts(tooltip.buildBodyRows(sectorMock, systemMock)))
                .containsExactly("Claim:", "None");
        }

        @Test
        void buildBodyRowsNamesTheSystemsStatusBeforeItsClaim() {
            // A dead system names its state first, so the claim below reads as a hold over empty
            // ground rather than over a colony.
            var statusRow = CellTooltipRows.buildStandaloneRow("Unpopulated");

            statusRowMock
                .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), anyBoolean()))
                .thenReturn(Optional.of(statusRow));

            stubBreakdown(SystemClaimBreakdown.NONE);

            var rows = tooltip.buildBodyRows(sectorMock, systemMock);
            var claimHeadingUnderTheStatusRow = 1;

            assertThat(rows.get(0))
                .isSameAs(statusRow);
            assertThat(readLabelText(rows, claimHeadingUnderTheStatusRow))
                .isEqualTo("Claim:");
        }

        @Test
        void buildBodyRowsCountsUndiscoveredColoniesWhenJudgingTheSystemEmpty() {
            // The breakdown scores every market present, found or not, so the status above it has to
            // admit the same ones - otherwise an unfound colony's system reads "Unpopulated" directly
            // above the rows scoring the faction that holds it.
            stubBreakdown(SystemClaimBreakdown.NONE);

            tooltip.buildBodyRows(sectorMock, systemMock);

            statusRowMock.verify(
                () -> SystemStatusRow.resolveStatusRow(sectorMock, systemMock, true));
        }

        @Test
        void buildBodyRowsFallsBackToTheIdForAFactionTheSectorCannotResolve() {
            stubBreakdown(new SystemClaimBreakdown(
                null,
                "ghost_faction",
                List.of(new FactionClaimScore("ghost_faction", TOP_SCORE, true))));

            var claimRow = readTableRow(tooltip.buildBodyRows(sectorMock, systemMock), CLAIM_ROW);

            assertThat(readLabelRun(claimRow, LABEL_RUN).text())
                .isEqualTo("ghost_faction");
        }
    }

    // Hands the tooltip the contest it is about, standing in for the market walk that would otherwise
    // have to be driven through a live economy to produce it.
    private void stubBreakdown(SystemClaimBreakdown breakdown) {
        claimBreakdownReaderFake.setBreakdown(SYSTEM_ID, breakdown);
    }

    // The box read top to bottom as the words a player sees, headings and entries alike - the shape
    // most of these cases are about, which asserting row by row would bury.
    private static List<String> readLabelTexts(List<TooltipRow> rows) {
        return rows
            .stream()
            .map(row -> readLabelRun(row, LABEL_RUN).text())
            .toList();
    }

    private static String readLabelText(List<TooltipRow> rows, int rowIndex) {
        return readLabelRun(rows.get(rowIndex), LABEL_RUN).text();
    }

    // Reads one body line as the table row it is. The body is typed on the row supertype, since a
    // centred line is a legal shape for one, but every line these cases assert on lays into the box's
    // columns - which is where the crest and value slots live.
    private static TooltipRow.TableRow readTableRow(List<TooltipRow> rows, int rowIndex) {
        return (TooltipRow.TableRow) rows.get(rowIndex);
    }

    private static FactionAPI factionNamed(String displayNameLong, String crestSpritePath) {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getDisplayNameLong())
            .thenReturn(displayNameLong);
        when(factionMock.getCrest())
            .thenReturn(crestSpritePath);

        return factionMock;
    }
}
