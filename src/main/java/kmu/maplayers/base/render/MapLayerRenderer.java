package kmu.maplayers.base.render;

/**
 * Draws one layer's overlay over the sector map. The terrain surface that owns the map's render pass
 * asks the active layer for its renderer and hands it the frame, so what is drawn is decided by
 * whichever layer the player has selected rather than by the surface naming a feature.
 *
 * <p>It is a separate type from {@code MapLayer} rather than a method on it because the two are
 * different roles: a layer is a descriptor (id, tab label, body controls, hotkey) that the tab strip
 * composes, while a renderer emits geometry for a frame. Keeping them apart is also what lets a
 * switch-only tab exist at all - it simply has no renderer, and the surface treats that as nothing to
 * draw.
 *
 * <p>Implementations are reached through a registered layer, so they live for the session and never
 * enter a save. They may therefore hold derived caches and render state directly, with none of the
 * transient marking and lazy rebuilding a save-serialised holder would need.
 */
public interface MapLayerRenderer {

    /**
     * Emits this layer's overlay for one map frame. Called from inside the map's render pass, so an
     * implementation may read the GL state that pass binds. Mirrors the engine's own
     * {@code renderOnMap} signature, since that pass is what ultimately drives it.
     *
     * @param factor    the per-vertex scale the map's render pass applies, folding in its zoom
     * @param alphaMult the pass's alpha multiplier, which fades the whole overlay with the map
     */
    void renderOnMap(float factor, float alphaMult);
}
