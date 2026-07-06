package kmu.politicalmap.layer;

import kmu.util.KmuStrings;

import org.lwjgl.input.Keyboard;

/**
 * The empty view: selecting it paints no overlay, so the sector map reads as vanilla. It is a
 * first-class tab rather than an off state, so the bar always shows what is and is not drawn
 * and the player has an explicit "show nothing" pick. Its caption states plainly that no
 * overlay is up, so the empty map is understood as a choice, not a failure to draw.
 */
public final class NoLayer implements PoliticalMapLayer {
    // Jumps here on N by default. Mirrors the Keycode default in LunaSettings.csv.
    private static final String SHORTCUT_FIELD = "kmu_politicalMapNoLayerKey";

    @Override
    public String getId() {
        return "no_layer";
    }

    @Override
    public String getTabLabelKey() {
        return KmuStrings.POLITICAL_MAP_TAB_NO_LAYER;
    }

    @Override
    public String getCaptionKey() {
        return KmuStrings.POLITICAL_MAP_CAPTION_NO_LAYER;
    }

    @Override
    public int getDefaultShortcutKeycode() {
        return Keyboard.KEY_N;
    }

    @Override
    public String getShortcutSettingKey() {
        return SHORTCUT_FIELD;
    }
}
