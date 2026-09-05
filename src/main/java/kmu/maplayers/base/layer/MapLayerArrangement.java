package kmu.maplayers.base.layer;

import java.util.List;

/**
 * How the player has arranged their tab bar: the order they put the layers in, and the ones they
 * took off it.
 *
 * <p>Held apart from the active pick because the two are different kinds of thing. Which tab a
 * screen is on is a fact about one campaign and rides in that save; how the bar itself is laid out
 * is a preference about the interface, the same whichever campaign is loaded - so a player who
 * ordered their bar once does not order it again per save.
 *
 * <p><b>Neither list is authoritative.</b> This is a preference laid over whatever is registered
 * rather than a roster of its own: {@link ArrangedLayers} names an id nothing registers and skips
 * it, and appends a registered layer this says nothing about. So a mod installed, removed or
 * renamed costs the player nothing and needs no migration, and an id is kept here rather than
 * pruned when its mod goes - a layer put back finds the place it was given.
 *
 * @param orderedLayerIds the ids the player has placed, left to right; the layers they have never
 *                        arranged are simply absent rather than listed at the end
 * @param hiddenLayerIds  the ids whose tabs the player has taken off the bar. Hiding is not
 *                        switching off: the layer stays registered and stays resolvable, so a save
 *                        holding a hidden layer as its pick still paints it
 */
public record MapLayerArrangement(
    List<String> orderedLayerIds,
    List<String> hiddenLayerIds) {

    /**
     * What a player who has never opened the dialog has, and what an unreadable store answers with:
     * an arrangement that states nothing, over which the roster reads exactly as it registered.
     */
    public static final MapLayerArrangement UNARRANGED =
        new MapLayerArrangement(List.of(), List.of());

    public MapLayerArrangement {
        // Copied rather than held, so the value cannot be moved out from under a reader by whoever
        // built it - the lists come off a parsed file or a dialog's working state, both of which go
        // on being edited after the value is made.
        orderedLayerIds = List.copyOf(orderedLayerIds);
        hiddenLayerIds = List.copyOf(hiddenLayerIds);
    }

    /**
     * @param layerId the layer's registered id
     * @return whether the player has taken this layer's tab off the bar
     */
    public boolean isLayerHidden(String layerId) {
        return hiddenLayerIds.contains(layerId);
    }
}
