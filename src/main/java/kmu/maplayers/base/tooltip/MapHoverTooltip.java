package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;

import java.util.Optional;

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
     * Where the press that moves on from {@code detailLevel} would take this box over {@code system}
     * - the question the pass claiming the cycle key asks before acting on a press, and the same one
     * the hint at the box's foot is worded from.
     *
     * <p>Answered as the destination rather than as a yes or no, because the pass that claims the key
     * has to set the level it lands on and no other party can work it out: the cycle wraps at the
     * deepest level the box itself holds anything at, not at the deepest the level enum declares. A
     * box asked only whether it had more would leave the stepping to a caller that cannot see where
     * its tree ends.
     *
     * <p>Asked of the box rather than read off the level alone, because a level admitting a deeper
     * tier is not the same as this box having anything at that tier: a claim is settled over colonies
     * with no patrol entering it anywhere, so the level naming patrol tiers is one that box can never
     * fill. Offered it regardless, the player presses the key and sees the same box again - and,
     * since the level is shared and holds across hovers, the next system that <em>does</em> differ
     * then opens at a depth they did not choose.
     *
     * <p>Asked per system rather than once per box because part of the answer lives there: what a box
     * holds turns on what the cursor is over, and a system it lists nothing for has nothing to open
     * up at all.
     *
     * <p>A box taking no part in the detail cycle offers nowhere at every level, the default here: it
     * draws no hint, so a press it swallowed would be one the player was never told about.
     *
     * @param sector      the live sector, whose economy the answer may read
     * @param system      the star system under the cursor
     * @param detailLevel how deep the box is being read now, which the press moves on from
     * @return the level one press moves to, or empty where the press would change nothing the player
     *         can see
     */
    default Optional<HoverTooltipDetailLevel> resolveNextLevelFor(
            SectorAPI sector,
            StarSystemAPI system,
            HoverTooltipDetailLevel detailLevel) {

        return Optional.empty();
    }
}
