package kmu.mods.nexerelin;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import kmu.starsector.listeners.MarketTransferListener;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

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
final class NexerelinMarketTransferRelayTests {

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
        void tellsEveryRegisteredListener() {

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
        void passesOverASectorWithoutAListenerManager() {

            var relayWithoutManager = new NexerelinMarketTransferRelay(buildSectorWith(null));

            assertThatCode(() -> relayWithoutManager.reportMarketTransfered(
                    marketMock, newHolderMock, oldHolderMock, true, true, List.of(), 1.0f))
                .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("kmu.mods.nexerelin.NexerelinMarketTransferRelayTests#listListenerFailures")
        void keepsAListenerThatThrewFromReachingNexerelin(Throwable listenerFailure) {
            // Nexerelin tells its listeners from the end of its market transfer, with no catch of
            // its own above it: a throw escaping here would reach the engine and end the game.
            doThrow(listenerFailure)
                .when(firstListenerMock)
                .reportMarketTransferred(any(), any(), any(), anyBoolean());
            when(listenerManagerMock.getListeners(MarketTransferListener.class))
                .thenReturn(List.of(firstListenerMock));

            assertThatCode(() -> relayTransfer())
                .doesNotThrowAnyException();
        }

        @Test
        void tellsTheListenersAfterOneThatThrew() {
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
        void tellsAListenerThatThrewOfTheNextTransfer() {
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
        void tellsNobodySinceTheTransferReportsTheHolderChange() {
            // An invasion finishing does not itself transfer holding; a successful one is
            // reported through the transfer, so relaying this too would report it twice.
            relay.reportInvasionFinished(
                mock(CampaignFleetAPI.class), newHolderMock, marketMock, 3.0f, true);

            verifyNoInteractions(listenerManagerMock);
        }
    }

    // Both halves of what a listener can throw: a fault of its own, and a member it reads that is
    // no longer where it was compiled against.
    static Stream<Throwable> listListenerFailures() {

        return Stream.of(
            new IllegalStateException("the refresh read a half-built overlay"),
            new NoSuchMethodError("a member the refresh reads has moved"));
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
