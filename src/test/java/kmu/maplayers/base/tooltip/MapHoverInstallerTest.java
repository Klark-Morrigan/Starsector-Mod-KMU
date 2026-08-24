package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.hover.MapHoverExpirer;
import kmu.starsector.listeners.RecordingListenerManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.starsector.listeners.SectorListenerFixtures.buildSector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the hover box's three halves as separate registrations, each replaced outright per load: the
 * render pass that draws it, the input pass that switches its detail, and the per-frame tick that
 * lets a hover go once no map pass is resolving one.
 */
class MapHoverInstallerTest {

    @Nested
    class InstallMapLayerHoverTooltip {

        @Test
        void reinstallsTheHoverTooltipDispatcherFreshAsTransient() {
            // Remove-then-add, transient: the dispatcher caches GL text, which must never enter a
            // save, and whatever is registered has to be cleared first or two would draw one box.
            var listenerManager = new RecordingListenerManager();

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
            // and a second registration alongside the first would flip the mode twice per press -
            // leaving it exactly where it started, so the key would look dead.
            var listenerManager = new RecordingListenerManager();

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

    @Nested
    class InstallMapHoverExpirer {

        @Test
        void installsTheExpirerAsATransientScriptClearedFirst() {
            // addTransientScript, never addScript: an EveryFrameScript that entered the save would
            // be restored alongside the one added on load, stacking another tick per reload and
            // baking this class's name into the file. Cleared first for the same reason the
            // listeners are - a second tick would close the window twice per frame, taking a hover
            // away on the frame the map published it.
            var sectorMock = mock(SectorAPI.class);

            MapHoverInstaller.installMapHoverExpirer(sectorMock);

            verify(sectorMock)
                .removeTransientScriptsOfClass(MapHoverExpirer.class);
            verify(sectorMock)
                .addTransientScript(any(MapHoverExpirer.class));
            verify(sectorMock, never())
                .addScript(any());
        }

        @Test
        void toleratesAMissingSector() {

            var expirerInstallOnNoSector = (Runnable) () ->
                MapHoverInstaller.installMapHoverExpirer(null);

            assertThatCode(expirerInstallOnNoSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class RemoveMapHoverExpirer {

        @Test
        void removesTheExpirerByItsOwnClass() {
            // By class rather than by instance, which is safe only because this script is this
            // mod's own: no sibling mod runs one over the same sector to be taken out with it.
            var sectorMock = mock(SectorAPI.class);

            MapHoverInstaller.removeMapHoverExpirer(sectorMock);

            verify(sectorMock)
                .removeTransientScriptsOfClass(MapHoverExpirer.class);
        }

        @Test
        void toleratesAMissingSector() {

            var expirerRemovalOnNoSector = (Runnable) () ->
                MapHoverInstaller.removeMapHoverExpirer(null);

            assertThatCode(expirerRemovalOnNoSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class UninstallAll {

        @Test
        void clearsEveryHalfOfTheBoxAndRegistersNothingBack() {
            // All three, because they are separate registrations: leaving the input half behind
            // would keep swallowing the toggle key for a box that is no longer drawn, and leaving
            // the tick behind would go on closing a window nothing publishes into.
            var listenerManager = new RecordingListenerManager();
            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getListenerManager())
                .thenReturn(listenerManager);

            MapHoverInstaller.uninstallAll(sectorMock);

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(MapLayerCellTooltip.class, HoverTooltipDetailModeInput.class);

            verify(sectorMock)
                .removeTransientScriptsOfClass(MapHoverExpirer.class);

            assertThat(listenerManager.getAddedListeners())
                .isEmpty();
        }

        @Test
        void toleratesAMissingListenerManager() {

            var hoverUninstallOnNullManager = (Runnable) () ->
                MapHoverInstaller.uninstallAll(buildSector(null));

            assertThatCode(hoverUninstallOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }
}
