package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.math.geometry.BoxEdge;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.SidebarFoldSelection;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;

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
 */
final class BaseSidebarHostTest {
    private static final int UNBOUND = 0;
    private static final int FIRST_KEYCODE = 49;
    private static final int SECOND_KEYCODE = 25;
    private static final int UNRELATED_KEYCODE = 30;

    private static final String FIRST_SETTING_KEY = "kmu_testFirstLayerKey";
    private static final String SECOND_SETTING_KEY = "kmu_testSecondLayerKey";

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);

    @BeforeEach
    void registerTwoBoundLayers() {
        when(firstLayerMock.getShortcutSettingKey()).thenReturn(FIRST_SETTING_KEY);
        when(firstLayerMock.getDefaultShortcutKeycode()).thenReturn(FIRST_KEYCODE);
        when(secondLayerMock.getShortcutSettingKey()).thenReturn(SECOND_SETTING_KEY);
        when(secondLayerMock.getDefaultShortcutKeycode()).thenReturn(SECOND_KEYCODE);
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
            try (MockedStatic<KmuLunaSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            verify(layerSelectionMock).selectLayer(secondLayerMock);
            // Consumed so the key does not also trigger a binding on the screen underneath sharing it.
            verify(eventMock).consume();
        }

        @Test
        void handleKeyPressLeavesAKeyBoundToNoLayerUntouched() {
            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            var eventMock = mockKeyPress(UNRELATED_KEYCODE);
            try (MockedStatic<KmuLunaSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            verifyNoInteractions(layerSelectionMock);
            // Unconsumed, so the screen underneath still receives its own key.
            verify(eventMock, never()).consume();
        }

        @Test
        void handleKeyPressSkipsALayerWhoseShortcutThePlayerCleared() {
            // A cleared binding stores 0 (LWJGL's KEY_NONE), so a stray zero-valued press must match no
            // layer rather than falling onto the first cleared one.
            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            var eventMock = mockKeyPress(UNBOUND);
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(() -> KmuLunaSettings.getPoliticalMapLayerShortcut(anyString(), anyInt()))
                        .thenReturn(UNBOUND);

                host.handleKeyPress(eventMock);
            }

            verifyNoInteractions(layerSelectionMock);
            verify(eventMock, never()).consume();
        }

        @Test
        void handleKeyPressFollowsTheReboundKeycodeRatherThanTheLayerDefault() {
            // Rebinding is the way out of a clash with a screen's own bindings, so the jump must follow the
            // player's keycode and stop answering to the default.
            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            var eventMock = mockKeyPress(UNRELATED_KEYCODE);
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(() -> KmuLunaSettings.getPoliticalMapLayerShortcut(
                        FIRST_SETTING_KEY, FIRST_KEYCODE)).thenReturn(UNBOUND);
                settingsMock.when(() -> KmuLunaSettings.getPoliticalMapLayerShortcut(
                        SECOND_SETTING_KEY, SECOND_KEYCODE)).thenReturn(UNRELATED_KEYCODE);

                host.handleKeyPress(eventMock);
            }

            verify(layerSelectionMock).selectLayer(secondLayerMock);
            verify(eventMock).consume();
        }

        @Test
        void handleKeyPressWritesOnlyTheHostsOwnPick() {
            // Each screen keeps its own tab, so a shortcut pressed on one screen must not move the other's.
            var layerSelectionMock = mock(ActiveLayerSelection.class);
            var otherScreenSelectionMock = mock(ActiveLayerSelection.class);
            var host = createHost(layerSelectionMock);
            createHost(otherScreenSelectionMock);
            var eventMock = mockKeyPress(FIRST_KEYCODE);
            try (MockedStatic<KmuLunaSettings> settingsMock = mockDefaultBindings()) {
                host.handleKeyPress(eventMock);
            }

            verify(layerSelectionMock).selectLayer(firstLayerMock);
            verifyNoInteractions(otherScreenSelectionMock);
        }
    }

    // A host carrying nothing but the plumbing under test: the shared key handling is the base's, so the
    // per-screen answers are stubbed out rather than bound to either live screen.
    private static SidebarHostFake createHost(ActiveLayerSelection layerSelection) {
        var foldSelectionMock = mock(SidebarFoldSelection.class);
        when(foldSelectionMock.isRailDocked()).thenReturn(false);
        return new SidebarHostFake(foldSelectionMock, layerSelection);
    }

    // Each layer bound to its own default, the state before the player rebinds anything.
    private static MockedStatic<KmuLunaSettings> mockDefaultBindings() {
        MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class);
        settingsMock.when(() -> KmuLunaSettings.getPoliticalMapLayerShortcut(
                FIRST_SETTING_KEY, FIRST_KEYCODE)).thenReturn(FIRST_KEYCODE);
        settingsMock.when(() -> KmuLunaSettings.getPoliticalMapLayerShortcut(
                SECOND_SETTING_KEY, SECOND_KEYCODE)).thenReturn(SECOND_KEYCODE);
        return settingsMock;
    }

    private static InputEventAPI mockKeyPress(int keycode) {
        var eventMock = mock(InputEventAPI.class);
        when(eventMock.getEventValue()).thenReturn(keycode);
        return eventMock;
    }

    // The base host with its per-screen questions answered as "nothing to draw": only the shared key
    // handling is under test here, and each concrete host pins its own gate, anchor, and edges.
    private static final class SidebarHostFake extends BaseSidebarHost {

        private SidebarHostFake(SidebarFoldSelection foldSelection, ActiveLayerSelection layerSelection) {
            super(foldSelection, layerSelection);
        }

        @Override
        public boolean isOverlayShowing() {
            return false;
        }

        @Override
        public TabPanelPlacement resolvePlacement() {
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
    }
}
