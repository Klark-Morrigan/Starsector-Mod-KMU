package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipLabelPlacement;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.politicalmap.base.PoliticalMapDevToggles;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture.buildStandingOnOneMarket;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the shape a claim contest is read in: the claim is always stated, the rivals who could have
 * taken the system and the ones who never could are told apart into their own blocks, and a system
 * held by decree says so on the claim line without losing the market standings behind it.
 *
 * <p>The banner heading a decreed box is the layer's rather than this box's, so it is pinned with the
 * heading itself ({@link PoliticalMapCellTooltipTest}); the marker asserted here is what the banner
 * does not answer - why this line outranks the higher-scoring one beneath it.
 *
 * <p>The breakdown itself is stood in for through the reader seam - it has its own suite in KMLib -
 * so what is left is the part this class alone decides: which lines are emitted, under which heading,
 * in what order, and grouped into which blocks.
 */
final class SystemClaimTooltipTest {

    private static final String SYSTEM_ID = "askonia";

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    private static final String PIRATES = "pirates";

    private static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";

    // A line's label is one run, except the claim line of a decreed system, which continues into the
    // marker calling the decree out.
    private static final int LABEL_RUN = 0;
    private static final int MARKER_RUN = 1;

    // The two lines every box opens with, whatever the contest below them holds, by their place in the
    // flat run the box draws.
    private static final int CLAIM_HEADING_ROW = 0;
    private static final int CLAIM_ROW = 1;

    // The blocks a box with no status line holds, in draw order.
    private static final int CLAIM_SECTION = 0;
    private static final int SECOND_SECTION = 1;

    // What a block naming one faction comes to: its heading and the one line beneath it.
    private static final int HEADED_ONE_ENTRY_ROW_COUNT = 2;

    // Scores stand for market standings only, so any weights serve; four figures on the top one, so a
    // dropped thousands separator fails the assertion rather than passing unnoticed.
    private static final int TOP_SCORE = 1200;
    private static final int RIVAL_SCORE = 8;
    private static final int OUTSIDER_SCORE = 3;

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();
    private final SystemClaimTooltip tooltip = new SystemClaimTooltip(claimBreakdownReaderFake);

    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private MockedStatic<SystemStatusRow> statusRowMock;
    private MockedStatic<PoliticalMapDevToggles> devTogglesMock;

