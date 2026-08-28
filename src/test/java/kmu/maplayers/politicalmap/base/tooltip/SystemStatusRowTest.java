package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.colonies.SystemColonies;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.visibility.ColonyKnowledge;
import kmu.maplayers.base.visibility.ColonyVisibility;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static kmu.maplayers.DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static kmu.maplayers.base.visibility.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildAbandonedStationMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link SystemStatusRow}: a populated system yields no status at all, an empty one names itself
 * Decivilised or Unpopulated depending on whether a colony there has collapsed, and the
 * reveal decides whether an undiscovered colony already counts as populating the system. Both arms of
 * the known projection are pinned - a found base populates its system however concealed it stays, and
 * an open colony does so before it is reached - along with the shape that satisfies neither, since a
 * gate reading only one of the two would answer one of those cases backwards and either print
 * "Unpopulated" over a settled cell or make a missing line the tell that a base is hiding. The row's
 * shape is pinned too - a banner set across the box, carrying its words and nothing else - since that
 * is what lets it read as a statement about the whole system rather than as an entry of a list.
 *
 * <p>The derelict pair is what pins <em>which</em> question the line answers. A hulk passes the fog
 * outright, so a row wired to the listing beneath it would call a system of wrecks populated - and
 * would answer identically on every other case here, none of which stages one.
 */
final class SystemStatusRowTest {

    // The status line is one plain run, so its label is read at the first of them.
    private static final int STATUS_RUN = 0;

    // The size the staged hulk carries. Nothing the status line reads weighs a colony, so a case
    // varying this would vary nothing the line can see - it is here because the shared builder
    // states a size for the weighing suites that share it.
    private static final int DERELICT_SIZE = 4;

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

