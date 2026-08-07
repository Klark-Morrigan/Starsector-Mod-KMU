package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.FactionClaimScore;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.CellTooltipRowReads;
import kmu.maplayers.base.tooltip.CellTooltipRows;
import kmu.maplayers.politicalmap.base.PoliticalMapDevToggles;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture.buildStandingOnOneMarket;
import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.readLabelTexts;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the detail mode adds to the claims box: every faction it lists opened up into the colonies
 * that faction holds the system with, and each colony into the terms its claim score was summed from.
 *
 * <p>The one fact this box alone decides is that a faction is accounted for by the colonies its own
 * standing was read from - the thing the line above it cannot say, since a standing is one colony's
 * score and the faction may hold several. Where those colonies then hang, and which of them leads, is
 * the resolver's ({@link ClaimScoreRowResolverTest}); the claimant, the decree, the three headings and
 * the lines naming the factions belong to the shape both claim boxes share, and are pinned through the
 * ordinary one ({@link SystemClaimTooltipTest}).
 */
final class ExpandedSystemClaimTooltipTest {

    private static final String SYSTEM_ID = "askonia";

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    // A line's label is one run, except the claim line of a decreed system, which continues into the
    // marker calling the decree out.
    private static final int LABEL_RUN = 0;
    private static final int MARKER_RUN = 1;

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
    // market posed takes the head of the listing.
    private static final int FIRST_LISTED = 1;

    private static final boolean IS_TERRITORIAL = true;

    // A system the contest itself settled - no decree over it - which is the state an account is
    // ordinarily resolved under and the one in which the strongest market is called out.
    private static final SystemClaimBreakdown CONTESTED_SYSTEM =
        new SystemClaimBreakdown(null, HEGEMONY, List.of());

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();
    private final ExpandedSystemClaimTooltip tooltip =
        new ExpandedSystemClaimTooltip(claimBreakdownReaderFake);

    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private MockedStatic<SystemStatusRow> statusRowMock;
    private MockedStatic<PoliticalMapDevToggles> devTogglesMock;

    @BeforeEach
    void installStringsColoursAndTheSystemStatusSeam() {

        StarsectorSettingsFake.installSettings();
        CellTooltipPaletteFake.installPalette();

        // The status line has its own suite and would otherwise demand a live economy here, so it is
        // stood in as absent - a populated system - for every case but the one about it.
        statusRowMock = Mockito.mockStatic(SystemStatusRow.class);
        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), anyBoolean()))
            .thenReturn(Optional.empty());

        // The reveal is a live LunaLib read, unreachable from the test JVM; stood in as off, the state
        // every case here is posed under.
        devTogglesMock = Mockito.mockStatic(PoliticalMapDevToggles.class);
        devTogglesMock
            .when(PoliticalMapDevToggles::readFromLunaSettings)
            .thenReturn(PoliticalMapDevToggles.NONE);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);

        stubFaction(sectorMock, HEGEMONY, "The Hegemony", "graphics/hegemony_crest.png");
        stubFaction(sectorMock, TRITACHYON, "Tri-Tachyon", null);
    }

    @AfterEach
    void clearStringsColoursAndTheSystemStatusSeam() {

        devTogglesMock.close();
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
            var standing = new FactionClaimScore(
                HEGEMONY,
                IS_TERRITORIAL,
                buildMarket("Chicomoztoc", TOP_SCORE),
                List.of(buildMarket("Culann", LESSER_SCORE)));

            assertThat(readLabelTexts(tooltip.resolveAccountEntries(CONTESTED_SYSTEM, standing)))
                .containsExactly("Chicomoztoc", "Culann");
        }

        @Test
        void resolveAccountEntriesBreaksEachMarketDownIntoItsTerms() {
            // A market's own line is a sum too, so the account goes one level further: the terms that
            // built its score hang beneath it rather than the number being left to be taken on trust.
            var entries = tooltip.resolveAccountEntries(
                CONTESTED_SYSTEM,
                buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL));

            assertThat(readLabelTexts(entries.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void resolveAccountEntriesCallsOutTheStrongestMarketOfAContestedSystem() {
            // The box's half of the rule: it reads how the system was settled off the very contest it
            // is drawing, so the call-out appears exactly where the contest decided something.
            var entries = tooltip.resolveAccountEntries(
                CONTESTED_SYSTEM,
                buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL));

            assertThat(entries.get(0).line().qualifierText())
                .isEqualTo("strongest");
        }

        @Test
        void resolveAccountEntriesCallsOutNoMarketOfASystemHeldByDecree() {
            // The other half: a decree took the system before any market was weighed, so no market's
            // score decided anything and none is called out for it.
            var entries = tooltip.resolveAccountEntries(
                new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of()),
                buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL));

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
        void buildBodySectionsKeepsTheDecreeMarkerOnTheClaimLine() {
            // The decree is what took the system, and it is no less true of the box the player asked
            // for detail from - a marker present in one mode and gone in the other would read as the
            // detail having disproved it.
            stubBreakdown(new SystemClaimBreakdown(
                HEGEMONY,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL))));

            var claimRow = readTableRow(tooltip.buildBodySections(sectorMock, systemMock), CLAIM_ROW);

            assertThat(readLabelTextRun(claimRow, LABEL_RUN).text())
                .isEqualTo("The Hegemony");
            assertThat(readLabelRun(claimRow, MARKER_RUN))
                .isEqualTo(new TextSpan(" (core)", HIGHLIGHT));
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
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any(), anyBoolean()))
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

    // A market scoring its size alone, for a standing a case states by the markets under it rather
    // than by the arithmetic inside one. Every one of them heads the listing, since no case here is
    // about a tie or where the economy put anything.
    private static MarketClaimBreakdown buildMarket(String marketName, int marketSize) {
        return new MarketClaimBreakdown(
            marketName,
            FIRST_LISTED,
            marketSize,
            NO_SIBLING_MARKETS,
            OptionalInt.empty());
    }
}
