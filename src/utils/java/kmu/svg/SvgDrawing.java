package kmu.svg;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Limits;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

/**
 * An SVG document being built up shape by shape, in world coordinates.
 *
 * <p>Deliberately narrow: a backdrop, polygons, polylines, circles, and rings gathered into one
 * even-odd path. That is the whole of it, and the narrowness is what makes {@link SvgRasteriser}
 * possible - the two are written against each other, so a reader can check in a minute that
 * what is drawn here is what comes back as a picture. A general SVG writer would be a surface
 * nobody could hold in their head, paired with a renderer nobody could verify.
 *
 * <p>Knows nothing of what it is drawing. It takes rings of points and colours in SVG's own
 * notation, so the same emitter serves any caller with a shape and an opinion about how it
 * should look; deciding WHICH colour means what is the caller's, and a decision made here
 * would be one caller's taste imposed on the rest.
 *
 * <p>Y is flipped once, at the top, rather than at every vertex. World y grows upward and
 * SVG y grows downward, and a drawing that flipped per-point would come out upside down the
 * first time a caller forgot - which reads as a geometry fault that is not there.
 */
public final class SvgDrawing {

    private static final int HEX_DIGITS = 6;

    // Drops the alpha byte an AWT colour packs above its three channels, which SVG has no
    // notation for in a hex literal.
    private static final int RGB_MASK = 0xffffff;

    // One decimal place. These are world units thousands wide, so a tenth is already below
    // anything a drawing can show, and full precision would multiply the file size for
    // digits no eye and no diff can use.
    private static final String COORDINATE_FORMAT = "%.1f";

    private final StringBuilder svg = new StringBuilder();

    /**
     * Opens a drawing over the given extent.
     *
     * @param bounds    the world box the drawing covers; shapes outside it are simply clipped
     * @param viewWidth how many pixels wide the drawing declares itself, the height following
     *                  from the box's own proportions so nothing is stretched
     * @param backdrop  what to lay behind everything, in SVG colour notation
     */
    public SvgDrawing(Bounds bounds, int viewWidth, String backdrop) {

        var width = bounds.maxX() - bounds.minX();
        var height = bounds.maxY() - bounds.minY();
        var viewHeight = (int) Math.round(viewWidth * height / width);

        svg
            .append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(viewWidth)
            .append("\" height=\"")
            .append(viewHeight)
            .append("\" viewBox=\"0 0 ")
            .append(formatCoordinate(width))
            .append(' ')
            .append(formatCoordinate(height))
            .append("\">\n<rect width=\"100%\" height=\"100%\" fill=\"")
            .append(backdrop)
            .append("\"/>\n<g transform=\"translate(")
            .append(formatCoordinate(-bounds.minX()))
            .append(' ')
            .append(formatCoordinate(bounds.maxY()))
            .append(") scale(1 -1)\">\n");
    }

    /**
     * An AWT colour in the notation SVG reads.
     *
     * <p>Offered because a caller's colours usually already exist as AWT ones, chosen for
     * some other drawing of the same thing. Converting at the boundary is what stops a second
     * drawing from keeping its own copy of every value and drifting out of step with the first.
     *
     * @param colour the colour; its alpha is dropped, opacity being a paint's business
     * @return it as {@code #rrggbb}
     */
    public static String formatColour(Color colour) {

        var packed = Integer.toHexString(colour.getRGB() & RGB_MASK);

        return "#" + "0".repeat(HEX_DIGITS - packed.length()) + packed;
    }

    /**
     * Draws a closed ring.
     *
     * <p>A ring of fewer than three points encloses nothing, and is skipped rather than
     * emitted: SVG would draw it as a hairline or as nothing at all, which is a mark that
     * looks like geometry but is only a degenerate shape showing through.
     *
     * @param ring  the points, in order; the closing edge back to the first is implied
     * @param paint how to fill and outline it
     */
    public void drawPolygon(List<double[]> ring, SvgPaint paint) {

        if (ring.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return;
        }

        svg.append("<polygon points=\"");
        appendPoints(ring);
        svg.append('"');

        appendPaint(paint);
    }

