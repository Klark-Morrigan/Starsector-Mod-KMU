package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.visibility.ColonyObservation;
import kmu.maplayers.base.visibility.SectorColonySightings;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockUnseatedMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

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
    class ReportMarketChange {

        @Test
        void marksTheMarketsSystemStale() {
            MarketPoliticsRefresh.reportMarketChange(mockMarketInSystem("sys"),
                    "colony resize", "prevSize=3");

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksTheMarketsSystemStaleWithEmptyContext() {
            MarketPoliticsRefresh.reportMarketChange(mockMarketInSystem("sys"),
                    "colony resize", "");

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            MarketPoliticsRefresh.reportMarketChange(mockUnseatedMarket(), "colony resize",
                    "prevSize=3");

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            MarketPoliticsRefresh.reportMarketChange(null, "colony resize", "prevSize=3");

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

                SectorPoliticsFixtures.stubGlobalSector(globalMock, sector);

                MarketPoliticsRefresh.reportMarketChange(
                    findColonyIn(sector),
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

                SectorPoliticsFixtures.stubGlobalSector(globalMock, sector);

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

                SectorPoliticsFixtures.stubGlobalSector(globalMock, sector);

                MarketPoliticsRefresh.recordObservationsIn(null);
            }
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
