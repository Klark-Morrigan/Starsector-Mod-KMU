package kmu.ui.context;

import kmu.starsector.listeners.RecordingListenerManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.starsector.listeners.SectorListenerFixtures.buildSector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the market UI context tracker's registration shape: its class cleared before one is added,
 * and added transient, so a repeated install leaves exactly one and none enters a save.
 */
class MarketUiContextInstallerTest {

    @Nested
    class InstallMarketUiContextTracker {

        @Test
        void registersTheTrackerFreshAsTransient() {

            var listenerManager = new RecordingListenerManager();

            MarketUiContextInstaller.installMarketUiContextTracker(buildSector(listenerManager));

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(StarsectorMarketUiContextTracker.class);

            assertThat(listenerManager.getAddedListeners())
                .singleElement()
                .isInstanceOf(StarsectorMarketUiContextTracker.class);

            assertThat(listenerManager.getAddedTransientFlags())
                .containsExactly(true);
        }

        @Test
        void leavesExactlyOneTrackerWhenInstalledTwice() {
            // The install is idempotent by clearing rather than by checking: whatever is already
            // registered under the class goes before the fresh one is added, so a second install
            // cannot leave two trackers answering the same market.
            var listenerManager = new RecordingListenerManager();
            var sector = buildSector(listenerManager);

            MarketUiContextInstaller.installMarketUiContextTracker(sector);
            MarketUiContextInstaller.installMarketUiContextTracker(sector);

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(
                    StarsectorMarketUiContextTracker.class,
                    StarsectorMarketUiContextTracker.class);

            assertThat(listenerManager.getAddedListeners())
                .hasSize(2);
        }

        @Test
        void toleratesAMissingListenerManager() {

            var trackerInstallOnNullManager = (Runnable) () ->
                MarketUiContextInstaller.installMarketUiContextTracker(buildSector(null));

            assertThatCode(trackerInstallOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }
}
