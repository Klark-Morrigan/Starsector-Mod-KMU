package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
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
 * Pins {@link PoliticalMapDiscoveryListener}: discovering a market marks its
 * system politics-stale (the same targeted refresh a colony resize uses), while
 * discovering something with no market (a jump point, inert salvage) marks
 * nothing - accessibility is the sector watcher's job, not the listener's. The
 * stale set is drained to read it, and drained before each case, since the board
 * it lives on is process-wide.
 */
final class PoliticalMapDiscoveryListenerTest {
    private final PoliticalMapDiscoveryListener listener = new PoliticalMapDiscoveryListener();

    @BeforeEach
    void drainAnyPendingStaleSystems() {
        MapLayerRefresh.drainStaleGroupingSystemIds();
    }

    @Nested
    class ReportEntityDiscovered {

        @Test
        void marksSystemStaleWhenDiscoveredEntityHasAMarket() {
            // Build the market fully before the entity stub: mockMarketInSystem stubs
            // internally, so nesting it inside when(...).thenReturn(...) would trip
            // Mockito's unfinished-stubbing guard.
            var marketMock = mockMarketInSystem("sys");
            var entityMock = mock(SectorEntityToken.class);
            when(entityMock.getMarket()).thenReturn(marketMock);

            listener.reportEntityDiscovered(entityMock);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketlessEntity() {
            var beforeGeometry = MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.GEOMETRY);

            // A jump point has no market; accessibility is judged elsewhere, so the
            // listener marks no system and requests no geometry rebuild.
            listener.reportEntityDiscovered(mock(JumpPointAPI.class));

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
            assertThat(MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.GEOMETRY))
                    .isEqualTo(beforeGeometry);
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            var marketMock = mockUnseatedMarket();
            var entityMock = mock(SectorEntityToken.class);
            when(entityMock.getMarket()).thenReturn(marketMock);

            listener.reportEntityDiscovered(entityMock);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }
}
