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
 * Pins the map gate the install site hands the hover-tooltip dispatcher: that it is the look-blind
 * read, and not one of the look-aware ones. Which read is wired in is a choice made at the install
 * site alone - the dispatcher takes it as a supplier and so can say nothing about which map states
 * open it, and each presence class answers only for itself - so a gate narrowed to the schematic
 * read, which is false in exactly the mode this feature exists to reach, would otherwise pass every
 * other test in the suite.
 *
 * <p>Integration by necessity rather than by preference: the wiring is one expression, so the only
 * way to exercise it is through everything it reaches - the mod plugin's install, the real presence
 * class, its live intel-screen binding, and the dispatcher's own gate chain. Only the sector map's
 * view state is stood in for, being the one input with a seam to stand in at; the intel half needs
 * none, since off a running game it fails closed to "no visor" on its own.
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
            // false in by design. A gate narrowed to that read would hide the box exactly where the
            // starscape terrain half is painting the layers it belongs to.
            renderInstalledDispatcher(SectorMapState.SHOWING_STARSCAPE);

            verify(tooltipMock)
                .renderFor(sectorMock, systemMock);
        }

        @Test
        void drawsWhileTheSectorMapIsShowingTheOrdinarySchematic() {
            // The look that already worked, kept honest: reaching the starscape one must not have
            // cost the other.
            renderInstalledDispatcher(SectorMapState.SHOWING_SCHEMATIC);

            verify(tooltipMock)
                .renderFor(sectorMock, systemMock);
        }

        @Test
        void drawsNothingWhileNeitherHostShowsAMap() {
            // The ordinary state on every screen that is not a map, which this listener is called
            // for all the same. Without this, a gate wired permanently open would pass both cases
            // above.
            renderInstalledDispatcher(SectorMapState.NOT_SHOWING);

            verifyNoInteractions(tooltipMock);
        }
    }

    // Installs the dispatcher the way the game does and renders one frame of it, with the sector
    // map's view state answering as told and the intel half left to fail closed.
    //
    // All three of the sector map's reads are answered, not just the one the live gate consults, so
    // that a gate narrowed to a look-aware read is caught by drawing the wrong picture rather than
    // missed by reading an unstubbed default.
    //
    // The install happens outside the mocked statics on purpose: it is where the presence class and
    // its live intel-screen binding are built, and that binding takes its logger from Global at
    // class-init, which a mocked Global would answer null for once and for the rest of the JVM.
    private void renderInstalledDispatcher(SectorMapState sectorMapState) {

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
                .when(CampaignMapView::isSectorMapShowing)
                .thenReturn(sectorMapState != SectorMapState.NOT_SHOWING);
            mapViewMock
                .when(CampaignMapView::isSectorMapWithStarscapeOff)
                .thenReturn(sectorMapState == SectorMapState.SHOWING_SCHEMATIC);
            mapViewMock
                .when(CampaignMapView::isSectorMapInStarscapeMode)
                .thenReturn(sectorMapState == SectorMapState.SHOWING_STARSCAPE);

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

    // The three sector-map states this gate can meet, named rather than spelled as a row of booleans
    // so a case reads as the screen it stands for. The map's reads are not independent - it cannot
    // wear both looks, nor either while it is absent - so one state drives all three.
    private enum SectorMapState {
        SHOWING_SCHEMATIC,
        SHOWING_STARSCAPE,
        NOT_SHOWING
    }
}
