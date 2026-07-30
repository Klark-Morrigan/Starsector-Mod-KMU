package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.base.refresh.MapLayerRefresh;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapMarketTransferListener}: a Nexerelin market transfer
 * marks the transferred colony's star system politics-stale, while a transfer of
 * a market with no star system marks nothing. An invasion finishing is a no-op,
 * since the transfer - not the invasion outcome - is what changes the owner. The
 * stale set is drained to read it, so each case clears it first.
 */
final class PoliticalMapMarketTransferListenerTest {
    private final PoliticalMapMarketTransferListener listener =
            new PoliticalMapMarketTransferListener();

    // Named to match Nexerelin's interface, which misspells "transferred" with a
    // single r.
    @Nested
    class ReportMarketTransfered {

        @Test
        void marksTheTransferredColonysSystemStale() {
            MapLayerRefresh.drainStalePoliticsSystemIds();

            listener.reportMarketTransfered(marketInSystem("sys"), faction("attacker"),
                    faction("defender"), true, true, List.of(), 1.0f);

            assertThat(MapLayerRefresh.drainStalePoliticsSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            MapLayerRefresh.drainStalePoliticsSystemIds();
            var marketMock = mock(MarketAPI.class);
            when(marketMock.getStarSystem()).thenReturn(null);

            listener.reportMarketTransfered(marketMock, faction("attacker"), faction("defender"),
                    true, true, List.of(), 1.0f);

            assertThat(MapLayerRefresh.drainStalePoliticsSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            MapLayerRefresh.drainStalePoliticsSystemIds();

            listener.reportMarketTransfered(null, faction("attacker"), faction("defender"),
                    true, true, List.of(), 1.0f);

            assertThat(MapLayerRefresh.drainStalePoliticsSystemIds()).isEmpty();
        }
    }

    @Nested
    class ReportInvasionFinished {

        @Test
        void marksNothingSinceTransferReportsTheOwnerChange() {
            MapLayerRefresh.drainStalePoliticsSystemIds();

            // An invasion finishing does not itself transfer ownership; the refresh
            // keys off reportMarketTransferred, so this callback marks nothing.
            listener.reportInvasionFinished(mock(CampaignFleetAPI.class), faction("attacker"),
                    marketInSystem("sys"), 3.0f, true);

            assertThat(MapLayerRefresh.drainStalePoliticsSystemIds()).isEmpty();
        }
    }

    private static FactionAPI faction(String id) {
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn(id);
        return factionMock;
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
