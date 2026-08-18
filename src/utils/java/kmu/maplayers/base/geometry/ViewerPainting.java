package kmu.maplayers.base.geometry;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * How this package puts a shape on screen: the handful of Java2D moves every drawing shares.
 *
 * <p>Gathered because the map is drawn by more than one thing now - the cells and clusters
 * by the viewer itself, and each rival void construction by its own overlay - and all of
 * them build a path from a ring, fill it under an opaque outline, and tint an owner. Left
 * with whichever class happened to need them first, the overlays would have had to reach
 * back into the viewer for them, which is the wrong way round: an overlay is drawn by the
 * window, not the other way about.
 *
 * <p>Nothing here reads a setting except where an owner's colour is asked for, which cannot
 * be answered without knowing whether jitter is on and how far it spreads.
 */
final class ViewerPainting {

    // Wider than a cell edge, because a span is read against a fill rather than against the
    // black, and it has to stay findable at the zoom where a whole pocket fits on screen.
    static final float SPAN_STROKE = 120f;

    // How wide the mark on a run that crosses a cell is drawn. Heavier than any other line
    // here, because it has to be findable at the zoom where a whole sector fits on screen -
    // and shared, because two drawings marking the same fault at two weights read as two
    // different findings.
    static final float CROSSING_STROKE = 240f;

    // How wide a cell's own border is drawn. Shared because both drawings of this map use it
    // and because it is not only a look: it is how far either side of the true edge the drawn
    // edge reaches, which decides what a reader can see being on the wrong side of it.
    // A fill's own outline is read against the fill rather than against the black, so it
    // wants a fraction of the weight a line crossing open void needs. Here rather than on
    // each overlay that draws one: two copies of a stroke width is how two shapes come to be
    // drawn at weights that were meant to match and quietly do not.
    static final float FILL_EDGE_STROKE = SPAN_STROKE / 4f;

    static final float RING_STROKE = 90f;

    static final int OPAQUE_ALPHA = 255;

    private static final int HUE_RANGE = 360;

    // A radius either side of the centre makes the box a circle is drawn in.
    private static final int DIAMETERS = 2;

    // The odd 32-bit approximation of the golden ratio, the standard multiplier for
    // spreading a hash's low bits across the whole word. Only its bit pattern matters, not
    // its value.
    private static final int HASH_MIX_MULTIPLIER = 0x9E3779B9;

    private ViewerPainting() {
    }

    // One chosen colour for every owner, optionally spread in brightness so neighbours can
    // still be told apart. Brightness rather than hue on purpose: a hue jitter makes each
    // owner look like a different faction, which is what the shipped palette means, while a
    // brightness jitter reads as one thing seen in several places.
    static Color resolveOwnedColour(ViewerSettings settings, String ownerId) {
        return settings.jitterOwned
            ? jitterBrightness(settings.ownedCellColour, ownerId.hashCode(), settings.jitterStrength)
            : settings.ownedCellColour;
    }

    // Which map of the void the overlays are asking for. Shared rather than read at each of
    // them, because the two constructions drawn together have to be asked the same question
    // within one frame or they are describing different maps.
    static VoidPockets.PocketShaping resolvePocketShaping(ViewerSettings settings) {
        return settings.showPocketsAtTrueExtent
            ? VoidPockets.PocketShaping.AT_TRUE_EXTENT
            : VoidPockets.PocketShaping.WITH_CHANNEL;
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
        g2.setColor(applyAlpha(edge, OPAQUE_ALPHA));
        g2.draw(shape);
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

        var path = new Path2D.Double();

        for (var i = 0; i < ring.size(); i++) {

            if (i == 0) {
                path.moveTo(ring.get(i)[0], ring.get(i)[1]);
            } else {
                path.lineTo(ring.get(i)[0], ring.get(i)[1]);
            }
        }
        path.closePath();
        return path;
    }

    /** Paints the geometry in world coordinates under a pan/zoom transform. */

    /**
     * The line to draw for one span of void, pulled back at both ends by {@code trim}.
     *
     * <p>A span is found at the reach that defines the void, while a pocket is drawn at the
     * reach that leaves the border channel, so an untrimmed span overhangs into the channel
     * at each end and reads as crossing the cells rather than the void. No offsetting is
     * needed to find the pull-back: the span already runs along the line joining the two
     * sites, which is the line the moved reach is measured along.
     *
     * @param span the corridor to draw
     * @param trim how far to pull each end back - positive towards the cells, negative away
     *             from them, which is what a pocket pushed out to meet one owner's fills
     *             needs
     * @return the line to draw
     */
    static Line2D buildTrimmedSpan(CellGaps.CellGap span, double trim) {

        var runX = span.end()[0] - span.start()[0];
        var runY = span.end()[1] - span.start()[1];
        var length = Math.hypot(runX, runY);

        // A corridor narrower than two channels has no drawn outline for the cut to reach,
        // so trimming it would turn it inside out. Left at its true extent instead, where
        // it is at worst a short mark across a gap too tight to have been drawn anyway.
        var pullBack = length > 2 * trim ? trim / length : 0;

        return new Line2D.Double(
            span.start()[0] + runX * pullBack,
            span.start()[1] + runY * pullBack,
            span.end()[0] - runX * pullBack,
            span.end()[1] - runY * pullBack);
    }
}
