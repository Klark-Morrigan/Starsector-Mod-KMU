package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.colonies.ColonyObservation;
import kmlib.starsector.colonies.SectorColonySightings;

import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockUnseatedMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins {@link MarketPoliticsRefresh}, the shared seat guard the politics
 * listeners funnel through: a market seated in a star system marks that system
 * stale, while a null market or one with no star system (a deep-hyperspace
 * station) marks nothing. The stale set is drained to read it, and drained before
 * each case, since the board it lives on is process-wide.
 *
 * <p>The same events write down what the system's inhabitants can see, so that a colony shown on
 * the strength of an observation can say how recent one is. The write is asserted through the
 * register the production reads open, an event that merely reached the recorder having proved
 * nothing about what it recorded.
 */
final class MarketPoliticsRefreshTest {

    private static final int COLONY_SIZE = 5;
    private static final int DERELICT_SIZE = 3;
    private static final String SYSTEM_ID = "kumari_kandam";

    @BeforeEach
    void drainAnyPendingStaleSystems() {
        MapLayerRefresh.drainStaleGroupingSystemIds();
    }

    @Nested
    class MarkSystemStaleForMarket {

        @Test
        void marksTheMarketsSystemStale() {
            MarketPoliticsRefresh.markSystemStaleForMarket(mockMarketInSystem("sys"),
                    "colony resize", "prevSize=3");

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksTheMarketsSystemStaleWithEmptyContext() {
            MarketPoliticsRefresh.markSystemStaleForMarket(mockMarketInSystem("sys"),
                    "colony resize", "");

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            MarketPoliticsRefresh.markSystemStaleForMarket(mockUnseatedMarket(), "colony resize",
                    "prevSize=3");

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            MarketPoliticsRefresh.markSystemStaleForMarket(null, "colony resize", "prevSize=3");

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void recordsWhatTheSystemsInhabitantsCanSeeAsOfTheEvent() {
            // The event is the moment the observation is worth dating: one that waited out the
            // staleness poll would date the sighting by as much as a poll cycle, or miss it
            // where the event is what removes the observer.
            var sector = buildSettledSystemWithADerelict();
            var derelict = findDerelictIn(sector);

            SectorPoliticsFixtures.openSectorMemory(sector);

            try (var globalMock = mockStatic(Global.class)) {

                stubSector(globalMock, sector);

                MarketPoliticsRefresh.markSystemStaleForMarket(
                    seatColonyInItsSystem(sector),
                    "colony resize",
                    "");
            }
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

            try (var globalMock = mockStatic(Global.class)) {

                stubSector(globalMock, sector);

                MarketPoliticsRefresh.recordObservationsIn(
                    SectorPoliticsFixtures.findSystemIn(sector, SYSTEM_ID));
            }
            assertThat(readObservationOf(sector, derelict).locationId())
                .isEqualTo(SYSTEM_ID);
        }

        @Test
        void recordsNothingForAMarketSeatedInNoSystem() {
            // A deep-hyperspace station names no system for a sighting, and a listener handed one
            // must cost the register nothing rather than fault on the way through.
            var sector = buildSettledSystemWithADerelict();

            SectorPoliticsFixtures.openSectorMemory(sector);

            try (var globalMock = mockStatic(Global.class)) {

                stubSector(globalMock, sector);

                MarketPoliticsRefresh.recordObservationsIn(null);
            }
            assertThat(readObservationOf(sector, findDerelictIn(sector)))
                .isNull();
        }
    }

    // One system holding a Hegemony colony and a derelict its people can see - the arrangement the
    // whole write exists for, since a derelict alone is observed by nobody.
    private static SectorAPI buildSettledSystemWithADerelict() {

        var hegemony = SectorPoliticsFixtures.buildFaction("hegemony");

        var sector = SectorPoliticsFixtures.buildSectorWithSystems(
            List.of(hegemony),
            SectorPoliticsFixtures.listSystemMarkets(
                SYSTEM_ID,
                SectorPoliticsFixtures.buildVisibleMarket(hegemony, COLONY_SIZE)));

        SectorPoliticsFixtures.placeMarketsOnSystemEntities(
            SectorPoliticsFixtures.findSystemIn(sector, SYSTEM_ID),
            SectorPoliticsFixtures.buildAbandonedStationMarket(DERELICT_SIZE));

        return sector;
    }

    // The derelict, which stands on one of the system's own entities rather than in its economy.
    private static MarketAPI findDerelictIn(SectorAPI sector) {
        return SectorPoliticsFixtures
            .findSystemIn(sector, SYSTEM_ID)
            .getAllEntities()
            .get(0)
            .getMarket();
    }

    // The colony an economy event names as its subject, seated in its system - which the seat
    // guard reads and the fixture's plain market answers null for.
    private static MarketAPI seatColonyInItsSystem(SectorAPI sector) {

        var system = SectorPoliticsFixtures.findSystemIn(sector, SYSTEM_ID);
        var colony = sector.getEconomy().getMarkets(system).get(0);

        when(colony.getStarSystem())
            .thenReturn(system);

        return colony;
    }

    // The sector every read behind the event reaches, and a logger for the class to open with:
    // the trace line is written where the class is first touched, and a null one there would
    // leave the field null for every later case in the same JVM.
    private static void stubSector(MockedStatic<Global> globalMock, SectorAPI sector) {

        globalMock.when(Global::getSector)
            .thenReturn(sector);
        globalMock.when(() -> Global.getLogger(any(Class.class)))
            .thenReturn(mock(Logger.class));
    }

    private static ColonyObservation readObservationOf(SectorAPI sector, MarketAPI colony) {
        return SectorColonySightings
            .readSightings(sector)
            .readObservation(colony.getId());
    }
}
