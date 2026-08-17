package kmu.maplayers.politicalmap.base.refresh;

import kmu.maplayers.base.refresh.MapLayerRefresh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockUnseatedMarket;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link MarketPoliticsRefresh}, the shared seat guard the politics
 * listeners funnel through: a market seated in a star system marks that system
 * stale, while a null market or one with no star system (a deep-hyperspace
 * station) marks nothing. The stale set is drained to read it, and drained before
 * each case, since the board it lives on is process-wide.
 */
final class MarketPoliticsRefreshTest {

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
    }
}
