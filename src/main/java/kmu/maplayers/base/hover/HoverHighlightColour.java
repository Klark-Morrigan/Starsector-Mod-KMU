package kmu.maplayers.base.hover;

import kmu.maplayers.base.theme.HoverHighlightStyle;

import java.awt.Color;

/**
 * The shade one hover burns in: the colour of the cell under the cursor, which only the layer
 * that owns that cell can name.
 *
 * <p>Its own resolve beside {@link HoverHighlightGeometry} rather than a step inside the paint
 * pass: what a highlight lights up and what it lights up in are two answers, and the pass burns
 * the colour it is handed rather than deciding one. Keeping them apart is what leaves this free
 * to be the cursor's own rule - the shade of the cell the pointer is on - without that rule
 * being the pass's.
 */
public final class HoverHighlightColour {

    // Resolves only; never instantiated.
    private HoverHighlightColour() {
    }

    /**
     * The colour the cursor's highlight paints in.
     *
     * @param source the active layer's answers about the frame it painted, which is what names
     *               the shade the hovered cell's fill draws in
     * @param hover  what the cursor is over this frame
     * @param style  the theme's highlight tier, which picks which of the owner's palette shades
     *               the highlight points at
     * @return that shade, or null when the cursor is over nothing (a parked hover, including
     *         when the highlight is disabled) or the selection paints nothing, so the caller
     *         skips the pass
     */
    public static Color resolveColourFor(
            HoverHighlightSource source,
            MapHover hover,
            HoverHighlightStyle style) {

        if (!hover.isHovering()) {
            return null;
        }
        return source.resolveHighlightColourOf(
            hover.hoveredSystemId(),
            style.colour());
    }
}
