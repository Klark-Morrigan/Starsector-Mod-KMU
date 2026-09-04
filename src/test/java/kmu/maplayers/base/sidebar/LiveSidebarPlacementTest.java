package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.MapLayer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the intel overlay's anchor math: the box hangs from the visor's top-left corner (UI origin
 * bottom-left, so the visor's top edge is its y plus its height), pushed down by the top padding, flush to
 * the visor's left, and capped to the visor's bottom - all expressed as screen padding for the
 * top-left-anchored layout. The tab band each panel stands its row in is its host's, and pinned there.
 *
 * <p>And where the tab row's letters and key hints come from: each layer's own answer, taken as drawn text
 * and as the keycode in force, so a layer shipped by another mod letters and binds its tab out of its own
 * bundle and its own settings.
 */
final class LiveSidebarPlacementTest {

    // A visor whose left edge is x = 100, bottom edge y = 50, and top edge y + height = 650.
    private static final Rectangle MAP_VISOR = new Rectangle(100f, 50f, 800f, 600f);

    // LWJGL's KEY_P and KEY_N, two real keys a layer could be answering with.
    private static final int POLITICAL_MAP_KEYCODE = 25;
    private static final int NO_LAYER_KEYCODE = 49;

    // What the player leaves behind by clearing a binding with Escape.
    private static final int UNBOUND_KEYCODE = 0;

    // Inside LWJGL's name table but not one of its keys, so it names nothing.
    private static final int UNNAMED_KEYCODE = 84;

    // Past the end of that table, which is indexed by keycode with no range check of its own.
    private static final int OFF_THE_KEYBOARD_KEYCODE = 9999;

    @Nested
    class ComputeIntelPadding {

        @Test
        void computeIntelPaddingHangsTheBoxFromTheVisorTopPushedDownByTheTopPadding() {
            // Visor top edge = 650; screen 1200 tall; the box top sits 40px below the visor top, so its
            // distance from the screen top is (1200 - 650) + 40 = 590.
            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 40);

            assertThat(padding.top())
                .isEqualTo(590);
        }

        @Test
        void computeIntelPaddingHangsTheBoxAtTheVisorTopWhenTheTopPaddingIsZero() {

            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 0);

            assertThat(padding.top())
                .isEqualTo(550);
        }

        @Test
        void computeIntelPaddingSitsFlushAgainstTheVisorLeftEdgeAndGrowsRightward() {

            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 40);

            assertThat(padding.left())
                .isEqualTo(100);
            assertThat(padding.right())
                .isZero();
        }

        @Test
        void computeIntelPaddingCapsTheBodyToTheVisorBottom() {

            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 40);

            assertThat(padding.bottom())
                .isEqualTo(50);
        }
    }

    @Nested
    class ResolveTabLabels {

        @Test
        void resolveTabLabelsLettersEachTabFromItsOwnLayersAnswer() {
            // The inversion this pins: the bar draws what the layer hands back and resolves no key of its
            // own, which is what lets a layer from another mod letter its tab out of a bundle KMU has no
            // reader for. Two layers, so a bar reading one fixed source would show one of them twice.
            var firstLayerMock = mock(MapLayer.class);
            var secondLayerMock = mock(MapLayer.class);

            when(firstLayerMock.resolveTabLabelText())
                .thenReturn("No Layer");
            when(secondLayerMock.resolveTabLabelText())
                .thenReturn("Trade Routes");

            assertThat(LiveSidebarPlacement.resolveTabLabels(List.of(firstLayerMock, secondLayerMock)))
                .containsExactly("No Layer", "Trade Routes");
        }

        @Test
        void resolveTabLabelsKeepsATabForALayerAnsweringABlankLabel() {
            // A layer whose text resolves to nothing - a missing bundle entry, or a player-set name
            // cleared - keeps its place in the row. Dropping it would take the tab away with it, leaving
            // the player no way back to a layer they can still be holding as their pick.
            var blankLabelLayerMock = mock(MapLayer.class);
            var labelledLayerMock = mock(MapLayer.class);

            when(blankLabelLayerMock.resolveTabLabelText())
                .thenReturn("");
            when(labelledLayerMock.resolveTabLabelText())
                .thenReturn("Political Map");

            assertThat(LiveSidebarPlacement.resolveTabLabels(List.of(blankLabelLayerMock, labelledLayerMock)))
                .containsExactly("", "Political Map");
        }
    }

    @Nested
    class ResolveTabShortcuts {

        @Test
        void resolveTabShortcutsHintsEachTabWithTheKeyItsOwnLayerAnswersTo() {
            // The inversion this pins: the bar prints the key the layer hands back and reads no settings
            // field of its own, which is what lets a layer from another mod bind its tab in a settings file
            // KMU has no reader for. Two layers, so a bar reading one fixed source would hint one of them
            // twice.
            var firstLayerMock = mock(MapLayer.class);
            var secondLayerMock = mock(MapLayer.class);

            when(firstLayerMock.resolveShortcutKeycode())
                .thenReturn(NO_LAYER_KEYCODE);
            when(secondLayerMock.resolveShortcutKeycode())
                .thenReturn(POLITICAL_MAP_KEYCODE);

            assertThat(LiveSidebarPlacement.resolveTabShortcuts(List.of(firstLayerMock, secondLayerMock)))
                .containsExactly("N", "P");
        }

        @Test
        void resolveTabShortcutsHintsNoKeyForALayerThePlayerLeftUnbound() {
            // A cleared binding is a key the tab must stop advertising: LWJGL still names 0 "NONE", so an
            // ungated read would print a key the player cannot press. The entry stays, since the control
            // pairs hints to labels by index.
            var unboundLayerMock = mock(MapLayer.class);
            var boundLayerMock = mock(MapLayer.class);

            when(unboundLayerMock.resolveShortcutKeycode())
                .thenReturn(UNBOUND_KEYCODE);
            when(boundLayerMock.resolveShortcutKeycode())
                .thenReturn(POLITICAL_MAP_KEYCODE);

            assertThat(LiveSidebarPlacement.resolveTabShortcuts(List.of(unboundLayerMock, boundLayerMock)))
                .containsExactly(null, "P");
        }

        @Test
        void resolveTabShortcutsHintsNoKeyForACodeLwjglCannotName() {
            // A real keycode LWJGL holds no name for. The tab keeps its label and simply says nothing about
            // what presses it, rather than printing a blank bracket beside the name.
            var unnamedLayerMock = mock(MapLayer.class);

            when(unnamedLayerMock.resolveShortcutKeycode())
                .thenReturn(UNNAMED_KEYCODE);

            assertThat(LiveSidebarPlacement.resolveTabShortcuts(List.of(unnamedLayerMock)))
                .containsExactly((String) null);
        }

        @Test
        void resolveTabShortcutsHintsNoKeyForACodePastTheKeyboard() {
            // The bound test earns its place here: the keycode is now an arbitrary mod's number and LWJGL
            // indexes its name table by it unchecked, so without the gate one layer answering nonsense
            // would take the whole tab row down instead of costing itself a hint.
            var offTheKeyboardLayerMock = mock(MapLayer.class);

            when(offTheKeyboardLayerMock.resolveShortcutKeycode())
                .thenReturn(OFF_THE_KEYBOARD_KEYCODE);

            assertThat(LiveSidebarPlacement.resolveTabShortcuts(List.of(offTheKeyboardLayerMock)))
                .containsExactly((String) null);
        }
    }

    @Nested
    class BuildTabsSpec {

        @Test
        void buildTabsSpecLightsTheTabOfTheScreensOwnActiveLayer() {

            var layers = List.of(buildLayerMock("No Layer"), buildLayerMock("Political Map"));

            var spec = LiveSidebarPlacement.buildTabsSpec(
                layers,
                layers.get(1),
                mock(ActiveLayerSelection.class));

            // The lit index is the layer's row in the registry, which is what pairs the drawn tab with the
            // body beneath it - both index the same list.
            assertThat(spec.selectedIndex())
                .isEqualTo(1);
        }

        @Test
        void buildTabsSpecLightsNoTabForAnActiveLayerTheRegistryNoLongerHolds() {
            // A pick can outlive its layer: the mod that registered it leaves the load order while the
            // screen is still holding it. The row then lights nothing rather than lighting a tab that
            // stands for something else, and NO_SELECTION is what says so.
            var layers = List.of(buildLayerMock("No Layer"), buildLayerMock("Political Map"));

            var spec = LiveSidebarPlacement.buildTabsSpec(
                layers,
                buildLayerMock("Trade Routes"),
                mock(ActiveLayerSelection.class));

            assertThat(spec.selectedIndex())
                .isEqualTo(ControlSpec.NO_SELECTION);
            assertThat(spec.isLit())
                .isFalse();
        }

        @Test
        void buildTabsSpecSelectsTheLayerAtTheClickedTab() {
            // The switch rides on the control's own action, so the tab that was drawn at an index and the
            // layer a click on it selects are the same registry row - no separate tab callback to fall out
            // of step with the row.
            var layers = List.of(buildLayerMock("No Layer"), buildLayerMock("Political Map"));
            var selectionMock = mock(ActiveLayerSelection.class);

            LiveSidebarPlacement.buildTabsSpec(layers, layers.get(0), selectionMock)
                .action()
                .activateCell(1);

            verify(selectionMock).selectLayer(layers.get(1));
        }

        @Test
        void buildTabsSpecPairsEveryLabelWithAShortcutSlot() {
            // The control reads the two lists by index, so a row whose hints ran shorter than its labels
            // would hand a tab someone else's key - or none where one was bound.
            var layers = List.of(
                buildLayerMock("No Layer"),
                buildLayerMock("Political Map"),
                buildLayerMock("Trade Routes"));

            var spec = LiveSidebarPlacement.buildTabsSpec(
                layers,
                layers.get(0),
                mock(ActiveLayerSelection.class));

            assertThat(spec.labels())
                .hasSize(3);
            assertThat(spec.shortcuts())
                .hasSameSizeAs(spec.labels());
        }

        // A layer that letters its tab and answers no key, which is the shape every case here is about -
        // the label matters because the row refuses a null one, and the keycode is left at its unbound
        // default so no case turns on a hint it did not arrange.
        private MapLayer buildLayerMock(String label) {

            var layerMock = mock(MapLayer.class);
            when(layerMock.resolveTabLabelText())
                .thenReturn(label);

            return layerMock;
        }
    }
}
