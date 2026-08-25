package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.animation.TraverseDurations;
import kmlib.math.geometry.BoxEdge;
import kmlib.mods.consolecommands.ConsoleCommandsOverlay;
import kmlib.starsector.ui.input.UiCursor;
import kmlib.starsector.ui.render.gl.style.WidgetStyle;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;
import kmlib.testfixtures.mods.consolecommands.ConsoleOverlayPresenceFake;
import kmlib.testfixtures.starsector.settings.ModStateScopes;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.SidebarFoldSelection;
import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;

import static kmlib.testfixtures.starsector.settings.StubbedModIds.CONSOLE_COMMANDS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the shortcut key that jumps to a layer, which every host shares because the panel offers the same
 * tabs on whichever screen it draws. The jump reads the player's rebound keycode rather than the layer's
 * default, so a clash with a screen's own bindings is settled by rebinding; it writes only the host's own
 * pick, so a press on one screen leaves the other screen's tab where it was; and it consumes only a press
 * it acted on, so every other key reaches the screen underneath.
 *
 * <p>Pins the shared "is the sidebar live" gate with it: a console taking the keyboard stands every host
 * down, which is what frees the shortcut keys above to type rather than switch tabs.
 */
final class BaseSidebarHostTest {

    private static final int UNBOUND = 0;
    private static final int FIRST_KEYCODE = 49;
    private static final int SECOND_KEYCODE = 25;
    private static final int UNRELATED_KEYCODE = 30;

    private static final String FIRST_SETTING_KEY = "kmu_testFirstLayerKey";
    private static final String SECOND_SETTING_KEY = "kmu_testSecondLayerKey";

    // Where each registered layer's tab sits in the row, the panel building its tabs from the same registry
    // in the same order - so these are the indices a blink has to land on.
    private static final int FIRST_LAYER_TAB_INDEX = 0;
    private static final int SECOND_LAYER_TAB_INDEX = 1;

    // A whole traverse in one step, so a started blink stands at its peak and an unstarted one at rest -
    // telling the two apart in one number rather than by walking frames. The same pace each way, since
    // only the rise is read here and the paces the panel actually runs at are KMLib's to pin.
    private static final float FULL_STEP_SECONDS = 1f;
    private static final TraverseDurations DURATIONS = TraverseDurations.createSymmetric(1f);
    private static final float TOLERANCE = 0.0001f;