    @BeforeEach
    void installColoursAndTheSystemStatusSeam() {
        CellTooltipPaletteFake.installPalette();

        // The status line has its own suite and would otherwise demand a live economy here, so it is
        // stood in as absent - a populated system - for every case but the one about it.
        statusRowMock = Mockito.mockStatic(SystemStatusRow.class);
        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), anyBoolean()))
            .thenReturn(Optional.empty());

        // The reveal is a live LunaLib read, unreachable from the test JVM; stood in as off, the
        // state every case but the reveal's own is posed under.
        devTogglesMock = Mockito.mockStatic(PoliticalMapDevToggles.class);
        devTogglesMock
            .when(PoliticalMapDevToggles::readFromLunaSettings)
            .thenReturn(PoliticalMapDevToggles.NONE);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);

        stubFaction(sectorMock, HEGEMONY, "The Hegemony", HEGEMONY_CREST);
        stubFaction(sectorMock, TRITACHYON, "Tri-Tachyon", null);
        stubFaction(sectorMock, PIRATES, "Pirates", null);
    }

    @AfterEach
    void clearColoursAndTheSystemStatusSeam() {
        devTogglesMock.close();
        statusRowMock.close();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class BuildBodySections {

        @Test
        void buildBodySectionsNamesTheClaimantWithItsCrestAndScoreUnderTheClaimHeading() {

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true))));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(readLabelText(sections, CLAIM_HEADING_ROW))
                .isEqualTo("Claim:");

            var claimRow = readTableRow(sections, CLAIM_ROW);

            assertThat(readLabelRun(claimRow, LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", PLAYER_BRIGHT));
            assertThat(claimRow.labelledRow().leadingRowSlot())
                .isEqualTo(new RowSlot.Image(HEGEMONY_CREST));
            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", HIGHLIGHT)));
        }

        @Test
        void buildBodySectionsSortsTheRivalsIntoContestedAndNonTerritorialBlocks() {
            // The two kinds of presence answer different questions - who nearly took the system, and
            // who is merely there - so they are told apart by the heading they sit under rather than
            // by a note on a line.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, true),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, false))));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(readLabelTexts(sections))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Non-territorial:",
                    "Pirates");
        }

        @Test
        void buildBodySectionsHoldsEachHeadingWithTheLinesItNames() {
            // Each block is a heading and its own entries, so the box parts one block from the next
            // and nothing inside a block - the shape the whole reading of the box rests on.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, false))));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(sections)
                .hasSize(2);
            assertThat(sections.get(CLAIM_SECTION).rows())
                .hasSize(HEADED_ONE_ENTRY_ROW_COUNT);
            assertThat(sections.get(SECOND_SECTION).rows())
                .hasSize(HEADED_ONE_ENTRY_ROW_COUNT);
        }

        @Test
        void buildBodySectionsKeepsRivalsInTheOrderTheContestRankedThem() {
            // The breakdown hands its standings over strongest first, which is the order a contest is
            // read in - a section that re-ordered or reversed them would put the nearest challenger
            // last while every other assertion in this suite still passed.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, true),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, true))));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(readLabelTexts(sections))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Pirates");
        }

        @Test
        void buildBodySectionsSetsHeadingsApartFromTheEntriesBeneathThem() {
            // The two faults the review found on this box: a heading laid inside the crest gutter reads
            // as indented under nothing, and claim lines drawn as nested rows encode a second tier this
            // box does not have - claims resolve per faction, so every line here is an entry.
            var contestedHeadingRow = 2;
            var contestedEntryRow = 3;

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, true))));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(readLabelRun(readTableRow(sections, contestedHeadingRow), LABEL_RUN))
                .isEqualTo(new TextSpan("Contested by:", HIGHLIGHT));
            assertThat(readTableRow(sections, contestedHeadingRow).labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
            assertThat(readTableRow(sections, contestedHeadingRow).labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);

            assertThat(readTableRow(sections, CLAIM_ROW).indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
            assertThat(readTableRow(sections, contestedEntryRow).indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
        }

        @Test
        void buildBodySectionsOmitsContestedWhenTheClaimantIsTheOnlyTerritorialFaction() {
            // An uncontested claim has to read as uncontested, and a heading standing over no lines
            // would read as a contest whose rivals failed to resolve.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true))));

            assertThat(readLabelTexts(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly("Claim:", "The Hegemony");
        }

        @Test
        void buildBodySectionsMarksACoreClaimAndKeepsItsMarketScore() {
            // The decree is what took the system, so it is called out in the highlight colour on the
            // claim line itself - while the number beside it stays the faction's market standing,
            // which the decree does not erase.
            stubBreakdown(new SystemClaimBreakdown(
                HEGEMONY,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true))));

            var claimRow = readTableRow(tooltip.buildBodySections(sectorMock, systemMock), CLAIM_ROW);

            assertThat(readLabelRun(claimRow, LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", PLAYER_BRIGHT));
            assertThat(readLabelRun(claimRow, MARKER_RUN))
                .isEqualTo(new TextSpan("(core)", HIGHLIGHT));
            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", HIGHLIGHT)));
        }

        @Test
        void buildBodySectionsDropsTheDisplacedTopScorerIntoContested() {
            // The regression this guards: a decree must not collapse the box to one line. The faction
            // that would have claimed by score is simply not the claimant, so it reads as contesting -
            // which is what shows the player a core imposed over a stronger presence.
            stubBreakdown(new SystemClaimBreakdown(
                PIRATES,
                PIRATES,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, false))));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(readLabelTexts(sections))
                .containsExactly(
                    "Claim:",
                    "Pirates",
                    "Contested by:",
                    "The Hegemony");

            var displacedScorerRow = 3;

            assertThat(readTableRow(sections, displacedScorerRow).labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", HIGHLIGHT)));
        }

        @Test
        void buildBodySectionsShowsNoScoreForACoreFactionHoldingNoMarketThere() {
            // A decree needs no colony behind it, so the claimant is named with the value column left
            // blank rather than with a nought it never scored.
            stubBreakdown(new SystemClaimBreakdown(
                HEGEMONY,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, true))));

            var claimRow = readTableRow(tooltip.buildBodySections(sectorMock, systemMock), CLAIM_ROW);

            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(HIGHLIGHT)));
        }

        @Test
        void buildBodySectionsStatesTheClaimAsNoneWhenNobodyCanTakeTheSystem() {
            // A faction present but barred from claiming leaves the system unclaimed, which the box has
            // to say outright - the claim heading over nothing would read as a failure to resolve one.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                null,
                List.of(buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, false))));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(readLabelTexts(sections))
                .containsExactly(
                    "Claim:",
                    "None",
                    "Non-territorial:",
                    "Pirates");

            assertThat(readTableRow(sections, CLAIM_ROW).labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
        }

        @Test
        void buildBodySectionsOpensAnUnclaimedSystemsClaimAtTheContentEdge() {
            // The crest gutter is the box's one column, widened here by the crested line in the block
            // below - so the claim block, listing only the word for nobody, has to open flush under its
            // own heading rather than behind a gutter no line of it can fill.
            var nonTerritorialEntryRow = 3;

            stubBreakdown(new SystemClaimBreakdown(
                null,
                null,
                List.of(buildStandingOnOneMarket(HEGEMONY, OUTSIDER_SCORE, false))));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(readLabelTexts(sections))
                .containsExactly("Claim:", "None", "Non-territorial:", "The Hegemony");

            assertThat(readTableRow(sections, CLAIM_ROW).labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
            assertThat(readTableRow(sections, nonTerritorialEntryRow).labelPlacement())
                .isEqualTo(TooltipLabelPlacement.ALIGNED_WITH_CRESTS);
        }

        @Test
        void buildBodySectionsStatesTheClaimEvenForASystemNobodyIsPresentIn() {
            // The claim section is unconditional: a hover over a dead system still answers the question
            // the layer poses, rather than drawing a box the player has to interpret the absence of.
            stubBreakdown(SystemClaimBreakdown.NONE);

            assertThat(readLabelTexts(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly("Claim:", "None");
        }

        @Test
        void buildBodySectionsNamesTheSystemsStatusBeforeItsClaimAndInABlockOfItsOwn() {
            // A dead system names its state first, so the claim below reads as a hold over an empty
            // system rather than over a colony - and parted from it, since the two answer different
            // questions.
            var statusRow = CellTooltipRows.buildBannerRow(null, "Unpopulated");

            statusRowMock
                .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), anyBoolean()))
                .thenReturn(Optional.of(statusRow));

            stubBreakdown(SystemClaimBreakdown.NONE);

            var sections = tooltip.buildBodySections(sectorMock, systemMock);
            var statusSection = 0;
            var claimSectionUnderTheStatus = 1;

            assertThat(sections.get(statusSection).rows())
                .containsExactly(statusRow);
            assertThat(readLabelTextRun(
                    sections.get(claimSectionUnderTheStatus).rows().get(0),
                    LABEL_RUN)
                    .text())
                .isEqualTo("Claim:");
        }

        @Test
        void buildBodySectionsJudgesTheSystemEmptyUnderTheNormalRevealWhileItIsOff() {
            // An undiscovered colony must not count: suppressing the status line for one would make
            // the missing line itself the tell that something is hiding in the system.
            stubBreakdown(SystemClaimBreakdown.NONE);

            tooltip.buildBodySections(sectorMock, systemMock);

            statusRowMock.verify(
                () -> SystemStatusRow.resolveStatusRow(sectorMock, systemMock, false));
        }

        @Test
        void buildBodySectionsJudgesTheSystemEmptyUnderTheDevRevealWhileItIsOn() {
            // The reveal is read live off the same toggle the faction layer samples, so a player who
            // has turned it on is not told two different things by two layers about one system.
            devTogglesMock
                .when(PoliticalMapDevToggles::readFromLunaSettings)
                .thenReturn(new PoliticalMapDevToggles(true, false));

            stubBreakdown(SystemClaimBreakdown.NONE);

            tooltip.buildBodySections(sectorMock, systemMock);

            statusRowMock.verify(
                () -> SystemStatusRow.resolveStatusRow(sectorMock, systemMock, true));
        }

        @Test
        void buildBodySectionsFallsBackToTheIdForAFactionTheSectorCannotResolve() {
            stubBreakdown(new SystemClaimBreakdown(
                null,
                "ghost_faction",
                List.of(buildStandingOnOneMarket("ghost_faction", TOP_SCORE, true))));

            var claimRow = readTableRow(tooltip.buildBodySections(sectorMock, systemMock), CLAIM_ROW);

            assertThat(readLabelTextRun(claimRow, LABEL_RUN).text())
                .isEqualTo("ghost_faction");
        }
    }

    // Hands the tooltip the contest it is about, standing in for the market walk that would otherwise
    // have to be driven through a live economy to produce it.
    private void stubBreakdown(SystemClaimBreakdown breakdown) {
        claimBreakdownReaderFake.setBreakdown(SYSTEM_ID, breakdown);
    }

    // The box read top to bottom as the words a player sees, headings and entries alike - the shape
    // most of these cases are about, which asserting block by block would bury. How those lines are
    // grouped is the subject of one case of its own.
    private static List<String> readLabelTexts(List<TooltipSection> sections) {
        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .map(row -> readLabelTextRun(row, LABEL_RUN).text())
            .toList();
    }

    private static String readLabelText(List<TooltipSection> sections, int rowIndex) {
        return readLabelTextRun(
            TooltipSection.readRowsInOrder(sections).get(rowIndex),
            LABEL_RUN)
            .text();
    }

    // Reads one body line as the table row it is. A block's lines are typed on the row supertype, since
    // a centred line is a legal shape for one, but every line these cases assert on lays into the box's
    // columns - which is where the crest and value slots live.
    private static TooltipRow.TableRow readTableRow(List<TooltipSection> sections, int rowIndex) {
        return (TooltipRow.TableRow) TooltipSection
            .readRowsInOrder(sections)
            .get(rowIndex);
    }
}
