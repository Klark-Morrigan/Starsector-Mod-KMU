package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MapLayerSectorWatcher;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapColonisationListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapColonySizeListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapDecivListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapDiscoveryListener;
import kmu.starsector.listeners.RecordingListenerManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the political map's per-frame watcher as a transient script built fresh per load over a
 * tracker flushed of whatever the previous save left in it, and the taking back of every live
 * repaint when the layers are switched off.
 */
class PoliticalMapInstallerTest {

    @Nested
    class InstallMapLayerSectorWatcher {

        @Test
        void installsTheWatcherAsATransientScript() {
            // addTransientScript, never addScript: an EveryFrameScript that entered the save
            // would be restored alongside the one added on load, so every reload would stack
            // another poller - and would bake this class's name into the file.
            var sectorMock = mock(SectorAPI.class);

            PoliticalMapInstaller.installMapLayerSectorWatcher(sectorMock);

            verify(sectorMock)
                .addTransientScript(any(MapLayerSectorWatcher.class));
            verify(sectorMock, never())
                .addScript(any());
        }

        @Test
        void installsAFreshWatcherPerLoadSoItsBaselinesStartEmpty() {
            // The watcher's staleness source diffs each poll against its own last read, so a
            // source carried across loads would compare the newly loaded sector against the
            // one just left and mark every system that differs stale on the first poll.
            var firstLoadSectorMock = mock(SectorAPI.class);
            var secondLoadSectorMock = mock(SectorAPI.class);

            PoliticalMapInstaller.installMapLayerSectorWatcher(firstLoadSectorMock);
            PoliticalMapInstaller.installMapLayerSectorWatcher(secondLoadSectorMock);

            var firstWatcher = ArgumentCaptor.forClass(MapLayerSectorWatcher.class);
            var secondWatcher = ArgumentCaptor.forClass(MapLayerSectorWatcher.class);

            verify(firstLoadSectorMock)
                .addTransientScript(firstWatcher.capture());
            verify(secondLoadSectorMock)
                .addTransientScript(secondWatcher.capture());

            assertThat(secondWatcher.getValue())
                .isNotSameAs(firstWatcher.getValue());
        }

        @Test
        void flushesTheSharedMovingTrackerBeforeInstalling() {
            // The tracker is a process-lifetime singleton, so without this flush a system id
            // reused by the next save is measured against the previous save's last-seen
            // position and reads as having teleported.
            try (var movingStaticMock = mockStatic(MovingSystems.class)) {

                var movingSystemsMock = mock(MovingSystems.class);

                movingStaticMock
                    .when(MovingSystems::getInstance)
                    .thenReturn(movingSystemsMock);

                PoliticalMapInstaller.installMapLayerSectorWatcher(mock(SectorAPI.class));

                verify(movingSystemsMock)
                    .reset();
            }
        }

        @Test
        void toleratesANullSector() {

            var watcherInstallOnNullSector = (Runnable) () ->
                PoliticalMapInstaller.installMapLayerSectorWatcher(null);

            assertThatCode(watcherInstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class UninstallAll {

        @Test
        void clearsEveryLiveRepaintItRegisters() {
            // All four listeners and the watcher, since each would otherwise go on marking an
            // overlay stale that nothing is drawing - work paid for a feature switched off.
            var listenerManager = new RecordingListenerManager();
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getListenerManager())
                .thenReturn(listenerManager);

            PoliticalMapInstaller.uninstallAll(sectorMock);

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(
                    PoliticalMapDiscoveryListener.class,
                    PoliticalMapColonySizeListener.class,
                    PoliticalMapDecivListener.class,
                    PoliticalMapColonisationListener.class);

            assertThat(listenerManager.getAddedListeners())
                .isEmpty();

            verify(sectorMock)
                .removeTransientScriptsOfClass(MapLayerSectorWatcher.class);
        }

        @Test
        void toleratesANullSector() {

            var politicalMapUninstallOnNullSector = (Runnable) () ->
                PoliticalMapInstaller.uninstallAll(null);

            assertThatCode(politicalMapUninstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class RemoveMapLayerSectorWatcher {

        @Test
        void stopsTheWatcherByClassSinceThisModOwnsIt() {
            // By class rather than by instance, which is safe only because MapLayerSectorWatcher is
            // the framework's own: no sibling mod is running one over the same sector.
            var sectorMock = mock(SectorAPI.class);

            PoliticalMapInstaller.removeMapLayerSectorWatcher(sectorMock);

            verify(sectorMock)
                .removeTransientScriptsOfClass(MapLayerSectorWatcher.class);
        }

        @Test
        void toleratesANullSector() {

            var watcherRemovalOnNullSector = (Runnable) () ->
                PoliticalMapInstaller.removeMapLayerSectorWatcher(null);

            assertThatCode(watcherRemovalOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }
}