            assertThat(resolveStatusRowIn(sector, system, BASE_FOG))
                .isEmpty();
        }

        @Test
        void resolveStatusRowNamesAnEmptySystemUnpopulated() {

            var system = buildSystemWithPlanets();
            var row = resolveStatusRowIn(buildSectorHoldingMarkets(system), system, BASE_FOG);

            assertThat(readLabelTextRun(row.orElseThrow(), STATUS_RUN).text())
                .isEqualTo("Unpopulated");
        }

        @Test
        void resolveStatusRowNamesASystemWithARevealedRuinDecivilised() {

            // The ruin is a colony the set holds and nobody is living on, so it reaches this line
            // through habitation like any other - and what it changes is which status the player is
            // told, the system holding nobody either way.
            var system = buildSystemWithPlanets();

            placeRevealedDecivilisedPlanetIn(system);

            var row = resolveStatusRowIn(buildSectorHoldingMarkets(system), system, BASE_FOG);

            assertThat(readLabelTextRun(row.orElseThrow(), STATUS_RUN).text())
                .isEqualTo("Decivilised");
        }

        @Test
        void resolveStatusRowIsEmptyForALivingColonyBesideARuin() {
            // A ruin never heads a system somebody still lives in. Habitation admits both, so the
            // line has to part them by kind rather than by the projection's emptiness - and the box
            // beneath goes on naming the collapsed colony along with the governed one.
            var system = buildSystemWithPlanets();

            placeRevealedDecivilisedPlanetIn(system);

            var sector = buildSectorHoldingMarkets(system, buildColony());

            assertThat(resolveStatusRowIn(sector, system, BASE_FOG))
                .isEmpty();
        }

        @Test
        void resolveStatusRowSetsTheStatusAcrossTheBoxAsWordsAlone() {
            // Centred and crestless: the status holds over everything the box goes on to say, so it
            // speaks for the box rather than aligning to the crest gutter and value column the entries
            // below it share. Being a centred line is what makes it carry no such columns at all.
            var system = buildSystemWithPlanets();
            var row = resolveStatusRowIn(buildSectorHoldingMarkets(system), system, BASE_FOG)
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

            assertThat(resolveStatusRowIn(sector, system, BASE_FOG))
                .isEmpty();
        }

        @Test
        void resolveStatusRowCallsASystemEmptyWhereItsOnlyColonyIsUndiscoveredHoweverPubliclyListed() {
            // The pair above and this one are the two halves hiddenness and discovery come apart
            // on, and the fog reads only the second: a raided base stays concealed and counts,
            // while a colony the game lists publicly does not until its entity is found. The cell
            // beneath the box withholds it on the same rule, so the two still agree.
            var system = buildSystemWithPlanets();
            var sector = buildSectorHoldingMarkets(system, buildUndiscoveredListedColony());

            var row = resolveStatusRowIn(sector, system, BASE_FOG)
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

            assertThat(resolveStatusRowIn(sector, system, BASE_FOG))
                .isEmpty();
        }

        @Test
        void resolveStatusRowCallsASystemHoldingOnlyADerelictUnpopulated() {
            // The line asks about habitation, not about what may be named. The hulk passes the fog
            // outright - un-hidden, on a found entity - so the listing beneath this row goes on
            // naming it, and the row still says nobody lives here, which is the true reading of a
            // system with one wreck in it.
            var system = buildSystemWithPlanets();
            var sector = buildSectorHoldingMarkets(system);

            // Through the entity side, as a vanilla hulk arrives: the economy never registers one,
            // so listing it would pose a market the sector does not hold.
            hangMarketsOnSystemEntities(system, buildAbandonedStationMarket(DERELICT_SIZE));

            var row = resolveStatusRowIn(sector, system, BASE_FOG)
                .orElseThrow();

            assertThat(readLabelTextRun(row, STATUS_RUN).text())
                .isEqualTo("Unpopulated");
        }

        @Test
        void resolveStatusRowIsEmptyForAColonyStandingBesideADerelict() {
            // The same hulk with somebody settled beside it. The row reads populated on the
            // colony's account while the box beneath names both, so the derelict is neither
            // counted as habitation nor withheld from the listing.
            var system = buildSystemWithPlanets();
            var sector = buildSectorHoldingMarkets(system, buildColony());

            hangMarketsOnSystemEntities(system, buildAbandonedStationMarket(DERELICT_SIZE));

            assertThat(resolveStatusRowIn(sector, system, BASE_FOG))
                .isEmpty();
        }

        @Test
        void resolveStatusRowCountsAnUndiscoveredColonyUnderTheReveal() {
            // The same system reads populated or empty purely on the reveal, so a body showing all
            // factions never contradicts itself with an "Unpopulated" line above the factions it lists.
            var system = buildSystemWithPlanets();
            var sector = buildSectorHoldingMarkets(system, buildUndiscoveredColony());

            assertThat(resolveStatusRowIn(sector, system, UNDER_THE_REVEAL))
                .isEmpty();
            assertThat(resolveStatusRowIn(sector, system, BASE_FOG))
                .isPresent();
        }
    }

    // The status the box resolves for one system: its colonies walked once, read under the rule a
    // case poses against that same sector's record of what has been seen.
    //
    // Paired here rather than at each case because the walk and the knowledge have to come out of
    // one sector: two builders called in one expression would hand the row one sector's colonies
    // and another's observations, and every gate case would then pass for the wrong reason.
    private static Optional<TooltipRow.CentredRow> resolveStatusRowIn(
            SectorAPI sector,
            StarSystemAPI system,
            ColonyVisibility rule) {

        return SystemStatusRow.resolveStatusRow(
            readColoniesIn(sector, system),
            ColonyKnowledge.over(sector, rule));
    }

    // The system's colonies, selected as the box drawing this line selects them - the row takes
    // the walk rather than making one, so every case here states which walk it is reading.
    private static Colonies readColoniesIn(SectorAPI sector, StarSystemAPI system) {
        return SystemColonies.readColoniesIn(sector, system);
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

        var sector = buildSectorHoldingMarkets(system);

        hangMarketsOnSystemEntities(system, colony);

        return sector;
    }

    // Hangs markets on the system's own entities without registering any with the economy - the
    // shape vanilla builds a derelict in, and the only way to pose one, since the routine that
    // makes an abandoned station pointedly never lists it.
    //
    // Each entity is wired to carry its market as well as being listed by the system. Both halves
    // are load-bearing: the walk finds an entity through the system and then asks it for a market,
    // so an entity listed without one puts nothing in the colony set at all - and a case posing a
    // market this way would be asserting an empty system while reading as though it posed one.
    private static void hangMarketsOnSystemEntities(StarSystemAPI system, MarketAPI... markets) {

        // The entities finish their own stubbing before the system's opens, so Mockito does not
        // see one stubbing nested inside another.
        var entities = new ArrayList<SectorEntityToken>();

        for (var market : markets) {
            var entityMock = market.getPrimaryEntity();

            when(entityMock.getMarket())
                .thenReturn(market);

            entities.add(entityMock);
        }
        when(system.getAllEntities())
            .thenReturn(entities);
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
    private static MarketAPI buildUndiscoveredListedColony() {
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
}
