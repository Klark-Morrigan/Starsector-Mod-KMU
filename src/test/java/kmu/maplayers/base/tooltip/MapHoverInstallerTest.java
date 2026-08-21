package kmu.maplayers.base.tooltip;

import kmu.starsector.listeners.RecordingListenerManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.starsector.listeners.SectorListenerFixtures.buildSector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the hover box's two halves as separate registrations, each replaced outright per load: the
 * render pass that draws it, and the input pass that switches its detail.
 */
class MapHoverInstallerTest {

    @Nested
    class InstallMapLayerHoverTooltip {

        @Test
        void reinstallsTheHoverTooltipDispatcherFreshAsTransient() {
            // Remove-then-add, transient: the dispatcher caches GL text, which must never enter a save,
            // and a registration an older save carried has to be cleared or two would draw the same box.
            var listenerManager = new RecordingListenerManager(false);

            MapHoverInstaller.installMapLayerHoverTooltip(buildSector(listenerManager));

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(MapLayerCellTooltip.class);

            assertThat(listenerManager.getAddedListeners())
                .singleElement()
                .isInstanceOf(MapLayerCellTooltip.class);

            assertThat(listenerManager.getAddedTransientFlags())
                .containsExactly(true);
        }

        @Test
        void toleratesAMissingListenerManager() {

            var tooltipInstallOnNullManager = (Runnable) () ->
                MapHoverInstaller.installMapLayerHoverTooltip(buildSector(null));

            assertThatCode(tooltipInstallOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallHoverTooltipDetailModeInput {

        @Test
        void reinstallsTheDetailModeInputListenerFreshAsTransient() {
            // Remove-then-add, transient: the toggle is a live view preference that enters no save,
            // and a registration an older save carried would flip the mode twice per press - leaving
            // it exactly where it started, so the key would look dead.
            var listenerManager = new RecordingListenerManager(false);

            MapHoverInstaller.installHoverTooltipDetailModeInput(buildSector(listenerManager));

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(HoverTooltipDetailModeInput.class);

            assertThat(listenerManager.getAddedListeners())
                .singleElement()
                .isInstanceOf(HoverTooltipDetailModeInput.class);

            assertThat(listenerManager.getAddedTransientFlags())
                .containsExactly(true);
        }

        @Test
        void toleratesAMissingListenerManager() {

            var detailModeInputInstallOnNullManager = (Runnable) () ->
                MapHoverInstaller.installHoverTooltipDetailModeInput(buildSector(null));

            assertThatCode(detailModeInputInstallOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }
}
