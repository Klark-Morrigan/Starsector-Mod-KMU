package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.Global;

import kmu.maplayers.base.refresh.MapLayerRefresh;
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
 * Pins {@link PoliticalMapDecivListener}: a completed decivilisation marks its
 * own star system politics-stale (whether or not the colony was fully
 * destroyed), while the pre-deciv phase and a market with no star system (a
 * deep-hyperspace station) mark nothing. The stale set is drained to read it, and
 * drained before each case, since the board it lives on is process-wide.
 *
 * <p>The pre-deciv phase does write one thing, and it is the reason that phase is listened to at
 * all: what the dying colony still vouches for is recorded while it can still be read.
 */
final class PoliticalMapDecivListenerTest {

    private static final int COLONY_SIZE = 5;
    private static final int DERELICT_SIZE = 3;
    private static final String SYSTEM_ID = "kumari_kandam";

    private final PoliticalMapDecivListener listener = new PoliticalMapDecivListener();

    @BeforeEach
    void drainAnyPendingStaleSystems() {
        MapLayerRefresh.drainStaleGroupingSystemIds();
    }

    @Nested
    class ReportColonyDecivilized {

        @Test
        void marksTheDecivilisedColonysSystemStale() {
            listener.reportColonyDecivilized(mockMarketInSystem("sys"), false);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksTheSystemStaleEvenWhenFullyDestroyed() {
            listener.reportColonyDecivilized(mockMarketInSystem("sys"), true);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            listener.reportColonyDecivilized(mockUnseatedMarket(), false);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            listener.reportColonyDecivilized(null, false);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }

    @Nested
    class ReportColonyAboutToBeDecivilized {

        @Test
        void marksNothingSinceTheColonyIsStillFactionOwned() {
            listener.reportColonyAboutToBeDecivilized(mockMarketInSystem("sys"), false);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void recordsWhatTheDyingColonyStillVouchesFor() {
            // The one write that cannot be deferred. The colony is about to stop vouching for the
            // derelict beside it, and once it has there is nobody left to date that observation
            // by - so it is taken here, while the colony can still be read as the observer it is.
            var sector = SectorPoliticsFixtures.buildSystemHoldingAColonyAndADerelict(
                SYSTEM_ID,
                COLONY_SIZE,
                DERELICT_SIZE);

            var system = SectorPoliticsFixtures.findSystemIn(sector, SYSTEM_ID);

            SectorPoliticsFixtures.openSectorMemory(sector);

            try (var globalMock = mockStatic(Global.class)) {

                SectorPoliticsFixtures.stubGlobalSector(globalMock, sector);

                listener.reportColonyAboutToBeDecivilized(
                    sector.getEconomy().getMarkets(system).get(0),
                    false);
            }
            var derelict = system.getAllEntities().get(0).getMarket();

            assertThat(SectorColonySightings
                    .readSightings(sector)
                    .readObservation(derelict.getId())
                    .locationId())
                .isEqualTo(SYSTEM_ID);
        }

        @Test
        void recordsNothingForAMarketSeatedInNoSystem() {
            listener.reportColonyAboutToBeDecivilized(mockUnseatedMarket(), false);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void recordsNothingForNullMarket() {
            listener.reportColonyAboutToBeDecivilized(null, false);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }
}
