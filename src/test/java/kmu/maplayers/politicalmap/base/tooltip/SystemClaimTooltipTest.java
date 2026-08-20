package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.ColonyVisibility;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipLabelPlacement;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.CellTooltipRowReads;
import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.base.visibility.MapVisibilityOverrides;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture.buildPresenceOnlyStanding;
import static kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture.buildStandingOnOneMarket;
import static kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture.buildUnfoundPresenceOnlyStanding;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.GRAY;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.LABEL_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARKED_LABEL_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARKED_QUALIFIER_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARK_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
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
 * in what order, and grouped into which blocks. What the counterpart box goes on to hang beneath those
 * lines is {@link ExpandedSystemClaimTooltipTest}'s.
 */
final class SystemClaimTooltipTest {

    // The "show all factions" reveal on: the fog lifted outright, and no gate held, which
    // is the widest rule any surface reads under.
    private static final ColonyVisibility UNDER_THE_REVEAL =
        new ColonyVisibility(true, Set.of());

    private static final String SYSTEM_ID = "askonia";

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    private static final String PIRATES = "pirates";

    private static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";

    // The two lines a box over a populated system opens with, whatever the contest below them holds, by
    // their place in the flat run the box draws.
    private static final int CLAIM_HEADING_ROW = 0;
    private static final int CLAIM_ROW = 1;

    // Where the claim lands in a box whose system holds nobody: one line later, under the banner saying
    // so, which such a box always opens with.
    private static final int DECREED_CLAIM_ROW = 2;

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

    // Whether a faction may claim a system at all, which is what routes it into one rival block or the
    // other - and, deliberately, the only thing that does.
    private static final boolean IS_TERRITORIAL = true;
    private static final boolean IS_NON_TERRITORIAL = false;

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();
    private final SystemClaimTooltip tooltip = new SystemClaimTooltip(claimBreakdownReaderFake);

    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private MockedStatic<SystemStatusRow> statusRowMock;
    private MockedStatic<MapVisibilityOverrides> visibilityOverridesMock;

