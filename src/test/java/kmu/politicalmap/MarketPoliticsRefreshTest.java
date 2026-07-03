package kmu.politicalmap;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link MarketPoliticsRefresh}, the shared seat guard the politics
 * listeners funnel through: a market seated in a star system marks that system
 * stale, while a null market or one with no star system (a deep-hyperspace
 * station) marks nothing. The stale set is drained to read it, so each case
 * clears it first.
 */
final class MarketPoliticsRefreshTest {

    @Nested
    class MarkSystemStaleForMarket {

        @Test
        void marksTheMarketsSystemStale() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();

            MarketPoliticsRefresh.markSystemStaleForMarket(marketInSystem("mkt", "sys"),
                    "colony resize", "prevSize=3");

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).containsExactly("sys");
        }

        @Test
        void marksTheMarketsSystemStaleWithEmptyContext() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();

            MarketPoliticsRefresh.markSystemStaleForMarket(marketInSystem("mkt", "sys"),
                    "colony resize", "");

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();
            var marketMock = mock(MarketAPI.class);
            when(marketMock.getStarSystem()).thenReturn(null);

            MarketPoliticsRefresh.markSystemStaleForMarket(marketMock, "colony resize",
                    "prevSize=3");

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();

            MarketPoliticsRefresh.markSystemStaleForMarket(null, "colony resize", "prevSize=3");

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
