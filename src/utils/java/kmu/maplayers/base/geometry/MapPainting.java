package kmu.maplayers.base.geometry;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * How this package puts a shape on screen: the handful of Java2D moves every drawing shares.
 *
 * <p>Gathered because the map is drawn by more than one thing - the cells and clusters by the
 * viewer itself, each rival void construction by its own overlay, and the whole map again by
 * the rasteriser that turns the SVG into a picture - and all of them build a path from a ring,
 * fill it under an opaque outline, and tint a colour. Left with whichever class happened to
 * need them first, every other drawing would have had to reach back into the window for them,
 * which is the wrong way round.
 *
 * <p>Named for the map rather than for the window for the same reason: nothing here knows what
 * the sliders are set to. What a setting comes to mean is {@link ViewerSettings}' own business,
 * and what the map is painted WITH is {@link MapLook}'s.
 */
final class MapPainting {

    private static final int HUE_RANGE = 360;

    // A radius either side of the centre makes the box a circle is drawn in.
    private static final int DIAMETERS = 2;

    // The odd 32-bit approximation of the golden ratio, the standard multiplier for
    // spreading a hash's low bits across the whole word. Only its bit pattern matters, not
    // its value.
    private static final int HASH_MIX_MULTIPLIER = 0x9E3779B9;

    private MapPainting() {
    }

    static Color jitterBrightness(Color base, int seed, float strength) {

        var hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);

        // Hashes cluster in their low bits, so the spread is taken from a well-mixed value
        // rather than from the seed itself - otherwise consecutive ids come out identical.
        var mixed = Math.floorMod(Integer.reverse(seed * HASH_MIX_MULTIPLIER), HUE_RANGE) / (float) HUE_RANGE;
        var brightness = Math.max(0f, Math.min(
            1f,
            hsb[2] + (mixed - 0.5f) * strength));

        return Color.getHSBColor(hsb[0], hsb[1], brightness);
    }

    // The one way anything filled is drawn here: a translucent body under an opaque outline.
    // Shared rather than repeated per layer so an owner's cell, an unowned cell and the
    // partition underneath read as the same kind of thing in different colours - which is the
    // only reason it is possible to tell at a glance which of them a shape belongs to.
    static void paintFilledShape(
            Graphics2D g2,
            Path2D shape,
            Color fill,
            int fillAlpha,
            Color edge) {

        g2.setColor(applyAlpha(fill, fillAlpha));
        g2.fill(shape);
        g2.setColor(applyAlpha(edge, MapLook.OPAQUE_ALPHA));
        g2.draw(shape);
    }

    // The one way a proposed line is drawn here: closed rings stroked at span weight in one
    // opaque colour, filled with nothing. Shared by the settled coast and the continent
    // preview because the two exist to be compared, and two stanzas of stroke-and-colour
    // setup is how two lines meant to differ only in colour come to differ in weight as well.
    static void paintLineRings(Graphics2D g2, List<List<double[]>> rings, Color colour) {

        prepareSpanStroke(g2, colour);

        for (var ring : rings) {
            g2.draw(buildPath(ring));
        }
    }

    // The open-run sibling: same weight and colour, but the path is left unclosed. A run of
    // border has two real ends, and closing it would stroke a chord straight across the cell
    // it was traced on - drawing a line that exists only as an artefact of the closing.
    static void paintLineRuns(Graphics2D g2, List<List<double[]>> runs, Color colour) {

        prepareSpanStroke(g2, colour);

        for (var run : runs) {
            g2.draw(buildOpenPath(run));
        }
    }

    private static void prepareSpanStroke(Graphics2D g2, Color colour) {

        g2.setStroke(new BasicStroke(MapLook.SPAN_STROKE));
        g2.setColor(applyAlpha(colour, MapLook.OPAQUE_ALPHA));
    }

    static Color applyAlpha(Color colour, int alpha) {
        return new Color(
            colour.getRed(),
            colour.getGreen(),
            colour.getBlue(),
            alpha);
    }

    /**
     * The circle of one radius about one point.
     *
     * <p>Java2D takes the box a circle sits in rather than its centre and radius, and the
     * conversion is the sort of arithmetic that gets written out by hand at every call and
     * mistyped at one of them.
     *
     * @param centre where it is centred
     * @param radius how far it reaches
     * @return the circle
     */
    static Ellipse2D.Double buildCircle(double[] centre, double radius) {

        return new Ellipse2D.Double(
            centre[0] - radius, centre[1] - radius, radius * DIAMETERS, radius * DIAMETERS);
    }

    static Path2D buildPath(List<double[]> ring) {

        var path = buildOpenPath(ring);

        path.closePath();
        return path;
    }

    // A polyline with real ends: the points and nothing more. The closed builder above is
    // this plus the edge back to the start.
    static Path2D buildOpenPath(List<double[]> run) {

        var path = new Path2D.Double();

        for (var i = 0; i < run.size(); i++) {

            if (i == 0) {
                path.moveTo(run.get(i)[0], run.get(i)[1]);
            } else {
                path.lineTo(run.get(i)[0], run.get(i)[1]);
            }
        }
        return path;
    }

    /**
     * The line to draw for one span of void, end to end.
     *
     * <p>At its true extent, with nothing pulled back. A span drawn here is a wall - a line
     * laid across void to close it off - and a wall is the boundary itself rather than
     * something either side stops short of, so both of its ends are where they belong.
     *
     * @param span the corridor to draw
     * @return the line to draw
     */
    static Line2D buildSpanLine(CellGap span) {

        return new Line2D.Double(
            span.start()[0],
            span.start()[1],
            span.end()[0],
            span.end()[1]);
    }
}
