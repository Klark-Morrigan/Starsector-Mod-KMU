package kmu.maplayers;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;

import kmlib.starsector.colonies.ColonyVisibility;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.tooltip.SystemStatusRow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmlib.starsector.colonies.ColonyVisibility.BASE_FOG;

import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelTextRun;
import static kmu.maplayers.base.visibility.ColonyVisibilityFixtures.UNDER_THE_REVEAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the one claim neither suite either side of it can make: that the cell a system is drawn as
 * and the status line drawn over it answer "does anybody live here" off one rule.
 *
 * <p>The cell classifies through {@link PoliticalMapInhabitation}, the box through
 * {@link SystemStatusRow}, and each has its own suite pinning its own answer. Nothing in that pair
 * stops the two drifting onto different colony projections, which is the state that lets a hover
 * print "Unpopulated" across a system the map beneath the cursor has painted as settled.
 *
 * <p>Integration rather than unit, because the agreement is a property of the shared colony read
 * underneath both - stub it and the wiring this exists to catch is what gets asserted. Only the
 * strings and colours the row is built from are stood in; the sector is read for real.
 *
 * <p>A revealed dead world is the one system both call inhabited while the box still speaks: the
 * status is what names it Decivilised, and its cell is settled because a known ruin is somebody
 * having lived there. Agreement is therefore "a settled system is never called unpopulated", not
 * "a settled system is silent".
 */
final class SystemInhabitationAgreementIntegrationTest {

    // The status line is one plain run, so its label is read at the first of them.
    private static final int STATUS_RUN = 0;

    @BeforeEach
    void installStringsAndColours() {
        // The palette fake owns the settings proxy and the Misc colours the row is built from, so
        // installing it is the whole of the setup a status line needs.
        CellTooltipPaletteFake.installPalette();
    }

    @AfterEach
    void clearStringsAndColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class ClassifyAndReportOneSystem {

        @Test
        void anOpenColonyThePlayerHasNotReachedLeavesTheCellEmptyAndTheStatusSpoken() {
            // Being publicly listed does not make a colony known, so this withholds exactly as a
            // concealed base does - and the point of the case is that both surfaces withhold it
            // together. A cell drawn as settled over a box saying "Unpopulated" would be the
            // disagreement this suite exists to catch, whichever way the fog had drifted.
            var system = buildSystem();
            var sector = buildSectorListing(system, buildOpenUnreachedColony());

            assertThat(isInhabited(sector, system, BASE_FOG))
                .isFalse();
            assertThat(readStatus(sector, system, BASE_FOG))
                .isEqualTo("Unpopulated");
        }

        @Test
        void aColonyTheEconomyDoesNotListSettlesTheCellAndSilencesTheStatus() {
            // Galatia Academy's shape. Both surfaces have to find it through the entity walk, or
            // one of them reports a station flying a faction's colours as belonging to nobody.
            var system = buildSystem();
            var sector = buildSectorHoldingUnlistedMarket(system, buildOpenColony());

            assertThat(isInhabited(sector, system, BASE_FOG))
                .isTrue();
            assertThat(SystemStatusRow.resolveStatusRow(sector, system, BASE_FOG))
                .isEmpty();
        }

        @Test
        void aDerelictLeavesTheCellEmptyAndTheStatusSpokenThoughTheBoxMayNameIt() {
            // The one shape the two surfaces answer alike about while the listing beside them says
            // something else. The hulk passes the fog outright, so a cell or a status wired to the
            // listing would call a system of wrecks settled - and the pair only agrees here because
            // both read habitation.
            var system = buildSystem();
            var sector = buildSectorHoldingUnlistedMarket(system, buildAbandonedStation());

            assertThat(isInhabited(sector, system, BASE_FOG))
                .isFalse();
            assertThat(readStatus(sector, system, BASE_FOG))
                .isEqualTo("Unpopulated");
        }

        @Test
        void aFoundConcealedBaseSettlesTheCellAndSilencesTheStatus() {
            // Raiding a base never un-hides its market, so a rule reading public listing alone
            // would call a system the player has stood in empty.
            var system = buildSystem();
            var sector = buildSectorListing(system, buildFoundConcealedBase());

            assertThat(isInhabited(sector, system, BASE_FOG))
                .isTrue();
            assertThat(SystemStatusRow.resolveStatusRow(sector, system, BASE_FOG))
                .isEmpty();
        }

        @Test
        void anUnfoundConcealedBaseLeavesTheCellEmptyAndTheStatusSpoken() {
            // The shape the fog keeps back. Both surfaces must withhold it together: a cell drawn
            // as settled, or a status line withheld, is the same tell either way.
            var system = buildSystem();
            var sector = buildSectorListing(system, buildUnfoundConcealedBase());

            assertThat(isInhabited(sector, system, BASE_FOG))
                .isFalse();
            assertThat(readStatus(sector, system, BASE_FOG))
                .isEqualTo("Unpopulated");
        }

        @Test
        void anUnfoundConcealedBaseSettlesTheCellAndSilencesTheStatusUnderTheReveal() {
            // One toggle reaching both reads, so a map showing every faction never carries a box
            // calling one of the systems it has just filled in empty.
            var system = buildSystem();
            var sector = buildSectorListing(system, buildUnfoundConcealedBase());

            assertThat(isInhabited(sector, system, UNDER_THE_REVEAL))
                .isTrue();
            assertThat(SystemStatusRow.resolveStatusRow(sector, system, UNDER_THE_REVEAL))
                .isEmpty();
        }

        @Test
        void aRevealedDeadWorldSettlesTheCellAndNamesItselfDecivilised() {
            // The deliberate exception: settled and still spoken for. A known ruin is somebody
            // having lived there, and the line is what says which of the two the system is.
            var system = buildSystemWithRevealedRuin();
            var sector = buildSectorListing(system);

            assertThat(isInhabited(sector, system, BASE_FOG))
                .isTrue();
            assertThat(readStatus(sector, system, BASE_FOG))
                .isEqualTo("Decivilised");
        }

        @Test
        void aSystemHoldingNobodyLeavesTheCellEmptyAndTheStatusSpoken() {

            var system = buildSystem();
            var sector = buildSectorListing(system);

            assertThat(isInhabited(sector, system, BASE_FOG))
                .isFalse();
            assertThat(readStatus(sector, system, BASE_FOG))
                .isEqualTo("Unpopulated");
        }
    }

