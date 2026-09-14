package kmu.maplayers.base.geometry.render;

import java.awt.Color;

/**
 * How one filled shape is painted: a translucent body under an opaque outline.
 *
 * <p>The three never travel apart. Every fill on this map is a body at some opacity with an
 * edge over it, and the three are chosen together - a colour picked for a layer, an opacity
 * chosen for the map as a whole, an edge that is usually the body's own colour. Passed loose
 * they are three arguments threaded through five painting methods, two of them adjacent
 * colours that can be handed over the wrong way round and still compile.
 *
 * <p>Held by the drawing rather than by a palette, because opacity is not part of what a thing
 * is coloured: a colour is chosen opaque and drawn at whatever weight the layer wants it, and
 * baking the opacity in would mean a second entry per colour for every weight it is ever
 * wanted at.
 *
 * @param fill  what the body is painted in
 * @param alpha how solid the body is, from 0 to 255
 * @param edge  what the outline is painted in, at full opacity
 */
public record FillLook(
    Color fill,
    int alpha,
    Color edge) {

    /**
     * The same look with the body and the outline in one colour, which is what a fill whose
     * edge is only there to separate it from its neighbours wants.
     *
     * @param colour what to paint both in
     * @param alpha  how solid the body is
     * @return the look
     */
    public static FillLook paintedIn(Color colour, int alpha) {

        return new FillLook(colour, alpha, colour);
    }
}
