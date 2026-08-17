package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;

import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

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

    // The surface reads the compatibility constraint before it draws anything, and that read reaches
    // LunaLib, which no test has. Held for the class so every case runs against a stated constraint
    // rather than a live settings file; Mockito's own default answers it off, which is the shipped
    // default and what all but the constraint's own cases mean to describe.
    private MockedStatic<KmuMapLayerSettings> mapLayerSettingsMock;

    @BeforeEach
    void stubTheCompatibilityConstraint() {
        mapLayerSettingsMock = mockStatic(KmuMapLayerSettings.class);
    }

    @AfterEach
    void releaseTheCompatibilityConstraint() {
        mapLayerSettingsMock.close();
    }

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

    // The claim is a process-lifetime instance the surfaces reach through its singleton, so a frame
    // opened by one case would otherwise be the frame the next case's surface finds already
    // prepared. Cleared at both ends so neither the order within this class nor the order between
    // classes can decide whether a preparation happens.
    @BeforeEach
    @AfterEach
    void discardFramePreparationClaim() {
        MapFramePreparationClaim.getInstance().discardFrameTrackingFromPreviousSave();
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
        void renderOnMapPreparesOnceAcrossTwoSurfacesPaintingOneFrame() {
            // The band pinning alone cannot settle this: whether a surface draws is that surface's
            // own answer, so two can paint the lower band of one frame. Both must still draw their
            // bands - the frame is genuinely painted twice over - while the preparation behind them,
            // which steps the cursor's arrival latch, happens once.
            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                MapFramePreparationClaim.getInstance().renderInUICoordsBelowUI(null);

                new SectorMapLayerTerrainPlugin().renderOnMap(1.5f, 0.25f);
                new SectorMapLayerTerrainPlugin().renderOnMap(1.5f, 0.25f);

                verify(layerRendererMock, times(1))
                    .prepareFrame(anyFloat());
                verify(layerRendererMock, times(2))
                    .renderOnMap(1.5f, 0.25f, MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);
            }
        }

        @Test
        void renderOnMapPreparesAgainOnceTheNextFrameOpens() {
            // A claim spent for good would leave the map painting the draw lists of whichever frame
            // happened to prepare first, which is the opposite fault and the worse one.
            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                var claim = MapFramePreparationClaim.getInstance();
                var plugin = new SectorMapLayerTerrainPlugin();

                claim.renderInUICoordsBelowUI(null);
                plugin.renderOnMap(1.5f, 0.25f);

                claim.renderInUICoordsBelowUI(null);
                plugin.renderOnMap(1.5f, 0.25f);

                verify(layerRendererMock, times(2))
                    .prepareFrame(1.5f);
            }
        }

        @Test
        void renderOnMapDrawsNothingWhileConstrainedToItsHostsWithNoneShowing() {
            // The compatibility constraint, and the pass it exists to stop: a map another mod built
            // drives this same hook, and with the constraint on the layers are not that mod's to
            // draw. Nothing at all happens - not the preparation either, so a foreign pass cannot
            // take the frame's single preparation from the map that is entitled to it.
            mapLayerSettingsMock
                .when(KmuMapLayerSettings::getMapLayersOnlyOnTheirHosts)
                .thenReturn(true);

            try (var globalMock = mockStatic(Global.class)) {

                // No sector, so the presence read behind the constraint fails closed to no map
                // showing - which is the state a foreign pass runs in.
                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                new SectorMapLayerTerrainPlugin().renderOnMap(1.5f, 0.25f);

                verifyNoInteractions(layerRendererMock);
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
