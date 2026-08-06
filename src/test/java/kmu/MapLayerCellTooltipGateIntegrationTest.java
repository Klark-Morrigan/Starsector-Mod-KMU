package kmu;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.map.presence.CampaignMapView;
import kmlib.starsector.ui.map.presence.SectorMapState;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.hover.MapHover;
import kmu.maplayers.base.hover.MapHoverState;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.maplayers.base.tooltip.HoverTooltipDetailMode;
import kmu.maplayers.base.tooltip.HoverTooltipDetailModeInput;
import kmu.maplayers.base.tooltip.HoverTooltipDetailModeState;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.tooltip.MapLayerCellTooltip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static kmu.maplayers.base.hover.HoverSwitchScopes.runWithHoverTooltipSwitchOn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the map gate the install site hands the hover-tooltip feature - to the render listener that
 * draws the box, and to the input listener that claims the key selecting which box - that it is the
 * look-blind read, and not one of the look-aware ones. Which read is wired in is a choice made at
 * the install site alone - both listeners take it as a supplier and so can say nothing about which
 * map states open it, and each presence class answers only for itself - so a gate narrowed to the
 * schematic read, which is false in exactly the mode this feature exists to reach, would otherwise
 * pass every other test in the suite.
 *
 * <p>Both listeners are checked against the same map states because the two must agree: a key
 * claimed where no box can draw is swallowed from the screen underneath, and a key dead where the
 * box is live leaves the player unable to switch the box in front of them. Sharing one gate seam is
 * what makes them agree; that the install site hands that seam the same read twice is what this
 * pins.
 *
 * <p>Integration by necessity rather than by preference: the wiring is one expression, so the only
 * way to exercise it is through everything it reaches - the mod plugin's install, the real presence
 * class, its live intel-screen binding, and the listeners' own gate chains. Only the sector map's
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

        // The hovered box has a second amount of detail to state, so the key's own offer test is open
        // and a case below turns on the map gate it is about rather than on what the box holds.
        when(tooltipMock.isOfferingExpansionFor(any(), any()))
            .thenReturn(true);

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

        // The mode holder is a process-wide singleton like the hover, so a flip left standing would
        // reach the next test as a detail level it never asked for.
        HoverTooltipDetailModeState.getInstance().discardModeFromPreviousSave();
    }

    @Nested
    class InstalledMapGate {

        @Test
        void drawsWhileTheSectorMapIsInStarscapeMode() {
            // The mode the whole feature exists to reach, and the one the schematic read answers
            // false in by design. A gate narrowed to that read would hide the box exactly where the
            // Starscape terrain half is painting the layers it belongs to.
            renderInstalledDispatcher(SectorMapState.SHOWING_IN_STARSCAPE_MODE);

            verify(tooltipMock)
                .renderFor(sectorMock, systemMock);
        }

        @Test
        void drawsWhileTheSectorMapIsShowingTheOrdinarySchematic() {
            // The look that already worked, kept honest: reaching the Starscape one must not have
            // cost the other.
            renderInstalledDispatcher(SectorMapState.SHOWING_WITH_STARSCAPE_OFF);

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

    @Nested
    class InstalledDetailModeToggleGate {

        @Test
        void claimsTheTogglePressWhileTheSectorMapIsInStarscapeMode() {
            // The same mode the box is drawn in above, and the one a gate copied from the older
            // sector-map-only read would answer false in - leaving F1 dead in exactly the look the
            // box it switches is live in, with every unit test still passing.
            var eventMock = pressToggleOnInstalledInput(SectorMapState.SHOWING_IN_STARSCAPE_MODE);

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.EXPANDED);

            verify(eventMock)
                .consume();
        }

        @Test
        void claimsTheTogglePressWhileTheSectorMapIsShowingTheOrdinarySchematic() {
            
            var eventMock = pressToggleOnInstalledInput(SectorMapState.SHOWING_WITH_STARSCAPE_OFF);

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.EXPANDED);

            verify(eventMock)
                .consume();
        }

        @Test
        void leavesTheTogglePressAloneWhileNeitherHostShowsAMap() {
            // The half that keeps the key honest the other way: this listener is called for the
            // whole campaign UI, so a gate wired permanently open would swallow F1 on every screen
            // the player is on, where no box could be showing to switch.
            var eventMock = pressToggleOnInstalledInput(SectorMapState.NOT_SHOWING);

            assertThat(HoverTooltipDetailModeState.getInstance().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);

            verifyNoInteractions(eventMock);
        }
    }

    // Installs the dispatcher the way the game does and renders one frame of it.
    private void renderInstalledDispatcher(SectorMapState sectorMapState) {

        var dispatcher = installListener(
            KMU_ModPlugin::installMapLayerHoverTooltip, MapLayerCellTooltip.class);

        runWithHoverTooltipSwitchOn(() -> runWithSectorMapState(sectorMapState,
            () -> dispatcher.renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class))));
    }

    // Installs the toggle listener the way the game does and feeds it one press of its key.
    private InputEventAPI pressToggleOnInstalledInput(SectorMapState sectorMapState) {

        var toggleInput = installListener(
            KMU_ModPlugin::installHoverTooltipDetailModeInput, HoverTooltipDetailModeInput.class);

        var eventMock = mock(InputEventAPI.class);

        when(eventMock.isKeyDownEvent())
            .thenReturn(true);
        when(eventMock.getEventValue())
            .thenReturn(Keyboard.KEY_F1);

        runWithHoverTooltipSwitchOn(() -> runWithSectorMapState(sectorMapState,
            () -> toggleInput.processCampaignInputPreCore(List.of(eventMock))));

        return eventMock;
    }

    // Runs body with the sector map answering as told and the intel half left to fail closed.
    //
    // The state is supplied whole rather than as the reads derived from it, so a case names the
    // screen the player is on and every gate that could be wired in - look-aware or not - answers
    // from the same one input. A gate narrowed to the wrong read is then caught by acting on the
    // wrong screen rather than missed by reading an unstubbed default.
    private void runWithSectorMapState(SectorMapState sectorMapState, Runnable body) {
        try (var mapViewMock = mockStatic(CampaignMapView.class);
             var globalMock = mockStatic(Global.class)) {

            mapViewMock
                .when(CampaignMapView::resolveSectorMapState)
                .thenReturn(sectorMapState);

            globalMock
                .when(Global::getSector)
                .thenReturn(sectorMock);

            body.run();
        }
    }

    // The listener the mod plugin registers, taken from the registration itself rather than built
    // here - constructing one locally would hand it a gate this test wrote, which is the very thing
    // under test.
    //
    // The install runs outside the mocked statics on purpose: it is where the presence class and its
    // live intel-screen binding are built, and that binding takes its logger from Global at
    // class-init, which a mocked Global would answer null for once and for the rest of the JVM.
    private <T> T installListener(Consumer<SectorAPI> runInstallStep, Class<T> listenerType) {

        var listenerManagerMock = mock(ListenerManagerAPI.class);
        var installSectorMock = mock(SectorAPI.class);

        when(installSectorMock.getListenerManager())
            .thenReturn(listenerManagerMock);

        runInstallStep.accept(installSectorMock);

        var installedListener = ArgumentCaptor.forClass(Object.class);

        verify(listenerManagerMock)
            .addListener(installedListener.capture(), eq(true));

        return listenerType.cast(installedListener.getValue());
    }
}
