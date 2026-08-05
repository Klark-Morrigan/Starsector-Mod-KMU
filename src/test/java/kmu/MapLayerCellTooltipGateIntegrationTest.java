package kmu;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ViewportAPI;

import kmlib.starsector.ui.map.presence.CampaignMapView;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.tooltip.MapLayerCellTooltip;
import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the map gate the install site hands the hover-tooltip dispatcher: that it is the union of the
 * two map-presence reads and not either one alone. The dispatcher takes that read as a supplier and
 * so can say nothing about which map states open it; the presence seams each answer for one state and
 * know nothing of the other. The composition that joins them is a lambda at the install site, which
 * leaves this the only level where a gate quietly narrowed back to the schematic-only read - the state
 * that is false in exactly the mode the feature exists to reach - would be caught.
 *
 * <p>Integration by necessity rather than by preference: the union is one expression, so the only
 * way to exercise it is through everything it is made of - the mod plugin's install, both real
 * presence classes, their live intel-screen binding, and the dispatcher's own gate chain. Only the
 * sector map's view state is stood in for, being the one input with a seam to stand in at; the intel
 * half needs none, since off a running game it fails closed to "no visor" on its own.
 */
class MapLayerCellTooltipGateIntegrationTest {

    private final MapHoverTooltip tooltipMock = mock(MapHoverTooltip.class);
    private final MapLayer tooltipLayerMock = mock(MapLayer.class);
    private final MapLayerRenderer layerRendererMock = mock(MapLayerRenderer.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    @BeforeEach
    void hoverACellOnALayerShowingATooltip() {

        when(tooltipLayerMock.getId())
            .thenReturn("tooltip_layer");
        when(tooltipLayerMock.getMapRenderer())
            .thenReturn(layerRendererMock);

        when(layerRendererMock.resolveHoverTooltip())
            .thenReturn(Optional.of(tooltipMock));

        when(systemMock.getId())
            .thenReturn("system");
        when(sectorMock.getStarSystems())
            .thenReturn(List.of(systemMock));

        MapLayerRegistry.registerLayers(List.of(tooltipLayerMock), tooltipLayerMock);

        // The registry is static, so a screen left wired would outlive its test; a fresh fake starts
        // each test from the intel screen closed, which resolves reads to the map screen's pick.
        MapLayerRegistry.registerIntelScreen(new IntelScreenViewFake());

        // Everything below the map gate is open, so what the box does is the gate's answer alone.
        MapHoverState
            .getInstance()
            .publishHover(new MapHover("system", List.of("system")));
    }

    @AfterEach
    void restoreTheSharedState() {
        MapLayerRosters.restoreNonEmptyRoster();
        MapHoverState.getInstance().clearHover();
    }

    @Nested
    class InstalledMapGate {

        @Test
        void drawsWhileTheSectorMapIsInStarscapeMode() {
            // The mode the whole feature exists to reach, and the one the schematic read answers
            // false in by design. A gate that kept that read alone would hide the box exactly where
            // the starscape terrain half is painting the layers it belongs to.
            renderInstalledDispatcher(false, true);

            verify(tooltipMock)
                .renderFor(sectorMock, systemMock);
        }

        @Test
        void drawsWhileTheSectorMapIsShowingTheOrdinarySchematic() {
            // The half that already worked, kept honest: widening the gate must not have swapped one
            // single read for another.
            renderInstalledDispatcher(true, false);

            verify(tooltipMock)
                .renderFor(sectorMock, systemMock);
        }

        @Test
        void drawsNothingWhileNeitherHostShowsAMap() {
            // Both halves false - the ordinary state on every screen that is not a map, which this
            // listener is called for all the same. Without this, a gate wired permanently open would
            // pass the two cases above.
            renderInstalledDispatcher(false, false);

            verifyNoInteractions(tooltipMock);
        }
    }

    // Installs the dispatcher the way the game does and renders one frame of it, with the sector
    // map's view state answering as told and the intel half left to fail closed.
    //
    // The install happens outside the mocked statics on purpose: it is where the presence pair and
    // their live intel-screen binding are built, and that binding takes its logger from Global at
    // class-init, which a mocked Global would answer null for once and for the rest of the JVM.
    private void renderInstalledDispatcher(
            boolean isSectorMapWithStarscapeOff,
            boolean isSectorMapInStarscapeMode) {

        var dispatcher = installDispatcher();

        try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class);
             MockedStatic<CampaignMapView> mapViewMock = mockStatic(CampaignMapView.class);
             MockedStatic<Global> globalMock = mockStatic(Global.class)) {

            settingsMock
                .when(KmuMapLayerSettings::getMapHoveringEnabled)
                .thenReturn(true);
            settingsMock
                .when(KmuMapLayerSettings::getMapHoverTooltipEnabled)
                .thenReturn(true);

            mapViewMock
                .when(CampaignMapView::isSectorMapWithStarscapeOff)
                .thenReturn(isSectorMapWithStarscapeOff);
            mapViewMock
                .when(CampaignMapView::isSectorMapInStarscapeMode)
                .thenReturn(isSectorMapInStarscapeMode);

            globalMock
                .when(Global::getSector)
                .thenReturn(sectorMock);

            dispatcher.renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class));
        }
    }

    // The dispatcher the mod plugin registers, taken from the registration itself rather than built
    // here - constructing one locally would hand it a gate this test wrote, which is the very thing
    // under test.
    private MapLayerCellTooltip installDispatcher() {

        var listenerManagerMock = mock(ListenerManagerAPI.class);
        var installSectorMock = mock(SectorAPI.class);

        when(installSectorMock.getListenerManager())
            .thenReturn(listenerManagerMock);

        KMU_ModPlugin.installMapLayerHoverTooltip(installSectorMock);

        var installedListener = ArgumentCaptor.forClass(Object.class);
        
        verify(listenerManagerMock)
            .addListener(installedListener.capture(), eq(true));

        return (MapLayerCellTooltip) installedListener.getValue();
    }
}
