package kmu.maplayers.base.layer;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.settings.KmuMapKeybindSettings;
import kmu.util.KmuStrings;

import java.util.List;

/**
 * The empty view: selecting it paints no overlay, so the sector map reads as vanilla. It is a
 * first-class tab rather than an off state, so the player has an explicit "show nothing" pick and
 * an empty map is understood as a choice rather than a failure to draw. Its tab opens no body
 * (empty controls), for the same reason.
 *
 * <p>Where the map's own chrome carries a control that empties the map, this tab stands down in its
 * favour - {@link ScreenLayerTabs} withholds it per screen, that being where such a control stands
 * or fails to. So it is the pick a screen falls back to rather than one always on offer.
 */
public final class NoLayer implements MapLayer {

    /** The one shared instance; the registration and any state gate reference this pick. */
    public static final NoLayer INSTANCE = new NoLayer();

    // LunaLib stores this tab's key under the field id; the row it names is where the key is decided.
    private static final String SHORTCUT_SETTING_FIELD = "kmu_map_keybinds_layers_noLayer";

    private NoLayer() {
    }

    @Override
    public String getId() {
        return "no_layer";
    }

    @Override
    public String resolveTabLabelText() {
        return KmuStrings.get(KmuStrings.MAP_LAYER_TAB_NO_LAYER);
    }

    @Override
    public boolean isOfferedAsDefaultPick() {
        // Leads the strip without being the pick: a save that has never been touched opens on a map
        // that paints, and this tab is where the player goes to ask for the map they already had.
        return false;
    }

    @Override
    public List<ControlSpec> getBodyControls(ScreenMemoryScope memoryScope) {
        // The empty view opens no control panel: its tab only clears the map paint. The asking screen
        // goes unread for the same reason - a body with no controls in it stores nothing, so there is
        // no preference here for a screen to hold its own version of.
        return List.of();
    }

    @Override
    public int resolveShortcutKeycode() {
        // The framework asks for the key in force rather than for a field to read, so the LunaLib
        // lookup is this layer's own: it is the only thing that knows its row exists in KMU's settings
        // file, and a layer from another mod answers however its own mod stores keys.
        return KmuMapKeybindSettings.getMapLayerShortcut(SHORTCUT_SETTING_FIELD);
    }

    @Override
    public MapLayerStanding resolveStanding() {
        // Nothing to stand up: this tab registers no listener, polls nothing and heals nothing, so
        // there is no sector wiring for taking its tab off the bar to save. It is always standing in
        // the only sense that means anything here - selecting it costs a paint that draws nothing.
        return null;
    }

    @Override
    public MapLayerRenderer resolveRenderer(MapLayerInstallation installation) {
        // No renderer is the "show nothing" pick expressed to the map surface: it draws whatever the
        // active layer draws, and this layer draws nothing. The installation goes unread for the
        // same reason - there is nothing here that a sector could differ in.
        return null;
    }
}
