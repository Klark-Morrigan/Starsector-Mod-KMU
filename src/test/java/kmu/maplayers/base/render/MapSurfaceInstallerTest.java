package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.map.icons.MapIconReseater;
import kmlib.starsector.ui.map.presence.CampaignMapView;
import kmlib.starsector.ui.map.presence.SectorMapState;
import kmlib.starsector.ui.map.probes.MapIconLayeringProbe;
import kmlib.testfixtures.starsector.listeners.RecordingListenerManager;

import kmu.maplayers.base.installation.MapLayerInstallations;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static kmlib.testfixtures.starsector.listeners.SectorListenerFixtures.buildSector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how the render surfaces are stood up: the per-frame scripts as transient ones built fresh
 * per load and held by the sector they were installed on, and the frame boundary registered on that
 * sector's own claim.
 */
class MapSurfaceInstallerTest {

    // Advancing an EveryFrameScript from a test says nothing about elapsed time - the reseat reads
    // no clock - so the value only has to be one the engine could plausibly pass.
    private static final float ONE_FRAME = 0.016f;

    // The index is process-wide, so a sector installed on by one case would go on holding that
    // case's script for the next. Outside any Global stand-in on purpose: this index holds a logger
    // taken from Global at class load, so a first load inside a mocked scope would leave it null for
    // the rest of the JVM.
    @BeforeEach
    @AfterEach
    void clearEveryInstallation() {
        MapLayerInstallations.disposeEveryInstallation();
    }

    @Nested
    class InstallStarscapeTerrainReseater {

        @Test
        void installsTheReseaterAsATransientScript() {
            // addTransientScript, never addScript: this script takes the Starscape terrain out of
            // hyperspace for one advance, so one restored from a save alongside the one added on
            // load would have two latches racing to move and put back the same entity - and would
            // bake a library class's name into the file.
            var sectorMock = mock(SectorAPI.class);

            MapLayerInstallations.installMachineryOn(sectorMock);
            MapSurfaceInstaller.installStarscapeTerrainReseater(sectorMock);

            verify(sectorMock)
                .addTransientScript(any(MapIconReseater.class));
            verify(sectorMock, never())
                .addScript(any());
        }

        @Test
        void installsAFreshReseaterPerLoadSoTheFirstMapOpenIsStillReseated() {
            // The latch arms on the edge into "a Starscape map is showing", so a script carried
            // across loads would come back believing that edge had already passed and skip the
            // reseat the newly loaded sector's first map open is owed.
            var firstLoadSectorMock = mock(SectorAPI.class);
            var secondLoadSectorMock = mock(SectorAPI.class);

            MapLayerInstallations.installMachineryOn(firstLoadSectorMock);
            MapSurfaceInstaller.installStarscapeTerrainReseater(firstLoadSectorMock);

            MapLayerInstallations.installMachineryOn(secondLoadSectorMock);
            MapSurfaceInstaller.installStarscapeTerrainReseater(secondLoadSectorMock);

            var firstReseater = ArgumentCaptor.forClass(MapIconReseater.class);
            var secondReseater = ArgumentCaptor.forClass(MapIconReseater.class);

            verify(firstLoadSectorMock)
                .addTransientScript(firstReseater.capture());
            verify(secondLoadSectorMock)
                .addTransientScript(secondReseater.capture());

            assertThat(secondReseater.getValue())
                .isNotSameAs(firstReseater.getValue());
        }

        @Test
        void holdsEachSectorsReseaterUnderThatSectorsOwnInstallation() {
            // What one slot for the whole process could not do. A second sector installed on would
            // take the slot over, and the first sector's removal would then reach for the second
            // sector's script - taking nothing off the sector still running one, and asking the
            // wrong sector to drop a script it never had.
            var sectorMock = mock(SectorAPI.class);
            var otherSectorMock = mock(SectorAPI.class);

            MapLayerInstallations.installMachineryOn(sectorMock);
            MapSurfaceInstaller.installStarscapeTerrainReseater(sectorMock);

            MapLayerInstallations.installMachineryOn(otherSectorMock);
            MapSurfaceInstaller.installStarscapeTerrainReseater(otherSectorMock);

            var installedReseater = ArgumentCaptor.forClass(MapIconReseater.class);

            verify(sectorMock)
                .addTransientScript(installedReseater.capture());

            MapSurfaceInstaller.removeStarscapeTerrainReseater(sectorMock);

            verify(sectorMock)
                .removeTransientScript(installedReseater.getValue());
            verify(otherSectorMock, never())
                .removeTransientScript(any());
        }

