package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the one thing this reader decides for itself: that a key it cannot get an answer for is unbound
 * rather than a key of its own choosing. Every other settings section mirrors its row's default as a Java
 * fallback, so the walk that holds the two spellings together covers them; a keycode has no second
 * spelling to hold it against, which leaves the fallback stated in exactly one place - here.
 *
 * <p>The field IDs are pinned as literals alongside it, since a rename in the reader would otherwise read
 * a row the shipped table never writes and answer unbound forever, which looks exactly like a key the
 * player has cleared. What each row is worth on a fresh install is the table's, and pinned there.
 */
final class KmuMapKeybindSettingsTest {

    // The live field IDs, as the shipped table spells them.
    private static final String NO_LAYER_FIELD = "kmu_map_keybinds_layers_noLayer";
    private static final String FILTER_ROW_TOGGLE_FIELD = "kmu_map_keybinds_filters_mapLayersToggle";

    // What LunaLib is standing in as having stored. Arbitrary: what is under test is that the reader
    // hands back the row's answer rather than that the answer is any particular key.
    private static final int STORED_KEYCODE = 20;

    // LWJGL's KEY_NONE, which every path taking a keycode reads as no key at all.
    private static final int UNBOUND = 0;

    @Nested
    class GetMapLayerShortcut {

        @Test
        void getMapLayerShortcutAnswersTheRowTheCallerNames() {

            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(NO_LAYER_FIELD, UNBOUND))
                    .thenReturn(STORED_KEYCODE);

                assertThat(KmuMapKeybindSettings.getMapLayerShortcut(NO_LAYER_FIELD))
                    .isEqualTo(STORED_KEYCODE);
            }
        }

        @Test
        void getMapLayerShortcutIsUnboundWhenTheRowCannotBeRead() {
            // The rule this reader exists to hold: the fallback it hands the settings substrate is "no
            // key". A keycode there instead would bind a key the shipped table never chose, and would go
            // on doing so with nothing on screen or in the settings dialog to say where it came from.
            //
            // Stood in as a substrate that answers with whatever fallback it is handed, which is what an
            // unset or unreadable row gets - so what comes back is the fallback itself.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(anyString(), anyInt()))
                    .thenAnswer(invocation -> invocation.getArgument(1));

                assertThat(KmuMapKeybindSettings.getMapLayerShortcut(NO_LAYER_FIELD))
                    .isZero();
            }
        }
    }

    @Nested
    class GetMapFilterRowToggleShortcut {

        @Test
        void getMapFilterRowToggleShortcutAnswersItsOwnRow() {
            // Its ID is the reader's own rather than a caller's, this box being KMU's chrome with no
            // layer behind it - so a typo here is invisible until a player finds the key does nothing.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(FILTER_ROW_TOGGLE_FIELD, UNBOUND))
                    .thenReturn(STORED_KEYCODE);

                assertThat(KmuMapKeybindSettings.getMapFilterRowToggleShortcut())
                    .isEqualTo(STORED_KEYCODE);
            }
        }
    }
}
