package kmu.mods.nexerelin;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import kmu.starsector.listeners.MarketTransferListener;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins {@link NexerelinMarketTransferRelay}: a Nexerelin transfer reaches every
 * {@link MarketTransferListener} registered on the relay's own sector, with the holders in KMU's
 * order rather than Nexerelin's; and the invasion callbacks that change no holder reach nobody.
 */
final class NexerelinMarketTransferRelayTest {

    private final ListenerManagerAPI listenerManagerMock = mock(ListenerManagerAPI.class);
    private final MarketTransferListener firstListenerMock = mock(MarketTransferListener.class);
    private final MarketTransferListener secondListenerMock = mock(MarketTransferListener.class);
    private final MarketAPI marketMock = mock(MarketAPI.class);
    private final FactionAPI oldHolderMock = mock(FactionAPI.class);
    private final FactionAPI newHolderMock = mock(FactionAPI.class);

    private final NexerelinMarketTransferRelay relay =
        new NexerelinMarketTransferRelay(buildSectorWith(listenerManagerMock));

    @Nested
    class ReportMarketTransfered {

        @Test
        void reportMarketTransferedTellsEveryRegisteredListener() {

            when(listenerManagerMock.getListeners(MarketTransferListener.class))
                .thenReturn(List.of(firstListenerMock, secondListenerMock));

            // Nexerelin hands the new holder before the old one; the listeners take them the
            // other way round, so a relay passing them straight through would swap them.
            relay.reportMarketTransfered(
                marketMock, newHolderMock, oldHolderMock, true, true, List.of(), 1.0f);

            verify(firstListenerMock)
                .reportMarketTransferred(marketMock, oldHolderMock, newHolderMock, true);
            verify(secondListenerMock)
                .reportMarketTransferred(marketMock, oldHolderMock, newHolderMock, true);
        }

        @Test
        void reportMarketTransferedTellsNobodyOnASectorWithoutAListenerManager() {

            var relayWithoutManager = new NexerelinMarketTransferRelay(buildSectorWith(null));

            relayWithoutManager.reportMarketTransfered(
                marketMock, newHolderMock, oldHolderMock, true, true, List.of(), 1.0f);

            verifyNoInteractions(firstListenerMock);
        }
    }

    @Nested
    class ReportInvasionFinished {

        @Test
        void reportInvasionFinishedTellsNobodySinceTheTransferReportsTheHolderChange() {
            // An invasion finishing does not itself transfer holding; a successful one is
            // reported through the transfer, so relaying this too would report it twice.
            relay.reportInvasionFinished(
                mock(CampaignFleetAPI.class), newHolderMock, marketMock, 3.0f, true);

            verifyNoInteractions(listenerManagerMock);
        }
    }

    private static SectorAPI buildSectorWith(ListenerManagerAPI listenerManager) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getListenerManager())
            .thenReturn(listenerManager);

        return sectorMock;
    }
}
