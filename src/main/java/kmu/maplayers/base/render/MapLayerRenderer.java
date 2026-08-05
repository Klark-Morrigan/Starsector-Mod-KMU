package kmu.maplayers.base.render;

import kmu.maplayers.base.tooltip.MapHoverTooltip;

import java.util.Optional;

/**
 * Everything one layer draws over the sector map: the overlay itself, in the map's own render pass,
 * and the hover box shown for the cell under the cursor, in the later UI pass. Both passes ask the
 * active layer for its renderer and take what it supplies, so what is drawn is decided by whichever
 * layer the player has selected rather than by either pass naming a feature.
 *
 * <p>It is a separate type from {@code MapLayer} rather than a method on it because the two are
 * different roles: a layer is a descriptor (id, tab label, body controls, hotkey) that the tab strip
 * composes, while a renderer emits geometry for a frame. Keeping them apart is also what lets a
 * switch-only tab exist at all - it simply has no renderer, and both passes treat that as nothing to
 * draw.
 *
 * <p>Implementations are reached through a registered layer, so they live for the session and never
 * enter a save. They may therefore hold derived caches and render state directly, with none of the
 * transient marking and lazy rebuilding a save-serialised holder would need.
 */
public interface MapLayerRenderer {

    /**
     * Brings whatever this layer draws from up to date for the frame about to be painted, and
     * resolves anything the frame has to read from the live game rather than emit - the cursor, most
     * of all. Called once per frame, before the first band is drawn.
     *
     * <p>It is a separate call because a frame can be painted in more than one pass: the map's own
     * nebula icons sit between two of a layer's bands, so the parts either side of them are
     * emitted by different surfaces. Preparation that ran per pass would resolve the cursor against
     * a half-drawn frame and repeat every staleness check, so the contract says once rather than
     * leaving each renderer to guard itself.
     *
     * <p>Defaulting to nothing makes "no preparation" the base case: a layer that emits fixed
     * geometry has nothing to bring up to date.
     *
     * @param factor the per-vertex scale the map's render pass applies, folding in its zoom - the
     *               same value the bands are drawn with, since a cursor read has to resolve against
     *               the geometry the frame will actually paint
     */
    default void prepareFrame(float factor) {
    }

    /**
     * Emits one band of this layer's overlay for the frame {@link #prepareFrame} has just made
     * ready. Called from inside the map's render pass, so an implementation may read the GL state
     * that pass binds. Mirrors the engine's own {@code renderOnMap} signature, since that pass is
     * what ultimately drives it.
     *
     * <p>A band that a layer has nothing to put in is drawn as nothing, not refused: which sub-layers
     * ride above the nebulae is the surface's question to ask and the layer's to answer with an
     * empty pass.
     *
     * @param factor    the per-vertex scale the map's render pass applies, folding in its zoom
     * @param alphaMult the pass's alpha multiplier, which fades the whole overlay with the map
     * @param band      which side of the map's nebula icons this pass is painting
     */
    void renderOnMap(float factor, float alphaMult, MapOverlayBand band);

    /**
     * The hover box this layer shows for the star system under the cursor, or empty when it shows
     * none. Read once per frame by the hover dispatcher, which owns the gates every hover box shares
     * and draws whatever is supplied here, so a layer opts into a tooltip by injecting one rather
     * than by a branch in the dispatcher.
     *
     * <p>Defaulting to empty makes "no hover box" the base case: a layer that paints the map need not
     * also have something to say about one cell of it.
     *
     * @return this layer's hover box, or empty for a layer that shows none
     */
    default Optional<MapHoverTooltip> resolveHoverTooltip() {
        return Optional.empty();
    }
}