    // What the cell is classified on, under the rule the box is asked with - the pairing being the
    // whole point, a case handing the two different rules would prove nothing.
    //
    // Reached through the map's own inhabitation read rather than through the framework rule
    // beneath it, so what a case pins is the classification the rebuild actually makes.
    private static boolean isInhabited(
            SectorAPI sector,
            StarSystemAPI system,
            ColonyVisibility colonyVisibility) {

        return PoliticalMapInhabitation.isSystemInhabited(
            HolderPass.over(sector, colonyVisibility, HolderGrouping.identity()),
            system);
    }

    // The status line's words. Read through orElseThrow rather than defended, since a case
    // reaching here has already stated that a line is expected.
    private static String readStatus(
            SectorAPI sector,
            StarSystemAPI system,
            ColonyVisibility colonyVisibility) {

        var row = SystemStatusRow
            .resolveStatusRow(sector, system, colonyVisibility)
            .orElseThrow();

        return readLabelTextRun(row, STATUS_RUN).text();
    }

    // A sector whose economy lists exactly these markets in the system.
    private static SectorAPI buildSectorListing(StarSystemAPI system, MarketAPI... markets) {

        var economyMock = mock(EconomyAPI.class);

        when(economyMock.getMarkets(system))
            .thenReturn(List.of(markets));

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getEconomy())
            .thenReturn(economyMock);

        return sectorMock;
    }

    // A sector listing nothing in the system, the market hanging on one of the system's own
    // entities instead - what only the entity walk finds, and the only shape a derelict comes in,
    // vanilla never registering one with the economy.
    private static SectorAPI buildSectorHoldingUnlistedMarket(
            StarSystemAPI system,
            MarketAPI colony) {

        // The entity is wired to carry its market, and both finish stubbing before the system's
        // opens, so Mockito sees no stubbing nested inside another.
        var entityMock = colony.getPrimaryEntity();

        when(entityMock.getMarket())
            .thenReturn(colony);

        var sector = buildSectorListing(system);

        when(system.getAllEntities())
            .thenReturn(List.of(entityMock));

        return sector;
    }

    private static StarSystemAPI buildSystem() {
        return mock(StarSystemAPI.class);
    }

    private static StarSystemAPI buildSystemWithRevealedRuin() {

        var planet = buildRevealedRuin();
        var systemMock = buildSystem();

        when(systemMock.getPlanets())
            .thenReturn(List.of(planet));

        return systemMock;
    }

    /** An ordinary colony: owned, open, on an entity the player has reached. */
    private static MarketAPI buildOpenColony() {
        return buildColonyOnEntity(false, false);
    }

    // A colony surfaced into the open ahead of being reached: nothing concealed about the market,
    // its entity still awaiting discovery.
    private static MarketAPI buildOpenUnreachedColony() {
        return buildColonyOnEntity(true, false);
    }

    // A base once raided: its entity is discovered, its market stays hidden for good.
    private static MarketAPI buildFoundConcealedBase() {
        return buildColonyOnEntity(false, true);
    }

    // A base still to be found: concealed and on a discoverable entity, so it fails both arms of
    // the known projection - the one shape the fog has to keep back.
    private static MarketAPI buildUnfoundConcealedBase() {
        return buildColonyOnEntity(true, true);
    }

    // A derelict station: a found, open market held by nobody and carrying vanilla's
    // abandoned-station condition. The condition marks a hulk and the neutral owner parts one from
    // a station somebody keeps, so both are the shape rather than details of it.
    private static MarketAPI buildAbandonedStation() {

        var marketMock = buildColonyOnEntity(false, false);
        var factionMock = marketMock.getFaction();

        when(marketMock.hasCondition(Conditions.ABANDONED_STATION))
            .thenReturn(true);
        when(factionMock.isNeutralFaction())
            .thenReturn(true);

        return marketMock;
    }

    // The two-axis shape the named colonies above are points on, kept private so no case poses
    // itself as a pair of bare booleans - which axis a case varies is the whole of what it says.
    private static MarketAPI buildColonyOnEntity(boolean isEntityDiscoverable, boolean isHidden) {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.isDiscoverable())
            .thenReturn(isEntityDiscoverable);

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getId())
            .thenReturn("hegemony");

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFaction())
            .thenReturn(factionMock);
        when(marketMock.getPrimaryEntity())
            .thenReturn(entityMock);
        when(marketMock.isHidden())
            .thenReturn(isHidden);

        return marketMock;
    }

    // A surveyed planet carrying a revealed decivilised condition - the ruin of a colony the
    // player has already seen die. Its market is the condition-only placeholder, owned by nobody,
    // so no colony read admits it and only the ruin arm reports the system as settled.
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
