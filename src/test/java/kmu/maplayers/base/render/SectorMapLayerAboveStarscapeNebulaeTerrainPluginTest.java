package kmu.maplayers.base.render;

import kmu.maplayers.base.layer.MapLayerRegistry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
