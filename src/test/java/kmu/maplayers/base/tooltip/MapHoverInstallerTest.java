package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.testfixtures.starsector.listeners.RecordingListenerManager;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.hover.MapHoverExpirer;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static kmlib.testfixtures.starsector.listeners.SectorListenerFixtures.buildSector;

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

    // The cell a published hover names, so the tick can be shown letting one go.
    private static final String HOVERED_SYSTEM_ID = "system";

    // The elapsed time a frame hands a script. Unread by the tick, which counts frames rather than
    // seconds, so any value states the same thing.
    private static final float ONE_FRAME = 0.016f;

    @AfterEach
    void clearEveryMachinery() {
        // The index is process-wide, so a sector installed on here would outlive its case.
        SectorMapMachineryIndex.disposeAllMachinery();
    }

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
    class InstallHoverTooltipDetailLevelInput {

        @Test
        void reinstallsTheDetailLevelInputListenerFreshAsTransient() {
            // Remove-then-add, transient: the level is a live view preference that enters no save,
            // and a second registration alongside the first would advance the level twice per press -
            // every press would skip a depth the player never saw.
            var listenerManager = new RecordingListenerManager();

            MapHoverInstaller.installHoverTooltipDetailLevelInput(buildSector(listenerManager));

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(HoverTooltipDetailLevelInput.class);

            assertThat(listenerManager.getAddedListeners())
                .singleElement()
                .isInstanceOf(HoverTooltipDetailLevelInput.class);

            assertThat(listenerManager.getAddedTransientFlags())
                .containsExactly(true);
        }

        @Test
        void toleratesAMissingListenerManager() {

            var detailModeInputInstallOnNullManager = (Runnable) () ->
                MapHoverInstaller.installHoverTooltipDetailLevelInput(buildSector(null));

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
        void installsTheExpirerOverTheSectorsOwnHoverState() {
            // The script ticks on the frames of the sector it is registered on, so the window it
            // closes has to be that sector's. Built against any other, the sector being played would
            // keep the last cell any map resolved for the rest of the session - which is the whole
            // fault the tick exists to prevent, reintroduced through its wiring.
            var sectorMock = mock(SectorAPI.class);
            var hoverState = SectorMapMachineryIndex
                .installMachineryOn(sectorMock)
                .resolveHoverState();

            MapHoverInstaller.installMapHoverExpirer(sectorMock);

            var scriptCaptor = ArgumentCaptor.forClass(EveryFrameScript.class);

            verify(sectorMock)
                .addTransientScript(scriptCaptor.capture());

            hoverState.publishHover(new MapHover(HOVERED_SYSTEM_ID, List.of(HOVERED_SYSTEM_ID)));

            // Two ticks: the first closes the window the publish opened, the second finds no pass
            // republished and lets the hover go.
            scriptCaptor.getValue().advance(ONE_FRAME);
            scriptCaptor.getValue().advance(ONE_FRAME);

            assertThat(hoverState.getHover())
                .isSameAs(MapHover.NONE);
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
            // would keep swallowing the cycle key for a box that is no longer drawn, and leaving
            // the tick behind would go on closing a window nothing publishes into.
            var listenerManager = new RecordingListenerManager();
            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getListenerManager())
                .thenReturn(listenerManager);

            MapHoverInstaller.uninstallAll(sectorMock);

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(MapLayerCellTooltip.class, HoverTooltipDetailLevelInput.class);

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
