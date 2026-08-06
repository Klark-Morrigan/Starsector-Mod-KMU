package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.CellTooltipRowReads;
import kmu.maplayers.politicalmap.base.dominance.BaseSizeFactor;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;
import kmu.maplayers.politicalmap.base.dominance.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.base.tooltip.SystemStandingsTooltip.ListedGroup;
import kmu.settings.HiddenMarketScalingChoice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NESTED_MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readTableRow;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.FULL_STABILITY;
import static kmu.maplayers.politicalmap.base.tooltip.StandingsTooltipSeamsFake.VIEW_GROUPING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the detail mode adds: the same ranked groups, each opened up into the colonies behind its
 * score and the factors behind each colony's.
 *
 * <p>The two facts this box alone decides are that a group is listed over its <em>colonies</em> - not
 * its member factions, which is what the ordinary box's line already carries - and that a bloc's
 * colonies are gathered across every faction flying for it, since that is what its score was summed
 * over.
 *
 * <p>The ranking, the status line, the decree, the two headings and the lines naming the blocs belong
 * to the shape both boxes share and are pinned with it ({@link SystemStandingsTooltipTest}); what is
 * stood in for here is everything but the nesting.
 */
final class ExpandedSystemDominationTooltipTest {

    private static final String SYSTEM_ID = "askonia";

    private static final String BLOC_CREST = "graphics/rebel_pact_crest.png";
    private static final String BLOC_SCORE = "9,000";

    // The lines a box with no status and no decree lays out, in draw order.
    private static final int GROUP_HEADER_ROW = 1;
    private static final int FIRST_COLONY_ROW = 2;
    private static final int FIRST_FACTOR_ROW = 3;

    // Stability is left unweighed throughout, so a colony breaks down into the one factor each case is
    // about rather than into a stability line every assertion would have to step over.
    private static final DominanceRules ANY_RULES = new DominanceRules(
        false,
        new BaseSizeWeighting(1.0, HiddenMarketScalingChoice.NORMAL, 2.5, 1.0),
        new StationWeighting(false, 3.0, 0.5, 0.5),
        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    private static final DominancePass ANY_PASS =
        new DominancePass(ANY_RULES, false, VIEW_GROUPING);

    // One bloc of two factions, so a case can tell "gathered across the bloc" from "the first
    // member's colonies".
    private static final GroupStanding ALLIED_BLOC = new GroupStanding(
        "rebel_pact",
        9000,
        List.of(new FactionStanding("hegemony", 6000), new FactionStanding("tritachyon", 3000)));

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();
    private final ExpandedSystemDominationTooltip tooltip =
        new ExpandedSystemDominationTooltip(claimBreakdownReaderFake);

    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    // The one seam this box reaches that the ordinary box does not, so it is stood up here rather than
    // in the shared fixture. CALLS_REAL_METHODS keeps the weight arithmetic beside it live, since the
    // numbers on a factor line are read through the very same class.
    private MockedStatic<KnownMarketFootprints> footprintsMock;

    @BeforeEach
    void installColoursAndTheRankingSeams() {
        
        CellTooltipPaletteFake.installPalette();
        StandingsTooltipSeamsFake.installSeams(ANY_PASS);

        footprintsMock = Mockito.mockStatic(KnownMarketFootprints.class, Mockito.CALLS_REAL_METHODS);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);
    }

