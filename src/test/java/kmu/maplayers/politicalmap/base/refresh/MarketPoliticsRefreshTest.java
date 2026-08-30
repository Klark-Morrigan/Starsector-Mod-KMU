package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.visibility.ColonyObservation;
import kmu.maplayers.base.visibility.SectorColonySightings;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockUnseatedMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins {@link MarketPoliticsRefresh}, the shared seat guard the politics
 * listeners funnel through: a market seated in a star system marks that system
 * stale, while a null market or one with no star system (a deep-hyperspace
 * station) marks nothing.
 *
 * <p>Both halves are read off the sector handed in - the board of that sector's installation, and
 * that sector's register - since a report is about the sector its listener was installed on rather
 * than about whichever the player currently has loaded. Machinery is installed on a sector of this
 * suite's own per case, so the board each case reads starts empty.
 *
 * <p>The observation write is asserted through the register the production reads open, an event
 * that merely reached the recorder having proved nothing about what it recorded.
 */
final class MarketPoliticsRefreshTest {

    private static final int COLONY_SIZE = 5;
    private static final int DERELICT_SIZE = 3;
    private static final String SYSTEM_ID = "kumari_kandam";

    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private final MapLayerRefreshBoard refreshBoard =
        MapLayerInstallations.installMachineryOn(sectorMock).resolveRefreshBoard();

    @Nested
    class ReportMarketChange {

        @Test
        void marksTheMarketsSystemStale() {

            MarketPoliticsRefresh.reportMarketChange(
                sectorMock,
                mockMarketInSystem("sys"),
                "colony resize",
                "prevSize=3");

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }

        @Test
        void marksTheMarketsSystemStaleWithEmptyContext() {

            MarketPoliticsRefresh.reportMarketChange(
                sectorMock,
                mockMarketInSystem("sys"),
                "colony resize",
                "");

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {

            MarketPoliticsRefresh.reportMarketChange(
                sectorMock,
                mockUnseatedMarket(),
                "colony resize",
                "prevSize=3");

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {

            MarketPoliticsRefresh.reportMarketChange(
                sectorMock,
                null,
                "colony resize",
                "prevSize=3");

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void marksOnlyTheReportedSectorsBoard() {
            // The defect this argument exists to close: a report belongs to the sector its
            // listener was installed on, so one resolved off the running game would mark whichever
            // sector the player has loaded for an event in another.
            var otherSectorMock = mock(SectorAPI.class);
            var otherInstallation = MapLayerInstallations.installMachineryOn(otherSectorMock);

            MarketPoliticsRefresh.reportMarketChange(
                sectorMock,
                mockMarketInSystem("sys"),
                "colony resize",
                "");

            assertThat(otherInstallation.resolveRefreshBoard().drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void recordsWhatTheSystemsInhabitantsCanSeeAsOfTheEvent() {
            // The event is the moment the observation is worth dating: one that waited out the
            // staleness poll would date the sighting by as much as a poll cycle, or miss it
            // where the event is what removes the observer.
            var sector = buildSettledSystemWithADerelict();
            var derelict = findDerelictIn(sector);

            SectorPoliticsFixtures.openSectorMemory(sector);
            MarketPoliticsRefresh.reportMarketChange(
                sector,
                findColonyIn(sector),
                "colony resize",
                "");

            assertThat(readObservationOf(sector, derelict).locationId())
                .isEqualTo(SYSTEM_ID);
        }
    }

    @Nested
    class RecordObservationsIn {

        @Test
        void recordsTheGatedColoniesTheSystemsInhabitantsCanSee() {
            // The entry point the deciv listener takes before the colony about to die stops
            // vouching for whatever else stands in its system.
            var sector = buildSettledSystemWithADerelict();
            var derelict = findDerelictIn(sector);

            SectorPoliticsFixtures.openSectorMemory(sector);
            MarketPoliticsRefresh.recordObservationsIn(
                sector,
                SectorPoliticsFixtures.findSystemIn(sector, SYSTEM_ID));

            assertThat(readObservationOf(sector, derelict).locationId())
                .isEqualTo(SYSTEM_ID);
        }

        @Test
        void recordsNothingForAMarketSeatedInNoSystem() {
            // A deep-hyperspace station names no system for a sighting, and a listener handed one
            // must cost the register nothing rather than fault on the way through.
            var sector = buildSettledSystemWithADerelict();

            SectorPoliticsFixtures.openSectorMemory(sector);
            MarketPoliticsRefresh.recordObservationsIn(sector, null);

            assertThat(readObservationOf(sector, findDerelictIn(sector)))
                .isNull();
        }

        @Test
        void recordsNothingForNoSector() {
            // A listener installed on nothing - the shape a start-up step reaches when the engine
            // has yet to build a sector - must cost the register nothing rather than fault.
            var sector = buildSettledSystemWithADerelict();

            SectorPoliticsFixtures.openSectorMemory(sector);
            MarketPoliticsRefresh.recordObservationsIn(
                null,
                SectorPoliticsFixtures.findSystemIn(sector, SYSTEM_ID));

            assertThat(readObservationOf(sector, findDerelictIn(sector)))
                .isNull();
        }
    }

    // One system holding a Hegemony colony and a derelict its people can see - the arrangement the
    // whole write exists for, since a derelict alone is observed by nobody.
    private static SectorAPI buildSettledSystemWithADerelict() {

        return SectorPoliticsFixtures.buildSystemHoldingAColonyAndADerelict(
            SYSTEM_ID,
            COLONY_SIZE,
            DERELICT_SIZE);
    }

    // The derelict, which stands on one of the system's own entities rather than in its economy.
    private static MarketAPI findDerelictIn(SectorAPI sector) {

        return SectorPoliticsFixtures
            .findSystemIn(sector, SYSTEM_ID)
            .getAllEntities()
            .get(0)
            .getMarket();
    }

    // The colony an economy event names as its subject.
    private static MarketAPI findColonyIn(SectorAPI sector) {

        return sector
            .getEconomy()
            .getMarkets(SectorPoliticsFixtures.findSystemIn(sector, SYSTEM_ID))
            .get(0);
    }

    private static ColonyObservation readObservationOf(SectorAPI sector, MarketAPI colony) {

        return SectorColonySightings
            .readSightings(sector)
            .readObservation(colony.getId());
    }
}
