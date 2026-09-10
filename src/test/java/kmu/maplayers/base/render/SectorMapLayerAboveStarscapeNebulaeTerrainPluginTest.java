package kmu.maplayers.base.render;

import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.settings.KmuMapHoverSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static kmu.maplayers.base.render.MapSurfaceFixtures.seatSurfacesInAnInstalledSector;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Pins the whole of what this surface adds over the one it extends: it paints the upper band and
 * nothing else, and it prepares no frame. Both are what make it a second pass over the same frame
 * rather than a second copy of it - painting the lower band here would double every fill the surface
 * beneath already drew, and preparing again would repeat a refresh and step the cursor's arrival
 * latch a second time for one frame.
 *
 * <p>The cursor read is the one thing it does share with the surface beneath, because that read
 * belongs to a pass rather than to a frame: this is the map's last pass under Starscape, so its
 * transform is the one the frame's answer should come from.
 *
 * <p>Every case seats the surface on an installed sector, the stand-aside one included: a surface
 * with nowhere to resolve its machinery draws nothing whatever else is staged, so a case left
 * unseated would pass without pinning anything.
 */
final class SectorMapLayerAboveStarscapeNebulaeTerrainPluginTest {

    private static final float FACTOR = 1f;
    private static final float ALPHA_MULT = 1f;

    // A screen with the whole of its layers on it, which is the state every case here poses: what the
    // hide fade does to the pass is the base surface's subject, both halves drawing through the one pass.
    private static final float FULLY_SHOWN = 1f;

    private final MapLayerRenderer layerRendererMock = mock(MapLayerRenderer.class);

    // The surface reads the compatibility constraint before it draws, and that read reaches LunaLib,
    // which no test has. Stubbed for the class rather than per case because every case here is about
    // the band and the preparation; Mockito's own default answers the constraint off, which is both
    // the shipped default and the state these cases mean to describe.
    private MockedStatic<KmuMapHoverSettings> mapLayerSettingsMock;

    // Stood in for the class, but left unstubbed until a case asks: the stand-aside case asserts that
    // the registry was never reached at all, which a stubbing set up for every case would spend.
    private MockedStatic<MapLayerRegistry> layerRegistryMock;

    // The screens' own stand-in, opened beside it: the surface asks the registry what draws and the
    // screens how far through a hide the showing one stands, and a case posing one poses both.
    private MockedStatic<MapLayerScreens> layerScreensMock;

    @BeforeEach
    void standInForTheLayerRegistryAndSettings() {

        mapLayerSettingsMock = mockStatic(KmuMapHoverSettings.class);
        layerRegistryMock = mockStatic(MapLayerRegistry.class);
        layerScreensMock = mockStatic(MapLayerScreens.class);
    }

    @AfterEach
    void releaseTheLayerRegistryAndSettings() {

        layerScreensMock.close();
        layerRegistryMock.close();
        mapLayerSettingsMock.close();
    }

    // The index is process-wide, so a sector installed on by one case would go on answering for the
    // next.
    @BeforeEach
    @AfterEach
    void clearEveryInstallation() {
        MapLayerInstallations.disposeEveryInstallation();
    }

    // Puts a renderer behind the active pick, on a screen showing its layers in full, for the cases
    // about what this surface draws through it. The fade is stated rather than left to the stand-in's
    // own default, which would be a screen with none of its layers on it and would take every alpha
    // below to nothing - the pass is what these cases are about, not the dissolve.
    private void stubTheActiveLayersRendererOnAShownScreen() {

        layerRegistryMock
            .when(() -> MapLayerRegistry.resolveDrawnMapRenderer(any()))
            .thenReturn(layerRendererMock);

        layerScreensMock
            .when(MapLayerScreens::resolveShownFadeOnLiveScreen)
            .thenReturn(FULLY_SHOWN);
    }

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapPaintsTheUpperBandWhileAStarscapeMapIsShowing() {

            var plugin = new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin(() -> true);

            seatSurfacesInAnInstalledSector(plugin);
            stubTheActiveLayersRendererOnAShownScreen();

            plugin.renderOnMap(FACTOR, ALPHA_MULT);

            verify(layerRendererMock)
                .renderOnMap(FACTOR, ALPHA_MULT, MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);
        }

        @Test
        void renderOnMapLeavesTheLowerBandToTheSurfaceBeneathTheNebulae() {

            var plugin = new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin(() -> true);

            seatSurfacesInAnInstalledSector(plugin);
            stubTheActiveLayersRendererOnAShownScreen();

            plugin.renderOnMap(FACTOR, ALPHA_MULT);

            verify(layerRendererMock, never())
                .renderOnMap(anyFloat(), anyFloat(), eq(MapOverlayBand.BENEATH_STARSCAPE_NEBULAE));
        }

        @Test
        void renderOnMapLeavesTheFramesPreparationToTheSurfaceBeneathTheNebulae() {

            var plugin = new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin(() -> true);

            seatSurfacesInAnInstalledSector(plugin);
            stubTheActiveLayersRendererOnAShownScreen();

            plugin.renderOnMap(FACTOR, ALPHA_MULT);

            verify(layerRendererMock, never())
                .prepareFrame(anyFloat());
        }

        @Test
        void renderOnMapPublishesTheHoverThoughItPreparesNoFrame() {
            // The split the plugin's own frame call cannot express: this surface leaves the frame's
            // preparation to the one beneath it and still reads the cursor, because it is the pass
            // that draws last under Starscape and the last read is the one the frame keeps.
            var plugin = new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin(() -> true);

            seatSurfacesInAnInstalledSector(plugin);
            stubTheActiveLayersRendererOnAShownScreen();

            plugin.renderOnMap(FACTOR, ALPHA_MULT);

            verify(layerRendererMock)
                .publishHoverForPass(FACTOR);
        }

        @Test
        void renderOnMapStandsAsideWhileNoStarscapeMapIsShowing() {
            // Inherited, and pinned here because it is the guard that keeps this surface off a
            // schematic map - where the base half already paints both bands and this one would lay a
            // second set of names over its own.
            var plugin = new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin(() -> false);

            seatSurfacesInAnInstalledSector(plugin);

            plugin.renderOnMap(FACTOR, ALPHA_MULT);

            layerRegistryMock
                .verifyNoInteractions();
        }
    }
}
