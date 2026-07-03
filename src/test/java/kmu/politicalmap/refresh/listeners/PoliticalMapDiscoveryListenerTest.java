package kmu.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.politicalmap.refresh.PoliticalMapRefresh;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapDiscoveryListener}: discovering a market marks its
 * system politics-stale (the same targeted refresh a colony resize uses), while
 * discovering something with no market (a jump point, inert salvage) marks
 * nothing - accessibility is the access watcher's job, not the listener's. The
 * stale set is drained to read it, so each case clears it first.
 */
final class PoliticalMapDiscoveryListenerTest {
    private final PoliticalMapDiscoveryListener listener = new PoliticalMapDiscoveryListener();

    @Nested
    class ReportEntityDiscovered {

        @Test
        void marksSystemStaleWhenDiscoveredEntityHasAMarket() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();
            // Build the market fully before the entity stub: marketInSystem stubs
            // internally, so nesting it inside when(...).thenReturn(...) would trip
            // Mockito's unfinished-stubbing guard.
            var marketMock = marketInSystem("sys");
            var entityMock = mock(SectorEntityToken.class);
            when(entityMock.getMarket()).thenReturn(marketMock);

            listener.reportEntityDiscovered(entityMock);

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketlessEntity() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();
            var beforeGeometry = PoliticalMapRefresh.getGeometryRevision();

            // A jump point has no market; accessibility is judged elsewhere, so the
            // listener marks no system and requests no geometry rebuild.
            listener.reportEntityDiscovered(mock(JumpPointAPI.class));

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).isEmpty();
            assertThat(PoliticalMapRefresh.getGeometryRevision()).isEqualTo(beforeGeometry);
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            PoliticalMapRefresh.drainStalePoliticsSystemIds();
            var marketMock = mock(MarketAPI.class);
            when(marketMock.getStarSystem()).thenReturn(null);
            var entityMock = mock(SectorEntityToken.class);
            when(entityMock.getMarket()).thenReturn(marketMock);

            listener.reportEntityDiscovered(entityMock);

            assertThat(PoliticalMapRefresh.drainStalePoliticsSystemIds()).isEmpty();
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
