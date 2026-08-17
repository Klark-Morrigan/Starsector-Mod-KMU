package kmu.maplayers.politicalmap.base.refresh.listeners;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;

import kmu.maplayers.base.refresh.MapLayerRefresh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;
import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockUnseatedMarket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapMarketTransferListener}: a Nexerelin market transfer
 * marks the transferred colony's star system politics-stale, while a transfer of
 * a market with no star system marks nothing. An invasion finishing is a no-op,
 * since the transfer - not the invasion outcome - is what changes the holder. The
 * stale set is drained to read it, and drained before each case, since the board
 * it lives on is process-wide.
 */
final class PoliticalMapMarketTransferListenerTest {
    private final PoliticalMapMarketTransferListener listener =
            new PoliticalMapMarketTransferListener();

    @BeforeEach
    void drainAnyPendingStaleSystems() {
        MapLayerRefresh.drainStaleGroupingSystemIds();
    }

    // Named to match Nexerelin's interface, which misspells "transferred" with a
    // single r.
    @Nested
    class ReportMarketTransfered {

        @Test
        void marksTheTransferredColonysSystemStale() {
            listener.reportMarketTransfered(mockMarketInSystem("sys"), mockFaction("attacker"),
                    mockFaction("defender"), true, true, List.of(), 1.0f);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).containsExactly("sys");
        }

        @Test
        void marksNothingForMarketWithoutStarSystem() {
            listener.reportMarketTransfered(mockUnseatedMarket(), mockFaction("attacker"),
                    mockFaction("defender"), true, true, List.of(), 1.0f);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }

        @Test
        void marksNothingForNullMarket() {
            listener.reportMarketTransfered(null, mockFaction("attacker"),
                    mockFaction("defender"), true, true, List.of(), 1.0f);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }

    @Nested
    class ReportInvasionFinished {

        @Test
        void marksNothingSinceTransferReportsTheHolderChange() {
            // An invasion finishing does not itself transfer holding; the refresh
            // keys off reportMarketTransferred, so this callback marks nothing.
            listener.reportInvasionFinished(mock(CampaignFleetAPI.class), mockFaction("attacker"),
                    mockMarketInSystem("sys"), 3.0f, true);

            assertThat(MapLayerRefresh.drainStaleGroupingSystemIds()).isEmpty();
        }
    }

    private static FactionAPI mockFaction(String factionId) {
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn(factionId);
        return factionMock;
    }
}