    @BeforeEach
    void installColoursAndTheSystemStatusSeam() {
        CellTooltipPaletteFake.installPalette();

        // The status line has its own suite and would otherwise demand a live economy here, so it is
        // stood in as absent - a populated system - for every case but the one about it.
        statusRowMock = Mockito.mockStatic(SystemStatusRow.class);
        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), any()))
            .thenReturn(Optional.empty());

        // The reveal is a live LunaLib read, unreachable from the test JVM; stood in as off, the
        // state every case but the reveal's own is posed under.
        visibilityOverridesMock = Mockito.mockStatic(MapVisibilityOverrides.class);
        visibilityOverridesMock
            .when(MapVisibilityOverrides::readFromLunaSettings)
            .thenReturn(MapVisibilityOverrides.NONE);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);

        stubFaction(sectorMock, HEGEMONY, "The Hegemony", HEGEMONY_CREST);
        stubFaction(sectorMock, TRITACHYON, "Tri-Tachyon", null);
        stubFaction(sectorMock, PIRATES, "Pirates", null);
    }

    @AfterEach
    void clearColoursAndTheSystemStatusSeam() {
        visibilityOverridesMock.close();
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

            assertThat(readLabelRun(claimRow, MARK_RUN))
                .isEqualTo(new ImageSpan(HEGEMONY_CREST));
            assertThat(readLabelRun(claimRow, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", PLAYER_BRIGHT));
            assertThat(claimRow.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
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
        void buildBodySectionsListsATerritorialFactionTheContestNeverWeighedUnderContestedAtNought() {
            // A presence-only standing is a faction the mechanic reached nothing of - a concealed
            // base, or a station the economy does not list. Territorial, it is in the running by the
            // mechanic's own gate and scored nothing here, which is what the contested heading plus a
            // nought says exactly. The nought reads quiet: it is the contest's statement about a
            // faction it never weighed, not a figure that faction competed with and lost on.
            var presenceRow = 3;

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL))));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(readLabelTexts(sections))
                .containsExactly("Claim:", "The Hegemony", "Contested by:", "Tri-Tachyon");
            assertThat(readTableRow(sections, presenceRow).labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("0", GRAY)));
        }

        @Test
        void buildBodySectionsListsANonTerritorialFactionTheContestNeverWeighedUnderNonTerritorial() {
            // The other half of the same routing: a faction barred from claiming is barred whether or
            // not the mechanic weighed anything for it, and that block is where the box says so.
            var presenceRow = 3;

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildPresenceOnlyStanding(PIRATES, IS_NON_TERRITORIAL))));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(readLabelTexts(sections))
                .containsExactly("Claim:", "The Hegemony", "Non-territorial:", "Pirates");
            assertThat(readTableRow(sections, presenceRow).labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("0", GRAY)));
        }

        @Test
        void buildBodySectionsSortsBothKindsOfStandingByEligibilityRatherThanByKind() {
            // The block says how a faction stands to the claim, not what kind of record the contest
            // gave it - so two factions sharing an eligibility share a heading however differently
            // they were reached. Sorting by kind instead would file a pirate base's owner beside a
            // Remnant station's, which are ineligible and eligible respectively.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_NON_TERRITORIAL),
                    buildPresenceOnlyStanding(PIRATES, IS_NON_TERRITORIAL))));

            assertThat(readLabelTexts(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Non-territorial:",
                    "Tri-Tachyon",
                    "Pirates");
        }

        @Test
        void buildBodySectionsLeavesOutAFactionThePlayerHasFoundNoColonyOf() {
            // The known projection over the listing: a faction present only through colonies nobody
            // has found is named nowhere, since naming it would tell the player exactly what the fog
            // is keeping back - and the account beneath it would have nothing in it to boot.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildUnfoundPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL))));

            assertThat(readLabelTexts(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly("Claim:", "The Hegemony");
        }

        @Test
        void buildBodySectionsListsAFactionThePlayerHasNotFoundUnderTheDevReveal() {
            // The reveal is the state a player has asked to be shown everything in, so the same
            // faction is listed in full - the withholding is about what they have found rather than
            // about the box.
            visibilityOverridesMock
                .when(MapVisibilityOverrides::readFromLunaSettings)
                .thenReturn(new MapVisibilityOverrides(UNDER_THE_REVEAL, false));

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildUnfoundPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL))));

            assertThat(readLabelTexts(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly("Claim:", "The Hegemony", "Contested by:", "Tri-Tachyon");
        }

        @Test
        void buildBodySectionsShowsAQuietNoughtForADecreedClaimantTheContestNeverWeighed() {
            // A decree over a system its holder is present in through a concealed base alone: the
            // faction has a standing, so the claim line states the nought that standing reports
            // rather than the blank value column of a claimant holding nothing there at all.
            stubBreakdown(new SystemClaimBreakdown(
                HEGEMONY,
                HEGEMONY,
                List.of(buildPresenceOnlyStanding(HEGEMONY, IS_TERRITORIAL))));

            var claimRow = readTableRow(tooltip.buildBodySections(sectorMock, systemMock), CLAIM_ROW);

            assertThat(readLabelRun(claimRow, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", PLAYER_BRIGHT));
            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("0", GRAY)));
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
            assertThat(sections.get(CLAIM_SECTION).readRowsInOrder())
                .hasSize(HEADED_ONE_ENTRY_ROW_COUNT);
            assertThat(sections.get(SECOND_SECTION).readRowsInOrder())
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

            assertThat(readLabelRun(claimRow, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", PLAYER_BRIGHT));
            assertThat(readLabelRun(claimRow, MARKED_QUALIFIER_RUN))
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
        void buildBodySectionsOpensEveryClaimLineAtTheContentEdge() {
            // A crest rides in the label of the line carrying it, so the claim block listing only the
            // word for nobody opens flush under its own heading - level with the crested line in the
            // block below rather than a gutter's width apart from it.
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
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
        }

        @Test
        void buildBodySectionsStatesTheClaimAsNoneForAPopulatedSystemNobodyHasTaken() {
            // Nobody holding a system that is nonetheless lived in is a finding rather than an absence,
            // and the only line that states it - so the block stands whether or not anything scored.
            stubBreakdown(SystemClaimBreakdown.NONE);

            assertThat(readLabelTexts(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly("Claim:", "None");
        }

        @Test
        void buildBodySectionsDropsTheClaimBlockForAnUnclaimedSystemHoldingNobody() {
            // "None" beneath a banner already saying the system holds nobody answers the same absence
            // twice, so the block is dropped and the banner is left to say it once.
            stubSystemHoldingNobody();
            stubBreakdown(SystemClaimBreakdown.NONE);

            assertThat(readLabelTexts(tooltip.buildBodySections(sectorMock, systemMock)))
                .containsExactly("Unpopulated");
        }

        @Test
        void buildBodySectionsNamesTheDecreeHoldingASystemThatHoldsNobody() {
            // The half the banner does not answer: a decree over a system with nothing in it is a hold
            // the player can read nowhere else in the box, so it survives the drop above.
            stubSystemHoldingNobody();
            stubBreakdown(new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of()));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(readLabelTexts(sections))
                .containsExactly("Unpopulated", "Claim:", "The Hegemony");
            assertThat(readLabelRun(readTableRow(sections, DECREED_CLAIM_ROW), MARKED_QUALIFIER_RUN))
                .isEqualTo(new TextSpan("(core)", HIGHLIGHT));
        }

        @Test
        void buildBodySectionsNamesTheSystemsStatusBeforeItsClaimAndInABlockOfItsOwn() {
            // A dead system names its state first, so the claim below reads as a hold over an empty
            // system rather than over a colony - and parted from it, since the two answer different
            // questions.
            var statusRow = stubSystemHoldingNobody();

            stubBreakdown(new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of()));

            var sections = tooltip.buildBodySections(sectorMock, systemMock);
            var statusSection = 0;
            var claimSectionUnderTheStatus = 1;

            assertThat(sections.get(statusSection).readRowsInOrder())
                .containsExactly(statusRow);
            assertThat(readLabelTextRun(
                    sections.get(claimSectionUnderTheStatus).readRowsInOrder().get(0),
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
                () -> SystemStatusRow.resolveStatusRow(sectorMock, systemMock, ColonyVisibility.BASE_FOG));
        }

        @Test
        void buildBodySectionsJudgesTheSystemEmptyUnderTheDevRevealWhileItIsOn() {
            // The reveal is read live off the same toggle the faction layer samples, so a player who
            // has turned it on is not told two different things by two layers about one system.
            visibilityOverridesMock
                .when(MapVisibilityOverrides::readFromLunaSettings)
                .thenReturn(new MapVisibilityOverrides(UNDER_THE_REVEAL, false));

            stubBreakdown(SystemClaimBreakdown.NONE);

            tooltip.buildBodySections(sectorMock, systemMock);

            statusRowMock.verify(
                () -> SystemStatusRow.resolveStatusRow(sectorMock, systemMock, UNDER_THE_REVEAL));
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

    @Nested
    class ResolveAccountEntries {

        @Test
        void resolveAccountEntriesHangsNothingBeneathAFaction() {
            // What keeps the glance a glance: this box answers who claims the system on the score
            // alone, so every faction it lists reads as its line alone. The markets behind those
            // scores are the counterpart's, an F1 away.
            assertThat(tooltip.resolveAccountEntries(
                    new SystemClaimBreakdown(null, HEGEMONY, List.of()),
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true)))
                .isEmpty();
        }
    }

    @Nested
    class ResolveExpandedDetailName {

        @Test
        void resolveExpandedDetailNameOffersTheAccountBehindAScoredStanding() {
            // What the key at the foot of the box offers the player, in their words. Answered for
            // the pair at once because it is the one thing they agree on - the counterpart accounts
            // for the very scores the ordinary box states.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true))));

            assertThat(tooltip.resolveExpandedDetailName(sectorMock, systemMock))
                .contains("score contributions");
        }

        @Test
        void resolveExpandedDetailNameOffersTheAccountBehindAPresenceTheContestNeverWeighed() {
            // Such a faction's colonies are exactly what the player can read nowhere else in the box,
            // its line stating a nought and nothing more - so the key has something to open even
            // where the mechanic weighed the whole system at nothing.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                null,
                List.of(buildPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL))));

            assertThat(tooltip.resolveExpandedDetailName(sectorMock, systemMock))
                .contains("score contributions");
        }

        @Test
        void resolveExpandedDetailNameOffersNothingWhereTheProjectionListsNobody() {
            // The counterpart accounts for the factions this box lists, and the fog has left it
            // listing none. Both boxes would state the same claim line, so the key would do nothing
            // the player could see - and a hint over it would advertise that it would.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                null,
                List.of(buildUnfoundPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL))));

            assertThat(tooltip.resolveExpandedDetailName(sectorMock, systemMock))
                .isEmpty();
        }
    }

    @Nested
    class ResolveExpandedVariant {

        @Test
        void resolveExpandedVariantOffersTheColoniesBehindTheStandings() {
            // The ordinary box answers who claims the system; the detail mode has a fuller answer to
            // offer, so this box opts into it by naming a counterpart rather than by branching on a
            // mode of its own.
            assertThat(tooltip.resolveExpandedVariant())
                .containsInstanceOf(ExpandedSystemClaimTooltip.class);
        }

        @Test
        void resolveExpandedVariantAnswersAContestThroughThisBoxsOwnReader() {
            // A contest read one way on the glance and another on the detail would answer one hover two
            // ways, an F1 apart, so the counterpart is built on this box's reader rather than reaching
            // for the layer's shared one.
            var expandedVariant = (PoliticalMapCellTooltip) tooltip.resolveExpandedVariant().get();

            assertThat(expandedVariant.claimBreakdownReader)
                .isSameAs(claimBreakdownReaderFake);
        }
    }

    // Hands the tooltip the contest it is about, standing in for the market walk that would otherwise
    // have to be driven through a live economy to produce it.
    private void stubBreakdown(SystemClaimBreakdown breakdown) {
        claimBreakdownReaderFake.setBreakdown(SYSTEM_ID, breakdown);
    }

    // Puts the hovered system among the ones holding nobody, the state the status seam answers with a
    // banner. Returned so a case about where that banner sits can assert on the very row it stubbed.
    private TooltipRow.CentredRow stubSystemHoldingNobody() {
        
        var statusRow = CellTooltipRows.buildBannerRow(null, "Unpopulated");

        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), any()))
            .thenReturn(Optional.of(statusRow));

        return statusRow;
    }

    // The box read top to bottom as the words a player sees, headings and entries alike - the shape
    // most of these cases are about, which asserting block by block would bury. How those lines are
    // grouped is the subject of one case of its own.
    //
    // Read as each line's opening words rather than as its first run, since a faction line opens on its
    // crest - so one expected list covers a box mixing crested faction lines with markless headings.
    private static List<String> readLabelTexts(List<TooltipSection> sections) {
        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .map(CellTooltipRowReads::readOpeningWords)
            .toList();
    }

    private static String readLabelText(List<TooltipSection> sections, int rowIndex) {
        return CellTooltipRowReads.readOpeningWords(
            TooltipSection.readRowsInOrder(sections).get(rowIndex));
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
