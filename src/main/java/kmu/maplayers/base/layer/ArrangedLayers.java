package kmu.maplayers.base.layer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays a {@link MapLayerArrangement} over a roster and answers the row that comes out of it.
 *
 * <p>The rule sits apart from the value it reads because this is where the judgement is: what to do
 * with an id nothing registers, with a registered layer the player has never seen, with a stored
 * list that names one twice, and with an arrangement that would leave no tab at all. Folded into
 * the record, none of that would be reachable without building one. It is why a mod installed,
 * removed or renamed costs the player nothing and needs no migration.
 *
 * <p>Asked of a roster passed in rather than of the registry, and so reconciled afresh on every
 * read: {@link MapLayerRegistry} is never settled, and the answer is only ever about the roster as
 * it stood when it was asked.
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

        var orderedLayers = arrangeAllLayers(arrangement, rosterLayers);

        var visibleLayers = orderedLayers.stream()
            .filter(layer -> !arrangement.isLayerHidden(layer.getId()))
            .toList();

        return visibleLayers.isEmpty() && !orderedLayers.isEmpty()
            ? List.of(orderedLayers.get(0))
            : visibleLayers;
    }

    /**
     * The whole roster in the player's order, hidden layers included: the ids they placed, in that
     * order, then everything they never placed, in registration order.
     *
     * <p>Published beside the row the bar draws because arranging the bar is a different question
     * from drawing it. A control that moves and hides tabs has to show the player the tabs they have
     * already hidden - those being the only thing that can put one back - so it reads the order
     * without the hiding, while the bar reads both.
     *
     * <p>Taking each placed layer out of the roster index is what settles all three of the awkward
     * cases at once, rather than each needing a guard of its own: an id nothing registers takes
     * nothing out and contributes nothing, an id named twice finds it already gone the second time,
     * and whatever is left over is exactly the unplaced - in registration order, the index keeping
     * insertion order. Appending those rather than dropping them is what lets a mod be installed
     * after the arrangement was stored: its layer lands where registration order would have put it,
     * to the right of the layers it was built on.
     *
     * @param arrangement  the player's own arrangement
     * @param rosterLayers every registered layer, in registration order
     * @return every registered layer, left to right in the player's order
     */
    public static List<MapLayer> arrangeAllLayers(
            MapLayerArrangement arrangement,
            List<MapLayer> rosterLayers) {

        var unplacedLayers = indexRosterById(rosterLayers);
        var orderedLayers = new ArrayList<MapLayer>(rosterLayers.size());

        for (var layerId : arrangement.orderedLayerIds()) {

            var placedLayer = unplacedLayers.remove(layerId);
            if (placedLayer != null) {
                orderedLayers.add(placedLayer);
            }
        }
        orderedLayers.addAll(unplacedLayers.values());

        return List.copyOf(orderedLayers);
    }

    // The roster keyed by id, in registration order. By id rather than by identity, the placing
    // side being a string a past session wrote.
    //
    // The leftmost of two layers sharing an id wins, which the registry's own arbitration already
    // makes unreachable through it - but this takes any list, so the rule is stated rather than
    // assumed.
    private static Map<String, MapLayer> indexRosterById(List<MapLayer> rosterLayers) {

        var rosterIndex = new LinkedHashMap<String, MapLayer>();

        for (var rosterLayer : rosterLayers) {
            rosterIndex.putIfAbsent(rosterLayer.getId(), rosterLayer);
        }
        return rosterIndex;
    }
}
