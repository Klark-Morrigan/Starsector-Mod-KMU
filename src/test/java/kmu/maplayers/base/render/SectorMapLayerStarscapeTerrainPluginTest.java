package kmu.maplayers.base.render;

import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.settings.KmuMapLayerSettings;

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
 * Pins the two things this half adds to the map's draw: it paints only while a Starscape map is on
 * screen, so no two surfaces lay the same overlay down in one frame, and it paints the lower band
 * alone - the half of the picture the map's nebulae are allowed to fog. It is also the surface
 * that prepares the frame, that being what the lower band carries with it, while the cursor read it
 * takes beside that is a pass's own and is taken by the surface above it as well.
 *
 * <p>Every case seats the surface on an installed sector, the stand-aside one included: a surface
 * with nowhere to resolve its machinery draws nothing whatever else is staged, so a case left
 * unseated would pass without pinning anything.
 */
final class SectorMapLayerStarscapeTerrainPluginTest {

    private static final float FACTOR = 1f;
    private static final float ALPHA_MULT = 1f;

    private final MapLayerRenderer layerRendererMock = mock(MapLayerRenderer.class);

    // The surface reads the compatibility constraint before it draws, and that read reaches LunaLib,
    // which no test has. Stubbed for the class rather than per case because every case here is about
    // the bands and the stand-aside; Mockito's own default answers the constraint off, which is both
    // the shipped default and the state these cases mean to describe.
    private MockedStatic<KmuMapLayerSettings> mapLayerSettingsMock;

    // Stood in for the class, but left unstubbed until a case asks: the stand-aside case asserts that
    // the registry was never reached at all, which a stubbing set up for every case would spend.
    private MockedStatic<MapLayerRegistry> layerRegistryMock;

    @BeforeEach
    void standInForTheLayerRegistryAndSettings() {

        mapLayerSettingsMock = mockStatic(KmuMapLayerSettings.class);
        layerRegistryMock = mockStatic(MapLayerRegistry.class);
    }

    @AfterEach
    void releaseTheLayerRegistryAndSettings() {

        layerRegistryMock.close();
        mapLayerSettingsMock.close();
    }

    // The index is process-wide, so a sector installed on by one case would go on answering for the
    // next - including with the frame it left half prepared, the claim being the installation's.
    @BeforeEach
    @AfterEach
    void clearEveryInstallation() {
        MapLayerInstallations.disposeEveryInstallation();
    }

    // Puts a renderer behind the active pick, for the cases about what this surface draws through it.
    private void stubTheActiveLayersRenderer() {
        layerRegistryMock
            .when(() -> MapLayerRegistry.resolveActiveMapRenderer(any()))
            .thenReturn(layerRendererMock);
    }

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapPaintsTheLowerBandWhileAStarscapeMapIsShowing() {

            var plugin = new SectorMapLayerStarscapeTerrainPlugin(() -> true);

            seatSurfacesInAnInstalledSector(plugin);
            stubTheActiveLayersRenderer();

            plugin.renderOnMap(FACTOR, ALPHA_MULT);

            verify(layerRendererMock)
                .renderOnMap(FACTOR, ALPHA_MULT, MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);
        }

        @Test
        void renderOnMapLeavesTheUpperBandToTheSurfaceAboveTheNebulae() {
            // Painting it here as well would put the names back under the fog: this surface's icon
            // sits beneath the nebulae, so anything it emits is drawn beneath them whatever it is.
            var plugin = new SectorMapLayerStarscapeTerrainPlugin(() -> true);

            seatSurfacesInAnInstalledSector(plugin);
            stubTheActiveLayersRenderer();

            plugin.renderOnMap(FACTOR, ALPHA_MULT);

            verify(layerRendererMock, never())
                .renderOnMap(anyFloat(), anyFloat(), eq(MapOverlayBand.ABOVE_STARSCAPE_NEBULAE));
        }

        @Test
        void renderOnMapPreparesTheFrameForTheSurfaceAboveItAsWell() {
            // The upper surface prepares nothing, so this is the frame's only preparation whenever
            // Starscape is the look on screen.
            var plugin = new SectorMapLayerStarscapeTerrainPlugin(() -> true);

            seatSurfacesInAnInstalledSector(plugin);
            stubTheActiveLayersRenderer();

            plugin.renderOnMap(FACTOR, ALPHA_MULT);

            verify(layerRendererMock)
                .prepareFrame(FACTOR);
        }

        @Test
        void renderOnMapPublishesTheHoverForItsOwnPass() {
            // Both Starscape surfaces read, this one included: the read inverts the transform its
            // own pass bound, so a surface skipping it would leave the frame's answer to whichever
            // other pass drew - the fault this arrangement exists to close.
            var plugin = new SectorMapLayerStarscapeTerrainPlugin(() -> true);

            seatSurfacesInAnInstalledSector(plugin);
            stubTheActiveLayersRenderer();

            plugin.renderOnMap(FACTOR, ALPHA_MULT);

            verify(layerRendererMock)
                .publishHoverForPass(FACTOR);
        }

        @Test
        void renderOnMapStandsAsideWhileNoStarscapeMapIsShowing() {
            // The engine calls this half in either mode, so standing aside is the only thing
            // keeping it off the map while the base half is the one already drawing there.
            var plugin = new SectorMapLayerStarscapeTerrainPlugin(() -> false);

            seatSurfacesInAnInstalledSector(plugin);

            plugin.renderOnMap(FACTOR, ALPHA_MULT);

            layerRegistryMock
                .verifyNoInteractions();
        }
    }
}
