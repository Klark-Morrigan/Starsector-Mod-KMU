package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;

import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerRosters;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins what the map surface itself owes: the terrain override the map relies on, the dispatch that
 * makes it layer-agnostic, and the frame shape that dispatch imposes - both bands painted, bottom
 * first, after one preparation. {@code BaseTerrain.getActiveLayers} throws by default and
 * the engine calls it the moment the terrain is added on a fresh game, so failing to override it
 * crashed onGameLoad. The dispatch is pinned with a stand-in layer, since which concrete layers
 * exist is the composition root's business and the surface must not know: it draws through the
 * active pick's renderer, and treats an absent renderer or an absent pick alike as nothing to draw.
 * The draw-list build and GL emission live behind that renderer and are covered there; the emission
 * itself runs only in-engine.
 */
final class SectorMapLayerTerrainPluginTest {

    private final MapLayer drawingLayerMock = mock(MapLayer.class);
    private final MapLayerRenderer layerRendererMock = mock(MapLayerRenderer.class);
    private final IntelScreenViewFake intelScreenFake = new IntelScreenViewFake();

    @BeforeEach
    void registerADrawingLayer() {

        when(drawingLayerMock.getId())
            .thenReturn("drawing");
        when(drawingLayerMock.getMapRenderer())
            .thenReturn(layerRendererMock);

        MapLayerRegistry.registerLayers(List.of(drawingLayerMock), drawingLayerMock);

        // The registry is static, so a screen left wired would outlive its test; a fresh fake starts
        // each test from the intel screen closed, which resolves reads to the map screen's pick.
        MapLayerRegistry.registerIntelScreen(intelScreenFake);
    }

    @AfterEach
    void restoreARegisteredLayer() {
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @Nested
    class GetActiveLayers {

        @Test
        void getActiveLayersReturnsEmptyWithoutThrowing() {

            var plugin = new SectorMapLayerTerrainPlugin();

            assertThatCode(plugin::getActiveLayers)
                .doesNotThrowAnyException();
            assertThat(plugin.getActiveLayers())
                .isEmpty();
        }
    }

    @Nested
    class RenderOnMap {

        @Test
        void renderOnMapDrawsBothBandsThroughTheActiveLayersRendererWithTheFramesFactorAndAlpha() {

            try (var globalMock = mockStatic(Global.class)) {

                // No sector means no stored pick, so the registry resolves to the registered default.
                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                new SectorMapLayerTerrainPlugin().renderOnMap(1.5f, 0.25f);

                // Outside Starscape nothing of the map's is drawn between the bands, so this one
                // surface owes both of them - a band left unpainted here would be a sub-layer that
                // simply never appears on a schematic map.
                var bandOrder = inOrder(layerRendererMock);

                bandOrder
                    .verify(layerRendererMock)
                    .renderOnMap(1.5f, 0.25f, MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);
                bandOrder
                    .verify(layerRendererMock)
                    .renderOnMap(1.5f, 0.25f, MapOverlayBand.ABOVE_STARSCAPE_NEBULAE);
            }
        }

        @Test
        void renderOnMapPreparesTheFrameOnceBeforeDrawingAnyBand() {
            // The refresh and the cursor read run once however many passes the frame is painted in:
            // a second cursor read would resolve against a half-drawn frame, and a second refresh
            // would repeat the whole staleness check for nothing.
            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                new SectorMapLayerTerrainPlugin().renderOnMap(1.5f, 0.25f);

                var preparationOrder = inOrder(layerRendererMock);

                preparationOrder
                    .verify(layerRendererMock)
                    .prepareFrame(1.5f);
                preparationOrder
                    .verify(layerRendererMock)
                    .renderOnMap(1.5f, 0.25f, MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);

                verify(layerRendererMock, times(1))
                    .prepareFrame(anyFloat());
            }
        }

        @Test
        void renderOnMapDrawsNothingWhenTheActiveLayerHasNoRenderer() {
            // The "show nothing" tab's shape: a registered layer that simply supplies no renderer,
            // which must stay an ordinary layer here rather than a named special case.
            var silentLayerMock = mock(MapLayer.class);

            when(silentLayerMock.getId())
                .thenReturn("silent");

            MapLayerRegistry.registerLayers(List.of(silentLayerMock), silentLayerMock);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                new SectorMapLayerTerrainPlugin().renderOnMap(1f, 1f);

                verifyNoInteractions(layerRendererMock);
            }
        }

        @Test
        void renderOnMapDrawsNothingWithoutAnActiveLayer() {
            // The pre-registration frame: the terrain can be added before any composition root has run,
            // so the surface must survive a null pick rather than dereference it.
            MapLayerRegistry.registerLayers(List.of(), null);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                var plugin = new SectorMapLayerTerrainPlugin();

                assertThatCode(() -> plugin.renderOnMap(1f, 1f))
                    .doesNotThrowAnyException();
                    
                verifyNoInteractions(layerRendererMock);
            }
        }
    }
}
