package kmu;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.starsector.ui.map.presence.CampaignMapView;
import kmlib.starsector.ui.map.presence.SectorMapState;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.maplayers.base.tooltip.HoverTooltipDetailLevelInput;
import kmu.maplayers.base.tooltip.MapHoverFixtures;
import kmu.maplayers.base.tooltip.MapHoverInstaller;
import kmu.maplayers.base.tooltip.MapHoverTooltip;
import kmu.maplayers.base.tooltip.MapLayerCellTooltip;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevelState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static kmu.maplayers.base.hover.HoverSwitchScopes.runWithHoverTooltipSwitchOn;
import static kmu.maplayers.base.hover.HoverSwitchScopes.runWithHoverTooltipSwitchOnInGameSpace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the frame gate the install site hands the hover-tooltip feature - to the render listener that
 * draws the box, and to the input listener that claims the key selecting which box - that it is the
 * locatability read, and not the bare presence read nor one of the look-aware ones. Which read is
 * wired in is a choice made at the install site alone - both listeners take it as a supplier and so
 * can say nothing about which screens open it, and each read answers only for itself - so a gate
 * narrowed to the schematic read, which is false in exactly the look this feature exists to reach,
 * or to the presence read, which is false wherever a mod's docked map surface is the only map on
 * screen, would otherwise pass every other test in the suite.
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

    // The system under the cursor, named once so the hover, the sector's roster and the assertions
    // cannot drift onto three different ids.
    private static final String SYSTEM_ID = "system";

    private final MapHoverTooltip tooltipMock = mock(MapHoverTooltip.class);
    private final MapLayer tooltipLayerMock = mock(MapLayer.class);
    private final MapLayerRenderer layerRendererMock = mock(MapLayerRenderer.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    @BeforeEach
    void hoverACellOnALayerShowingATooltip() {

        when(tooltipLayerMock.getId())
            .thenReturn("tooltip_layer");
        when(tooltipLayerMock.resolveRenderer(any()))
            .thenReturn(layerRendererMock);

        when(layerRendererMock.resolveHoverTooltip())
            .thenReturn(Optional.of(tooltipMock));

        // The hovered box has a second amount of detail to state, so the key's own offer test is open
        // and a case below turns on the map gate it is about rather than on what the box holds.
        when(tooltipMock.resolveNextLevelFor(any(), any(), any()))
            .thenReturn(Optional.of(HoverTooltipDetailLevel.SYSTEM_COMPOSITION));

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);
        when(sectorMock.getStarSystems())
            .thenReturn(List.of(systemMock));

        MapLayerRegistry.registerLayers(List.of(tooltipLayerMock), tooltipLayerMock);

        // The registry is static, so a screen left wired would outlive its test; a fresh fake starts
        // each test from the intel screen closed, which resolves reads to the map screen's pick.
        MapLayerScreens.registerIntelScreen(new IntelScreenViewFake());

        // Everything below the map gate is open, so what the box does is the gate's answer alone.
        // Hovered on the sector every case runs as the live one, that being the machinery both
        // listeners resolve.
        MapHoverFixtures.hoverASystemOnAnInstalledSector(sectorMock, SYSTEM_ID);
    }

    @AfterEach
    void restoreTheSharedState() {

        MapLayerRosters.restoreNonEmptyRoster();

        // The index is process-wide, so a sector left installed would carry this test's hover into
        // the next one.
        MapLayerInstallations.disposeEveryInstallation();

        // The level holder is a process-wide singleton like the hover, so an advance left standing
        // would reach the next test as a detail level it never asked for.
        HoverTooltipDetailLevelState.getInstance().discardLevelFromPreviousSave();
    }

    @Nested
    class InstalledMapGate {

        @Test
        void drawsWhileTheSectorMapIsInStarscapeMode() {
            // The look the whole feature exists to reach, and the one the schematic read answers
            // false in by design. A gate narrowed to that read would hide the box exactly where the
            // Starscape terrain half is painting the layers it belongs to.
            renderInstalledDispatcher(SectorMapState.SHOWING_IN_STARSCAPE_MODE);

            verify(tooltipMock)
                .renderFor(eq(sectorMock), eq(systemMock), any());
        }

        @Test
        void drawsWhileTheSectorMapIsShowingTheOrdinarySchematic() {
            // The look that already worked, kept honest: reaching the Starscape one must not have
            // cost the other.
            renderInstalledDispatcher(SectorMapState.SHOWING_WITH_STARSCAPE_OFF);

            verify(tooltipMock)
                .renderFor(eq(sectorMock), eq(systemMock), any());
        }

        @Test
        void drawsNothingWhileNeitherHostShowsAMap() {
            // The ordinary state on every screen that is not a map, which this listener is called
            // for all the same. Without this, a gate wired permanently open would pass both cases
            // above.
            renderInstalledDispatcher(SectorMapState.NOT_SHOWING);

            verifyNoInteractions(tooltipMock);
        }

        @Test
        void drawsInGameSpaceWithThatPermissionGranted() {
            // The frames a mod's docked map surface is the only map on. No vanilla host is showing,
            // so a gate wired to the bare presence read would hide the box on exactly the surface
            // the player is pointing at - which is what this pins the install site against.
            runWithHoverTooltipSwitchOnInGameSpace(() -> runInGameSpace(() ->
                installedDispatcher().renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class))));

            verify(tooltipMock)
                .renderFor(eq(sectorMock), eq(systemMock), any());
        }

        @Test
        void drawsNothingInGameSpaceWithoutThatPermission() {
            // The other half: game space is not a frame the box draws on by itself. The permission is
            // what admits it, so withholding it leaves the vanilla hosts answering alone.
            runWithHoverTooltipSwitchOn(() -> runInGameSpace(() ->
                installedDispatcher().renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class))));

            verifyNoInteractions(tooltipMock);
        }
    }

    @Nested
    class InstalledDetailLevelCycleGate {

        @Test
        void claimsTheCycleKeyPressWhileTheSectorMapIsInStarscapeMode() {
            // The same mode the box is drawn in above, and the one a gate copied from the older
            // sector-map-only read would answer false in - leaving F1 dead in exactly the look the
            // box it switches is live in, with every unit test still passing.
            var eventMock = pressCycleKeyOnInstalledInput(SectorMapState.SHOWING_IN_STARSCAPE_MODE);

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);

            verify(eventMock)
                .consume();
        }

        @Test
        void claimsTheCycleKeyPressWhileTheSectorMapIsShowingTheOrdinarySchematic() {

            var eventMock = pressCycleKeyOnInstalledInput(SectorMapState.SHOWING_WITH_STARSCAPE_OFF);

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);

            verify(eventMock)
                .consume();
        }

        @Test
        void leavesTheCycleKeyPressAloneOverABoxWithNothingToExpand() {
            // Past every gate, on the map, over a hovered cell whose box has no second amount of
            // detail to state. The level is shared and holds across hovers, so swallowing the press
            // here would decide how the next system that does differ opens - which is why the offer
            // is asked of the box rather than assumed from the map being up.
            when(tooltipMock.resolveNextLevelFor(any(), any(), any()))
                .thenReturn(Optional.empty());

            var eventMock = pressCycleKeyOnInstalledInput(SectorMapState.SHOWING_WITH_STARSCAPE_OFF);

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);

            verify(eventMock, never())
                .consume();
        }

        @Test
        void claimsTheCycleKeyPressInGameSpaceWithThatPermissionGranted() {
            // The key has to reach wherever the box does, or the player gets a box in front of them
            // they cannot switch. Same permission, same seam, asserted on the other listener - which
            // is what would catch one of the two being wired to a different read.
            var eventMock = mock(InputEventAPI.class);

            when(eventMock.isKeyDownEvent())
                .thenReturn(true);
            when(eventMock.getEventValue())
                .thenReturn(Keyboard.KEY_F1);

            var cycleKeyInput = installListener(HoverTooltipDetailLevelInput.class);

            runWithHoverTooltipSwitchOnInGameSpace(() -> runInGameSpace(
                () -> cycleKeyInput.processCampaignInputPreCore(List.of(eventMock))));

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);

            verify(eventMock)
                .consume();
        }

        @Test
        void leavesTheCycleKeyPressAloneWhileNeitherHostShowsAMap() {
            // The half that keeps the key honest the other way: this listener is called for the
            // whole campaign UI, so a gate wired permanently open would swallow F1 on every screen
            // the player is on, where no box could be showing to switch.
            var eventMock = pressCycleKeyOnInstalledInput(SectorMapState.NOT_SHOWING);

            assertThat(HoverTooltipDetailLevelState.getInstance().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);

            verifyNoInteractions(eventMock);
        }
    }

    // Installs the dispatcher the way the game does and renders one frame of it.
    private void renderInstalledDispatcher(SectorMapState sectorMapState) {

        var dispatcher = installedDispatcher();

        runWithHoverTooltipSwitchOn(() -> runWithSectorMapState(sectorMapState,
            () -> dispatcher.renderInUICoordsAboveUIAndTooltips(mock(ViewportAPI.class))));
    }

    // The dispatcher the mod plugin registers, for the cases that settle the screen themselves.
    private MapLayerCellTooltip installedDispatcher() {
        return installListener(MapLayerCellTooltip.class);
    }

    // Installs the cycle-key listener the way the game does and feeds it one press of its key.
    private InputEventAPI pressCycleKeyOnInstalledInput(SectorMapState sectorMapState) {

        var cycleKeyInput = installListener(HoverTooltipDetailLevelInput.class);

        var eventMock = mock(InputEventAPI.class);

        when(eventMock.isKeyDownEvent())
            .thenReturn(true);
        when(eventMock.getEventValue())
            .thenReturn(Keyboard.KEY_F1);

        runWithHoverTooltipSwitchOn(() -> runWithSectorMapState(sectorMapState,
            () -> cycleKeyInput.processCampaignInputPreCore(List.of(eventMock))));

        return eventMock;
    }

    // Runs body with the sector map answering as told, the campaign screen out of game space, and
    // the intel half left to fail closed.
    private void runWithSectorMapState(SectorMapState sectorMapState, Runnable body) {
        runOnScreen(sectorMapState, false, body);
    }

    // Runs body on the campaign itself: no map on either host, no core screen up and no dialog, which
    // is the frame a mod's docked map surface is the only map on.
    private void runInGameSpace(Runnable body) {
        runOnScreen(SectorMapState.NOT_SHOWING, true, body);
    }

    // Runs body with the whole screen described at once: which map state the sector side reports, and
    // whether the campaign is showing game space.
    //
    // The screen is supplied whole rather than as the reads derived from it, so a case names where
    // the player is and every gate that could be wired in - look-aware or not, presence or
    // locatability - answers from the same one input. A gate narrowed to the wrong read is then
    // caught by acting on the wrong screen rather than missed by reading an unstubbed default.
    private void runOnScreen(
            SectorMapState sectorMapState,
            boolean isInGameSpace,
            Runnable body) {

        try (var mapViewMock = mockStatic(CampaignMapView.class);
             var globalMock = mockStatic(Global.class)) {

            mapViewMock
                .when(CampaignMapView::resolveSectorMapState)
                .thenReturn(sectorMapState);

            globalMock
                .when(Global::getSector)
                .thenReturn(sectorMock);

            // Game space is "no core tab and no dialog", so a screen out of it raises a tab. Read off
            // the live campaign UI rather than stubbed at the seam above, since which of the two the
            // install site wired is the thing under test.
            var campaignUiMock = mock(CampaignUIAPI.class);

            when(campaignUiMock.getCurrentCoreTab())
                .thenReturn(isInGameSpace ? null : CoreUITabId.MAP);
            when(sectorMock.getCampaignUI())
                .thenReturn(campaignUiMock);

            body.run();
        }
    }

    // The listener the hover installer registers, taken from the registration itself rather than
    // built here - constructing one locally would hand it a gate this test wrote, which is the very
    // thing under test.
    //
    // The whole installer is run and the wanted half picked out of what it registered, rather than
    // one step being called directly: the composition under test is the one a load performs, and a
    // case that drove a single step would go on passing if the load stopped performing it.
    //
    // The install runs outside the mocked statics on purpose: it is where the presence class and its
    // live intel-screen binding are built, and that binding takes its logger from Global at
    // class-init, which a mocked Global would answer null for once and for the rest of the JVM.
    private <T> T installListener(Class<T> listenerType) {

        var listenerManagerMock = mock(ListenerManagerAPI.class);
        var installSectorMock = mock(SectorAPI.class);

        when(installSectorMock.getListenerManager())
            .thenReturn(listenerManagerMock);

        MapHoverInstaller.installAll(installSectorMock);

        var installedListeners = ArgumentCaptor.forClass(Object.class);

        verify(listenerManagerMock, atLeastOnce())
            .addListener(installedListeners.capture(), eq(true));

        return installedListeners.getAllValues().stream()
            .filter(listenerType::isInstance)
            .map(listenerType::cast)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                "The hover installer registered no " + listenerType.getSimpleName()));
    }
}