    @AfterEach
    void clearColoursAndTheRankingSeams() {
        footprintsMock.close();
        StandingsTooltipSeamsFake.clearSeams();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class BuildBodySections {

        @Test
        void buildBodySectionsListsABlocOverTheColoniesBehindItsScore() {
            // The point of the mode: the group line is the ordinary box's, and what hangs beneath it is
            // where its number came from rather than who it was flying with.
            stubBloc(ALLIED_BLOC);
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            assertThat(readBodyLabelTexts())
                .containsExactly("Dominated by:", "Rebel Pact", "Jangala", "Size");
        }

        @Test
        void buildBodySectionsGathersEveryMemberFactionsColoniesUnderTheBloc() {
            // A bloc's score is the sum over its members' colonies, so the account of it lists all of
            // them ranked against each other rather than only the strongest member's.
            stubBloc(ALLIED_BLOC);
            stubBreakdowns(Map.of(
                "hegemony", List.of(buildBreakdown("Culann", 3.0)),
                "tritachyon", List.of(buildBreakdown("Eventide", 5.0))));

            assertThat(readBodyLabelTexts())
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Eventide",
                    "Size",
                    "Culann",
                    "Size");
        }

        @Test
        void buildBodySectionsStepsAColonyInUnderItsBlocAndAFactorUnderItsColony() {
            // How deep a line sits is what says what it is part of, and three levels read as one flat
            // list would leave a factor looking like a colony of the bloc's.
            stubBloc(ALLIED_BLOC);
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            var rows = readBodyRows();

            assertThat(readTableRow(rows, GROUP_HEADER_ROW).indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
            assertThat(readTableRow(rows, FIRST_COLONY_ROW).indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));
            assertThat(readTableRow(rows, FIRST_FACTOR_ROW).indent())
                .isCloseTo(NESTED_MEMBER_INDENT, within(TOLERANCE));
        }

        @Test
        void buildBodySectionsReadsTheColoniesUnderTheRankingsOwnPass() {
            // The parts have to be read under the rule and reveal the scores above them were ranked
            // through, or the box would explain a number with arithmetic that did not produce it.
            stubBloc(ALLIED_BLOC);
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            tooltip.buildBodySections(sectorMock, systemMock);

            footprintsMock.verify(
                () -> KnownMarketFootprints.readBreakdownByFaction(
                    sectorMock,
                    systemMock,
                    ANY_RULES,
                    false));
        }

        @Test
        void buildBodySectionsWalksTheEconomyOnceHoweverManyBlocsHoldTheSystem() {
            // The colonies of every bloc come out of one walk: read per bloc, two of them could be
            // explained from different reads of the same economy, and the walk itself is the most
            // expensive thing a hover does.
            stubBloc(ALLIED_BLOC, ALLIED_BLOC);
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            tooltip.buildBodySections(sectorMock, systemMock);

            // Counted on the real arguments rather than on matchers, since registering the stub is
            // itself an invocation and an any()-matched count would take it for a second read.
            footprintsMock.verify(
                () -> KnownMarketFootprints.readBreakdownByFaction(
                    sectorMock,
                    systemMock,
                    ANY_RULES,
                    false),
                Mockito.times(1));
        }

        @Test
        void buildBodySectionsFallsBackToTheSystemStatusWhenNothingRanks() {
            // An empty system says the same thing in either mode: there is no more detail to be had
            // about a system nobody holds.
            var statusRow = StandingsTooltipSeamsFake.stubStatusRow("Unpopulated");

            stubBloc();
            stubBreakdowns(Map.of());

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(0).rows())
                .containsExactly(statusRow);
        }
    }

    // Hands the box the standings it is about, each already paired with the crested line the resolver
    // would have named it with - which is how the shared shape hands a group over.
    private void stubBloc(GroupStanding... standings) {
        StandingsTooltipSeamsFake.stubListedGroups(Stream
            .of(standings)
            .map(standing -> new ListedGroup(
                CellTooltipEntry.createEntry(
                    CellTooltipEntryLine.createLine(BLOC_CREST, "Rebel Pact", BLOC_SCORE)),
                standing))
            .toArray(ListedGroup[]::new));
    }

    // Stands the economy read in as the colonies each faction holds in the system, so no case needs a
    // live economy to produce parts for the box to open up.
    private void stubBreakdowns(Map<String, List<MarketWeightBreakdown>> breakdownsByFactionId) {
        footprintsMock
            .when(() -> KnownMarketFootprints.readBreakdownByFaction(
                any(),
                any(),
                any(),
                anyBoolean()))
            .thenReturn(breakdownsByFactionId);
    }

    // One colony worth the given size points on its base size alone, so a case states a colony by the
    // one number it is ranked against its siblings by.
    private static MarketWeightBreakdown buildBreakdown(String marketName, double contribution) {
        return new MarketWeightBreakdown(
            marketName,
            false,
            FULL_STABILITY,
            new BaseSizeFactor(4, 4.0, contribution, 0.0),
            Optional.empty(),
            Optional.empty());
    }

    private List<String> readBodyLabelTexts() {
        return readBodyRows()
            .stream()
            .map(CellTooltipRowReads::readOpeningWords)
            .toList();
    }

    private List<TooltipRow> readBodyRows() {
        return TooltipSection.readRowsInOrder(tooltip.buildBodySections(sectorMock, systemMock));
    }
}
