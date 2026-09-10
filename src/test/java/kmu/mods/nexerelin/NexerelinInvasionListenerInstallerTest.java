package kmu.mods.nexerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;

import kmlib.testfixtures.starsector.listeners.RecordingListenerManager;

import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapMarketTransferListener;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static kmlib.testfixtures.starsector.settings.StubbedModIds.NEXERELIN;

import static kmu.maplayers.politicalmap.base.refresh.MarketRefreshFixtures.mockMarketInSystem;

import static org.assertj.core.api.Assertions.assertThat;
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
 *
 * <p>And that the listener is built against the sector it is being installed on, as its
 * vanilla-driven siblings are: it reports a conquest onto that sector's own refresh board, so an
 * installer handing over anything else would leave it marking whichever sector the player happens
 * to have loaded.
 */
final class NexerelinInvasionListenerInstallerTest {

    @Nested
    class InstallIfPresent {

        @Test
        void reinstallsFreshTransientListenerWhenNexEnabled() {

            var listenerManagerMock = mock(ListenerManagerAPI.class);
            var sectorMock = buildSectorWith(listenerManagerMock);

            try (var globalMock = mockStatic(Global.class)) {

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
        void buildsTheListenerAgainstTheSectorItIsInstalledOn() {
            // Read by driving the registered listener and looking for the mark on the installed
            // sector's board: what the wiring is for is where a conquest lands, and a listener
            // holding the right sector while marking elsewhere would pass a check on the field.
            //
            // The machinery is installed and the listener driven outside the Global block, since
            // both touch classes whose static logger would come back null if it were resolved
            // while Global is mocked.
            var listenerManager = new RecordingListenerManager();
            var sectorMock = buildSectorWith(listenerManager);
            var refreshBoard = SectorMapMachineryIndex
                .installMachineryOn(sectorMock)
                .resolveRefreshBoard();

            try (var globalMock = mockStatic(Global.class)) {

                stubModEnabled(globalMock, true);
                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock);
            }

            ((PoliticalMapMarketTransferListener) listenerManager.getAddedListeners().get(0))
                .reportMarketTransfered(
                    mockMarketInSystem("sys"),
                    mock(FactionAPI.class),
                    mock(FactionAPI.class),
                    true,
                    true,
                    List.of(),
                    1.0f);

            assertThat(refreshBoard.drainStaleGroupingSystemIds())
                .containsExactly("sys");
        }

        @Test
        void addsNothingWhenNexDisabled() {

            var listenerManagerMock = mock(ListenerManagerAPI.class);
            var sectorMock = buildSectorWith(listenerManagerMock);

            try (var globalMock = mockStatic(Global.class)) {

                stubModEnabled(globalMock, false);
                NexerelinInvasionListenerInstaller.installIfPresent(sectorMock);

                verify(listenerManagerMock, never())
                    .addListener(any(), anyBoolean());
                verify(listenerManagerMock, never())
                    .removeListenerOfClass(PoliticalMapMarketTransferListener.class);
            }
        }

        @Test
        void ignoresNullSectorWithoutConsultingModState() {

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
