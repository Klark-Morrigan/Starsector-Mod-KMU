package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.base.refresh.MapLayerRefresh;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapColonisationListener}: founding a colony marks the new
 * colony's star system politics-stale and abandoning one marks the dropped
 * colony's system, while a planet or market with no star system (a deep-hyperspace
 * station) marks nothing. The stale set is drained to read it, so each case clears
 * it first.
 */
final class PoliticalMapColonisationListenerTest {
    private final PoliticalMapColonisationListener listener = new PoliticalMapColonisationListener();

    @Nested
    class ReportPlayerColonizedPlanet {

        @Test
        void marksTheColonisedPlanetsSystemStale() {
            MapLayerRefresh.drainStaleGroupingSystemIds();
            // Build the market fully before the planet stub: marketInSystem stubs
            // internally, so nesting it inside when(...).thenReturn(...) would trip
            // Mockito's unfinished-stubbing guard.
            var marketMock = marketInSystem("sys");
            var planetMock = mock(PlanetAPI.class);
            when(planetMock.getId()).thenReturn("planet");
            when(planetMock.getMarket()).thenReturn(marketMock);

            listener.reportPlayerColonizedPlanet(planetMock);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForPlanetWithoutMarket() {
            MapLayerRefresh.drainStaleGroupingSystemIds();
            var planetMock = mock(PlanetAPI.class);
            when(planetMock.getId()).thenReturn("planet");
            when(planetMock.getMarket()).thenReturn(null);

            listener.reportPlayerColonizedPlanet(planetMock);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullPlanet() {
            MapLayerRefresh.drainStaleGroupingSystemIds();

            listener.reportPlayerColonizedPlanet(null);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }

    @Nested
    class ReportPlayerAbandonedColony {

        @Test
        void marksTheAbandonedColonysSystemStale() {
            MapLayerRefresh.drainStaleGroupingSystemIds();

            listener.reportPlayerAbandonedColony(marketInSystem("sys"));

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            MapLayerRefresh.drainStaleGroupingSystemIds();
            var marketMock = mock(MarketAPI.class);
            when(marketMock.getStarSystem()).thenReturn(null);

            listener.reportPlayerAbandonedColony(marketMock);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            MapLayerRefresh.drainStaleGroupingSystemIds();

            listener.reportPlayerAbandonedColony(null);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }

    private static MarketAPI marketInSystem(String systemId) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(systemId);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getId()).thenReturn("mkt");
        when(marketMock.getStarSystem()).thenReturn(systemMock);
        return marketMock;
    }
}
