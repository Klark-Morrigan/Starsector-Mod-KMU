package kmu.maplayers.base.layer;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.render.MapLayerRenderer;

import java.util.List;

/**
 * The roster of map layers, and the one answer everything that paints hangs off: which of them is in
 * play on the screen showing this frame. Each layer's own state reads {@link #isActive} to decide
 * whether it is the one to draw, and the map surface dispatches through
 * {@link #resolveActiveMapRenderer} - so all agree on one selection without sharing state directly.
 *
 * <p>{@link #getLayers()} is the roster and not the row of tabs any screen draws: which of them a
 * given screen offers is {@link ScreenLayerTabs}', a screen carrying a control of its own on the
 * game's chrome being offered one fewer. The roster stays whole underneath that, since a stored pick
 * is an id resolved against it - filtered, a save left on a withheld tab would read as an id from an
 * older build and fall back to a layer that paints.
 *
 * <p>The player's own state is not here: which layer each screen is set to, and whether that screen
 * shows its layers at all, belong to {@link MapLayerScreens}, whose live pair this reads. Two classes
 * because the roster is settled once at startup for the whole process while the picks move with every
 * click and ride in the save.
 *
 * <p>This is the feature-agnostic framework half: it knows nothing of any concrete layer. The set of
 * layers and the default pick are supplied once at startup by a composition root through
 * {@link #registerLayers}, so a new view is added by registering it rather than by editing this class.
 * An untouched save resolves to the registered default, as does one holding an id no longer registered.
 */
public final class MapLayerRegistry {

    // The end of the hide ramp: none of a screen's layers left on it, which is where the active pick
    // stops being answered at all.
    private static final float FULLY_HIDDEN = 0f;

    // The registered layers, in the order a screen offering all of them rows them up, and the pick an
    // untouched save resolves to. Empty until a composition root registers them at startup, before
    // any sector map can open.
    private static List<MapLayer> orderedLayers = List.of();
    private static MapLayer defaultLayer;

    private MapLayerRegistry() {
    }

    /**
     * Records the layers that exist and the pick an untouched save resolves to. Called once
     * by the composition root at startup: it is the only place a concrete layer is named, so
     * the framework here stays agnostic to which views exist.
     *
     * @param layers        the registered layers in row order, left to right
     * @param defaultLayer  the pick an untouched save (or a stale stored id) resolves to
     */
    public static void registerLayers(List<MapLayer> layers, MapLayer defaultLayer) {
        orderedLayers = List.copyOf(layers);
        MapLayerRegistry.defaultLayer = defaultLayer;
    }

    /**
     * @return every registered layer, in row order, left to right - the roster rather than any one
     *         screen's tabs
     */
    public static List<MapLayer> getLayers() {
        return orderedLayers;
    }

    /** @return the pick an untouched save resolves to, the fallback for an absent or stale stored pick. */
    public static MapLayer getDefaultLayer() {
        return defaultLayer;
    }

    /**
     * @return the active pick of the screen showing this frame, or null before a composition root has
     *         registered any layers, or once that screen's layers have wholly faded off it. This is
     *         what the map surface dispatches its render pass through, so the paint follows the tab
     *         the player is looking at without the surface naming a layer; it follows the live screen
     *         rather than one fixed screen, so the intel screen's own tab governs what paints there
     *         while the sector map keeps its own pick
     */
    public static MapLayer getActiveLayer() {

        // One resolution for both readings, so the pick answered and the show-or-hide state gating it
        // cannot come off different screens.
        var livePicks = MapLayerScreens.resolveLivePicks();

        // Hiding lands here rather than at each consumer, because every pass driven by the active pick
        // already treats "no pick" as nothing to draw: one read takes the overlay, the labels and the
        // hover box off the screen together.
        if (!isAnythingOfTheLayersOn(livePicks.layerVisibility())) {
            return null;
        }
        return livePicks.layerSelection().getActiveLayer();
    }

    /**
     * What draws for the screen showing this frame, over {@code installation}'s sector, or null when
     * nothing does - because no layer is picked yet (before a composition root has registered any),
     * because that screen's layers have faded off it, or because the active one draws nothing. They
     * are one answer on purpose: every pass driven by the active pick treats them alike, so neither a
     * switch-only tab nor a hidden screen needs a case of its own in any of them.
     *
     * <p>The installation is passed rather than resolved here because which sector is being drawn is
     * the caller's to know: the roster this registry holds is the process's, while the renderer it
     * hands back is one sector's.
     *
     * @param installation the machinery installed on the sector being drawn
     * @return that sector's renderer for the active pick, or null when nothing draws
     */
    public static MapLayerRenderer resolveActiveMapRenderer(MapLayerInstallation installation) {
        var activeLayer = getActiveLayer();
        if (activeLayer == null) {
            return null;
        }
        return activeLayer.resolveRenderer(installation);
    }

    /**
     * @return whether {@code layer} is the active pick of the screen showing this frame - the gate a
     *         layer's own state reads to decide whether it is the one in play
     */
    public static boolean isActive(MapLayer layer) {
        // Layers are singletons, so identity settles it without an id compare.
        return getActiveLayer() == layer;
    }

    // Whether anything of the given screen's layers is on it at all: a screen switched off goes on being
    // drawn until its ramp reaches the end, which is what dissolves it rather than blinking it out.
    //
    // The crisp pick settles a shown screen outright, and the fade is asked for only where the answer
    // could still turn on it. A screen coming back paints from the first frame of its ramp whatever the
    // fade reads, so consulting it there could only ever agree - at the price of a clock read, and of a
    // settings read behind it, on every frame the layers are simply on.
    private static boolean isAnythingOfTheLayersOn(MapLayerVisibility visibility) {
        return visibility.areLayersShown() || visibility.resolveShownFade() > FULLY_HIDDEN;
    }
}
