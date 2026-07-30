package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.PlanetAPI;

import kmu.maplayers.base.refresh.MapLayerRefresh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockUnseatedMarket;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapColonisationListener}: founding a colony marks the new
 * colony's star system politics-stale and abandoning one marks the dropped
 * colony's system, while a planet or market with no star system (a deep-hyperspace
 * station) marks nothing. The stale set is drained to read it, and drained before
 * each case, since the board it lives on is process-wide.
 */
final class PoliticalMapColonisationListenerTest {
    private final PoliticalMapColonisationListener listener = new PoliticalMapColonisationListener();

    @BeforeEach
    void drainAnyPendingStaleSystems() {
        MapLayerRefresh.drainStaleGroupingSystemIds();
    }

    @Nested
    class ReportPlayerColonizedPlanet {

        @Test
        void marksTheColonisedPlanetsSystemStale() {
            // Build the market fully before the planet stub: mockMarketInSystem stubs
            // internally, so nesting it inside when(...).thenReturn(...) would trip
            // Mockito's unfinished-stubbing guard.
            var marketMock = mockMarketInSystem("sys");
            var planetMock = mock(PlanetAPI.class);
            when(planetMock.getId()).thenReturn("planet");
            when(planetMock.getMarket()).thenReturn(marketMock);

            listener.reportPlayerColonizedPlanet(planetMock);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForPlanetWithoutMarket() {
            var planetMock = mock(PlanetAPI.class);
            when(planetMock.getId()).thenReturn("planet");
            when(planetMock.getMarket()).thenReturn(null);

            listener.reportPlayerColonizedPlanet(planetMock);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullPlanet() {
            listener.reportPlayerColonizedPlanet(null);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }

    @Nested
    class ReportPlayerAbandonedColony {

        @Test
        void marksTheAbandonedColonysSystemStale() {
            listener.reportPlayerAbandonedColony(mockMarketInSystem("sys"));

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            listener.reportPlayerAbandonedColony(mockUnseatedMarket());

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            listener.reportPlayerAbandonedColony(null);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }
}
