package kmu.mods.nexerelin;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import kmlib.testfixtures.starsector.settings.ModStateScopes;

import kmu.starsector.listeners.MarketTransferListener;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static kmlib.testfixtures.starsector.settings.StubbedModIds.NEXERELIN;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins {@link NexerelinInvasionListenerInstaller}: when Nexerelin is enabled the transfer relay is
 * (re)installed fresh and transient (not persisted), and removed again on the way out; when it is
 * disabled nothing is touched either way. Transience matters because the relay implements a
 * Nexerelin interface - persisting it would fail to load if Nexerelin were removed. The mod-enabled
 * gate is what makes the dependency optional, so the disabled cases are pinned alongside the
 * enabled ones.
 *
 * <p>And that the relay is built against the sector it is being installed on: it tells that
 * sector's own listeners about a conquest, so an installer handing over anything else would leave
 * it telling whichever sector the player happens to have loaded.
 */
final class NexerelinInvasionListenerInstallerTests {

    private final ListenerManagerAPI listenerManagerMock = mock(ListenerManagerAPI.class);
    private final SectorAPI sectorMock = buildSectorWith(listenerManagerMock);

    @Nested
    class InstallIfPresent {

        @Test
        void reinstallsAFreshTransientRelayWhenNexerelinIsEnabled() {

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock));

            // Remove-then-add: clears any copy an older build persisted into the save, then adds
            // the relay transiently (true) so it never enters the save - it implements a Nexerelin
            // interface and would fail to load if Nexerelin were removed.
            verify(listenerManagerMock)
                .removeListenerOfClass(NexerelinMarketTransferRelay.class);
            verify(listenerManagerMock)
                .addListener(any(NexerelinMarketTransferRelay.class), eq(true));
        }

        @Test
        void buildsTheRelayAgainstTheSectorItIsInstalledOn() {
            // Read by driving the registered relay and looking for the call on a listener of the
            // installed sector: what the wiring is for is where a conquest lands, and a relay
            // holding the right sector while telling elsewhere would pass a check on the field.
            var transferListenerMock = mock(MarketTransferListener.class);
            var marketMock = mock(MarketAPI.class);

            when(listenerManagerMock.getListeners(MarketTransferListener.class))
                .thenReturn(List.of(transferListenerMock));

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock));

            var installedRelay = ArgumentCaptor.forClass(NexerelinMarketTransferRelay.class);

            verify(listenerManagerMock)
                .addListener(installedRelay.capture(), eq(true));

            installedRelay.getValue().reportMarketTransfered(
                marketMock, null, null, true, true, List.of(), 1.0f);

            verify(transferListenerMock)
                .reportMarketTransferred(marketMock, null, null, true);
        }

        @Test
        void addsNothingWhenNexerelinIsDisabled() {

            ModStateScopes.runWithModEnabled(NEXERELIN, false, () ->
                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock));

            verifyNoInteractions(listenerManagerMock);
        }

        @Test
        void addsNothingToASectorWithoutAListenerManager() {

            var sectorWithoutManager = buildSectorWith(null);

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                assertThatCode(() -> NexerelinInvasionListenerInstaller.installIfPresent(sectorWithoutManager))
                    .doesNotThrowAnyException());
        }

        @Test
        void ignoresANullSector() {

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                assertThatCode(() -> NexerelinInvasionListenerInstaller.installIfPresent(null))
                    .doesNotThrowAnyException());
        }
    }

    @Nested
    class UninstallIfPresent {

        @Test
        void removesTheRelayWhenNexerelinIsEnabled() {

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                NexerelinInvasionListenerInstaller.uninstallIfPresent(sectorMock));

            verify(listenerManagerMock)
                .removeListenerOfClass(NexerelinMarketTransferRelay.class);
        }

        @Test
        void removesNothingWhenNexerelinIsDisabled() {
            // Naming the relay's class to remove it would load it, and with it the Nexerelin
            // interface it implements - which an install without Nexerelin does not have.
            ModStateScopes.runWithModEnabled(NEXERELIN, false, () ->
                NexerelinInvasionListenerInstaller.uninstallIfPresent(sectorMock));

            verifyNoInteractions(listenerManagerMock);
        }

        @Test
        void removesNothingFromASectorWithoutAListenerManager() {

            var sectorWithoutManager = buildSectorWith(null);

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                assertThatCode(() -> NexerelinInvasionListenerInstaller.uninstallIfPresent(sectorWithoutManager))
                    .doesNotThrowAnyException());
        }

        @Test
        void ignoresANullSector() {

            ModStateScopes.runWithModEnabled(NEXERELIN, true, () ->
                assertThatCode(() -> NexerelinInvasionListenerInstaller.uninstallIfPresent(null))
                    .doesNotThrowAnyException());
        }
    }

    private static SectorAPI buildSectorWith(ListenerManagerAPI listenerManager) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getListenerManager())
            .thenReturn(listenerManager);

        return sectorMock;
    }
}
