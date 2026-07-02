package kmu.politicalmap;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

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
            PoliticalMapRefresh.drainStalePoliticsSystemIds();

            listener.reportColonySizeChanged(marketInSystem("mkt", "sys"), 3);

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();
            var marketMock = mock(MarketAPI.class);
            when(marketMock.getId()).thenReturn("mkt");
            when(marketMock.getStarSystem()).thenReturn(null);

            listener.reportColonySizeChanged(marketMock, 3);

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();

            listener.reportColonySizeChanged(null, 3);

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).isEmpty();
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
