package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
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
import java.util.Set;

import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevel.PATROL_DETAILS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the one thing neither tooltip's own suite can: that the faction layer and the claims layer
 * answer "is anyone living here" the same way for one system, under one live reveal.
 *
 * <p>Each box resolves its status through a different route - the faction layer off the pass it
 * ranks under, the claims layer off a direct settings read - and each suite verifies only its own
 * route. That leaves the two free to drift apart onto different toggles while both suites stay
 * green, which is exactly the state this pair was in before: one layer calling a system
 * unpopulated while the other, a keystroke away, said nothing about it.
 *
 * <p>Integration rather than unit, because the agreement is a property of the whole chain - the
 * dev toggle, the pass built from it, the shared status row, and the discovery filter underneath -
 * and stubbing any link of it would assert the wiring this exists to catch. Only the two LunaLib
 * reads and the ranking either side of the status line are stood in; the reveal travels for real.
 */
final class PoliticalMapStatusRevealIntegrationTest {

    // The status line is one plain run, so its label is read at the first of them.
    private static final int STATUS_RUN = 0;

    // The status banner heads the body, so the section carrying it is the first of them.
    private static final int STATUS_SECTION = 0;

    // Weighting the ranking never reaches, since the standings are stood in - present only because
    // a pass cannot be built without one.
    private static final DominanceRules ANY_RULES = new DominanceRules(false,
        new BaseSizeWeighting(1.0, null, 1.0, 1.0),
        new StationWeighting(false, 1.0, 0.5, 0.5),
        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();
    private final SystemClaimTooltip claimTooltip =
        new SystemClaimTooltip(claimBreakdownReaderFake, HolderGrouping::identity);
    private final SystemDominationTooltip dominationTooltip =
        new SystemDominationTooltip(claimBreakdownReaderFake, HolderGrouping::identity);

    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    private MockedStatic<MapVisibilityRules> visibilityRulesMock;
    private MockedStatic<DominanceRules> dominanceRulesMock;
    private MockedStatic<PoliticalMapViewRegistry> viewRegistryMock;
    private MockedStatic<SystemStandings> standingsMock;

    @BeforeEach
    void installSettingsAndTheLunaReads() {
        // The palette fake owns the settings proxy and the Misc colours the rows are built from, so
        // installing it is the whole of the colour setup - a second Misc mock would collide.
        CellTooltipPaletteFake.installPalette();

        // The two LunaLib reads, unreachable from the test JVM. The reveal is deliberately NOT
        // among them for the faction layer - it reaches the toggle through the pass it builds, so
        // standing that read in would erase the very link under test.
        dominanceRulesMock = Mockito.mockStatic(DominanceRules.class);
        dominanceRulesMock
            .when(DominanceRules::readFromLunaSettings)
            .thenReturn(ANY_RULES);

        visibilityRulesMock = Mockito.mockStatic(MapVisibilityRules.class);
        setReveal(false);

        var viewMock = mock(PoliticalMapView.class);

        when(viewMock.resolveGrouping())
            .thenReturn(HolderGrouping.identity());

        viewRegistryMock = Mockito.mockStatic(PoliticalMapViewRegistry.class);
        viewRegistryMock
            .when(PoliticalMapViewRegistry::getActiveView)
            .thenReturn(viewMock);

        // The standings are the faction box's other content and would demand a live economy of
        // their own; stood in as empty, the status line is what both bodies are compared on.
        standingsMock = Mockito.mockStatic(SystemStandings.class);
        standingsMock
            .when(() -> SystemStandings.rankByDominationScore(
                any(StarSystemAPI.class),
                any(DominancePass.class)))
            .thenReturn(List.of());

        when(systemMock.getPlanets())
            .thenReturn(List.of());
    }

    @AfterEach
    void clearSettingsAndTheLunaReads() {
        standingsMock.close();
        viewRegistryMock.close();
        visibilityRulesMock.close();
        dominanceRulesMock.close();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class ComposeBody {

        @Test
        void bothLayersCallASystemUnpopulatedWhileItsOnlyBaseIsUnknown() {

            var sector = buildSectorHolding(buildConcealedBase(true));

            // The leak that started this: the claims layer counted the unknown base and drew no
            // status, so its absence told the player a base was hiding there.
            assertThat(readStatus(claimTooltip.composeBody(sector, systemMock, PATROL_DETAILS).sections()))
                .isEqualTo("Unpopulated");
            assertThat(readStatus(dominationTooltip.composeBody(sector, systemMock, PATROL_DETAILS).sections()))
                .isEqualTo("Unpopulated");
        }

        @Test
        void neitherLayerCallsASystemUnpopulatedOnceItsBaseIsFound() {

            var sector = buildSectorHolding(buildConcealedBase(false));

            // Raiding the base never un-hides it, so a filter reading hiddenness would still call
            // this system empty. Both layers must agree it is not.
            assertThat(readStatus(claimTooltip.composeBody(sector, systemMock, PATROL_DETAILS).sections()))
                .isNull();
            assertThat(readStatus(dominationTooltip.composeBody(sector, systemMock, PATROL_DETAILS).sections()))
                .isNull();
        }

        @Test
        void bothLayersFollowTheDevRevealTogether() {

            setReveal(true);

            var sector = buildSectorHolding(buildConcealedBase(true));

            // One toggle, two boxes: the faction layer reaches it through its pass and the claims
            // layer reads it directly, and the same unknown base has to satisfy both.
            assertThat(readStatus(claimTooltip.composeBody(sector, systemMock, PATROL_DETAILS).sections()))
                .isNull();
            assertThat(readStatus(dominationTooltip.composeBody(sector, systemMock, PATROL_DETAILS).sections()))
                .isNull();
        }
    }

    // The status banner's words, or null when the body opens with something other than a status -
    // which for these systems means no status was drawn at all.
    private static String readStatus(List<TooltipSection> sections) {
        if (sections.isEmpty()) {
            return null;
        }
        var firstRow = sections.get(STATUS_SECTION).readRowsInOrder().get(0);

        return firstRow instanceof TooltipRow.CentredRow
            ? readLabelTextRun(firstRow, STATUS_RUN).text()
            : null;
    }

    private void setReveal(boolean shouldIncludeUndiscoveredMarkets) {
        visibilityRulesMock
            .when(MapVisibilityRules::readFromLunaSettings)
            .thenReturn(new MapVisibilityRules(
                new ColonyVisibility(
                    shouldIncludeUndiscoveredMarkets,
                    DecivilisedMarkets.DEFAULT_SURVEY_LEVEL,
                    Set.of()),
                false));
    }

    private SectorAPI buildSectorHolding(MarketAPI market) {

        var economyMock = mock(EconomyAPI.class);

        when(economyMock.getMarkets(systemMock))
            .thenReturn(List.of(market));

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getEconomy())
            .thenReturn(economyMock);

        return sectorMock;
    }

    // A base hidden from the economy for good, on an entity the player has or has not yet reached -
    // the one shape on which hiddenness and discovery come apart.
    private static MarketAPI buildConcealedBase(boolean isEntityDiscoverable) {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.isDiscoverable())
            .thenReturn(isEntityDiscoverable);

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFaction())
            .thenReturn(mock(FactionAPI.class));
        when(marketMock.getPrimaryEntity())
            .thenReturn(entityMock);
        when(marketMock.isHidden())
            .thenReturn(true);

        return marketMock;
    }
}