    // The pointer parked well off the panel, so nothing the frame advances can be a hover and a lit tab can
    // only have come from the blink.
    private static final float OFF_PANEL_COORDINATE = 5000f;

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);

    @BeforeEach
    void registerTwoBoundLayers() {

        when(firstLayerMock.getShortcutSettingKey())
            .thenReturn(FIRST_SETTING_KEY);
        when(firstLayerMock.getDefaultShortcutKeycode())
            .thenReturn(FIRST_KEYCODE);
        when(secondLayerMock.getShortcutSettingKey())
            .thenReturn(SECOND_SETTING_KEY);
        when(secondLayerMock.getDefaultShortcutKeycode())
            .thenReturn(SECOND_KEYCODE);

        // The registry is static, so a neighbour's layers would otherwise outlive their test.
        MapLayerRegistry.registerLayers(List.of(firstLayerMock, secondLayerMock), firstLayerMock);
    }

    @Nested
    class HandleKeyPress {

        @Test
        void handleKeyPressJumpsToTheLayerBoundToThePressedKey() {

            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            var eventMock = mockKeyPress(SECOND_KEYCODE);

            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            verify(layerSelectionMock)
                .selectLayer(secondLayerMock);

            // Consumed so the key does not also trigger a binding on the screen underneath sharing it.
            verify(eventMock)
                .consume();
        }

        @Test
        void handleKeyPressLeavesAKeyBoundToNoLayerUntouched() {

            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            var eventMock = mockKeyPress(UNRELATED_KEYCODE);

            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            verifyNoInteractions(layerSelectionMock);

            // Unconsumed, so the screen underneath still receives its own key.
            verify(eventMock, never())
                .consume();
        }

        @Test
        void handleKeyPressSkipsALayerWhoseShortcutThePlayerCleared() {
            // A cleared binding stores 0 (LWJGL's KEY_NONE), so a stray zero-valued press must match no
            // layer rather than falling onto the first cleared one.
            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            var eventMock = mockKeyPress(UNBOUND);

            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class)) {
                settingsMock
                    .when(() -> KmuMapLayerSettings.getMapLayerShortcut(anyString(), anyInt()))
                    .thenReturn(UNBOUND);

                host.handleKeyPress(eventMock);
            }

            verifyNoInteractions(layerSelectionMock);
            verify(eventMock, never())
                .consume();
        }

        @Test
        void handleKeyPressFollowsTheReboundKeycodeRatherThanTheLayerDefault() {
            // Rebinding is the way out of a clash with a screen's own bindings, so the jump must follow the
            // player's keycode and stop answering to the default.
            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            var eventMock = mockKeyPress(UNRELATED_KEYCODE);

            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class)) {

                settingsMock
                    .when(() -> KmuMapLayerSettings.getMapLayerShortcut(FIRST_SETTING_KEY, FIRST_KEYCODE))
                    .thenReturn(UNBOUND);
                settingsMock
                    .when(() -> KmuMapLayerSettings.getMapLayerShortcut(SECOND_SETTING_KEY, SECOND_KEYCODE))
                    .thenReturn(UNRELATED_KEYCODE);

                host.handleKeyPress(eventMock);
            }

            verify(layerSelectionMock)
                .selectLayer(secondLayerMock);
            verify(eventMock)
                .consume();
        }

        @Test
        void handleKeyPressBlinksTheTabOfTheLayerItJumpedTo() {
            // The only thing that tells the player a shortcut landed: a keyboard switch puts nothing under
            // the pointer, so an unblinked tab would read as a key the panel ignored. Blinking the wrong tab
            // would be worse than none, marking a switch that did not happen.
            var host = createHost(mock(ActiveLayerSelection.class));
            var eventMock = mockKeyPress(SECOND_KEYCODE);

            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            advanceAWholeTraverse(host);

            assertThat(hoverFractionAt(host, SECOND_LAYER_TAB_INDEX))
                .isCloseTo(1f, within(TOLERANCE));
            assertThat(hoverFractionAt(host, FIRST_LAYER_TAB_INDEX))
                .isCloseTo(0f, within(TOLERANCE));
        }

        @Test
        void handleKeyPressBlinksNoTabForAKeyBoundToNoLayer() {
            // Nothing switched, so nothing may be marked - a blink here would confirm a press the panel in
            // fact let through to the screen underneath.
            var host = createHost(mock(ActiveLayerSelection.class));
            var eventMock = mockKeyPress(UNRELATED_KEYCODE);

            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            advanceAWholeTraverse(host);

            assertThat(hoverFractionAt(host, FIRST_LAYER_TAB_INDEX))
                .isCloseTo(0f, within(TOLERANCE));
            assertThat(hoverFractionAt(host, SECOND_LAYER_TAB_INDEX))
                .isCloseTo(0f, within(TOLERANCE));
        }

        @Test
        void handleKeyPressWritesOnlyTheHostsOwnPick() {
            // Each screen keeps its own tab, so a shortcut pressed on one screen must not move the other's.
            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var otherScreenSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);

            createHost(otherScreenSelectionMock);

            var eventMock = mockKeyPress(FIRST_KEYCODE);

            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            verify(layerSelectionMock)
                .selectLayer(firstLayerMock);

            verifyNoInteractions(otherScreenSelectionMock);
        }
    }

    @Nested
    class IsOverlayShowing {

        @Test
        void isOverlayShowingIsFalseWhileAConsoleIsUpOverTheHostsOwnScreen() {
            // The whole point of the gate: the panel draws after the entire core UI, so a console overlay
            // it did not stand down for would be drawn under it while its shortcut keys ate the keystrokes
            // the console was opened to receive.
            var consolePresenceFake = new ConsoleOverlayPresenceFake();
            var host = createHostOnAShowingScreen(new ConsoleCommandsOverlay(consolePresenceFake));

            consolePresenceFake.openConsole();

            ModStateScopes.runWithModEnabled(CONSOLE_COMMANDS, true, () ->
                assertThat(host.isOverlayShowing())
                    .isFalse());
        }

        @Test
        void isOverlayShowingIsTrueOnAShowingScreenWithNoConsoleUp() {
            // The console read is the only thing added to the screen read, so a closed console has to leave
            // the panel exactly where it was - a gate stuck shut would take the sidebar off every screen.
            var host = createHostOnAShowingScreen(
                new ConsoleCommandsOverlay(new ConsoleOverlayPresenceFake()));

            assertThat(host.isOverlayShowing())
                .isTrue();
        }

        @Test
        void isOverlayShowingIsFalseOffTheHostsScreenWithNoConsoleUp() {

            var host = createHost(mock(ActiveLayerSelection.class));

            assertThat(host.isOverlayShowing())
                .isFalse();
        }

        @Test
        void isOverlayShowingLeavesTheScreenUnreadWhileAConsoleIsUp() {
            // A screen read walks live widgets, so the cheaper answer is asked first and the walk skipped
            // while the panel is standing down anyway.
            var consolePresenceFake = new ConsoleOverlayPresenceFake();
            var host = createHostOnAShowingScreen(new ConsoleCommandsOverlay(consolePresenceFake));

            consolePresenceFake.openConsole();

            ModStateScopes.runWithModEnabled(CONSOLE_COMMANDS, true, () -> host.isOverlayShowing());

            assertThat(host.screenReadCount)
                .isZero();
        }
    }

    // A host carrying nothing but the plumbing under test: the shared key handling is the base's, so the
    // per-screen answers are stubbed out rather than bound to either live screen.
    private static SidebarHostFake createHost(ActiveLayerSelection layerSelection) {
        return createHost(
            layerSelection,
            new ConsoleCommandsOverlay(new ConsoleOverlayPresenceFake()),
            false);
    }

    // A host whose own screen is up, so what the gate then answers is down to the console alone.
    private static SidebarHostFake createHostOnAShowingScreen(ConsoleCommandsOverlay consoleOverlay) {
        return createHost(mock(ActiveLayerSelection.class), consoleOverlay, true);
    }

    private static SidebarHostFake createHost(
            ActiveLayerSelection layerSelection,
            ConsoleCommandsOverlay consoleOverlay,
            boolean isHostScreenShowing) {

        var foldSelectionMock = mock(SidebarFoldSelection.class);

        when(foldSelectionMock.isRailDocked())
            .thenReturn(false);

        return new SidebarHostFake(
            foldSelectionMock,
            layerSelection,
            consoleOverlay,
            isHostScreenShowing);
    }

    // Each layer bound to its own default, the state before the player rebinds anything.
    private static MockedStatic<KmuMapLayerSettings> mockDefaultBindings() {

        var settingsMock = mockStatic(KmuMapLayerSettings.class);

        settingsMock
            .when(() -> KmuMapLayerSettings.getMapLayerShortcut(FIRST_SETTING_KEY, FIRST_KEYCODE))
            .thenReturn(FIRST_KEYCODE);
        settingsMock
            .when(() -> KmuMapLayerSettings.getMapLayerShortcut(SECOND_SETTING_KEY, SECOND_KEYCODE))
            .thenReturn(SECOND_KEYCODE);

        return settingsMock;
    }

    // Charges the host's panel one whole traverse of animation with the pointer off it, which is all a
    // blink needs to reach its peak. The cursor read the advance opens with is stubbed rather than left to
    // LWJGL, there being no display under a unit test to point at; a placement with no tabs laid in it and
    // no collapse handle then leaves the frame nothing to hover whatever the stub answers.
    private static void advanceAWholeTraverse(SidebarHostFake host) {
        try (var cursorMock = mockStatic(UiCursor.class)) {

            cursorMock
                .when(UiCursor::getUiX)
                .thenReturn(OFF_PANEL_COORDINATE);
            cursorMock
                .when(UiCursor::getUiY)
                .thenReturn(OFF_PANEL_COORDINATE);

            host.getController().advanceInputMotions(
                SidebarPlacements.placeSidebarWithNoTabsLaid(),
                FULL_STEP_SECONDS,
                DURATIONS);
        }
    }

    // How far onto the hovered shade one of the host's tabs stands, read the way the render pass reads it -
    // through the interaction sources, which is where a blink and a hover are composed into the one value a
    // strip paints from.
    private static float hoverFractionAt(SidebarHostFake host, int tabIndex) {
        return host
            .getController()
            .getInteractionSources()
            .headerTabs()
            .hoverSource()
            .resolveHoverFractionAt(tabIndex);
    }

    private static InputEventAPI mockKeyPress(int keycode) {

        var eventMock = mock(InputEventAPI.class);

        when(eventMock.getEventValue())
            .thenReturn(keycode);

        return eventMock;
    }

    // The base host with its per-screen questions answered as "nothing to draw": only the shared key
    // handling and the shared half of the gate are under test here, and each concrete host pins its own
    // screen read, anchor, edges, and look. A host resolving no placement is never asked to paint, so it
    // has no look to give.
    private static final class SidebarHostFake extends BaseSidebarHost {

        private final boolean isHostScreenShowing;

        // How often the screen half of the gate was asked, so "the console short-circuits it" can be
        // pinned as never reached rather than merely as an answer that came out false anyway.
        private int screenReadCount;

        private SidebarHostFake(
                SidebarFoldSelection foldSelection,
                ActiveLayerSelection layerSelection,
                ConsoleCommandsOverlay consoleOverlay,
                boolean isHostScreenShowing) {

            super(foldSelection, layerSelection, consoleOverlay);
            this.isHostScreenShowing = isHostScreenShowing;
        }

        @Override
        public TabPanelPlacement resolvePlacement() {
            return null;
        }

        @Override
        public WidgetStyle resolveWidgetStyle() {
            return null;
        }

        @Override
        public Set<BoxEdge> resolveBorderEdges(TabPanelPlacement placement) {
            return BoxEdge.ALL;
        }

        @Override
        public String describeViewState() {
            return "fake host";
        }

        @Override
        protected boolean isHostScreenShowing() {
            screenReadCount++;
            return isHostScreenShowing;
        }
    }
}
