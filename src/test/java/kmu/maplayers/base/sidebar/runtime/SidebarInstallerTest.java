package kmu.maplayers.base.sidebar.runtime;

import kmu.starsector.listeners.RecordingListenerManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.starsector.listeners.SectorListenerFixtures.buildSector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the sidebar's registration shape across every screen it draws on: both classes cleared
 * before either is re-added, and one render and one input listener per host.
 */
class SidebarInstallerTest {

    @Nested
    class InstallPoliticalMapSidebar {

        @Test
        void reinstallsEverySidebarListenerFreshAsTransient() {

            var listenerManager = new RecordingListenerManager(false);

            SidebarInstaller.installPoliticalMapSidebar(buildSector(listenerManager));

            // Remove-then-add the sidebar's render and input listeners: one remove per class clears any
            // registration an older save carried, then a fresh render+input instance is added for each of
            // the two hosts (sector map and intel screen) transiently, so none enters the save and exactly
            // one of each renders per screen.
            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(SidebarRenderer.class, SidebarInput.class);

            assertThat(listenerManager.getAddedListeners())
                .hasSize(4)
                .hasAtLeastOneElementOfType(SidebarRenderer.class)
                .hasAtLeastOneElementOfType(SidebarInput.class);

            assertThat(listenerManager.getAddedTransientFlags())
                .containsExactly(true, true, true, true);
        }

        @Test
        void toleratesAMissingListenerManager() {

            var sidebarInstallOnNullManager = (Runnable) () ->
                SidebarInstaller.installPoliticalMapSidebar(buildSector(null));

            assertThatCode(sidebarInstallOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }
}
