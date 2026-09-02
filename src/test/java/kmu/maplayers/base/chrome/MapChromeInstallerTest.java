package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.campaign.SectorAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins how the pass that maintains the filter row's tick box is registered: once, transient, and
 * cleared before it is added, since two of them would put two boxes over one pick.
 *
 * <p>And that neither half can take the load down with it. This installer's whole subject is a reach
 * into another party's widget, so a sector that refuses the registration has to cost the box and
 * leave every installer named after it still to run.
 */
final class MapChromeInstallerTest {

    @Nested
    class InstallAll {

        @Test
        void installsTheUpkeepAsATransientScriptClearedFirst() {
            // addTransientScript, never addScript: an EveryFrameScript that entered the save would
            // be restored beside the one each load adds, and the two would each find the row bare of
            // their own box and append one. Cleared first for the same reason.
            var sectorMock = mock(SectorAPI.class);

            MapChromeInstaller.installAll(sectorMock);

            verify(sectorMock)
                .removeTransientScriptsOfClass(MapLayerToggleUpkeep.class);
            verify(sectorMock)
                .addTransientScript(any(MapLayerToggleUpkeep.class));
            verify(sectorMock, never())
                .addScript(any());
        }

        @Test
        void swallowsASectorThatRefusesTheRegistration() {
            // The guard is the point: this is the one installer whose subject is a write into
            // another party's widget, and a load that aborted here would take every installer after
            // it with it - a player would lose the whole overlay over a control they never asked for.
            var sectorMock = mock(SectorAPI.class);
            doThrow(new IllegalStateException("no scripts"))
                .when(sectorMock)
                .addTransientScript(any());

            var chromeInstallOnRefusingSector = (Runnable) () ->
                MapChromeInstaller.installAll(sectorMock);

            assertThatCode(chromeInstallOnRefusingSector::run)
                .doesNotThrowAnyException();
        }

        @Test
        void toleratesAMissingSector() {

            var chromeInstallOnNoSector = (Runnable) () -> MapChromeInstaller.installAll(null);

            assertThatCode(chromeInstallOnNoSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class UninstallAll {

        @Test
        void stopsTheUpkeepByItsOwnClassAndRegistersNothingBack() {
            // By class rather than by instance, which is safe only because the script is this mod's
            // own: no sibling mod runs one over the same sector to be taken out with it.
            var sectorMock = mock(SectorAPI.class);

            MapChromeInstaller.uninstallAll(sectorMock);

            verify(sectorMock)
                .removeTransientScriptsOfClass(MapLayerToggleUpkeep.class);
            verify(sectorMock, never())
                .addTransientScript(any());
        }

        @Test
        void toleratesAMissingSector() {

            var chromeUninstallOnNoSector = (Runnable) () -> MapChromeInstaller.uninstallAll(null);

            assertThatCode(chromeUninstallOnNoSector::run)
                .doesNotThrowAnyException();
        }
    }
}
