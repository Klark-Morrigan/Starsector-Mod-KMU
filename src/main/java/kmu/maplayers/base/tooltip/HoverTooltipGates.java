package kmu.maplayers.base.tooltip;

import kmu.maplayers.base.hover.MapHoverGates;

import java.util.function.BooleanSupplier;

/**
 * The conditions under which a hover box could be drawn at all, before anything about what is under
 * the cursor: the settings switches that answer for every layer, and a map on screen to draw over.
 *
 * <p>Two passes read this and must never disagree - the render pass that draws the box, and the
 * input pass that claims the key selecting which box is drawn. Answering it in one place is what
 * keeps that key honest: it is claimed when and only when a box could be showing, so a condition
 * added here reaches both passes rather than whichever one its author had in mind.
 *
 * <p>Only what the two passes share belongs here. What the render pass waits on beyond it - a
 * hovered cell, the vanilla map drawing its own tooltip, a layer having injected a box at all - is
 * that pass's alone, because the mode the key sets persists across all three: it is a standing
 * choice about the next box to draw, not an answer about the cell the player happens to be over.
 */
public final class HoverTooltipGates {

    private HoverTooltipGates() {
    }

    /**
     * @param isAnyMapShowing whether a map is on screen at all - either host, either look. Supplied
     *                        rather than read here because the live read walks the running game's
     *                        widget tree on the intel side, which no test can stand up
     * @return whether a hover box could be drawn this frame
     */
    public static boolean canAnyBoxDraw(BooleanSupplier isAnyMapShowing) {
        // The settings tier first, so a player who has switched hover boxes off costs nothing on the
        // map read behind it.
        return MapHoverGates.isHoverTooltipEnabled() && isAnyMapShowing.getAsBoolean();
    }
}
