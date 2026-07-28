package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.widgets.TooltipLabelPlacement;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link SystemStatusRow}: a populated system yields no status at all, an empty one names itself
 * Decivilised or Unpopulated depending on whether the player has seen a dead colony there, and the
 * reveal decides whether an undiscovered colony already counts as populating the system. The row's
 * shape is pinned too - crestless, scoreless, and flush at no indent - since that is what lets it
 * read as a standalone line under the system-name header rather than as an entry of a list.
 */
final class SystemStatusRowTest {
    private static final float NO_INDENT = 0f;
    private static final float TOLERANCE = 0.001f;

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void installStringsAndColours() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();
        miscMock = Mockito.mockStatic(Misc.class);
        miscMock.when(Misc::getTextColor).thenReturn(Color.LIGHT_GRAY);
    }

    @AfterEach
    void clearStringsAndColours() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class ResolveStatusRow {

        @Test
        void resolveStatusRowIsEmptyForAPopulatedSystem() {
            var system = systemWithPlanets();

            var row = SystemStatusRow.resolveStatusRow(sectorHolding(system, colony()), system, false);

            assertThat(row).isEmpty();
        }

        @Test
        void resolveStatusRowNamesAnEmptySystemUnpopulated() {
            var system = systemWithPlanets();

            var row = SystemStatusRow.resolveStatusRow(sectorHolding(system), system, false);

            assertThat(row.orElseThrow().labelTextSpan().text()).isEqualTo("Unpopulated");
        }

        @Test
        void resolveStatusRowNamesASystemWithARevealedRuinDecivilised() {
            // The dead colony is gone from the economy, so the system is empty either way - what the
            // ruin changes is which status the player is told.
            var system = systemWithPlanets(revealedRuin());

            var row = SystemStatusRow.resolveStatusRow(sectorHolding(system), system, false);

            assertThat(row.orElseThrow().labelTextSpan().text()).isEqualTo("Decivilised");
        }

        @Test
        void resolveStatusRowLaysTheStatusFlushWithoutAnIndentCrestOrScore() {
            var system = systemWithPlanets();

            var row = SystemStatusRow.resolveStatusRow(sectorHolding(system), system, false)
                    .orElseThrow();

            assertThat(row.indent()).isCloseTo(NO_INDENT, within(TOLERANCE));
            assertThat(row.labelPlacement()).isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
            assertThat(row.hasCrest()).isFalse();
            assertThat(row.valueTextSpan().hasText()).isFalse();
        }

        @Test
        void resolveStatusRowCountsAnUndiscoveredColonyUnderTheReveal() {
            // The same system reads populated or empty purely on the reveal, so a body showing all
            // factions never contradicts itself with an "Unpopulated" line above the factions it lists.
            var system = systemWithPlanets();
            var sector = sectorHolding(system, undiscoveredColony());

            assertThat(SystemStatusRow.resolveStatusRow(sector, system, true)).isEmpty();
            assertThat(SystemStatusRow.resolveStatusRow(sector, system, false)).isPresent();
        }
    }

    private static SectorAPI sectorHolding(StarSystemAPI system, MarketAPI... markets) {
        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(system)).thenReturn(List.of(markets));
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        return sectorMock;
    }

    private static StarSystemAPI systemWithPlanets(PlanetAPI... planets) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getPlanets()).thenReturn(List.of(planets));
        return systemMock;
    }

    // An owned colony on an already-discovered entity: the plain "this system is populated" case.
    private static MarketAPI colony() {
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getFaction()).thenReturn(mock(FactionAPI.class));
        return marketMock;
    }

    // An owned colony the player has not found: its entity is still discoverable and the market is
    // hidden, so it counts only when the reveal drops the visibility filter.
    private static MarketAPI undiscoveredColony() {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.isDiscoverable()).thenReturn(true);
        var marketMock = colony();
        when(marketMock.getPrimaryEntity()).thenReturn(entityMock);
        when(marketMock.isHidden()).thenReturn(true);
        return marketMock;
    }

    // A surveyed planet carrying a revealed decivilised condition - the ruin of a colony the player
    // has already seen die.
    private static PlanetAPI revealedRuin() {
        var conditionMock = mock(MarketConditionAPI.class);
        var ruinMock = mock(MarketAPI.class);
        when(ruinMock.getSurveyLevel()).thenReturn(MarketAPI.SurveyLevel.FULL);
        when(ruinMock.getFirstCondition(Conditions.DECIVILIZED)).thenReturn(conditionMock);
        var planetMock = mock(PlanetAPI.class);
        when(planetMock.getMarket()).thenReturn(ruinMock);
        return planetMock;
    }
}
