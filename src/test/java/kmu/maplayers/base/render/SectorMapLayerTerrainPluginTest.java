package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;

import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.installation.MapLayerInstallations;
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

import static kmu.maplayers.base.render.MapSurfaceFixtures.seatSurfaceIn;
import static kmu.maplayers.base.render.MapSurfaceFixtures.seatSurfacesInAnInstalledSector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins what the map surface itself owes: the terrain override the map relies on, the sector it
 * resolves the frame for, the dispatch that makes it layer-agnostic, and the frame shape that
 * dispatch imposes - both bands painted, bottom first, after one preparation.
 * {@code BaseTerrain.getActiveLayers} throws by default and the engine calls it the moment the
 * terrain is added on a fresh game, so failing to override it crashed onGameLoad. The dispatch is
 * pinned with a stand-in layer, since which concrete layers exist is the composition root's business
 * and the surface must not know: it draws through the active pick's renderer, and treats an absent
 * renderer or an absent pick alike as nothing to draw. The draw-list build and GL emission live
 * behind that renderer and are covered there; the emission itself runs only in-engine.
 *
 * <p>Every case seats the surface on an installed sector first, that being what a surface needs
 * before it draws anything at all: it finds the machinery it paints through from the terrain entity
 * it rides on, having no other handle on the sector.
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
        // Whichever installation the surface resolves for the frame: which sector it draws is the
        // surface's to settle, and what this pins is that it draws through the answer it gets.
        when(drawingLayerMock.resolveRenderer(any()))
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

    // The index is process-wide, so a sector installed on by one case would otherwise still be
    // answering for the next - including with the frame it left half prepared, the claim being the
    // installation's. Cleared at both ends so neither the order within this class nor the order
    // between classes can decide what a surface resolves.
    //
    // Outside any Global stand-in on purpose: this index holds a logger taken from Global at class
    // load, so a first load inside a mocked scope would leave it null for the rest of the JVM.
    @BeforeEach
    @AfterEach
    void clearEveryInstallation() {
        MapLayerInstallations.disposeEveryInstallation();
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
        void renderOnMapDrawsThroughTheInstallationOfTheSectorItsTerrainSitsIn() {
            // The hook names no sector, and this surface cannot be handed one - it is rebuilt from
            // the save with no seam to inject through - so the entity it rides on is what says which
            // sector's machinery the frame belongs to. A surface resolving anything else would paint
            // one sector's overlay from another sector's cells.
            var plugin = new SectorMapLayerTerrainPlugin();
            var installation = seatSurfacesInAnInstalledSector(plugin);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                plugin.renderOnMap(1.5f, 0.25f);

                verify(drawingLayerMock)
                    .resolveRenderer(installation);
            }
        }

        @Test
        void renderOnMapDrawsNothingWhileItsTerrainSitsWhereNothingIsInstalled() {
            // A surface whose location has no machinery belongs to a sector nothing is drawing - a
            // save carrying the terrain with the overlay switched off, or a sector the layers were
            // taken off. Standing down is what keeps it from painting through the holder every
            // sector-less caller shares, which is a drawing of no sector at all.
            var plugin = new SectorMapLayerTerrainPlugin();

            seatSurfaceIn(plugin, mock(LocationAPI.class));

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                plugin.renderOnMap(1.5f, 0.25f);

                verifyNoInteractions(layerRendererMock);
            }
        }

        @Test
        void renderOnMapDrawsNothingBeforeTheEngineHasSeatedItsEntity() {
            // The entity is set on init, and a plugin the engine has built but not yet initialised
            // has no handle to resolve through at all. Reading past it would fault the render pass
            // rather than skip one frame.
            var plugin = new SectorMapLayerTerrainPlugin();

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThatCode(() -> plugin.renderOnMap(1.5f, 0.25f))
                    .doesNotThrowAnyException();

                verifyNoInteractions(layerRendererMock);
            }
        }

        @Test
        void renderOnMapDrawsBothBandsThroughTheActiveLayersRendererWithTheFramesFactorAndAlpha() {

            var plugin = new SectorMapLayerTerrainPlugin();

            seatSurfacesInAnInstalledSector(plugin);

            try (var globalMock = mockStatic(Global.class)) {

                // No sector means no stored pick, so the registry resolves to the registered default.
                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                plugin.renderOnMap(1.5f, 0.25f);

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
            // The refresh runs once however many passes the frame is painted in - a second one would
            // repeat the whole staleness check for nothing - and so does everything latched behind
            // it, the moment the cursor reaches a cell above all.
            var plugin = new SectorMapLayerTerrainPlugin();

            seatSurfacesInAnInstalledSector(plugin);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                plugin.renderOnMap(1.5f, 0.25f);

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
            var plugin = new SectorMapLayerTerrainPlugin();
            var secondPlugin = new SectorMapLayerTerrainPlugin();

            var installation = seatSurfacesInAnInstalledSector(plugin, secondPlugin);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                MapFramePreparationClaim.resolveClaimIn(installation).renderInUICoordsBelowUI(null);

                plugin.renderOnMap(1.5f, 0.25f);
                secondPlugin.renderOnMap(1.5f, 0.25f);

                verify(layerRendererMock, times(1))
                    .prepareFrame(anyFloat());
                verify(layerRendererMock, times(2))
                    .renderOnMap(1.5f, 0.25f, MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);
            }
        }

        @Test
        void renderOnMapPreparesEachSectorsFrameOnThatSectorsOwnClaim() {
            // Two sectors drawing in one frame each owe their own draw lists a preparation, so the
            // count that keeps two surfaces of one map to a single preparation must not reach across
            // maps: a shared claim would leave the second sector's overlay painting draw lists
            // nothing brought up to date.
            var plugin = new SectorMapLayerTerrainPlugin();
            var otherSectorsPlugin = new SectorMapLayerTerrainPlugin();

            var installation = seatSurfacesInAnInstalledSector(plugin);
            var otherInstallation = seatSurfacesInAnInstalledSector(otherSectorsPlugin);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                MapFramePreparationClaim.resolveClaimIn(installation).renderInUICoordsBelowUI(null);
                MapFramePreparationClaim.resolveClaimIn(otherInstallation)
                    .renderInUICoordsBelowUI(null);

                plugin.renderOnMap(1.5f, 0.25f);
                otherSectorsPlugin.renderOnMap(1.5f, 0.25f);

                verify(layerRendererMock, times(2))
                    .prepareFrame(anyFloat());
            }
        }

        @Test
        void renderOnMapPublishesTheHoverOnEveryPassOfOneFrame() {
            // The read is the one piece of per-frame work that turns on which pass is running: it
            // inverts the transform that pass bound. Pinned to the frame's first pass it would be
            // taken through whichever surface drew earliest - a map another mod composited, drawn
            // from the campaign HUD before the map screen - and the real map would then draw a hover
            // resolved through somebody else's zoom and pan. So every pass reads and the last wins,
            // while the preparation beside it still happens once.
            var plugin = new SectorMapLayerTerrainPlugin();
            var secondPlugin = new SectorMapLayerTerrainPlugin();

            var installation = seatSurfacesInAnInstalledSector(plugin, secondPlugin);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                MapFramePreparationClaim.resolveClaimIn(installation).renderInUICoordsBelowUI(null);

                plugin.renderOnMap(1.5f, 0.25f);
                secondPlugin.renderOnMap(1.5f, 0.25f);

                verify(layerRendererMock, times(2))
                    .publishHoverForPass(1.5f);
                verify(layerRendererMock, times(1))
                    .prepareFrame(anyFloat());
            }
        }

        @Test
        void renderOnMapPublishesTheHoverBeforeDrawingAnyBand() {
            // The highlight rides the same draw lists as the fill, so the answer has to be standing
            // before this pass emits any of them.
            var plugin = new SectorMapLayerTerrainPlugin();

            seatSurfacesInAnInstalledSector(plugin);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                plugin.renderOnMap(1.5f, 0.25f);

                var hoverOrder = inOrder(layerRendererMock);

                hoverOrder
                    .verify(layerRendererMock)
                    .publishHoverForPass(1.5f);
                hoverOrder
                    .verify(layerRendererMock)
                    .renderOnMap(1.5f, 0.25f, MapOverlayBand.BENEATH_STARSCAPE_NEBULAE);
            }
        }

        @Test
        void renderOnMapPreparesAgainOnceTheNextFrameOpens() {
            // A claim spent for good would leave the map painting the draw lists of whichever frame
            // happened to prepare first, which is the opposite fault and the worse one.
            var plugin = new SectorMapLayerTerrainPlugin();
            var installation = seatSurfacesInAnInstalledSector(plugin);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                var claim = MapFramePreparationClaim.resolveClaimIn(installation);

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

            var plugin = new SectorMapLayerTerrainPlugin();

            seatSurfacesInAnInstalledSector(plugin);

            try (var globalMock = mockStatic(Global.class)) {

                // No sector, so the presence read behind the constraint fails closed to no map
                // showing - which is the state a foreign pass runs in.
                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                plugin.renderOnMap(1.5f, 0.25f);

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

            var plugin = new SectorMapLayerTerrainPlugin();

            seatSurfacesInAnInstalledSector(plugin);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                plugin.renderOnMap(1f, 1f);

                verifyNoInteractions(layerRendererMock);
            }
        }

        @Test
        void renderOnMapDrawsNothingWithoutAnActiveLayer() {
            // The pre-registration frame: the terrain can be added before any composition root has run,
            // so the surface must survive a null pick rather than dereference it.
            MapLayerRegistry.registerLayers(List.of(), null);

            var plugin = new SectorMapLayerTerrainPlugin();

            seatSurfacesInAnInstalledSector(plugin);

            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThatCode(() -> plugin.renderOnMap(1f, 1f))
                    .doesNotThrowAnyException();

                verifyNoInteractions(layerRendererMock);
            }
        }
    }
}
