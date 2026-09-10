package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

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
 * station) marks nothing.
 *
 * <p>Read off the board of the sector the listener was installed on, which is where it reports and
 * is made fresh with the machinery for each case. That board is also what says the listener
 * reports against the sector it holds rather than against the running game: a mark aimed anywhere
 * else lands on the machinery that sector has not got, and never reaches here.
 */
final class PoliticalMapColonisationListenerTest {

    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private final MapLayerRefreshBoard refreshBoard =
        SectorMapMachineryIndex.installMachineryOn(sectorMock).resolveRefreshBoard();

    private final PoliticalMapColonisationListener listener =
        new PoliticalMapColonisationListener(sectorMock);

    @Nested
    class ReportPlayerColonizedPlanet {

        @Test
        void marksTheColonisedPlanetsSystemStale() {
            // Build the market fully before the planet stub: mockMarketInSystem stubs
            // internally, so nesting it inside when(...).thenReturn(...) would trip
            // Mockito's unfinished-stubbing guard.
            var marketMock = mockMarketInSystem("sys");
            var planetMock = mock(PlanetAPI.class);

            when(planetMock.getId())
                .thenReturn("planet");
            when(planetMock.getMarket())
                .thenReturn(marketMock);

            listener.reportPlayerColonizedPlanet(planetMock);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }

        @Test
        void marksNothingForPlanetWithoutMarket() {

            var planetMock = mock(PlanetAPI.class);

            when(planetMock.getId())
                .thenReturn("planet");
            when(planetMock.getMarket())
                .thenReturn(null);

            listener.reportPlayerColonizedPlanet(planetMock);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void marksNothingForNullPlanet() {

            listener.reportPlayerColonizedPlanet(null);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }
    }

    @Nested
    class ReportPlayerAbandonedColony {

        @Test
        void marksTheAbandonedColonysSystemStale() {

            listener.reportPlayerAbandonedColony(mockMarketInSystem("sys"));

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {

            listener.reportPlayerAbandonedColony(mockUnseatedMarket());

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {

            listener.reportPlayerAbandonedColony(null);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .isEmpty();
        }
    }
}
