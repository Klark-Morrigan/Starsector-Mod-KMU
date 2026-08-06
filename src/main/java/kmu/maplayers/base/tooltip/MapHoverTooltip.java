package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

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
     * Draws this layer's hover box for the hovered {@code system}. Called from the above-tooltips UI
     * pass with a current GL context, only after the shared gates pass, so it renders its box straight
     * away or returns without drawing when its own precondition does not hold.
     *
     * @param sector the live sector, whose economy the content may read
     * @param system the star system under the cursor, already resolved by the dispatcher
     */
    void renderFor(SectorAPI sector, StarSystemAPI system);

    /**
     * This box's richer counterpart - the one drawn in its place while the shared detail mode reads
     * {@link HoverTooltipDetailMode#EXPANDED} - or empty when this tooltip states one amount of
     * detail only.
     *
     * <p>The counterpart is a second {@code MapHoverTooltip} rather than a mode argument on
     * {@link #renderFor}, so a tooltip opts into the richer mode by supplying a second body, exactly
     * as a layer opts into having a tooltip at all by injecting one - and an implementation with
     * nothing richer to say carries no branch for a mode it does not support. Empty being the
     * default is what makes the fallback graceful: the dispatcher draws the normal box, so the
     * toggle simply shows nothing new over a tooltip that defines no counterpart.
     *
     * @return the box to draw instead of this one while the expanded mode holds, or empty when this
     *         tooltip defines no richer counterpart
     */
    default Optional<MapHoverTooltip> resolveExpandedVariant() {
        return Optional.empty();
    }

    /**
     * Whether switching detail modes over {@code system} would show the player anything this box does
     * not - the question the pass claiming the toggle key asks before acting on a press.
     *
     * <p>Asked of the box rather than inferred from {@link #resolveExpandedVariant} because a
     * counterpart existing is not the same as it having something to say: a box may define a richer
     * variant that, for this particular system, states exactly what this one already does. Pressing the
     * key there would flip a mode the player sees no result from, and - since the mode is shared and
     * holds across hovers - would leave the next system that <em>does</em> differ opening in a state they
     * did not choose.
     *
     * <p>Asked per system for the same reason the counterpart's own subject is
     * ({@code SystemCellTooltip.resolveExpandedDetailName}): whether there is anything more to show is a
     * fact about what the cursor is over, not about the box.
     *
     * @param sector the live sector, whose economy the answer may read
     * @param system the star system under the cursor
     * @return true when the toggle would change what the player sees for this system
     */
    default boolean isOfferingExpansionFor(SectorAPI sector, StarSystemAPI system) {
        return false;
    }
}
