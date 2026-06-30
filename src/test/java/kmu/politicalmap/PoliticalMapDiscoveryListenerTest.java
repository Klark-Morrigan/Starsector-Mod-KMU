package kmu.politicalmap;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapDiscoveryListener}: discovering a market requests a
 * content (drawables) refresh, while discovering something with no market (a
 * jump point, inert salvage) does not - accessibility is the access watcher's
 * job, not the listener's. The content counter is global and monotonic, so each
 * case asserts against the revision captured just before it.
 */
final class PoliticalMapDiscoveryListenerTest {
    private final PoliticalMapDiscoveryListener listener = new PoliticalMapDiscoveryListener();

    @Nested
    class ReportEntityDiscovered {

        @Test
        void requestsContentRefreshWhenDiscoveredEntityHasAMarket() {
            var before = PoliticalMapRefresh.getContentRevision();
            var entityMock = mock(SectorEntityToken.class);
            when(entityMock.getMarket()).thenReturn(mock(MarketAPI.class));

            listener.reportEntityDiscovered(entityMock);

            assertThat(PoliticalMapRefresh.getContentRevision()).isGreaterThan(before);
        }

        @Test
        void doesNotRefreshForMarketlessEntity() {
            var beforeContent = PoliticalMapRefresh.getContentRevision();
            var beforeGeometry = PoliticalMapRefresh.getGeometryRevision();

            // A jump point has no market; accessibility is judged elsewhere, so the
            // listener leaves both counters untouched.
            listener.reportEntityDiscovered(mock(JumpPointAPI.class));

            assertThat(PoliticalMapRefresh.getContentRevision()).isEqualTo(beforeContent);
            assertThat(PoliticalMapRefresh.getGeometryRevision()).isEqualTo(beforeGeometry);
        }
    }
}
