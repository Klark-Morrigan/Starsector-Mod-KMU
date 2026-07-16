package kmu.maplayers.politicalmap.base.render.style;

import kmu.settings.FactionPaletteChoice;

/**
 * One painted element's colour and opacity as the player authored them: a palette choice
 * (resolved against a cluster's two shades only at draw time, since one style serves many
 * clusters) paired with the opacity it paints at. A "No color" choice means the element is
 * not drawn at all, which leaves its opacity unread.
 *
 * <p>The unit every drawn element shares - a fill, a border, a seam - so the pair travels as
 * one value rather than as two parallel components each style has to spell out and each
 * reader has to keep in step. Widths stay outside it: only the two borders have one.
 */
public record ElementStyle(FactionPaletteChoice color, double opacity) {

    /** An element the player turned off, so no draw pass paints it. */
    public static final ElementStyle NOT_DRAWN = new ElementStyle(FactionPaletteChoice.NONE, 0);

    /** Whether this element paints at all, or was pointed at "No color". */
    public boolean isDrawn() {
        return color != FactionPaletteChoice.NONE;
    }
}
