package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.politicalmap.base.dominance.BaseSizeFactor;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;
import kmu.maplayers.politicalmap.base.dominance.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
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

import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.readLabelTexts;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.FULL_STABILITY;
import static kmu.maplayers.politicalmap.base.tooltip.StandingsTooltipSeamsFake.VIEW_GROUPING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the detail mode adds: every faction the box lists opened up into the colonies it holds the
 * system with, and each colony into the factors its weight was summed from.
 *
 * <p>The two facts this box alone decides are that a faction is accounted for by <em>its own</em>
 * colonies - the one thing a bloc's line above cannot say, since a bloc's score is the sum over its
 * members' - and that the economy behind all of them is read once for the box rather than once per
 * faction.
 *
 * <p>Where those colonies then hang is the shared resolution's ({@link StandingRowResolverTest}), and
 * the ranking, the status line, the decree, the two headings and the lines naming the blocs belong to
 * the shape both boxes share ({@link SystemStandingsTooltipTest}).
 */
final class ExpandedSystemDominationTooltipTest {

    private static final String SYSTEM_ID = "askonia";

    // Stability is left unweighed throughout, so a colony breaks down into the one factor each case is
    // about rather than into a stability line every assertion would have to step over.
    private static final DominanceRules ANY_RULES = new DominanceRules(
        false,
        new BaseSizeWeighting(1.0, HiddenMarketScalingChoice.NORMAL, 2.5, 1.0),
        new StationWeighting(false, 3.0, 0.5, 0.5),
        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    private static final DominancePass ANY_PASS =
        new DominancePass(ANY_RULES, false, VIEW_GROUPING);

    // Two factions of one bloc, so a case can tell "the faction's own colonies" from "the bloc's".
    private static final FactionStanding LEAD_MEMBER = new FactionStanding("hegemony", 6000);
    private static final FactionStanding OTHER_MEMBER = new FactionStanding("tritachyon", 3000);

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
    class CreateFactionAccountResolver {

        @Test
        void createFactionAccountResolverAccountsForAFactionWithTheColoniesItHolds() {
            // The point of the mode, and the one thing a bloc's line cannot state: a faction's score is
            // the sum over the colonies it holds here, so those are what its account lists - and a
            // sibling's colonies are the sibling's account, never this one's.
            stubBreakdowns(Map.of(
                "hegemony", List.of(buildBreakdown("Culann", 3.0)),
                "tritachyon", List.of(buildBreakdown("Eventide", 5.0))));

            var accountResolver =
                tooltip.createFactionAccountResolver(sectorMock, systemMock, ANY_PASS);

            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(LEAD_MEMBER)))
                .containsExactly("Culann");
            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(OTHER_MEMBER)))
                .containsExactly("Eventide");
        }

        @Test
        void createFactionAccountResolverBreaksEachColonyDownIntoItsFactors() {
            // A colony's own line is a sum too, so the account goes one level further: the factors that
            // moved its weight hang beneath it rather than the number being left to be taken on trust.
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            var colonyEntries = tooltip
                .createFactionAccountResolver(sectorMock, systemMock, ANY_PASS)
                .resolveAccountEntries(LEAD_MEMBER);

            assertThat(readLabelTexts(colonyEntries.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void createFactionAccountResolverAccountsForNothingWhereAFactionHoldsNoColonyHere() {
            // Nothing to account for reads as the faction listed by its line alone, which is exactly
            // what an empty answer means to the shape above - not a heading over an empty account.
            stubBreakdowns(Map.of("tritachyon", List.of(buildBreakdown("Eventide", 5.0))));

            assertThat(tooltip
                    .createFactionAccountResolver(sectorMock, systemMock, ANY_PASS)
                    .resolveAccountEntries(LEAD_MEMBER))
                .isEmpty();
        }

        @Test
        void createFactionAccountResolverReadsTheColoniesUnderTheRankingsOwnPass() {
            // The parts have to be read under the rule and reveal the scores above them were ranked
            // through, or the box would explain a number with arithmetic that did not produce it.
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            tooltip.createFactionAccountResolver(sectorMock, systemMock, ANY_PASS);

            footprintsMock.verify(
                () -> KnownMarketFootprints.readBreakdownByFaction(
                    sectorMock,
                    systemMock,
                    ANY_RULES,
                    false));
        }

        @Test
        void createFactionAccountResolverWalksTheEconomyOnceHoweverManyFactionsHoldTheSystem() {
            // Every faction's colonies come out of one walk: read per faction, two of them could be
            // explained from different reads of the same economy, and the walk itself is the most
            // expensive thing a hover does.
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            var accountResolver =
                tooltip.createFactionAccountResolver(sectorMock, systemMock, ANY_PASS);

            accountResolver.resolveAccountEntries(LEAD_MEMBER);
            accountResolver.resolveAccountEntries(OTHER_MEMBER);

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
    }

    @Nested
    class BuildBodySections {

        @Test
        void buildBodySectionsFallsBackToTheSystemStatusWhenNothingRanks() {
            // An empty system says the same thing in either mode: there is no more detail to be had
            // about a system nobody holds.
            var statusRow = StandingsTooltipSeamsFake.stubStatusRow("Unpopulated");

            StandingsTooltipSeamsFake.stubGroupEntries();
            stubBreakdowns(Map.of());

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(0).rows())
                .containsExactly(statusRow);
        }
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
}
