package kmu.maplayers.base.layer;

import kmlib.starsector.ui.controls.ControlSpec;

import java.util.List;

/**
 * One selectable view of the sector map: a tab in the on-map layer bar and, for the views
 * that paint, one styling of the sector's territory. The bar composes whatever layers are
 * registered with {@link MapLayerRegistry} into a row of tabs and switches between them,
 * exactly one active at a time - the same model as the map's own Sector/System tabs.
 *
 * <p>The framework exists so a new view (Nexerelin alliances is the next planned one) is a
 * matter of adding an implementation and registering it: the bar draws its tab, the input
 * listener hit-tests it, and its hotkey switches to it, all with no change to the UI code.
 * A layer here is a descriptor - id, tab label, body controls, hotkey - not a renderer; the
 * territory paint lives in a terrain plugin, which reads the active pick to decide whether
 * its view is the one to draw.
 */
public interface MapLayer {

    /**
     * @return the stable id stored in the save to remember the active layer; frozen once
     *         shipped, since renaming it silently resets existing saves to the default
     */
    String getId();

    /** @return the strings.json key for this layer's tab label. */
    String getTabLabelKey();

    /**
     * @return the control specs this layer's tab opens in its body once selected, top to
     *         bottom; empty for a tab that only switches the map paint and opens no body. The
     *         layer describes its controls, so a new view supplies its own body without the
     *         bar hard-coding one per layer.
     */
    List<ControlSpec> getBodyControls();

    /**
     * @return the LWJGL keycode this layer's tab jumps to before the player rebinds it in
     *         LunaLib; also the "[N]" / "[P]" hint the tab prints. Mirrors the matching
     *         Keycode default in data/config/LunaSettings.csv.
     */
    int getDefaultShortcutKeycode();

    /** @return the LunaLib field id holding the player's rebound shortcut keycode. */
    String getShortcutSettingKey();
}
