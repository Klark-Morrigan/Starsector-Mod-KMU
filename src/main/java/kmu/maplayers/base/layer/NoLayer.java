package kmu.maplayers.base.layer;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.util.KmuStrings;

import org.lwjgl.input.Keyboard;

import java.util.List;

/**
 * The empty view: selecting it paints no overlay, so the sector map reads as vanilla. It is a
 * first-class tab rather than an off state, so the bar always shows what is and is not drawn
 * and the player has an explicit "show nothing" pick. Its tab opens no body (empty controls),
 * so the empty map is understood as a choice, not a failure to draw.
 */
public final class NoLayer implements MapLayer {
    /** The one shared instance; the registration and any state gate reference this pick. */
    public static final NoLayer INSTANCE = new NoLayer();

    // Jumps here on N by default. Mirrors the Keycode default in LunaSettings.csv.
    private static final String SHORTCUT_FIELD = "kmu_politicalMapNoLayerKey";

    private NoLayer() {
    }

    @Override
    public String getId() {
        return "no_layer";
    }

    @Override
    public String getTabLabelKey() {
        return KmuStrings.POLITICAL_MAP_TAB_NO_LAYER;
    }

    @Override
    public List<ControlSpec> getBodyControls() {
        // The empty view opens no control panel: its tab only clears the map paint.
        return List.of();
    }

    @Override
    public int getDefaultShortcutKeycode() {
        return Keyboard.KEY_N;
    }

    @Override
    public String getShortcutSettingKey() {
        return SHORTCUT_FIELD;
    }

    @Override
    public MapLayerRenderer getMapRenderer() {
        // No renderer is the "show nothing" pick expressed to the map surface: it draws whatever the
        // active layer draws, and this layer draws nothing.
        return null;
    }
}
