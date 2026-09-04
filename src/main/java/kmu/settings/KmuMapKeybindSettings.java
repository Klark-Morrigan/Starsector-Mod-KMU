package kmu.settings;

import org.lwjgl.input.Keyboard;

/**
 * Every key the map overlay answers to: what the {@code Map - Keybinds} tab holds, and nothing else.
 *
 * <p>A section of {@link KmuMapLayerSettings} rather than a reader of its own. The split that
 * matters is by which package reads a knob, and these are read from the same place the rest of the
 * framework's chrome settings are - so this buys no isolation and is not claiming to. What it buys
 * is that one screen of the settings dialog has one class behind it: a key added to the tab has one
 * obvious home, and a reader wanting to know what the player can rebind reads a file rather than
 * sifting a large one.
 *
 * <p>The two readings here are different in kind, which is the other reason they sit together.
 * A layer's tab key is looked up by an id the caller supplies, because a layer owns the row its own
 * key is stored under and is the only thing that can name it; the callers are KMU's own two layers,
 * each answering the keycode its tab is in force with. The map-layer framework itself never reaches
 * here - it asks a layer for the key rather than for a field - so a layer shipped by another mod
 * binds its tab out of that mod's own settings and this file has nothing to say about it. The filter
 * row's box is KMU's own chrome with no such caller, so its id and default are stated here like any
 * other knob's.
 *
 * <p>What each key does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, and how any of them are rebound once more, in that tab's own note
 * row. The prose here answers only what those cannot: why a default is the key it is, and what a
 * caller has to know to use the value.
 */
public final class KmuMapKeybindSettings {

    // The tick box appended to the game's own map filter row. Its other knob - whether the box is
    // put there at all - is a dev hatch and lives with the rest of those; this is the only half of
    // it a player sets to taste.
    private static final String FILTER_ROW_TOGGLE_SHORTCUT_FIELD =
        "kmu_map_keybinds_filters_mapLayersToggle";

    // M for map, and free on both screens the box stands on. Not a digit: the vanilla row's own six
    // buttons are keyed to digits, and nothing in the game can be asked which of them a screen has
    // already taken - a shortcut lives on the individual widget, with no table of them to read.
    private static final int DEFAULT_FILTER_ROW_TOGGLE_SHORTCUT = Keyboard.KEY_M;

    private KmuMapKeybindSettings() {
    }

    /**
     * @return the LWJGL keycode that ticks and unticks the map layers' box on the vanilla filter
     *         row; M by default, and 0 where the player has cleared the binding. Read afresh each
     *         time a box is stood on a row, so a rebind reaches the next screen the player opens
     *         rather than waiting for the next load
     */
    public static int getMapFilterRowToggleShortcut() {
        return KmuLunaSettings.readInt(
            FILTER_ROW_TOGGLE_SHORTCUT_FIELD,
            DEFAULT_FILTER_ROW_TOGGLE_SHORTCUT);
    }

    /**
     * Resolves a layer tab's shortcut keycode from its LunaLib Keycode field, so the player can
     * rebind which key jumps to that layer.
     *
     * <p>The field id is the caller's rather than a constant here: each layer owns the id its own
     * shortcut is stored under, so one reader serves every KMU layer with a row on the tab and a
     * layer added later needs no case of its own here.
     *
     * <p>A keycode of 0 (LWJGL's {@code KEY_NONE}) means the player cleared the binding with
     * Escape, so the layer has no shortcut; callers treat that as unbound rather than a real key.
     *
     * @param settingKey     the LunaLib field id holding the rebound keycode
     * @param defaultKeycode the LWJGL keycode used when the field is unset or unreadable
     * @return the LWJGL keycode the layer's tab jumps to, or 0 when the shortcut is unbound
     */
    public static int getMapLayerShortcut(String settingKey, int defaultKeycode) {
        return KmuLunaSettings.readInt(settingKey, defaultKeycode);
    }
}
