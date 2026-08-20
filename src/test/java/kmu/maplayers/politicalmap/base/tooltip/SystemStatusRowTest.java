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

import kmlib.starsector.colonies.ColonyVisibility;
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
import java.util.Set;

import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link SystemStatusRow}: a populated system yields no status at all, an empty one names itself
 * Decivilised or Unpopulated depending on whether the player has seen a dead colony there, and the
 * reveal decides whether an undiscovered colony already counts as populating the system. Both arms of
 * the known projection are pinned - a found base populates its system however concealed it stays, and
 * an open colony does so before it is reached - along with the shape that satisfies neither, since a
 * gate reading only one of the two would answer one of those cases backwards and either print
 * "Unpopulated" over a settled cell or make a missing line the tell that a base is hiding. The row's
 * shape is pinned too - a banner set across the box, carrying its words and nothing else - since that
 * is what lets it read as a statement about the whole system rather than as an entry of a list.
 */
final class SystemStatusRowTest {

    // The status line is one plain run, so its label is read at the first of them.
    // The "show all factions" reveal on: the fog lifted outright, and no gate held, which
    // is the widest rule any surface reads under.
    private static final ColonyVisibility UNDER_THE_REVEAL =
        new ColonyVisibility(true, Set.of());

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
            var sector = buildSectorHoldingMarkets(system, buildColony());

            assertThat(SystemStatusRow.resolveStatusRow(sector, system, ColonyVisibility.BASE_FOG))
                .isEmpty();
        }

        @Test
        void resolveStatusRowNamesAnEmptySystemUnpopulated() {

            var system = buildSystemWithPlanets();
            var row = SystemStatusRow.resolveStatusRow(buildSectorHoldingMarkets(system), system, ColonyVisibility.BASE_FOG);

            assertThat(readLabelTextRun(row.orElseThrow(), STATUS_RUN).text())
                .isEqualTo("Unpopulated");
        }

        @Test
        void resolveStatusRowNamesASystemWithARevealedRuinDecivilised() {

            // The dead colony is gone from the economy, so the system is empty either way - what the
            // ruin changes is which status the player is told.
            var system = buildSystemWithPlanets(buildRevealedRuin());
            var row = SystemStatusRow.resolveStatusRow(buildSectorHoldingMarkets(system), system, ColonyVisibility.BASE_FOG);

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
                .resolveStatusRow(
                    buildSectorHoldingMarkets(system),
                    system,
                    ColonyVisibility.BASE_FOG)
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

            assertThat(SystemStatusRow.resolveStatusRow(sector, system, ColonyVisibility.BASE_FOG))
                .isEmpty();
        }

        @Test
        void resolveStatusRowCallsASystemEmptyWhereItsOnlyColonyIsUnfoundHoweverPubliclyListed() {
            // The pair above and this one are the two halves hiddenness and discovery come apart
            // on, and the fog reads only the second: a raided base stays concealed and counts,
            // while a colony the game lists publicly does not until its entity is found. The cell
            // beneath the box withholds it on the same rule, so the two still agree.
            var system = buildSystemWithPlanets();
            var sector = buildSectorHoldingMarkets(system, buildUnfoundListedColony());

            var row = SystemStatusRow
                .resolveStatusRow(sector, system, ColonyVisibility.BASE_FOG)
                .orElseThrow();

            assertThat(readLabelTextRun(row, STATUS_RUN).text())
                .isEqualTo("Unpopulated");
        }

        @Test
        void resolveStatusRowCountsAColonyTheEconomyDoesNotList() {
            // Galatia Academy's shape: a real colony on a real station that vanilla never
            // registers. Reading the economy's listing alone would call such a system empty while
            // both breakdown boxes below the line name the faction holding it.
            var system = buildSystemWithPlanets();
            var sector = buildSectorHoldingUnlistedColony(system, buildUnlistedColony());

            assertThat(SystemStatusRow.resolveStatusRow(sector, system, ColonyVisibility.BASE_FOG))
                .isEmpty();
        }

        @Test
        void resolveStatusRowCountsAnUndiscoveredColonyUnderTheReveal() {
            // The same system reads populated or empty purely on the reveal, so a body showing all
            // factions never contradicts itself with an "Unpopulated" line above the factions it lists.
            var system = buildSystemWithPlanets();
            var sector = buildSectorHoldingMarkets(system, buildUndiscoveredColony());

            assertThat(SystemStatusRow.resolveStatusRow(sector, system, UNDER_THE_REVEAL))
                .isEmpty();
            assertThat(SystemStatusRow.resolveStatusRow(sector, system, ColonyVisibility.BASE_FOG))
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

    // A sector whose economy lists nothing in the system, the colony hanging on one of the
    // system's own entities instead - what only the entity walk can find.
    private static SectorAPI buildSectorHoldingUnlistedColony(
            StarSystemAPI system,
            MarketAPI colony) {

        // The entity is read off the colony before the system's stubbing opens, so Mockito does
        // not see one stubbing nested inside another.
        var entities = List.of(colony.getPrimaryEntity());
        var sector = buildSectorHoldingMarkets(system);

        when(system.getAllEntities())
            .thenReturn(entities);

        return sector;
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

    // An open colony wired both ways - the market names its entity, the entity carries the market
    // - which is what lets the entity walk find a colony the economy never registered.
    private static MarketAPI buildUnlistedColony() {

        var colonyMock = buildColonyOnEntity(false, false);
        var entityMock = colonyMock.getPrimaryEntity();

        when(entityMock.getMarket())
            .thenReturn(colonyMock);

        return colonyMock;
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