    /**
     * Draws an open run of points.
     *
     * <p>Its ends are left where they are. A run with two real ends closed back up would draw
     * an edge straight across whatever it spans - a line that exists only as an artefact of
     * the closing, and which reads on the map as a real one.
     *
     * @param points the points, in order
     * @param paint  how to outline it
     */
    public void drawPolyline(List<double[]> points, SvgPaint paint) {

        svg.append("<polyline points=\"");
        appendPoints(points);
        svg.append('"');

        appendPaint(paint);
    }

    /**
     * Draws a circle.
     *
     * @param centre where it is centred
     * @param radius how far it reaches
     * @param paint  how to fill and outline it
     */
    public void drawCircle(double[] centre, double radius, SvgPaint paint) {

        svg.append("<circle cx=\"")
            .append(formatCoordinate(centre[0]))
            .append("\" cy=\"")
            .append(formatCoordinate(centre[1]))
            .append("\" r=\"")
            .append(formatCoordinate(radius))
            .append('"');

        appendPaint(paint);
    }

    /**
     * Draws many rings as ONE shape, filled under the even-odd rule.
     *
     * <p>Which is what makes a hole a hole. Under that rule a ring lying inside another
     * subtracts from it instead of adding, so an enclave, or a clearing punched out of a
     * territory, comes out as the gap it is. The same rings drawn one at a time would each be
     * filled in their own right, painting those gaps solid - the exact opposite of what they
     * were built to mean.
     *
     * @param rings the rings; any too short to enclose area are dropped, and if that leaves
     *              nothing then no shape is emitted at all
     * @param paint how to fill and outline the whole
     */
    public void drawRingsAsOneShape(List<List<double[]>> rings, SvgPaint paint) {

        var subPaths = new StringBuilder();

        for (var ring : rings) {

            if (ring.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                continue;
            }

            for (var vertex = 0; vertex < ring.size(); vertex++) {

                subPaths
                    .append(vertex == 0 ? 'M' : 'L')
                    .append(formatCoordinate(ring.get(vertex)[0]))
                    .append(' ')
                    .append(formatCoordinate(ring.get(vertex)[1]))
                    .append(' ');
            }
            subPaths.append("Z ");
        }

        if (subPaths.length() == 0) {
            return;
        }

        svg.append("<path fill-rule=\"evenodd\" d=\"")
            .append(subPaths)
            .append('"');

        appendPaint(paint);
    }

    /**
     * Closes the drawing.
     *
     * @return the whole document, ready to write
     */
    public String finishDrawing() {
        return svg + "</g>\n</svg>\n";
    }

    // Every shape ends the same way, so the fill and the outline are written in one place.
    // Spelled out per element instead, the attribute ORDER would be free to differ between
    // them, and two SVGs of the same map would stop diffing cleanly against each other.
    private void appendPaint(SvgPaint paint) {

        // Written even when there is none: SVG fills black by default, so a shape meant to be
        // hollow that says nothing comes out solid.
        svg.append(" fill=\"")
            .append(paint.fill() == null ? "none" : paint.fill())
            .append('"');

        if (paint.fillOpacity() < SvgPaint.FULLY_OPAQUE) {

            svg.append(" fill-opacity=\"")
                .append(paint.fillOpacity())
                .append('"');
        }

        if (paint.stroke() != null) {

            svg.append(" stroke=\"")
                .append(paint.stroke())
                .append("\" stroke-width=\"")
                .append(formatCoordinate(paint.strokeWidth()))
                .append('"');
        }
        svg.append("/>\n");
    }

    private void appendPoints(List<double[]> points) {

        for (var point : points) {

            svg.append(formatCoordinate(point[0]))
                .append(',')
                .append(formatCoordinate(point[1]))
                .append(' ');
        }
    }

    // Fixed to the root locale, not the machine's. A locale that writes decimals with a comma
    // would produce coordinates SVG reads as two numbers, and the drawing would come apart on
    // one developer's machine and nowhere else.
    private static String formatCoordinate(double value) {
        return String.format(Locale.ROOT, COORDINATE_FORMAT, value);
    }
}
