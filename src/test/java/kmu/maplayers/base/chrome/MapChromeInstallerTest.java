package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import kmu.maplayers.base.layer.MapLayerScreenControls;
import kmu.maplayers.base.layer.MapLayerScreens;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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
 *
 * <p>Clearing reaches past the script to what the screens believe, which is the half a registration
 * check would miss: the screens are held for the process while the picks they govern read the loaded
 * sector, so the word that a screen has a control is the one thing here able to cross from one
 * campaign into the next.
 */
final class MapChromeInstallerTest {

    // The map screen's frozen save key, restated rather than reached for: it is the one thing about
    // this that a rename must not silently follow.
    private static final String MAP_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_map";

    @AfterEach
    void forgetTheControlsThisCaseStood() {
        // The screens are static and the word never clears itself, so a case that stood one would
        // otherwise decide what every later case in the JVM reads.
        MapLayerScreenControls.forgetControlsAttached();
    }

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
        void installsOverNoControlCarriedInFromAnEarlierCampaign() {

            // An earlier campaign in this session, which got as far as standing a box on a row. Posed
            // before the sector below rather than inside it, and that is not only narrative order: the
            // fake stands in for Global wholesale, and the pass resolves its logger once, when its
            // class initialises. First initialised inside the stand-in, that logger is nothing for the
            // rest of the JVM.
            MapChromeInstaller.installAll(mock(SectorAPI.class));
            MapLayerScreenControls.standAControlOnTheShownScreen();

            try (var sectorMemoryFake = new SectorMemoryFake()) {

                // The next campaign, whose own save holds the layers hidden. The word belongs to the
                // process and the hide to the sector, so without the clearing this campaign acts on
                // its hide from the first frame on the strength of the previous campaign's box - with
                // no box yet on any row here, and possibly no room for one.
                sectorMemoryFake.storeValue(MAP_LAYERS_SHOWN_KEY, false);

                MapChromeInstaller.installAll(mock(SectorAPI.class));

                assertThat(MapLayerScreens.getMapPicks().layerVisibility().areLayersShown())
                    .isTrue();
            }
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
