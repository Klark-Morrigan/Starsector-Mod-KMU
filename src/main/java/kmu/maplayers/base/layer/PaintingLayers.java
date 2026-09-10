package kmu.maplayers.base.layer;

import java.util.List;

/**
 * Which layers put something on the map, and how many of a given row do.
 *
 * <p>One reading rather than a compare spelled at each caller, because the answer is a judgement about
 * {@link NoLayer} rather than something a layer states: the empty view's whole job is to draw nothing, so
 * it is the one tab any question about painting has to subtract. Two callers spelling that for themselves
 * would be two places to correct if the empty view ever stops being the only such tab.
 *
 * <p>Asked of a list passed in rather than of the registry, so the same reading answers a roster and one
 * screen's row without either caller having to say which it holds.
 */
public final class PaintingLayers {

    private PaintingLayers() {
    }

    /**
     * @param layers the roster or row being counted
     * @return how many of them draw anything
     */
    public static int countPaintingLayers(List<MapLayer> layers) {

        return (int) layers.stream()
            .filter(PaintingLayers::isLayerPainting)
            .count();
    }

    /**
     * @param layer the layer being asked about
     * @return whether it draws anything at all
     */
    public static boolean isLayerPainting(MapLayer layer) {
        // Layers are singletons, so identity settles it without an id compare.
        return layer != NoLayer.INSTANCE;
    }
}
