package kmu.maplayers.base.render;

/**
 * Emits one band of a painting layer's overlay, in the map's own render pass, from whatever the
 * layer's {@link MapFrameCache} last brought up to date. Which sub-layers ride in which band, and
 * in what order they stack, is the compositor's to settle - the frame sequence only says which band
 * the running pass paints.
 */
@FunctionalInterface
public interface MapFrameCompositor {

    /**
     * Paints this pass's band. A band the layer has nothing in is painted as nothing, not refused.
     *
     * @param mapFrame the pass's scale and fade, taken whole
     * @param band     which side of the map's nebula icons this pass is painting
     */
    void renderBand(MapFrame mapFrame, MapOverlayBand band);
}
