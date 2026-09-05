package kmu.maplayers.base.layer;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays a {@link MapLayerArrangement} over a roster and answers the row that comes out of it.
 *
 * <p>The rule sits apart from the value it reads because this is where the judgement is: what to do
 * with an id nothing registers, with a registered layer the player has never seen, with a stored
 * list that names one twice, and with an arrangement that would leave no tab at all. Folded into
 * the record, none of that would be reachable without building one.
 *
 * <p>Reconciled on every read rather than settled once, for the reason the roster itself is never
 * settled: a mod that depends on this one registers its layer after this one's load has returned,
 * so there is no moment at which the row is known to be whole. Every answer here is about the
 * roster as it stands when it is asked.
 */
public final class ArrangedLayers {

    private ArrangedLayers() {
    }

    /**
     * The row the player sees: the roster in the order they put it in, without the tabs they took
     * off.
     *
     * <p>Never empty over a non-empty roster. A bar with no tabs has no way back to itself - the
     * dialog that would put one back is reached from the bar - so an arrangement that hides
     * everything leaves the leading layer standing. Unreachable from the dialog, which refuses the
     * last visible tab; reachable from a hand-edited store, which is why the guard is here rather
     * than only there.
     *
     * @param arrangement  the player's own arrangement
     * @param rosterLayers every registered layer, in registration order
     * @return the layers to tab, left to right
     */
    public static List<MapLayer> arrangeVisibleLayers(
            MapLayerArrangement arrangement,
            List<MapLayer> rosterLayers) {

        var orderedLayers = orderLayers(arrangement, rosterLayers);

        var visibleLayers = orderedLayers.stream()
            .filter(layer -> !arrangement.isLayerHidden(layer.getId()))
            .toList();

        return visibleLayers.isEmpty() && !orderedLayers.isEmpty()
            ? List.of(orderedLayers.get(0))
            : visibleLayers;
    }

    // The whole roster in the player's order: the ids they placed, in that order, then everything
    // they never placed, in registration order.
    //
    // Appending the unplaced rather than dropping them is what lets a mod be installed after the
    // arrangement was stored: its layer lands where registration order would have put it, to the
    // right of the layers it was built on, and the player moves it from there if they want to.
    //
    // An id named twice by a hand-edited store places its layer once. The alternative is a row
    // carrying one layer under two tabs, which both write and read the same stored pick.
    private static List<MapLayer> orderLayers(
            MapLayerArrangement arrangement,
            List<MapLayer> rosterLayers) {

        var orderedLayers = new ArrayList<MapLayer>(rosterLayers.size());

        for (var layerId : arrangement.orderedLayerIds()) {

            var placedLayer = findLayerById(rosterLayers, layerId);

            // Null is an id from a mod no longer installed, or one it renamed. Skipped rather than
            // answered for: the store is a preference over what is registered, so what is not
            // registered is simply not in the row.
            if (placedLayer != null && !orderedLayers.contains(placedLayer)) {
                orderedLayers.add(placedLayer);
            }
        }
        for (var rosterLayer : rosterLayers) {
            if (!orderedLayers.contains(rosterLayer)) {
                orderedLayers.add(rosterLayer);
            }
        }
        return List.copyOf(orderedLayers);
    }

    // The registered layer under this id, or null for an id nothing registers. By id rather than by
    // identity, the stored side being a string a past session wrote.
    private static MapLayer findLayerById(List<MapLayer> rosterLayers, String layerId) {

        for (var rosterLayer : rosterLayers) {
            if (rosterLayer.getId().equals(layerId)) {
                return rosterLayer;
            }
        }
        return null;
    }
}
