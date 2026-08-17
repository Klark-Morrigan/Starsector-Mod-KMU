package kmu.maplayers.base.render;

import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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
 * beneath already drew, and preparing again would resolve the cursor a second time against a frame
 * already half painted.
 */
final class SectorMapLayerAboveStarscapeNebulaeTerrainPluginTest {

    private static final float FACTOR = 1f;
    private static final float ALPHA_MULT = 1f;

    // The surface reads the compatibility constraint before it draws, and that read reaches LunaLib,
    // which no test has. Stubbed for the class rather than per case because every case here is about
    // the band and the preparation; Mockito's own default answers the constraint off, which is both
    // the shipped default and the state these cases mean to describe.
    private MockedStatic<KmuMapLayerSettings> mapLayerSettingsMock;

    @BeforeEach
    void stubTheCompatibilityConstraint() {
        mapLayerSettingsMock = mockStatic(KmuMapLayerSettings.class);
    }

    @AfterEach
    void releaseTheCompatibilityConstraint() {
        mapLayerSettingsMock.close();
    }

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapPaintsTheUpperBandWhileAStarscapeMapIsShowing() {

            var layerRendererMock = mock(MapLayerRenderer.class);
            try (var layerRegistryMock = mockStatic(MapLayerRegistry.class)) {

                layerRegistryMock
                    .when(MapLayerRegistry::resolveActiveMapRenderer)
                    .thenReturn(layerRendererMock);

                new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin(() -> true)
                    .renderOnMap(FACTOR, ALPHA_MULT);

                verify(layerRendererMock)
                    .renderOnMap(FACTOR, ALPHA_MULT, MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);
            }
        }

        @Test
        void renderOnMapLeavesTheLowerBandToTheSurfaceBeneathTheNebulae() {

            var layerRendererMock = mock(MapLayerRenderer.class);

            try (var layerRegistryMock = mockStatic(MapLayerRegistry.class)) {

                layerRegistryMock
                    .when(MapLayerRegistry::resolveActiveMapRenderer)
                    .thenReturn(layerRendererMock);

                new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin(() -> true)
                    .renderOnMap(FACTOR, ALPHA_MULT);

                verify(layerRendererMock, never())
                    .renderOnMap(anyFloat(), anyFloat(),
                        eq(MapOverlayBand.BENEATH_STARSCAPE_NEBULAE));
            }
        }

        @Test
        void renderOnMapLeavesTheFramesPreparationToTheSurfaceBeneathTheNebulae() {

            var layerRendererMock = mock(MapLayerRenderer.class);

            try (var layerRegistryMock = mockStatic(MapLayerRegistry.class)) {

                layerRegistryMock
                    .when(MapLayerRegistry::resolveActiveMapRenderer)
                    .thenReturn(layerRendererMock);

                new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin(() -> true)
                    .renderOnMap(FACTOR, ALPHA_MULT);

                verify(layerRendererMock, never())
                    .prepareFrame(anyFloat());
            }
        }

        @Test
        void renderOnMapStandsAsideWhileNoStarscapeMapIsShowing() {
            // Inherited, and pinned here because it is the guard that keeps this surface off a
            // schematic map - where the base half already paints both bands and this one would lay a
            // second set of names over its own.
            try (var layerRegistryMock = mockStatic(MapLayerRegistry.class)) {

                new SectorMapLayerAboveStarscapeNebulaeTerrainPlugin(() -> false)
                    .renderOnMap(FACTOR, ALPHA_MULT);

                layerRegistryMock
                    .verifyNoInteractions();
            }
        }
    }
}