        @Test
        void wiresTheReseatersPlacementReadToTheLiveWidgetProbe() {
            // The reseat decides from where the icon actually sits, and this is the only place that
            // read is bound to something that can answer it. Nothing downstream would notice a
            // binding that never reached the widget - a script handed an unreadable placement simply
            // stands down, which is also what it does on every ordinary frame.
            var sectorMock = mock(SectorAPI.class);

            // Loaded before Global is stood in for, and this is not optional: the installer holds a
            // logger in a static field initialised from Global, so a class first loaded inside a
            // mockStatic scope keeps a null logger for the rest of the JVM and faults every later
            // test that logs. Answering null for a null sector is its own contract, covered next door.
            MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain(null);

            MapLayerInstallations.installMachineryOn(sectorMock);

            try (var mapViewMock = mockStatic(CampaignMapView.class);
                    var globalMock = mockStatic(Global.class);
                    var layeringProbeMock = mockStatic(MapIconLayeringProbe.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                // The reseat is scoped to a Starscape map, so the placement is only read while one
                // is up - which makes this the state the wiring can be observed in at all.
                mapViewMock
                    .when(CampaignMapView::resolveSectorMapState)
                    .thenReturn(SectorMapState.SHOWING_IN_STARSCAPE_MODE);

                MapSurfaceInstaller.installStarscapeTerrainReseater(sectorMock);

                var reseater = ArgumentCaptor.forClass(MapIconReseater.class);

                verify(sectorMock)
                    .addTransientScript(reseater.capture());

                reseater.getValue().advance(ONE_FRAME);

                layeringProbeMock
                    .verify(() -> MapIconLayeringProbe.readLayeringOf(any()));
            }
        }

        @Test
        void looksUpTheTerrainOfTheSectorItWasInstalledOnRatherThanTheRunningOne() {
            // A reseat is held per installation, so the terrain it lifts must be the terrain of the
            // sector it was installed for. Reading the running sector instead would have one
            // sector's script lift another sector's entity - and leave its own sector's map fogged
            // where the band was meant to clear it.
            var installedSectorMock = mock(SectorAPI.class);
            var runningSectorMock = mock(SectorAPI.class);

            // Loaded before Global is stood in for, so the installer's logger is resolved outside
            // the stubbed scope rather than left null for the rest of the JVM.
            MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain(null);

            MapLayerInstallations.installMachineryOn(installedSectorMock);

            try (var mapViewMock = mockStatic(CampaignMapView.class);
                    var globalMock = mockStatic(Global.class);
                    var terrainMock = mockStatic(MapLayerTerrainInstaller.class);
                    var layeringProbeMock = mockStatic(MapIconLayeringProbe.class)) {

                // The two sectors disagree, which is the whole of what makes this a regression
                // rather than a restatement: a lookup off the global answers the running one.
                globalMock
                    .when(Global::getSector)
                    .thenReturn(runningSectorMock);

                mapViewMock
                    .when(CampaignMapView::resolveSectorMapState)
                    .thenReturn(SectorMapState.SHOWING_IN_STARSCAPE_MODE);

                MapSurfaceInstaller.installStarscapeTerrainReseater(installedSectorMock);

                var reseater = ArgumentCaptor.forClass(MapIconReseater.class);

                verify(installedSectorMock)
                    .addTransientScript(reseater.capture());

                reseater.getValue().advance(ONE_FRAME);

                terrainMock.verify(() -> MapLayerTerrainInstaller
                    .findAboveStarscapeNebulaeTerrain(installedSectorMock));
                terrainMock.verify(
                    () -> MapLayerTerrainInstaller
                        .findAboveStarscapeNebulaeTerrain(runningSectorMock),
                    never());
            }
        }

        @Test
        void toleratesANullSector() {

            var reseaterInstallOnNullSector = (Runnable) () ->
                MapSurfaceInstaller.installStarscapeTerrainReseater(null);

            assertThatCode(reseaterInstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class UninstallAll {

        @Test
        void takesTheFramePreparationClaimOffTheSectorAndStopsTheReseat() {
            // Within the session that registered them both are still running: the claim still
            // arbitrating a frame nothing prepares, and the reseat still moving an entity that has
            // just been removed. The terrain removal beside them is pinned on the terrain installer.
            var listenerManager = new RecordingListenerManager();
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getListenerManager())
                .thenReturn(listenerManager);

            MapLayerInstallations.installMachineryOn(sectorMock);
            MapSurfaceInstaller.installStarscapeTerrainReseater(sectorMock);

            var installedReseater = ArgumentCaptor.forClass(MapIconReseater.class);

            verify(sectorMock)
                .addTransientScript(installedReseater.capture());

            MapSurfaceInstaller.uninstallAll(sectorMock);

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(MapFramePreparationClaim.class);

            verify(sectorMock)
                .removeTransientScript(installedReseater.getValue());
        }

        @Test
        void toleratesANullSector() {

            var surfaceUninstallOnNullSector = (Runnable) () ->
                MapSurfaceInstaller.uninstallAll(null);

            assertThatCode(surfaceUninstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class RemoveStarscapeTerrainReseater {

        @Test
        void takesOffTheScriptThisInstalledRatherThanEveryScriptOfItsClass() {
            // MapIconReseater is KMLib's, so another mod may be running its own over the same
            // sector, and a removal by class would take that one with it.
            var sectorMock = mock(SectorAPI.class);

            MapLayerInstallations.installMachineryOn(sectorMock);
            MapSurfaceInstaller.installStarscapeTerrainReseater(sectorMock);

            var installedReseater = ArgumentCaptor.forClass(MapIconReseater.class);

            verify(sectorMock)
                .addTransientScript(installedReseater.capture());

            MapSurfaceInstaller.removeStarscapeTerrainReseater(sectorMock);

            verify(sectorMock)
                .removeTransientScript(installedReseater.getValue());
            verify(sectorMock, never())
                .removeTransientScriptsOfClass(any());
        }
    }

    @Nested
    class InstallMapFramePreparationClaim {

        @Test
        void registersTheSectorsOwnClaimFreshAsTransient() {
            // The claim the surfaces over this sector ask, not one built beside it: a listener
            // opening frames on a claim nothing consults would leave every surface preparing per
            // pass. Remove-then-add and transient besides - it holds where the current frame stands,
            // which is live view state that enters no save, and two registered would open the frame
            // twice, releasing a second preparation into the frame the first already handed out.
            var listenerManager = new RecordingListenerManager();
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getListenerManager())
                .thenReturn(listenerManager);

            var installation = MapLayerInstallations.installMachineryOn(sectorMock);

            MapSurfaceInstaller.installMapFramePreparationClaim(sectorMock);

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(MapFramePreparationClaim.class);

            assertThat(listenerManager.getAddedListeners())
                .singleElement()
                .isSameAs(MapFramePreparationClaim.resolveClaimIn(installation));

            assertThat(listenerManager.getAddedTransientFlags())
                .containsExactly(true);
        }

        @Test
        void registersAClaimNoPreviousSessionLeftArmed() {
            // What clearing the claim by hand used to be for. A claim standing at "preparation
            // taken" with the frame that took it long gone refuses every asker, and the overlay
            // freezes on whatever the last prepared frame built - so the claim a load registers has
            // to be one no earlier session reached. Installing the machinery is what makes it so.
            var listenerManager = new RecordingListenerManager();
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getListenerManager())
                .thenReturn(listenerManager);

            var previousInstallation = MapLayerInstallations.installMachineryOn(sectorMock);
            var previousClaim = MapFramePreparationClaim.resolveClaimIn(previousInstallation);

            previousClaim.renderInUICoordsBelowUI(null);
            previousClaim.claimPreparation();

            MapLayerInstallations.installMachineryOn(sectorMock);
            MapSurfaceInstaller.installMapFramePreparationClaim(sectorMock);

            assertThat(listenerManager.getAddedListeners())
                .singleElement()
                .isNotSameAs(previousClaim);

            assertThat(((MapFramePreparationClaim) listenerManager.getAddedListeners().get(0))
                    .claimPreparation())
                .isTrue();
        }

        @Test
        void toleratesAMissingListenerManager() {

            var claimInstallOnNullManager = (Runnable) () ->
                MapSurfaceInstaller.installMapFramePreparationClaim(buildSector(null));

            assertThatCode(claimInstallOnNullManager::run)
                .doesNotThrowAnyException();
        }
    }
}
