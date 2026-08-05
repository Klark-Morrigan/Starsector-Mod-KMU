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

import kmlib.starsector.ui.text.TextSpan;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.List;

import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link SystemStatusRow}: a populated system yields no status at all, an empty one names itself
 * Decivilised or Unpopulated depending on whether the player has seen a dead colony there, and the
 * reveal decides whether an unfound colony already counts as populating the system. Both sides of the
 * discovery gate are pinned - a found base populates its system however concealed it stays, and a
 * listed colony does not until it is reached - because a filter reading hiddenness instead would
 * answer one of those two backwards. The row's
 * shape is pinned too - a banner set across the box, carrying its words and nothing else - since that
 * is what lets it read as a statement about the whole system rather than as an entry of a list.
 */
final class SystemStatusRowTest {

    // The status line is one plain run, so its label is read at the first of them.
    private static final int STATUS_RUN = 0;

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void installStringsAndColours() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();

        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(Color.LIGHT_GRAY);
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

            var system = buildSystemWithPlanets();
            var row = SystemStatusRow.resolveStatusRow(buildSectorHoldingMarkets(system, buildColony()), system, false);

            assertThat(row)
                .isEmpty();
        }

        @Test
        void resolveStatusRowNamesAnEmptySystemUnpopulated() {

            var system = buildSystemWithPlanets();
            var row = SystemStatusRow.resolveStatusRow(buildSectorHoldingMarkets(system), system, false);

            assertThat(readLabelTextRun(row.orElseThrow(), STATUS_RUN).text())
                .isEqualTo("Unpopulated");
        }

        @Test
        void resolveStatusRowNamesASystemWithARevealedRuinDecivilised() {

            // The dead colony is gone from the economy, so the system is empty either way - what the
            // ruin changes is which status the player is told.
            var system = buildSystemWithPlanets(buildRevealedRuin());
            var row = SystemStatusRow.resolveStatusRow(buildSectorHoldingMarkets(system), system, false);

            assertThat(readLabelTextRun(row.orElseThrow(), STATUS_RUN).text())
                .isEqualTo("Decivilised");
        }

        @Test
        void resolveStatusRowSetsTheStatusAcrossTheBoxAsWordsAlone() {
            // Centred and crestless: the status holds over everything the box goes on to say, so it
            // speaks for the box rather than aligning to the crest gutter and value column the entries
            // below it share. Being a centred line is what makes it carry no such columns at all.
            var system = buildSystemWithPlanets();
            var row = SystemStatusRow
                .resolveStatusRow(buildSectorHoldingMarkets(system), system, false)
                .orElseThrow();

            assertThat(row.labelRuns())
                .containsExactly(new TextSpan("Unpopulated", Color.LIGHT_GRAY));
        }

        @Test
        void resolveStatusRowTreatsAFoundConcealedBaseAsPopulatingTheSystem() {
            // Raiding a base never un-hides its market, and the system plainly holds people either
            // way - emptiness is about what the player has seen, not about what is publicly listed.
            var system = buildSystemWithPlanets();
            var sector = buildSectorHoldingMarkets(system, buildFoundConcealedBase());

            assertThat(SystemStatusRow.resolveStatusRow(sector, system, false))
                .isEmpty();
        }

        @Test
        void resolveStatusRowNamesASystemEmptyWhileItsOnlyListedColonyIsUnfound() {
            // The case parting a discovery gate from a known-to-player one, which would admit this
            // colony on its un-hidden arm and quietly report a system the player has never reached.
            var system = buildSystemWithPlanets();
            var sector = buildSectorHoldingMarkets(system, buildUnfoundListedColony());

            assertThat(readLabelTextRun(
                    SystemStatusRow.resolveStatusRow(sector, system, false).orElseThrow(),
                    STATUS_RUN)
                    .text())
                .isEqualTo("Unpopulated");
        }

        @Test
        void resolveStatusRowCountsAnUndiscoveredColonyUnderTheReveal() {
            // The same system reads populated or empty purely on the reveal, so a body showing all
            // factions never contradicts itself with an "Unpopulated" line above the factions it lists.
            var system = buildSystemWithPlanets();
            var sector = buildSectorHoldingMarkets(system, buildUndiscoveredColony());

            assertThat(SystemStatusRow.resolveStatusRow(sector, system, true))
                .isEmpty();
            assertThat(SystemStatusRow.resolveStatusRow(sector, system, false))
                .isPresent();
        }
    }

    private static SectorAPI buildSectorHoldingMarkets(StarSystemAPI system, MarketAPI... markets) {

        var economyMock = mock(EconomyAPI.class);

        when(economyMock.getMarkets(system))
            .thenReturn(List.of(markets));

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getEconomy())
            .thenReturn(economyMock);

        return sectorMock;
    }

    private static StarSystemAPI buildSystemWithPlanets(PlanetAPI... planets) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getPlanets())
            .thenReturn(List.of(planets));

        return systemMock;
    }

    // An owned colony on an already-discovered entity: the plain "this system is populated" case.
    private static MarketAPI buildColony() {

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFaction())
            .thenReturn(mock(FactionAPI.class));

        return marketMock;
    }

    // An owned colony the player has not found: its entity is still discoverable and the market is
    // hidden, so it counts only when the reveal drops the visibility filter.
    private static MarketAPI buildUndiscoveredColony() {
        return buildColonyOnEntity(true, true);
    }

    // A base the player has found and raided: its entity is discovered, yet the market stays hidden
    // for good, since hiddenness is not a discovery state that clears.
    private static MarketAPI buildFoundConcealedBase() {
        return buildColonyOnEntity(false, true);
    }

    // A colony surfaced into the open ahead of being reached: publicly listed, its entity still
    // awaiting discovery - the other half of the pair hiddenness and discovery come apart on.
    private static MarketAPI buildUnfoundListedColony() {
        return buildColonyOnEntity(true, false);
    }

    // The two-axis shape the three named colonies above are points on, kept private so no case is
    // posed as a pair of bare booleans - which of the two is being varied is the whole point here.
    private static MarketAPI buildColonyOnEntity(
            boolean isEntityDiscoverable,
            boolean isHidden) {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.isDiscoverable())
            .thenReturn(isEntityDiscoverable);

        var marketMock = buildColony();

        when(marketMock.getPrimaryEntity())
            .thenReturn(entityMock);
        when(marketMock.isHidden())
            .thenReturn(isHidden);

        return marketMock;
    }

    // A surveyed planet carrying a revealed decivilised condition - the ruin of a colony the player
    // has already seen die.
    private static PlanetAPI buildRevealedRuin() {

        var conditionMock = mock(MarketConditionAPI.class);
        var ruinMock = mock(MarketAPI.class);
        
        when(ruinMock.getSurveyLevel())
            .thenReturn(MarketAPI.SurveyLevel.FULL);

        when(ruinMock.getFirstCondition(Conditions.DECIVILIZED))
            .thenReturn(conditionMock);

        var planetMock = mock(PlanetAPI.class);

        when(planetMock.getMarket())
            .thenReturn(ruinMock);

        return planetMock;
    }
}
