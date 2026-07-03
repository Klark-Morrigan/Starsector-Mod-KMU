package kmu.starsector.nexerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import kmu.politicalmap.refresh.listeners.PoliticalMapMarketTransferListener;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link NexerelinInvasionListenerInstaller}: the market-transfer listener
 * is registered only when Nexerelin is enabled and not already present, and the
 * listener is transient (not persisted). The mod-enabled gate is what makes the
 * Nex dependency optional, so the disabled case is pinned alongside the install.
 */
final class NexerelinInvasionListenerInstallerTest {

    private static final String NEXERELIN_MOD_ID = "nexerelin";

    @Nested
    class InstallIfPresent {

        @Test
        void addsTransientListenerWhenNexEnabledAndNotYetPresent() {
            var listenerManagerMock = mock(ListenerManagerAPI.class);
            when(listenerManagerMock.hasListenerOfClass(PoliticalMapMarketTransferListener.class))
                    .thenReturn(false);
            var sectorMock = sectorWith(listenerManagerMock);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubModEnabled(globalMock, true);

                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock);

                // false: the listener implements a Nex interface, so it is not
                // serialised into the save and is re-added on each load instead.
                verify(listenerManagerMock)
                        .addListener(any(PoliticalMapMarketTransferListener.class), eq(false));
            }
        }

        @Test
        void addsNothingWhenNexDisabled() {
            var listenerManagerMock = mock(ListenerManagerAPI.class);
            var sectorMock = sectorWith(listenerManagerMock);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubModEnabled(globalMock, false);

                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock);

                verify(listenerManagerMock, never()).addListener(any(), anyBoolean());
            }
        }

        @Test
        void addsNothingWhenListenerAlreadyPresent() {
            var listenerManagerMock = mock(ListenerManagerAPI.class);
            when(listenerManagerMock.hasListenerOfClass(PoliticalMapMarketTransferListener.class))
                    .thenReturn(true);
            var sectorMock = sectorWith(listenerManagerMock);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubModEnabled(globalMock, true);

                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock);

                verify(listenerManagerMock, never()).addListener(any(), anyBoolean());
            }
        }

        @Test
        void ignoresNullSectorWithoutConsultingModState() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                // Null short-circuits before the mod-enabled check, so Global is
                // never consulted.
                NexerelinInvasionListenerInstaller.installIfPresent(null);

                globalMock.verifyNoInteractions();
            }
        }
    }

    private static SectorAPI sectorWith(ListenerManagerAPI listenerManager) {
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getListenerManager()).thenReturn(listenerManager);
        return sectorMock;
    }

    private static void stubModEnabled(MockedStatic<Global> globalMock, boolean isEnabled) {
        var settingsMock = mock(SettingsAPI.class);
        var modManagerMock = mock(ModManagerAPI.class);
        globalMock.when(Global::getSettings).thenReturn(settingsMock);
        when(settingsMock.getModManager()).thenReturn(modManagerMock);
        when(modManagerMock.isModEnabled(NEXERELIN_MOD_ID)).thenReturn(isEnabled);
    }
}
