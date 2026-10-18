package kmu.maplayers.base.layer;

import com.fs.starfarer.api.Global;

import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The roster of map layers: every layer installed, in the order a screen offering all of them rows
 * them up, and the default pick a save without one resolves to.
 *
 * <p>{@link #getLayers()} is the roster and not the row of tabs any screen draws: which of them a
 * given screen offers is {@link ScreenLayerTabs}', a screen carrying a control of its own on the
 * game's chrome being offered one fewer. The roster stays whole underneath that, since a stored pick
 * is an ID resolved against it - filtered, a save left on a withheld tab would read as an ID from an
 * older build and fall back to a layer that paints.
 *
 * <p>Which layer is on the screen this frame is not here but on {@link MapLayerScreens}, which is
 * built over this roster. Two classes because the roster is a fact about what is installed, written
 * at mod load and held for the whole process, while the picks move with every click and ride in the
 * save.
 *
 * <p>This is the feature-agnostic framework half: it knows nothing of any concrete layer. Layers
 * arrive one at a time through {@link #registerLayer}, the composition root registering what it
 * ships, so a layer is added by registering it rather than by editing this class.
 *
 * <p>One roster for the process, made by the composition root and handed to what reads it. An
 * instance rather than a set of statics, so a reader holds the roster it was given and a fresh one
 * starts from nothing without anything having to empty it.
 */
public final class MapLayerRegistry {

    private static final Logger LOG = Global.getLogger(MapLayerRegistry.class);

    // The registered layers, in the order a screen offering all of them rows them up. Empty until the
    // composition root registers the first, which happens at mod load, before any sector map can open.
    //
    // Replaced wholesale on each registration rather than added to in place, so what a caller was
    // handed stays the row it asked for, whatever registers after it asked.
    private List<MapLayer> orderedLayers = List.of();

    /**
     * Adds one layer to the roster, at the right-hand end of the row. Called by the composition root
     * as it loads: that is the only place a concrete layer is named, so the framework here stays
     * agnostic to which ones exist.
     *
     * <p>Row order is registration order, and nothing on a layer states where it belongs. A layer can
     * see neither the row nor who else stands in it, so a position it declared would be a guess every
     * layer would make the same way. Where the player wants otherwise, that is an arrangement to make
     * over the whole row rather than a number each layer picks for itself.
     *
     * <p>Two <em>different</em> layers under one ID are arbitrated rather than tabbed twice: the later
     * takes the earlier one's place in the row, and the exchange is logged naming both. They would
     * otherwise share the stored pick that names the ID, so a row offering both would carry two tabs a
     * save cannot tell apart. The same layer registering again is neither a clash nor a second tab -
     * it changes nothing and says nothing.
     *
     * @param layer the layer to add, or to stand in the place of one already registered under its ID
     */
    public void registerLayer(MapLayer layer) {

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

        LOG.debug("Map layer '" + layer.getId() + "' registered; layers=" + orderedLayers.size());
    }

    /**
     * @return every registered layer, in row order, left to right - the roster rather than any one
     *         screen's tabs
     */
    public List<MapLayer> getLayers() {
        return orderedLayers;
    }

    /**
     * The layer registered under {@code layerId}, or null for an ID nothing has registered - an ID a
     * save holds from a build that shipped a layer since removed, or from a mod no longer installed.
     *
     * @param layerId the stored ID to resolve against the roster
     * @return that layer, or null where nothing is registered under the ID
     */
    public MapLayer resolveLayerById(String layerId) {

        var layerIndex = findIndexOfLayerId(layerId);
        return layerIndex < 0
            ? null
            : orderedLayers.get(layerIndex);
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
    public MapLayer getDefaultLayer() {

        for (var layer : orderedLayers) {

            if (layer.isOfferedAsDefaultPick()) {
                return layer;
            }
        }

        return orderedLayers.isEmpty()
            ? null
            : orderedLayers.get(0);
    }

    // Says one ID reached the row twice, which is silent to the player: the log is where the author of
    // the layer that lost finds out why their tab is not the one on the bar. Named by class, that
    // being what says which mod each side came from.
    private void warnOfTheIdTwoLayersShare(MapLayer displacedLayer, MapLayer registeredLayer) {

        LOG.warn("Two map layers registered under the id '" + registeredLayer.getId() + "': "
            + displacedLayer.getClass().getName() + " gives way to "
            + registeredLayer.getClass().getName() + ", which takes its place in the row.");
    }

    // Where a layer under this ID already stands in the row, or -1 for an ID nothing has registered.
    // Compared by ID rather than by identity, since the case this answers is two layers arriving with
    // one ID and no notion of each other's objects.
    private int findIndexOfLayerId(String layerId) {

        for (var index = 0; index < orderedLayers.size(); index++) {

            if (orderedLayers.get(index).getId().equals(layerId)) {
                return index;
            }
        }

        return -1;
    }
}
