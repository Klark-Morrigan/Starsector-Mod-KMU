package kmu.ui.context;

import kmu.starsector.listeners.RecordingListenerManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.starsector.listeners.SectorListenerFixtures.buildSector;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the market UI context tracker's registration shape: added when absent, and never added
 * beside a registration a reloaded save carried back.
 */
class MarketUiContextInstallerTest {

    @Nested
    class InstallMarketUiContextTracker {

        @Test
        void installsMarketUiContextTrackerWhenMissing() {

            var listenerManager = new RecordingListenerManager(false);

            MarketUiContextInstaller.installMarketUiContextTracker(buildSector(listenerManager));

            assertThat(listenerManager.getAddedListeners())
                .singleElement()
                .isInstanceOf(StarsectorMarketUiContextTracker.class);

            assertThat(listenerManager.getAddedTransientFlags())
                .containsExactly(true);
        }

        @Test
        void doesNotInstallDuplicateMarketUiContextTracker() {

            var listenerManager = new RecordingListenerManager(true);

            MarketUiContextInstaller.installMarketUiContextTracker(buildSector(listenerManager));

            assertThat(listenerManager.getAddedListeners())
                .isEmpty();
        }
    }
}
