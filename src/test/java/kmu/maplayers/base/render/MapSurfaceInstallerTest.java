package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.map.icons.MapIconReseater;
import kmlib.starsector.ui.map.presence.CampaignMapView;
import kmlib.starsector.ui.map.presence.SectorMapState;
import kmlib.starsector.ui.map.probes.MapIconLayeringProbe;

import kmu.starsector.listeners.RecordingListenerManager;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static kmu.starsector.listeners.SectorListenerFixtures.buildSector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins how the render surfaces are stood up: the per-frame scripts as transient ones built fresh
 * per load, and the frame preparation claim cleared before anything can take it again.
 */
class MapSurfaceInstallerTest {

    // Advancing an EveryFrameScript from a test says nothing about elapsed time - the reseat reads
    // no clock - so the value only has to be one the engine could plausibly pass.
    private static final float ONE_FRAME = 0.016f;

    @Nested
    class InstallStarscapeTerrainReseater {

        @Test
        void installsTheReseaterAsATransientScript() {
            // addTransientScript, never addScript: this script takes the Starscape terrain out of
            // hyperspace for one advance, so one restored from a save alongside the one added on
            // load would have two latches racing to move and put back the same entity - and would
            // bake a library class's name into the file.
            var sectorMock = mock(SectorAPI.class);

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

            MapSurfaceInstaller.installStarscapeTerrainReseater(firstLoadSectorMock);
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
        void toleratesANullSector() {

            var reseaterInstallOnNullSector = (Runnable) () ->
                MapSurfaceInstaller.installStarscapeTerrainReseater(null);

            assertThatCode(reseaterInstallOnNullSector::run)
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class InstallMapFramePreparationClaim {

        @Test
        void reinstallsTheFramePreparationClaimFreshAsTransient() {
            // Remove-then-add, transient: it holds where the current frame stands, which is live view
            // state that enters no save, and two registered would open the frame twice - releasing a
            // second preparation into the frame the first already handed out.
            var listenerManager = new RecordingListenerManager(false);

            MapSurfaceInstaller.installMapFramePreparationClaim(buildSector(listenerManager));

            assertThat(listenerManager.getRemovedListenerClasses())
                .containsExactly(MapFramePreparationClaim.class);

            assertThat(listenerManager.getAddedListeners())
                .singleElement()
                .isSameAs(MapFramePreparationClaim.getInstance());

            assertThat(listenerManager.getAddedTransientFlags())
                .containsExactly(true);
        }

        @Test
        void clearsTheClaimEvenWhenNoListenerCanBeRegistered() {
            // The failure this ordering is for. With no listener manager nothing will ever open
            // another frame, so a claim left taken by the previous session would refuse every
            // preparation for the rest of this one and freeze the overlay on stale draw lists.
            // Clearing first is what turns that into preparing per pass instead.
            MapFramePreparationClaim.getInstance().renderInUICoordsBelowUI(null);
            MapFramePreparationClaim.getInstance().claimPreparation();

            MapSurfaceInstaller.installMapFramePreparationClaim(buildSector(null));

            assertThat(MapFramePreparationClaim.getInstance().claimPreparation())
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
