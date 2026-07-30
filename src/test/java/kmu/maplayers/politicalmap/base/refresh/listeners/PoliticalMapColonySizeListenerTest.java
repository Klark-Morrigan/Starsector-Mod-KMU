package kmu.maplayers.politicalmap.base.refresh.listeners;

import kmu.maplayers.base.refresh.MapLayerRefresh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockUnseatedMarket;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link PoliticalMapColonySizeListener}: a colony resize marks its own
 * star system politics-stale, while a resize on a market with no star system (a
 * deep-hyperspace station) marks nothing. The stale set is drained to read it,
 * and drained before each case, since the board it lives on is process-wide.
 */
final class PoliticalMapColonySizeListenerTest {
    private final PoliticalMapColonySizeListener listener = new PoliticalMapColonySizeListener();

    @BeforeEach
    void drainAnyPendingStaleSystems() {
        MapLayerRefresh.drainStaleGroupingSystemIds();
    }

    @Nested
    class ReportColonySizeChanged {

        @Test
        void marksTheResizedColonysSystemStale() {
            listener.reportColonySizeChanged(mockMarketInSystem("sys"), 3);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            listener.reportColonySizeChanged(mockUnseatedMarket(), 3);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            listener.reportColonySizeChanged(null, 3);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }
}
