package kmu.maplayers.base.layer;

import kmu.settings.KmuMapKeybindSettings;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the two things the empty view resolves for itself. The bar draws what a layer hands back and reads
 * neither a bundle nor a settings file of its own, so the key and the field id a layer reaches for are now
 * the layer's own facts: swapped for another tab's, they are a wrong label and a stolen shortcut on screen
 * and nothing else catches either. What the strings key is worded as, and what the settings row defaults
 * to in the shipped CSV, are pinned against those files elsewhere.
 */
final class NoLayerTest {

    // The live LunaLib field id, pinned as a literal: a rename here silently drops the player's rebind and
    // returns the tab to its default key.
    private static final String SHORTCUT_SETTING_FIELD = "kmu_map_keybinds_layers_noLayer";

    // A key the player rebound to, distinct from the default so a read that ignored the store still fails.
    private static final int REBOUND_KEYCODE = 20;

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
    class ResolveShortcutKeycode {

        @Test
        void resolveShortcutKeycodeReadsTheEmptyViewsOwnRebindingField() {
            // The layer holds both halves of the read now - which field the rebind lands in and what the
            // tab answers to before there is one - so a wrong id here silently ignores the player's rebind
            // while every framework test stays green.
            try (var settingsMock = mockStatic(KmuMapKeybindSettings.class)) {

                settingsMock
                    .when(() -> KmuMapKeybindSettings.getMapLayerShortcut(
                        SHORTCUT_SETTING_FIELD,
                        Keyboard.KEY_N))
                    .thenReturn(REBOUND_KEYCODE);

                assertThat(NoLayer.INSTANCE.resolveShortcutKeycode())
                    .isEqualTo(REBOUND_KEYCODE);
            }
        }
    }
}
