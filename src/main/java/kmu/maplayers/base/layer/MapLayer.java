package kmu.maplayers.base.layer;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.render.MapLayerRenderer;

import java.util.List;

/**
 * One selectable view of the sector map: a tab in the on-map layer bar and, for the views
 * that paint, one styling of the sector's clusters. The bar composes whatever layers are
 * registered with {@link MapLayerRegistry} into a row of tabs and switches between them,
 * exactly one active at a time - the same model as the map's own Sector/System tabs.
 *
 * <p>The framework exists so a new view is a matter of adding an implementation and
 * registering it: the bar draws its tab, the input listener hit-tests it, and its hotkey
 * switches to it, all with no change to the UI code.
 * A layer here is a descriptor - id, tab label, body controls, hotkey - not a renderer; drawing is
 * a separate role a layer may fill, supplied through {@link #resolveRenderer} and driven by the
 * terrain surface that owns the map's render pass.
 *
 * <p>A layer is registered once for the process, while what it draws with is one sector's - so a
 * layer holds no renderer and is asked for the one belonging to the sector being drawn. That is what
 * keeps the roster a mod-load fact while the caches behind the paint begin and end with a sector.
 */
public interface MapLayer {

    /**
     * @return the stable id stored in the save to remember the active layer; frozen once
     *         shipped, since renaming it silently resets existing saves to the default
     */
    String getId();

    /**
     * The text drawn on this layer's tab, resolved rather than keyed: a strings key is only meaningful
     * to the bundle holding it, and the framework holds one bundle while a layer may come from any mod.
     *
     * <p>Asked for per read rather than taken once, matching how the shortcut hint is already resolved:
     * the tabs are rebuilt from the roster each frame, so a label that follows a setting or a save
     * follows it, instead of being frozen at whatever it said when the layer registered.
     *
     * @return this layer's tab label; blank leaves a drawable but unlettered tab rather than no tab
     */
    String resolveTabLabelText();

    /**
     * @return the control specs this layer's tab opens in its body once selected, top to
     *         bottom; empty for a tab that only switches the map paint and opens no body. The
     *         layer describes its controls, so a new view supplies its own body without the
     *         bar hard-coding one per layer.
     */
    List<ControlSpec> getBodyControls();

    /**
     * The keycode this layer's tab jumps to and prints as its hint, resolved rather than described by
     * a rebinding field: a settings field id is only meaningful to the settings file holding it, and
     * the framework reads one file while a layer may come from any mod. A layer offering no rebinding
     * at all answers a constant rather than inventing a field id it has no reader for.
     *
     * <p>Asked for per read, like the label above: the tab hints and the key claim are both rebuilt
     * from the roster each frame, so a rebind reaches the next frame rather than the next load.
     *
     * <p><b>Having no shortcut is a first-class answer.</b> Non-positive means unbound, and the whole
     * hotkey path is then inert for this layer - the tab prints no hint and no press ever selects it,
     * the tab itself staying exactly where it is in the row. The framework defaults nothing in its
     * place: a layer that wants no key says so by answering zero and needs no settings row, and a
     * layer whose key comes from a setting that cannot be read is unbound rather than bound to a
     * number the framework picked. That is also the state a player reaches by clearing a binding.
     *
     * @return the LWJGL keycode in force, or a non-positive code for a layer with no shortcut
     */
    int resolveShortcutKeycode();

    /**
     * What draws this layer's overlay for one sector while it is the active pick, or null for a
     * layer that draws nothing - a switch-only tab, which the map surface reads as nothing to draw.
     * Declared here rather than defaulted to null so every layer answers the question deliberately;
     * a new view that forgets to draw fails to compile rather than coming up blank.
     *
     * <p>An implementation that draws holds its renderer in the installation rather than in itself,
     * so the same registered layer answers for two sectors with two renderers - each over the draw
     * lists cut from its own sector, and each released when that sector's machinery is.
     *
     * @param installation the machinery installed on the sector being drawn
     * @return that sector's renderer for this layer, or null for a layer that draws nothing
     */
    MapLayerRenderer resolveRenderer(MapLayerInstallation installation);
}
