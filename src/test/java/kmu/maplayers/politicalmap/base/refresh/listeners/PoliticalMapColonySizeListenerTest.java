package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.base.refresh.MapLayerRefresh;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapColonySizeListener}: a colony resize marks its own
 * star system politics-stale, while a resize on a market with no star system (a
 * deep-hyperspace station) marks nothing. The stale set is drained to read it,
 * so each case clears it first.
 */
final class PoliticalMapColonySizeListenerTest {
    private final PoliticalMapColonySizeListener listener = new PoliticalMapColonySizeListener();

    @Nested
    class ReportColonySizeChanged {

        @Test
        void marksTheResizedColonysSystemStale() {
            MapLayerRefresh.drainStaleGroupingSystemIds();

            listener.reportColonySizeChanged(marketInSystem("mkt", "sys"), 3);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            MapLayerRefresh.drainStaleGroupingSystemIds();
            var marketMock = mock(MarketAPI.class);
            when(marketMock.getId()).thenReturn("mkt");
            when(marketMock.getStarSystem()).thenReturn(null);

            listener.reportColonySizeChanged(marketMock, 3);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            MapLayerRefresh.drainStaleGroupingSystemIds();

            listener.reportColonySizeChanged(null, 3);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }

    private static MarketAPI marketInSystem(String marketId, String systemId) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(systemId);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getId()).thenReturn(marketId);
        when(marketMock.getStarSystem()).thenReturn(systemMock);
        return marketMock;
    }
}
