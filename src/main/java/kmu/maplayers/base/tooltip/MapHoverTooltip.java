package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;

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
     * Draws this layer's hover box for the hovered {@code system}, read to {@code detailLevel}. Called
     * from the above-tooltips UI pass with a current GL context, only after the shared gates pass, so
     * it renders its box straight away or returns without drawing when its own precondition does not
     * hold.
     *
     * <p>The level is handed in rather than read here, so every box drawn on one frame states the same
     * depth: it is one shared choice about how much the player wants told, not a fact about any one
     * layer's subject matter.
     *
     * @param sector      the live sector, whose economy the content may read
     * @param system      the star system under the cursor, already resolved by the dispatcher
     * @param detailLevel how deep the player has asked the box to read
     */
    void renderFor(SectorAPI sector, StarSystemAPI system, HoverTooltipDetailLevel detailLevel);

    /**
     * Whether the press that moves on from {@code detailLevel} would change what the player sees over
     * {@code system} - the question the pass claiming the cycle key asks before acting on a press.
     *
     * <p>Asked of the box rather than read off the level alone, because a level admitting a deeper
     * tier is not the same as this box having anything at that tier: a box may compose a tree that,
     * for this particular system, runs no deeper than the shallowest level already shows. Pressing
     * the key there would advance a level the player sees no result from, and - since the level is
     * shared and holds across hovers - would leave the next system that <em>does</em> differ opening
     * at a depth they did not choose.
     *
     * <p>The level is handed in rather than left out because the answer turns on where the press
     * lands as much as on what the box holds: the cycle wraps, so from the deepest level the press
     * collapses the box, which acts whatever the system holds.
     *
     * <p>Asked per system rather than once per box because that is where the rest of the answer
     * lives: whether there is anything more to show turns on what the cursor is over - a system a box
     * lists nothing for has nothing to open up - and not on which box is drawing.
     *
     * <p>A box taking no part in the detail cycle answers no at every level, the default here: it
     * draws no hint, so a press it swallowed would be one the player was never told about.
     *
     * @param sector      the live sector, whose economy the answer may read
     * @param system      the star system under the cursor
     * @param detailLevel how deep the box is being read now, which the press moves on from
     * @return true when the key would change what the player sees for this system
     */
    default boolean isOfferingExpansionFor(
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel) {

        return false;
    }
}
