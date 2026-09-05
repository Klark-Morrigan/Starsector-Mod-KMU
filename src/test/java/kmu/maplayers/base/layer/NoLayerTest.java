package kmu.maplayers.base.layer;

import kmu.settings.KmuMapKeybindSettings;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the two things the empty view resolves for itself. The bar draws what a layer hands back and reads
 * neither a bundle nor a settings file of its own, so the strings key and the settings row a layer reaches
 * for are now the layer's own facts: swapped for another tab's, they are a wrong label and a stolen
 * shortcut on screen and nothing else catches either. What the strings key is worded as, and what the
 * settings row is worth on a fresh install, are pinned against those files elsewhere - this layer states
 * no key of its own, which is the second case below.
 */
final class NoLayerTest {

    // The live LunaLib field id, pinned as a literal: a rename here silently drops the player's rebind and
    // leaves the tab keyless.
    private static final String SHORTCUT_SETTING_FIELD = "kmu_map_keybinds_layers_noLayer";

    // Whatever the settings row is answering with - the value is arbitrary, since the point is that the
    // layer hands it back untouched rather than that it is any particular key.
    private static final int BOUND_KEYCODE = 20;

    @Nested
    class ResolveTabLabelText {

        @Test
        void resolveTabLabelTextLettersTheTabFromTheEmptyViewsOwnKey() {

            try (var stringsMock = mockStatic(KmuStrings.class)) {

                stringsMock
                    .when(() -> KmuStrings.get(KmuStrings.MAP_LAYER_TAB_NO_LAYER))
                    .thenReturn("No Layer");

                assertThat(NoLayer.INSTANCE.resolveTabLabelText())
                    .isEqualTo("No Layer");
            }
        }
    }

    @Nested
    class GetBodyControls {

        @Test
        void getBodyControlsOpensNoPanelForTheScreenThatAsked() {
            // The empty view's whole contract is to draw nothing, so its tab opens nothing either - on
            // whichever screen's panel it is selected. Nothing here stores a preference, which is why the
            // screen goes unread rather than partitioning anything.
            assertThat(NoLayer.INSTANCE.getBodyControls(ScreenMemoryScopes.createStandInScreen()))
                .isEmpty();
        }
    }

    @Nested
    class ResolveShortcutKeycode {

        @Test
        void resolveShortcutKeycodeReadsTheEmptyViewsOwnRebindingField() {
            // Which row the rebind lands in is the layer's own fact now, so a wrong id here silently
            // ignores the player's rebind while every framework test stays green.
            try (var settingsMock = mockStatic(KmuMapKeybindSettings.class)) {

                settingsMock
                    .when(() -> KmuMapKeybindSettings.getMapLayerShortcut(SHORTCUT_SETTING_FIELD))
                    .thenReturn(BOUND_KEYCODE);

                assertThat(NoLayer.INSTANCE.resolveShortcutKeycode())
                    .isEqualTo(BOUND_KEYCODE);
            }
        }

        @Test
        void resolveShortcutKeycodeLeavesTheTabUnboundWhenTheSettingsRowAnswersNoKey() {
            // No key of this layer's own stands behind the row: a settings read answering nothing leaves
            // the tab unbound, which the bar draws no hint for and matches no press against. A fallback
            // keycode here would be a second answer to what the shipped table already decides, and would
            // bind a key the player had cleared.
            try (var settingsMock = mockStatic(KmuMapKeybindSettings.class)) {

                assertThat(NoLayer.INSTANCE.resolveShortcutKeycode())
                    .isZero();
            }
        }
    }
}
