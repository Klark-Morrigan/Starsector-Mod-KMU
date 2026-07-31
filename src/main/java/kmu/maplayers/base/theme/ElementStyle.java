package kmu.maplayers.base.theme;

/**
 * One painted element's colour and opacity as the player authored them: an {@link ElementPaint}
 * selection (resolved against a cluster's own shades only at draw time, since one style serves
 * many clusters) paired with the opacity it paints at.
 *
 * <p>The unit every drawn element shares - a fill, a border, a seam, a cluster name - so the
 * pair travels as one value rather than as two parallel components each style has to spell out
 * and each reader has to keep in step. Widths stay outside it: only the two borders have one.
 */
public record ElementStyle(
    ElementPaint color,
    double opacity) {

    /** An element the player turned off, so no draw pass paints it. */
    public static final ElementStyle NOT_DRAWN = new ElementStyle(null, 0);

    /**
     * Whether this element puts any ink on the map: it needs both a colour to paint in and an
     * opacity above zero, since either one alone still paints nothing. Lets a builder drop the
     * element's geometry outright rather than tessellate a shape the GL pass would blend away
     * to nothing.
     *
     * <p>A null selection is the "paints nothing" state, which is how a layer expresses its own
     * no-colour option without this tier having to know the option exists.
     *
     * <p>Not every element treats no-colour as off - a cluster name reads its colour choice off
     * the border it inherits from and falls back to a legible shade when that border is hidden -
     * so this is the test for an element that is genuinely optional, not a universal gate every
     * reader must apply.
     */
    public boolean isDrawn() {
        return color != null && opacity > 0;
    }
}
