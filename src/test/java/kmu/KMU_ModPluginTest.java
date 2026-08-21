package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.render.MapSurfaceInstaller;
import kmu.maplayers.base.sidebar.runtime.SidebarInstaller;
import kmu.maplayers.base.tooltip.MapHoverInstaller;
import kmu.maplayers.politicalmap.base.PoliticalMapInstaller;
import kmu.settings.KmuFeatureSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Pins what the entry point itself is answerable for: being the plugin the engine constructs, and
 * the one branch it makes - whether the map layers are stood up at all, and when it re-decides.
 *
 * <p>What each installer registers is pinned beside that installer, not here. The entry point only
 * names them in order, and a suite that re-asserted their registrations would be a second copy of
 * every installer's own contract - one that passes for as long as nobody moves a listener.
 */
class KMU_ModPluginTest {

    @Nested
    class ModIdentity {

        @Test
        void extendsStarsectorBaseModPlugin() {

            assertThat(new KMU_ModPlugin())
                .isInstanceOf(BaseModPlugin.class);
        }
    }

    @Nested
    class ApplyMapLayerFeature {

        @Test
        void standsUpEverySurfaceWhileTheFeatureIsOn() {
            // The shipped state, and the one a player who has never opened the settings is in.
            var sectorMock = mock(SectorAPI.class);

            try (var installers = new MapLayerInstallerMocks()) {

                installers.setMapLayersEnabled(true);

                KMU_ModPlugin.applyMapLayerFeature(sectorMock);

                installers.verifyStoodUpFor(sectorMock);
            }
        }

        @Test
        void takesEverySurfaceBackWhileTheFeatureIsOff() {
            // Switched off has to leave the sector map as vanilla draws it, which is more than
            // declining to install: within the session that registered them the listeners and
            // scripts are still running, and the terrain entities persist into the save besides.
            var sectorMock = mock(SectorAPI.class);

            try (var installers = new MapLayerInstallerMocks()) {

                installers.setMapLayersEnabled(false);

                KMU_ModPlugin.applyMapLayerFeature(sectorMock);

                installers.verifyTakenBackFor(sectorMock);
                installers.verifyNothingStoodUpFor(sectorMock);
            }
        }
    }

    @Nested
    class ApplyMapLayerFeatureIfToggled {

        @Test
        void leavesTheOverlayAloneWhenSomeOtherSettingChanged() {
            // LunaLib announces that the settings changed rather than which one did, so acting every
            // time would tear the overlay down and rebuild it whenever an unrelated slider moved -
            // dropping the hover box's cached text and re-arming the frame claim for nothing.
            var sectorMock = mock(SectorAPI.class);

            try (var installers = new MapLayerInstallerMocks()) {

                installers.setMapLayersEnabled(true);

                KMU_ModPlugin.applyMapLayerFeature(sectorMock);
                installers.forgetWhatHasHappenedSoFar();

                KMU_ModPlugin.applyMapLayerFeatureIfToggled(sectorMock);

                installers.verifyNothingStoodUpFor(sectorMock);
                installers.verifyNothingTakenBackFor(sectorMock);
            }
        }

        @Test
        void takesTheOverlayBackWhenTheToggleItselfMoved() {
            // The point of the whole hookup: the switch is answered where the player flipped it,
            // rather than at whatever load happens next.
            var sectorMock = mock(SectorAPI.class);

            try (var installers = new MapLayerInstallerMocks()) {

                installers.setMapLayersEnabled(true);

                KMU_ModPlugin.applyMapLayerFeature(sectorMock);
                installers.forgetWhatHasHappenedSoFar();

                installers.setMapLayersEnabled(false);

                KMU_ModPlugin.applyMapLayerFeatureIfToggled(sectorMock);

                installers.verifyTakenBackFor(sectorMock);
            }
        }
    }

    // The four installers the entry point decides between, mocked together because every case here
    // is about which of them were called and which were not - a case holding only the ones it
    // asserts on would let an unmocked installer reach a real sector mock and answer for itself.
    private static final class MapLayerInstallerMocks implements AutoCloseable {

        private final MockedStatic<KmuFeatureSettings> featureSettingsMock =
            mockStatic(KmuFeatureSettings.class);
        private final MockedStatic<PoliticalMapInstaller> politicalMapMock =
            mockStatic(PoliticalMapInstaller.class);
        private final MockedStatic<MapSurfaceInstaller> surfaceMock =
            mockStatic(MapSurfaceInstaller.class);
        private final MockedStatic<SidebarInstaller> sidebarMock =
            mockStatic(SidebarInstaller.class);
        private final MockedStatic<MapHoverInstaller> hoverMock =
            mockStatic(MapHoverInstaller.class);

        private void setMapLayersEnabled(boolean areMapLayersEnabled) {

            featureSettingsMock
                .when(KmuFeatureSettings::areMapLayersEnabled)
                .thenReturn(areMapLayersEnabled);
        }

        // Clears the record so a case can establish what was last applied and then assert only on
        // what the call under test did, rather than on that plus the setup that preceded it.
        private void forgetWhatHasHappenedSoFar() {

            politicalMapMock.clearInvocations();
            surfaceMock.clearInvocations();
            sidebarMock.clearInvocations();
            hoverMock.clearInvocations();
        }

        private void verifyStoodUpFor(SectorAPI sector) {

            politicalMapMock.verify(() -> PoliticalMapInstaller.installAll(sector));
            surfaceMock.verify(() -> MapSurfaceInstaller.installAll(sector));
            sidebarMock.verify(() -> SidebarInstaller.installAll(sector));
            hoverMock.verify(() -> MapHoverInstaller.installAll(sector));
        }

        private void verifyTakenBackFor(SectorAPI sector) {

            politicalMapMock.verify(() -> PoliticalMapInstaller.uninstallAll(sector));
            surfaceMock.verify(() -> MapSurfaceInstaller.uninstallAll(sector));
            sidebarMock.verify(() -> SidebarInstaller.uninstallAll(sector));
            hoverMock.verify(() -> MapHoverInstaller.uninstallAll(sector));
        }

        private void verifyNothingStoodUpFor(SectorAPI sector) {

            politicalMapMock.verify(() -> PoliticalMapInstaller.installAll(sector), never());
            surfaceMock.verify(() -> MapSurfaceInstaller.installAll(sector), never());
            sidebarMock.verify(() -> SidebarInstaller.installAll(sector), never());
            hoverMock.verify(() -> MapHoverInstaller.installAll(sector), never());
        }

        private void verifyNothingTakenBackFor(SectorAPI sector) {

            politicalMapMock.verify(() -> PoliticalMapInstaller.uninstallAll(sector), times(0));
            surfaceMock.verify(() -> MapSurfaceInstaller.uninstallAll(sector), times(0));
            sidebarMock.verify(() -> SidebarInstaller.uninstallAll(sector), times(0));
            hoverMock.verify(() -> MapHoverInstaller.uninstallAll(sector), times(0));
        }

        @Override
        public void close() {

            hoverMock.close();
            sidebarMock.close();
            surfaceMock.close();
            politicalMapMock.close();
            featureSettingsMock.close();
        }
    }
}
