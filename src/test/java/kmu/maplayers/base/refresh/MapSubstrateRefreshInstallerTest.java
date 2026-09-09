package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins the substrate's poll as a transient script built fresh per load against the sector it is
 * installed on, and taken back under its own class so a layer switching off cannot reach it.
 */
final class MapSubstrateRefreshInstallerTest {

    @Nested
    class InstallMapSubstrateSectorWatcher {

        @Test
        void installsTheSweepAsATransientScript() {
            // addTransientScript, never addScript: an EveryFrameScript that entered the save would
            // be restored alongside the one added on load, so every reload would stack another
            // sweep - and would bake this class's name into the file.
            var sectorMock = mock(SectorAPI.class);

            MapSubstrateRefreshInstaller.installMapSubstrateSectorWatcher(sectorMock);

            verify(sectorMock)
                .addTransientScript(any(MapSubstrateSectorWatcher.class));
            verify(sectorMock, never())
                .addScript(any());
        }

        @Test
        void buildsAFreshSweepPerLoadSoItWritesIntoTheSectorItWasMadeOver() {
            // The source holds the sector it sweeps, so one carried across loads would go on
            // recording observations into the sector just left while the loaded one learnt
            // nothing - and nothing on screen would report the register standing still.
            var firstLoadSectorMock = mock(SectorAPI.class);
            var secondLoadSectorMock = mock(SectorAPI.class);

            MapSubstrateRefreshInstaller.installMapSubstrateSectorWatcher(firstLoadSectorMock);
            MapSubstrateRefreshInstaller.installMapSubstrateSectorWatcher(secondLoadSectorMock);

            var firstSweep = ArgumentCaptor.forClass(MapSubstrateSectorWatcher.class);
            var secondSweep = ArgumentCaptor.forClass(MapSubstrateSectorWatcher.class);

            verify(firstLoadSectorMock)
                .addTransientScript(firstSweep.capture());
            verify(secondLoadSectorMock)
                .addTransientScript(secondSweep.capture());

            assertThat(secondSweep.getValue())
                .isNotSameAs(firstSweep.getValue());
        }

        @Test
        void toleratesANullSector() {

            var sweepInstallOnNullSector = (Runnable) () ->
                MapSubstrateRefreshInstaller.installMapSubstrateSectorWatcher(null);

            assertThatCode(sweepInstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class RemoveMapSubstrateSectorWatcher {

        @Test
        void stopsTheSweepUnderItsOwnClassRatherThanALayers() {
            // The engine clears transient scripts by exact class, which is the whole reason this
            // poll has a class of its own: cleared as a layer's watcher it would go down whenever
            // a layer did, and the shared record would stop accruing with nothing to say so.
            var sectorMock = mock(SectorAPI.class);

            MapSubstrateRefreshInstaller.removeMapSubstrateSectorWatcher(sectorMock);

            verify(sectorMock)
                .removeTransientScriptsOfClass(MapSubstrateSectorWatcher.class);
            verify(sectorMock, never())
                .removeTransientScriptsOfClass(MapLayerSectorWatcher.class);
        }

        @Test
        void toleratesANullSector() {

            var sweepRemovalOnNullSector = (Runnable) () ->
                MapSubstrateRefreshInstaller.removeMapSubstrateSectorWatcher(null);

            assertThatCode(sweepRemovalOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallAll {

        @Test
        void registersTheSweepBehindItsFailureBoundary() {
            // The one step a load runs. Guarded, so a sector that refuses the registration costs
            // the sweep rather than the rest of the map layers standing up behind it.
            var sectorMock = mock(SectorAPI.class);

            MapSubstrateRefreshInstaller.installAll(sectorMock);

            verify(sectorMock)
                .addTransientScript(any(MapSubstrateSectorWatcher.class));
        }
    }

    @Nested
    class UninstallAll {

        @Test
        void clearsTheSweepItRegisters() {
            // What a player switching the overlay off asks for: the sweep would otherwise go on
            // walking the sector every few seconds for a feature nothing is drawing.
            var sectorMock = mock(SectorAPI.class);

            MapSubstrateRefreshInstaller.uninstallAll(sectorMock);

            verify(sectorMock)
                .removeTransientScriptsOfClass(MapSubstrateSectorWatcher.class);
        }
    }
}
