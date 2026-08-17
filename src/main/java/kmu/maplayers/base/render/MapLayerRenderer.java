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
     * answers anything the frame owes once rather than per pass. Called once per frame, before the
     * first band is drawn.
     *
     * <p>It is a separate call because a frame can be painted in more than one pass: the map's own
     * nebula icons sit between two of a layer's bands, so the parts either side of them are
     * emitted by different surfaces. Work that ran per pass would repeat every staleness check, and
     * anything latched - the moment the cursor reaches a cell, above all - would be stepped once per
     * pass and report crossings the pointer never made. So the contract says once rather than
     * leaving each renderer to guard itself.
     *
     * <p>Defaulting to nothing makes "no preparation" the base case: a layer that emits fixed
     * geometry has nothing to bring up to date.
     *
     * @param factor the per-vertex scale the map's render pass applies, folding in its zoom - the
     *               same value the bands are drawn with
     */
    default void prepareFrame(float factor) {
    }

    /**
     * Resolves what the cursor is over and publishes it for the frame. Called on every pass the
     * surface admits, before that pass's bands are drawn, and the frame's last pass owns the answer.
     *
     * <p>Per pass rather than beside the preparation above, because the read is the one piece of
     * per-frame work that depends on <em>which</em> pass is running: it inverts the transform that
     * pass bound and divides by the {@code factor} it supplied. The hook this all hangs off names no
     * caller, and a mod compositing a sector map of its own drives it too - from the campaign HUD,
     * so before the map screen. A read pinned to the frame's first pass is therefore taken through
     * whichever transform happened to draw earliest, and the map the player is pointing at then
     * draws a hover resolved through somebody else's zoom and pan. Last write wins is what puts the
     * answer on the pass that drew last.
     *
     * <p>Defaulting to nothing makes "no hover" the base case: a layer that paints need not answer
     * the pointer at all.
     *
     * @param factor the per-vertex scale this pass applies, folding in its zoom - the value the
     *               cursor read has to undo, since a hover has to resolve against the geometry this
     *               pass will actually paint
     */
    default void publishHoverForPass(float factor) {
    }

    /**
     * Emits one band of this layer's overlay, for a frame {@link #prepareFrame} has brought up to
     * date and {@link #publishHoverForPass} has resolved the cursor against. Called from inside the
     * map's render pass, so an implementation may read the GL state that pass binds. Mirrors the
     * engine's own {@code renderOnMap} signature, since that pass is what ultimately drives it.
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
