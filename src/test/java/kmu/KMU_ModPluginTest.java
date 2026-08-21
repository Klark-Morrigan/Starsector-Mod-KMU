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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * Pins what the entry point itself is answerable for: being the plugin the engine constructs, and
 * the one branch it makes - whether the map layers are stood up at all.
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
    class InstallMapLayers {

        @Test
        void standsUpEverySurfaceWhileTheFeatureIsOn() {
            // The shipped state, and the one a player who has never opened the settings is in.
            var sectorMock = mock(SectorAPI.class);

            try (var featureSettingsMock = mockStatic(KmuFeatureSettings.class);
                    var politicalMapMock = mockStatic(PoliticalMapInstaller.class);
                    var surfaceMock = mockStatic(MapSurfaceInstaller.class);
                    var sidebarMock = mockStatic(SidebarInstaller.class);
                    var hoverMock = mockStatic(MapHoverInstaller.class)) {

                featureSettingsMock
                    .when(KmuFeatureSettings::areMapLayersEnabled)
                    .thenReturn(true);

                KMU_ModPlugin.installMapLayers(sectorMock);

                politicalMapMock.verify(() -> PoliticalMapInstaller.installAll(sectorMock));
                surfaceMock.verify(() -> MapSurfaceInstaller.installAll(sectorMock));
                sidebarMock.verify(() -> SidebarInstaller.installAll(sectorMock));
                hoverMock.verify(() -> MapHoverInstaller.installAll(sectorMock));
            }
        }

        @Test
        void takesTheSurfacesBackAndStandsUpNothingWhileTheFeatureIsOff() {
            // Switched off has to mean the sector map is left as vanilla draws it, which is more
            // than declining to install: the terrain entities a previous load added persist in the
            // save, so they have to be taken out or the feature is off everywhere but on screen.
            var sectorMock = mock(SectorAPI.class);

            try (var featureSettingsMock = mockStatic(KmuFeatureSettings.class);
                    var politicalMapMock = mockStatic(PoliticalMapInstaller.class);
                    var surfaceMock = mockStatic(MapSurfaceInstaller.class);
                    var sidebarMock = mockStatic(SidebarInstaller.class);
                    var hoverMock = mockStatic(MapHoverInstaller.class)) {

                featureSettingsMock
                    .when(KmuFeatureSettings::areMapLayersEnabled)
                    .thenReturn(false);

                KMU_ModPlugin.installMapLayers(sectorMock);

                surfaceMock.verify(() -> MapSurfaceInstaller.uninstallAll(sectorMock));

                surfaceMock.verify(
                    () -> MapSurfaceInstaller.installAll(sectorMock),
                    never());

                politicalMapMock.verify(
                    () -> PoliticalMapInstaller.installAll(sectorMock),
                    never());

                sidebarMock.verify(
                    () -> SidebarInstaller.installAll(sectorMock),
                    never());

                hoverMock.verify(
                    () -> MapHoverInstaller.installAll(sectorMock),
                    never());
            }
        }
    }
}
