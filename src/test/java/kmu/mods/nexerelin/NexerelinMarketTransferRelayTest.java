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

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins {@link NexerelinMarketTransferRelay}: a Nexerelin transfer reaches every
 * {@link MarketTransferListener} registered on the relay's own sector, with the holders in KMU's
 * order rather than Nexerelin's; and the invasion callbacks that change no holder reach nobody.
 *
 * <p>The failure cases are the other half. Nexerelin tells the relay from the end of its own
 * market transfer with no catch around it, so a listener that throws has to stop at the relay and
 * leave both the listeners beside it and the next transfer untouched.
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
            relayTransfer();

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

        @Test
        void reportMarketTransferedKeepsAListenerThatThrewFromReachingNexerelin() {
            // Nexerelin tells its listeners from the end of its market transfer, with no catch of
            // its own above it: a throw escaping here would reach the engine and end the game.
            doThrow(new IllegalStateException("the refresh read a half-built overlay"))
                .when(firstListenerMock)
                .reportMarketTransferred(any(), any(), any(), anyBoolean());
            when(listenerManagerMock.getListeners(MarketTransferListener.class))
                .thenReturn(List.of(firstListenerMock));

            assertThatCode(() -> relayTransfer())
                .doesNotThrowAnyException();
        }

        @Test
        void reportMarketTransferedKeepsAListenerThatCouldNotLinkFromReachingNexerelin() {

            doThrow(new NoSuchMethodError("a member the refresh reads has moved"))
                .when(firstListenerMock)
                .reportMarketTransferred(any(), any(), any(), anyBoolean());
            when(listenerManagerMock.getListeners(MarketTransferListener.class))
                .thenReturn(List.of(firstListenerMock));

            assertThatCode(() -> relayTransfer())
                .doesNotThrowAnyException();
        }

        @Test
        void reportMarketTransferedTellsTheListenersAfterOneThatThrew() {
            // One listener failing costs only its own work; the ones after it still hear of the
            // transfer.
            doThrow(new IllegalStateException("the refresh read a half-built overlay"))
                .when(firstListenerMock)
                .reportMarketTransferred(any(), any(), any(), anyBoolean());
            when(listenerManagerMock.getListeners(MarketTransferListener.class))
                .thenReturn(List.of(firstListenerMock, secondListenerMock));

            relayTransfer();

            verify(secondListenerMock)
                .reportMarketTransferred(marketMock, oldHolderMock, newHolderMock, true);
        }

        @Test
        void reportMarketTransferedTellsAListenerThatThrewOfTheNextTransfer() {
            // What failed was that transfer's state, not a build that no longer fits, so the
            // listener is not taken out and the next transfer is tried.
            doThrow(new IllegalStateException("the refresh read a half-built overlay"))
                .doNothing()
                .when(firstListenerMock)
                .reportMarketTransferred(any(), any(), any(), anyBoolean());
            when(listenerManagerMock.getListeners(MarketTransferListener.class))
                .thenReturn(List.of(firstListenerMock));

            relayTransfer();
            relayTransfer();

            verify(firstListenerMock, times(2))
                .reportMarketTransferred(marketMock, oldHolderMock, newHolderMock, true);
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

    // A capture, as Nexerelin reports one: the new holder before the old.
    private void relayTransfer() {

        relay.reportMarketTransfered(
            marketMock, newHolderMock, oldHolderMock, true, true, List.of(), 1.0f);
    }

    private static SectorAPI buildSectorWith(ListenerManagerAPI listenerManager) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getListenerManager())
            .thenReturn(listenerManager);

        return sectorMock;
    }
}
