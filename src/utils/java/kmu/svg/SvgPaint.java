package kmu.svg;

/**
 * How one shape is filled and outlined.
 *
 * <p>The four values always travel together - a fill without its opacity, or a stroke without
 * its width, is not a decision anything can act on - and passing them loose meant every
 * drawing call carried four positional arguments of which two were colours. Two adjacent
 * arguments of one type is how a fill and an outline come to be swapped with nothing to catch
 * it, since either order compiles and the drawing merely comes out wrong.
 *
 * <p>Colours are held in SVG's own notation rather than as {@link java.awt.Color}, because SVG
 * accepts forms AWT cannot hold - {@code hsl(...)} among them - and converting through a
 * colour model would silently shift the shade. {@link SvgDrawing#formatColour} is there for
 * callers that do start from an AWT colour.
 *
 * @param fill        what to fill with, or null to leave the inside empty
 * @param fillOpacity how solid the fill is, from 0 to 1; anything at or above 1 is left
 *                    unstated, since fully opaque is what SVG already assumes
 * @param stroke      what to outline with, or null for no outline at all
 * @param strokeWidth how heavy that outline is, in the drawing's own units
 */
public record SvgPaint(String fill, double fillOpacity, String stroke, double strokeWidth) {

    // What SVG assumes when nothing says otherwise, and so what this leaves unwritten.
    static final double FULLY_OPAQUE = 1.0;

    /**
     * An outline round an empty inside.
     *
     * @param stroke      what to outline with
     * @param strokeWidth how heavy that outline is
     * @return the paint
     */
    public static SvgPaint outlineOnly(String stroke, double strokeWidth) {
        return new SvgPaint(null, FULLY_OPAQUE, stroke, strokeWidth);
    }

    /**
     * A body with no outline at all.
     *
     * @param fill what to fill with
     * @return the paint
     */
    public static SvgPaint fillOnly(String fill) {
        return new SvgPaint(fill, FULLY_OPAQUE, null, 0);
    }

    /**
     * A body under an outline, the body see-through enough to read what is beneath it.
     *
     * <p>The opacity is asked for rather than assumed. What a fill is drawn OVER is the
     * caller's business - a shape stacked on the raw partition has to let it through, and one
     * drawn against the backdrop has nothing to show - so a single figure baked in here would
     * be one caller's choice imposed on every other.
     *
     * @param fill        what to fill with
     * @param fillOpacity how solid that fill is, from 0 to 1
     * @param stroke      what to outline with
     * @param strokeWidth how heavy that outline is
     * @return the paint
     */
    public static SvgPaint filledOutline(
            String fill,
            double fillOpacity,
            String stroke,
            double strokeWidth) {

        return new SvgPaint(fill, fillOpacity, stroke, strokeWidth);
    }
}
