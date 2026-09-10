package kmu.maplayers.base.layer;

import com.fs.starfarer.api.Global;

import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.render.MapLayerRenderer;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The roster of map layers, and the one answer everything that paints hangs off: which of them is on
 * the screen showing this frame. Each layer's own state reads {@link #isDrawnLayer} to decide whether
 * it is the one to draw, and the map surface dispatches through {@link #resolveDrawnMapRenderer} - so
 * all agree on one reading without sharing state directly.
 *
 * <p>Drawn rather than picked, throughout. What a screen is set to is its {@link ActiveLayerSelection}
 * and stays that class's word; what is on the screen is {@link ScreenDrawnLayer}'s, and the two part
 * for exactly as long as a dissolve runs. Every reading here is the second, since every one of them
 * exists to answer a pass that paints.
 *
 * <p>{@link #getLayers()} is the roster and not the row of tabs any screen draws: which of them a
 * given screen offers is {@link ScreenLayerTabs}', a screen carrying a control of its own on the
 * game's chrome being offered one fewer. The roster stays whole underneath that, since a stored pick
 * is an id resolved against it - filtered, a save left on a withheld tab would read as an id from an
 * older build and fall back to a layer that paints.
 *
 * <p>The player's own state is not here: which layer each screen is set to, and whether that screen
 * shows its layers at all, belong to {@link MapLayerScreens}, whose live pair this reads. Two classes
 * because the roster is a fact about what is installed, written at mod load and held for the whole
 * process, while the picks move with every click and ride in the save.
 *
 * <p>This is the feature-agnostic framework half: it knows nothing of any concrete layer. Layers
 * arrive one at a time through {@link #registerLayer}, each mod's own composition root registering
 * what it ships, so a layer is added by registering it rather than by editing this class and no
 * mod's registration displaces another's.
 *
 * <p>Nothing is settled at any one moment, because there is no moment at which the roster is known
 * to be whole: a mod that depends on this one loads after it, so its layer lands after this mod's
 * own load has returned. Every read answers from whatever has registered by the time it is taken,
 * the default pick included.
 */
public final class MapLayerRegistry {

    private static final Logger LOG = Global.getLogger(MapLayerRegistry.class);

    // The registered layers, in the order a screen offering all of them rows them up. Empty until a
    // composition root registers the first, which happens at mod load, before any sector map can open.
    //
    // Replaced wholesale on each registration rather than added to in place, so what a caller was
    // handed stays the row it asked for: the roster is read per frame and a mod may still register
    // after those reads have begun.
    private static List<MapLayer> orderedLayers = List.of();

    private MapLayerRegistry() {
    }

    /**
     * Adds one layer to the roster, at the right-hand end of the row. Called by each mod's own
     * composition root as it loads: those are the only places a concrete layer is named, so the
     * framework here stays agnostic to which ones exist.
     *
     * <p>Row order is registration order, and nothing on a layer states where it belongs. A mod can
     * see neither the row nor who else stands in it, so a position it declared would be a guess every
     * mod would make the same way. What decides the row instead is load order, which already runs
     * dependencies first: the layers a mod is built on are registered to its left. Where the player
     * wants otherwise, that is an arrangement to make over the whole row rather than a number each
     * mod picks for itself.
     *
     * <p>Two <em>different</em> layers under one id are arbitrated rather than tabbed twice: the later
     * takes the earlier one's place in the row, and the exchange is logged naming both. They would
     * otherwise share the stored pick that names the id, so a row offering both would carry two tabs a
     * save cannot tell apart. The same layer registering again is neither a clash nor a second tab -
     * it changes nothing and says nothing.
     *
     * @param layer the layer to add, or to stand in the place of one already registered under its id
     */
    public static void registerLayer(MapLayer layer) {

        var replacedIndex = findIndexOfLayerId(layer.getId());

        if (replacedIndex >= 0 && orderedLayers.get(replacedIndex) == layer) {
            return;
        }
        var revisedLayers = new ArrayList<>(orderedLayers);

        if (replacedIndex < 0) {
            revisedLayers.add(layer);
        } else {
            warnOfTheIdTwoLayersShare(orderedLayers.get(replacedIndex), layer);
            revisedLayers.set(replacedIndex, layer);
        }
        orderedLayers = List.copyOf(revisedLayers);
    }

    /**
     * @return every registered layer, in row order, left to right - the roster rather than any one
     *         screen's tabs
     */
    public static List<MapLayer> getLayers() {
        return orderedLayers;
    }

    /**
     * The layer registered under {@code layerId}, or null for an id nothing has registered - an id a
     * save holds from a build that shipped a layer since removed, or from a mod no longer installed.
     *
     * @param layerId the stored id to resolve against the roster
     * @return that layer, or null where nothing is registered under the id
     */
    public static MapLayer resolveLayerById(String layerId) {

        var layerIndex = findIndexOfLayerId(layerId);

        return layerIndex < 0 ? null : orderedLayers.get(layerIndex);
    }

    /**
     * The pick an untouched save resolves to, and the fallback for an absent or stale stored pick:
     * the first registered layer offering itself as one. So the row's order settles the default too,
     * and a layer that leads the strip without wanting to be what a new save opens on simply declines
     * - which is how the empty view leads while a layer that paints is the pick.
     *
     * <p>A roster where nobody offers falls back to the leading layer rather than to no pick at all.
     * No pick paints nothing and lights no tab, leaving a player looking at a row with nothing to move
     * off - and a roster of foreign layers alone, none of which thought to offer, is exactly the case
     * that would produce it.
     *
     * @return the default pick, or null while nothing is registered
     */
    public static MapLayer getDefaultLayer() {

        for (var layer : orderedLayers) {
            if (layer.isOfferedAsDefaultPick()) {
                return layer;
            }
        }
        return orderedLayers.isEmpty() ? null : orderedLayers.get(0);
    }

    /**
     * @return the layer on the screen showing this frame, or null before a composition root has
     *         registered any layers, or once that screen's layers have wholly faded off it. This is
     *         what the map surface dispatches its render pass through, so the paint follows the tab
     *         the player is looking at without the surface naming a layer; it follows the live screen
     *         rather than one fixed screen, so the intel screen's own tab governs what paints there
     *         while the sector map keeps its own pick
     */
    public static MapLayer getDrawnLayer() {
        return MapLayerScreens.resolveLivePicks().drawnLayer().resolveDrawnLayer();
    }

    /**
     * What draws for the screen showing this frame, over {@code machinery}'s sector, or null when
     * nothing does - because no layer is picked yet (before a composition root has registered any),
     * because that screen's layers have faded off it, or because the active one draws nothing. They
     * are one answer on purpose: every pass driven by the active pick treats them alike, so neither a
     * switch-only tab nor a hidden screen needs a case of its own in any of them.
     *
     * <p>The machinery is passed rather than resolved here because which sector is being drawn is
     * the caller's to know: the roster this registry holds is the process's, while the renderer it
     * hands back is one sector's.
     *
     * @param machinery the machinery installed on the sector being drawn
     * @return that sector's renderer for the drawn layer, or null when nothing draws
     */
    public static MapLayerRenderer resolveDrawnMapRenderer(SectorMapMachinery machinery) {
        var drawnLayer = getDrawnLayer();
        if (drawnLayer == null) {
            return null;
        }
        return drawnLayer.resolveRenderer(machinery);
    }

    /**
     * @return whether {@code layer} is the one on the screen showing this frame - the gate a layer's
     *         own state reads to decide whether it is the one in play, which a layer dissolving off a
     *         switched-off screen still is until it is gone
     */
    public static boolean isDrawnLayer(MapLayer layer) {
        return isDrawnLayerOn(MapLayerScreens.resolveLivePicks(), layer);
    }

    /**
     * The same gate as {@link #isDrawnLayer} for a screen already in hand, rather than for whichever
     * is showing.
     *
     * <p>For a caller that needs more of one screen than its tab, and so has resolved which screen it
     * is answering for once and carried it. Resolved again here, the gate could answer a different
     * screen from the rest of that caller's frame.
     *
     * @param screenPicks the screen being answered for
     * @param layer       the layer asking whether it is the one on that screen
     * @return whether {@code layer} is the layer on that screen
     */
    public static boolean isDrawnLayerOn(ScreenLayerPicks screenPicks, MapLayer layer) {
        // Layers are singletons, so identity settles it without an id compare.
        return screenPicks.drawnLayer().resolveDrawnLayer() == layer;
    }

    /**
     * Empties the roster, leaving the reading a process that has registered nothing gives.
     *
     * <p>Package-private, unlike everything above it: registering is a load-time act and nothing in a
     * running game takes a layer back off, so a caller able to empty the roster could only ever take
     * every tab off the bar with no way to put one back.
     */
    static void forgetLayers() {
        orderedLayers = List.of();
    }

    // Says one id reached the row twice, which is silent to the player: the log is where the author of
    // the layer that lost finds out why their tab is not the one on the bar. Named by class, that
    // being what says which mod each side came from.
    private static void warnOfTheIdTwoLayersShare(MapLayer displacedLayer, MapLayer registeredLayer) {

        LOG.warn("Two map layers registered under the id '" + registeredLayer.getId() + "': "
            + displacedLayer.getClass().getName() + " gives way to "
            + registeredLayer.getClass().getName() + ", which takes its place in the row.");
    }

    // Where a layer under this id already stands in the row, or -1 for an id nothing has registered.
    // Compared by id rather than by identity, since the case this answers is two mods arriving with
    // one id and no notion of each other's objects.
    private static int findIndexOfLayerId(String layerId) {

        for (var index = 0; index < orderedLayers.size(); index++) {
            if (orderedLayers.get(index).getId().equals(layerId)) {
                return index;
            }
        }
        return -1;
    }
}
