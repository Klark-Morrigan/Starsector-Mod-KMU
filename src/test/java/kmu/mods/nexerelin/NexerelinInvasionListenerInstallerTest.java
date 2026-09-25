package kmu.mods.nexerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import kmu.starsector.listeners.MarketTransferListener;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static kmlib.testfixtures.starsector.settings.StubbedModIds.NEXERELIN;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link NexerelinInvasionListenerInstaller}: when Nexerelin is enabled the transfer relay is
 * (re)installed fresh and transient (not persisted), and when it is disabled nothing is touched.
 * Transience matters because the relay implements a Nex interface - persisting it would fail to
 * load if Nex were removed. The mod-enabled gate is what makes the Nex dependency optional, so the
 * disabled case is pinned alongside the install.
 *
 * <p>And that the relay is built against the sector it is being installed on: it tells that
 * sector's own listeners about a conquest, so an installer handing over anything else would leave
 * it telling whichever sector the player happens to have loaded.
 */
final class NexerelinInvasionListenerInstallerTest {

    @Nested
    class InstallIfPresent {

        @Test
        void installIfPresentReinstallsFreshTransientRelayWhenNexEnabled() {

            var listenerManagerMock = mock(ListenerManagerAPI.class);
            var sectorMock = buildSectorWith(listenerManagerMock);

            try (var globalMock = mockStatic(Global.class)) {

                stubModEnabled(globalMock, true);
                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock);

                // Remove-then-add: clears any copy an older build persisted into the save,
                // then adds the relay transiently (true) so it never enters the save - it
                // implements a Nex interface and would fail to load if Nex were removed.
                verify(listenerManagerMock)
                    .removeListenerOfClass(NexerelinMarketTransferRelay.class);
                verify(listenerManagerMock)
                    .addListener(any(NexerelinMarketTransferRelay.class), eq(true));
            }
        }

        @Test
        void installIfPresentBuildsTheRelayAgainstTheSectorItIsInstalledOn() {
            // Read by driving the registered relay and looking for the call on a listener of the
            // installed sector: what the wiring is for is where a conquest lands, and a relay
            // holding the right sector while telling elsewhere would pass a check on the field.
            var listenerManagerMock = mock(ListenerManagerAPI.class);
            var sectorMock = buildSectorWith(listenerManagerMock);
            var transferListenerMock = mock(MarketTransferListener.class);
            var marketMock = mock(MarketAPI.class);

            when(listenerManagerMock.getListeners(MarketTransferListener.class))
                .thenReturn(List.of(transferListenerMock));

            try (var globalMock = mockStatic(Global.class)) {

                stubModEnabled(globalMock, true);
                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock);
            }
            var installedRelay = ArgumentCaptor.forClass(NexerelinMarketTransferRelay.class);

            verify(listenerManagerMock)
                .addListener(installedRelay.capture(), eq(true));

            installedRelay.getValue().reportMarketTransfered(
                marketMock, null, null, true, true, List.of(), 1.0f);

            verify(transferListenerMock)
                .reportMarketTransferred(marketMock, null, null, true);
        }

        @Test
        void installIfPresentAddsNothingWhenNexDisabled() {

            var listenerManagerMock = mock(ListenerManagerAPI.class);
            var sectorMock = buildSectorWith(listenerManagerMock);

            try (var globalMock = mockStatic(Global.class)) {

                stubModEnabled(globalMock, false);
                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock);

                verify(listenerManagerMock, never())
                    .addListener(any(), anyBoolean());
                verify(listenerManagerMock, never())
                    .removeListenerOfClass(NexerelinMarketTransferRelay.class);
            }
        }

        @Test
        void installIfPresentIgnoresNullSectorWithoutConsultingModState() {

            try (var globalMock = mockStatic(Global.class)) {

                // Null short-circuits before the mod-enabled check, so Global is
                // never consulted.
                NexerelinInvasionListenerInstaller.installIfPresent(null);
                globalMock.verifyNoInteractions();
            }
        }
    }

    private static SectorAPI buildSectorWith(ListenerManagerAPI listenerManager) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getListenerManager())
            .thenReturn(listenerManager);

        return sectorMock;
    }

    private static void stubModEnabled(MockedStatic<Global> globalMock, boolean isEnabled) {

        var settingsMock = mock(SettingsAPI.class);
        var modManagerMock = mock(ModManagerAPI.class);

        globalMock
            .when(Global::getSettings)
            .thenReturn(settingsMock);

        when(settingsMock.getModManager())
            .thenReturn(modManagerMock);
        when(modManagerMock.isModEnabled(NEXERELIN))
            .thenReturn(isEnabled);
    }
}
