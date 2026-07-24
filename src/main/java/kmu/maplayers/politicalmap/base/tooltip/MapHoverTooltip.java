package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

/**
 * A political-map layer's hover tooltip: the box a view draws for the star system under the cursor. A
 * view injects one - or none - through {@code PoliticalMapView.resolveHoverTooltip}, and the shared
 * hover dispatcher ({@link MapLayerCellTooltip}) draws whichever the active view supplies. So whether
 * a layer shows a tooltip is decided by what it injects, not a per-layer flag: a new layer adds,
 * replaces, or omits its tooltip by what it returns rather than by a branch in the dispatcher.
 *
 * <p>The dispatcher owns the gates shared across any tooltip - the map is open, a cell is hovered, the
 * cursor is off the vanilla star icon - and resolves the hovered system before handing it here, so an
 * implementation owns only its own content, look, and any content-specific precondition (the
 * domination tooltip guards on a live economy itself).
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
