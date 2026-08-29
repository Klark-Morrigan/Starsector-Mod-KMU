package kmu.maplayers.base.geometry.render;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.WalledPocket;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
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
 * the sliders are set to. What a setting comes to mean is the viewer's own business,
 * and what the map is painted WITH is {@link MapLook}'s.
 */
public final class MapPainting {

    private static final int HUE_RANGE = 360;

    // A radius either side of the centre makes the box a circle is drawn in.
    private static final int DIAMETERS = 2;

    // The odd 32-bit approximation of the golden ratio, the standard multiplier for
    // spreading a hash's low bits across the whole word. Only its bit pattern matters, not
    // its value.
    private static final int HASH_MIX_MULTIPLIER = 0x9E3779B9;

    private MapPainting() {
    }

    /**
     * One colour shifted lighter or darker by a fixed amount for a given seed.
     *
     * <p>What tells two touching shapes apart when both are filled in the same colour. An
     * outline cannot do it where the shapes abut - one line reads as the edge of either - so
     * the shade carries the distinction instead.
     *
     * <p>Seeded rather than cycled, so a shape keeps its shade across every frame and every
     * run. Cycled by position in a list, a shape would change colour whenever something
     * elsewhere was added or dropped, which reads as the shape itself having changed.
     *
     * @param base     the colour to shift from
     * @param seed     what decides the shift, which should be something about the shape that
     *                 does not move - its place rather than its index
     * @param strength how far the shift may go, as a share of the whole brightness range
     * @return the shifted colour
     */
    public static Color jitterBrightness(Color base, int seed, float strength) {

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
    public static void paintFilledShape(
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

    /**
     * The one way closed void is filled here: each ring as a translucent body under an opaque
     * edge, at fill weight.
     *
     * <p>Shared by every construction that shuts void in, because they exist to be compared. A
     * coast round the whole sector and a coast round each continent close off the same kind of
     * thing, and two of these written separately is how the two come to be drawn at different
     * weights - at which point the difference on screen is the drawing rather than the
     * construction, and looking at them side by side stops answering anything.
     *
     * @param g2    what to draw with
     * @param rings the closed outlines to fill
     * @param fill  what to fill them with
     * @param alpha how solid the body is
     * @param edge  what to outline them in
     */
    public static void paintRingFills(
            Graphics2D g2,
            List<List<double[]>> rings,
            Color fill,
            int alpha,
            Color edge) {

        g2.setStroke(new BasicStroke(MapLook.FILL_EDGE_STROKE));

        for (var ring : rings) {
            paintFilledShape(g2, buildPath(ring), fill, alpha, edge);
        }
    }

    /**
     * The same fill for pockets, which carry their outlines rather than being one.
     *
     * @param g2      what to draw with
     * @param pockets the pockets, each of which may carry more than one outline
     * @param fill    what to fill them with
     * @param alpha   how solid the body is
     * @param edge    what to outline them in
     */
    public static void paintPocketFills(
            Graphics2D g2,
            List<WalledPocket> pockets,
            Color fill,
            int alpha,
            Color edge) {

        paintRingFills(g2, collectPocketOutlines(pockets), fill, alpha, edge);
    }

    // Every outline of every pocket, flattened - which is what filling them asks for, a
    // pocket's grouping of its own outlines mattering to what made them rather than to paint.
    private static List<List<double[]>> collectPocketOutlines(List<WalledPocket> pockets) {

        var outlines = new ArrayList<List<double[]>>(pockets.size());

        for (var walled : pockets) {
            outlines.addAll(walled.pocket().outlines());
        }
        return outlines;
    }

    /**
     * The fill between two closed rings, one inside the other - a margin, with the space
     * inside the inner ring left unpainted.
     *
     * <p>Even-odd rather than a subtraction worked out in geometry: the two rings share
     * stretches wherever the inner one runs along the outer - a drawn shore's fillets run on
     * the water's own edge - and a boolean subtraction of two nearly-coincident outlines is
     * exactly where geometry libraries produce slivers and self-intersections. The winding
     * rule gets the same answer by counting crossings, which two coincident edges cannot
     * upset.
     *
     * @param g2    what to draw with
     * @param outer the ring the fill runs out to
     * @param inner the ring the fill stops at, its inside left bare
     * @param fill  what to fill the margin with
     * @param alpha how solid the body is
     * @param edge  what to outline it in
     */
    public static void paintBetweenRings(
            Graphics2D g2,
            List<double[]> outer,
            List<double[]> inner,
            Color fill,
            int alpha,
            Color edge) {

        var margin = new Path2D.Double(Path2D.WIND_EVEN_ODD);

        margin.append(buildPath(outer), false);
        margin.append(buildPath(inner), false);

        g2.setStroke(new BasicStroke(MapLook.FILL_EDGE_STROKE));
        paintFilledShape(g2, margin, fill, alpha, edge);
    }

    // The one way a proposed line is drawn here: closed rings stroked at span weight in one
    // opaque colour, filled with nothing. Shared by the settled coast and the continent
    // preview because the two exist to be compared, and two stanzas of stroke-and-colour
    // setup is how two lines meant to differ only in colour come to differ in weight as well.
    public static void paintLineRings(Graphics2D g2, List<List<double[]>> rings, Color colour) {

        prepareSpanStroke(g2, colour);

        for (var ring : rings) {
            g2.draw(buildPath(ring));
        }
    }

    // The open-run sibling: same weight and colour, but the path is left unclosed. A run of
    // border has two real ends, and closing it would stroke a chord straight across the cell
    // it was traced on - drawing a line that exists only as an artefact of the closing.
    public static void paintLineRuns(Graphics2D g2, List<List<double[]>> runs, Color colour) {

        prepareSpanStroke(g2, colour);

        for (var run : runs) {
            g2.draw(buildOpenPath(run));
        }
    }

    private static void prepareSpanStroke(Graphics2D g2, Color colour) {

        g2.setStroke(new BasicStroke(MapLook.SPAN_STROKE));
        g2.setColor(applyAlpha(colour, MapLook.OPAQUE_ALPHA));
    }

    /**
     * The same colour at a given opacity.
     *
     * <p>Every colour on this map is chosen opaque and drawn at whatever weight the thing it
     * paints is drawn at, so the two are kept apart: a palette holds what a thing is coloured
     * and the drawing decides how solid. Baking opacity into the palette would mean a second
     * entry per colour for every weight it is ever wanted at.
     *
     * @param colour the colour
     * @param alpha  how solid, from 0 to 255
     * @return the colour at that opacity
     */
    public static Color applyAlpha(Color colour, int alpha) {
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
    public static Ellipse2D.Double buildCircle(double[] centre, double radius) {

        return new Ellipse2D.Double(
            centre[0] - radius, centre[1] - radius, radius * DIAMETERS, radius * DIAMETERS);
    }

    /**
     * A closed path round a ring of points.
     *
     * <p>The open path plus the edge back to the start. Closing it here rather than leaving
     * the caller to repeat the first point means a ring is stored once - a ring whose last
     * point repeats its first is one Java2D draws with a zero-length segment at the join, and
     * one whose does not is a shape with a gap, depending on which convention the caller
     * happened to follow.
     *
     * @param ring the points, in order
     * @return the closed path
     */
    public static Path2D buildPath(List<double[]> ring) {

        var path = buildOpenPath(ring);

        path.closePath();
        return path;
    }

    // A polyline with real ends: the points and nothing more. The closed builder above is
    // this plus the edge back to the start.
    public static Path2D buildOpenPath(List<double[]> run) {

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
    public static Line2D buildSpanLine(CellGap span) {

        return new Line2D.Double(
            span.start()[0],
            span.start()[1],
            span.end()[0],
            span.end()[1]);
    }
}
