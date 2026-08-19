package kmu.maplayers.base.tooltip;

import kmu.maplayers.base.hover.MapHoverGates;
import kmu.maplayers.base.hover.MapHoverPermission;

/**
 * The conditions under which a hover box could be drawn at all, before anything about what is under
 * the cursor: the settings switches that answer for every layer, and a frame the cursor can be
 * located against.
 *
 * <p>Two passes read this and must never disagree - the render pass that draws the box, and the
 * input pass that claims the key selecting which box is drawn. Answering it in one place is what
 * keeps that key honest: it is claimed when and only when a box could be showing, so a condition
 * added here reaches both passes rather than whichever one its author had in mind.
 *
 * <p>The frame half is the same answer the hover itself was resolved from, rather than a second
 * reading of the screen - see {@link MapHoverPermission}. A box states what a hover found, so a box
 * drawn on frames the hover is not resolved on would name a cell nobody is pointing at, and a hover
 * resolved on frames no box may draw would light a cell it then refuses to name. Both are the same
 * mistake, made in opposite directions, and one shared answer is what forecloses it: a permission
 * the player grants reaches the box and the cell wash together.
 *
 * <p>That answer arrives as {@link MapHoverPermission} rather than as a bare boolean, so the sharing
 * is what the compiler holds and not what the paragraph above asks for. Handed a plain supplier, a
 * caller could compose the two screen reads a second time and differently, and nothing would say so;
 * handed the type, it composes them where the type does or not at all.
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
     * @param hoverPermission the frames the map layers may answer the cursor on, held rather than
     *                        read here because its two screen reads walk the running game's widget
     *                        tree on the intel side, which no test can stand up
     * @return whether a hover box could be drawn this frame
     */
    public static boolean canAnyBoxDraw(MapHoverPermission hoverPermission) {
        // The settings tier first, so a player who has switched hover boxes off costs nothing on the
        // screen reads behind it.
        return MapHoverGates.isHoverTooltipEnabled() && hoverPermission.isCursorLocatable();
    }
}
