package kmu.starsector.nexerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapMarketTransferListener;

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
 * Pins {@link NexerelinInvasionListenerInstaller}: when Nexerelin is enabled the
 * market-transfer listener is (re)installed fresh and transient (not persisted), and
 * when it is disabled nothing is touched. Transience matters because the listener
 * implements a Nex interface - persisting it would fail to load if Nex were removed.
 * The mod-enabled gate is what makes the Nex dependency optional, so the disabled case
 * is pinned alongside the install.
 */
final class NexerelinInvasionListenerInstallerTest {

    private static final String NEXERELIN_MOD_ID = "nexerelin";

    @Nested
    class InstallIfPresent {

        @Test
        void reinstallsFreshTransientListenerWhenNexEnabled() {
            var listenerManagerMock = mock(ListenerManagerAPI.class);
            var sectorMock = sectorWith(listenerManagerMock);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                stubModEnabled(globalMock, true);

                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock);

                // Remove-then-add: clears any copy an older build persisted into the save,
                // then adds the listener transiently (true) so it never enters the save - it
                // implements a Nex interface and would fail to load if Nex were removed.
                verify(listenerManagerMock)
                        .removeListenerOfClass(PoliticalMapMarketTransferListener.class);
                verify(listenerManagerMock)
                        .addListener(any(PoliticalMapMarketTransferListener.class), eq(true));
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
                verify(listenerManagerMock, never())
                        .removeListenerOfClass(PoliticalMapMarketTransferListener.class);
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
