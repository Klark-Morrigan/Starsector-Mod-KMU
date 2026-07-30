package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * A map layer's hover tooltip: the box a layer draws for the star system under the cursor. A layer
 * injects one - or none - through {@code MapLayerRenderer.resolveHoverTooltip}, and the shared hover
 * dispatcher ({@link MapLayerCellTooltip}) draws whichever the active layer supplies. So whether a
 * layer shows a tooltip is decided by what it injects, not a per-layer flag: a new layer adds,
 * replaces, or omits its tooltip by what it returns rather than by a branch in the dispatcher.
 *
 * <p>The dispatcher owns the gates shared across any tooltip - the map is open, a cell is hovered, the
 * cursor is off the vanilla star icon - and resolves the hovered system before handing it here, so an
 * implementation owns only its own content, look, and any content-specific precondition (a tooltip
 * reading the economy guards on a live one itself).
 */
public interface MapHoverTooltip {

    /**
     * Draws this layer's hover box for the hovered {@code system}. Called from the above-tooltips UI
     * pass with a current GL context, only after the shared gates pass, so it renders its box straight
     * away or returns without drawing when its own precondition does not hold.
     *
     * @param sector the live sector, whose economy the content may read
     * @param system the star system under the cursor, already resolved by the dispatcher
     */
    void renderFor(SectorAPI sector, StarSystemAPI system);
}
