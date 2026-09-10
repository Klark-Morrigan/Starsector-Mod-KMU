package kmu.maplayers.base.layer;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.render.MapLayerRenderer;

import java.util.List;

/**
 * One selectable view of the sector map: a tab in the on-map layer bar and, for the views
 * that paint, one styling of the sector's clusters. The bar composes the layers registered with
 * {@link MapLayerRegistry} - as many of them as the screen it draws on is offered - into a row of
 * tabs and switches between them, exactly one active at a time, the same model as the map's own
 * Sector/System tabs.
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
     * Whether this layer offers itself as the pick a save with no stored one opens on. The first
     * registered layer answering yes is what the framework settles on, so a layer says only that it
     * is willing rather than which of the others it beats - no layer can see the rest of the row, and
     * a claim to be better than layers it cannot name would be a guess.
     *
     * <p>Declining is the answer for a layer whose picture the player has to go looking for, and for
     * one that paints nothing: an untouched save should open on something rather than on a row where
     * every tab is unlit.
     *
     * @return whether this layer is willing to be what a save with no pick of its own resolves to
     */
    boolean isOfferedAsDefaultPick();

    /**
     * The controls this layer's tab opens in its body once selected.
     *
     * <p>Built for a named screen rather than for the live one: a control placed in this body writes
     * whatever preference it stands for when it is clicked, and that write belongs to the screen the
     * panel it was placed on draws for. A layer given no screen would have to resolve one, which is a
     * second answer to a question the asking panel already holds - and the two part company whenever one
     * host lays its body out while the other screen is the one up.
     *
     * @param memoryScope the scope of the screen whose panel is opening this body, for the layer to pass
     *                    to every preference its controls read and write
     * @return the control specs this layer's tab opens in its body once selected, top to
     *         bottom; empty for a tab that only switches the map paint and opens no body. The
     *         layer describes its controls, so a new view supplies its own body without the
     *         bar hard-coding one per layer.
     */
    List<ControlSpec> getBodyControls(ScreenMemoryScope memoryScope);

    /**
     * The keycode this layer's tab jumps to and prints as its hint, resolved rather than described by
     * a rebinding field: a settings field id is only meaningful to the settings file holding it, and
     * the framework reads one file while a layer may come from any mod.
     *
     * <p>Asked for per read, like the label above: the tab hints and the key claim are both rebuilt
     * from the roster each frame, so a rebind reaches the next frame rather than the next load.
     *
     * @return the LWJGL keycode in force, or non-positive for a layer with no shortcut - a binding the
     *         player cleared, a setting that could not be read, or a layer that wants no key at all.
     *         The framework substitutes nothing for it: the tab keeps its place in the row, prints no
     *         hint, and answers no press
     */
    int resolveShortcutKeycode();

    /**
     * What this layer needs of a sector while its tab is on the bar, and how to take it back, or
     * null for a layer with no sector wiring at all - which is a layer that is simply always
     * standing. Declared rather than defaulted for the reason the renderer below is: a new view
     * answers the question deliberately instead of coming up silently unwired.
     *
     * <p>Held apart from registration because the two are asked at different times and of different
     * scopes. A layer registers once for the process, while what it registers on a sector begins and
     * ends with that sector - and with whether the player has that layer's tab on their bar at all,
     * which is what lets a tab they took off stop costing.
     *
     * @return this layer's pair, or null for a layer that needs nothing of a sector
     */
    MapLayerStanding resolveStanding();

    /**
     * What draws this layer's overlay for one sector while it is the active pick, or null for a
     * layer that draws nothing - a switch-only tab, which the map surface reads as nothing to draw.
     * Declared here rather than defaulted to null so every layer answers the question deliberately;
     * a new view that forgets to draw fails to compile rather than coming up blank.
     *
     * <p>An implementation that draws holds its renderer in the machinery rather than in itself,
     * so the same registered layer answers for two sectors with two renderers - each over the draw
     * lists cut from its own sector, and each released when that sector's machinery is.
     *
     * @param machinery the machinery installed on the sector being drawn
     * @return that sector's renderer for this layer, or null for a layer that draws nothing
     */
    MapLayerRenderer resolveRenderer(SectorMapMachinery machinery);
}
