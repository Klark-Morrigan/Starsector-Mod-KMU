package kmu.maplayers.politicalmap.base.render.hover;

import kmu.maplayers.base.hover.HoverHighlight;

import java.awt.Color;

/**
 * What one frame's preview lights up: the shapes it lit, and the one shade they all burn in.
 *
 * <p>The two travel together because neither is a decision on its own - shapes with no shade paint
 * nothing, and a shade with nothing lit paints nothing either - so a caller reading one without
 * the other could conclude that something is drawn when nothing is.
 *
 * @param highlight the loops to bloom off and the extents to wash
 * @param colour    the shade both burn in, or null when the previewed bloc resolved none
 */
public record PreviewHighlightPaint(HoverHighlight highlight, Color colour) {

    /**
     * Nothing is previewed, or what is previewed lights nothing up - the answer for every frame
     * the pointer is on no picker row, which is most of them.
     */
    public static final PreviewHighlightPaint NONE =
        new PreviewHighlightPaint(HoverHighlight.NONE, null);

    /**
     * @return whether this puts anything on the map at all, which is what a caller gates its pass
     *         on
     */
    public boolean isPainting() {
        return colour != null && !highlight.isEmpty();
    }
}
