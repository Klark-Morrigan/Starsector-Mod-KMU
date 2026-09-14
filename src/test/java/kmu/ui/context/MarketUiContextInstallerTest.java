package kmu.ui.context;

import kmlib.testfixtures.starsector.listeners.RecordingListenerManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmlib.testfixtures.starsector.listeners.SectorListenerFixtures.buildSector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the market UI context tracker's registration shape: its class cleared before one is added,
 * and added transient, so a repeated install leaves exactly one and none enters a save. Plus the
 * removal the other way, which is what a player switching the condition manager off mid-campaign
 * gets - across a load the tracker is gone by itself, being transient.
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

    @Nested
    class RemoveMarketUiContextTracker {

        @Test
        void clearsTheTrackerAddingNothingBack() {

            var listenerManager = new RecordingListenerManager();

            MarketUiContextInstaller.removeMarketUiContextTracker(buildSector(listenerManager));

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(StarsectorMarketUiContextTracker.class);

            // The half that makes this a removal rather than a reinstall: a tracker added back would
            // go on answering for a feature the player has switched off.
            assertThat(listenerManager.getAddedListeners())
                .isEmpty();
        }

        @Test
        void toleratesAMissingListenerManager() {

            var trackerRemovalOnNullManager = (Runnable) () ->
                MarketUiContextInstaller.removeMarketUiContextTracker(buildSector(null));

            assertThatCode(trackerRemovalOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }
}
