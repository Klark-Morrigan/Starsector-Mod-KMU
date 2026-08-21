package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.List;

/**
 * Writing names on the map, and reading back which name the pointer is on.
 *
 * <p>Names are drawn in SCREEN space, over the finished map. The world transform is scaled and
 * flipped so that y grows upward, and text put through it comes out mirrored and sized by the
 * zoom - so the region is placed through the transform and the name is drawn at the point that
 * comes back.
 *
 * <p>Painted after the map rather than as part of it, like the cursor readout and for the same
 * reason: a right click reads the colour under the pointer off a freshly painted frame, and a
 * name lying across a fill would be read as that fill's colour.
 *
 * <p><b>A name too big for its region fades rather than vanishing.</b> There are several
 * hundred cells and several hundred sections in a sector and their names are long, so at the
 * zoom where the whole map fits, drawing them all solid would be one illegible mass laid over
 * the thing it describes. Dropping them outright is the other extreme, and it loses the one
 * thing the far view has to say - that there is something there at all.
 *
 * <p>So opacity falls with how badly the name overruns its region: full where it fits, three
 * quarters where it only just does not, and away down towards a hundredth where the region is
 * a speck. Never to nothing, because a name that reached zero would be a region the map
 * silently stopped mentioning, and the reader would have no way to tell that from empty space.
 *
 * <p>Faint names OVERLAP, and overlapping is the point. Alpha compounds, so a hundred specks
 * on top of one another come out as a visible haze exactly where the map is dense, and a lone
 * far-off region stays a whisper. The reader gets a density reading for free out of the same
 * pass that writes the names, and it is honest by construction: what looks crowded is crowded.
 */
final class NamedRegions {

    // Small enough that a name takes little of the region it names, large enough to read at
    // a glance. Fixed rather than scaled with the zoom: a name sized by the zoom would grow
    // with the shape it sits on and never come to fit it.
    private static final float LABEL_POINT_SIZE = 11f;

    // Not a map colour and so not read off the palette: the backdrop exists to keep text
    // legible over whatever it lands on, which is a property of writing on a picture rather
    // than of the thing being written about.
    private static final Color LABEL_BACKDROP = new Color(0x00, 0x00, 0x00, 0xc0);

    // The margin between a name and the edge of its own backdrop. Enough that the glyphs do
    // not touch the fill showing past it, which is what makes the box read as a label rather
    // than as a shape on the map.
    private static final int LABEL_PADDING = 3;

    // How solidly a name is drawn when its region cannot quite hold it, and the floor it falls
    // to when the region is a speck. The floor is deliberately not zero: a name faded out
    // entirely is a region the map stopped mentioning, which a reader cannot tell from there
    // being nothing there.
    private static final float NEAR_FIT_OPACITY = 0.75f;
    private static final float LEAST_OPACITY = 0.01f;

    // How sharply the fade sets in between those two. Squared rather than straight, so a name
    // that nearly fits stays readable while one at a quarter of its width is already down to
    // about a twentieth - which is what keeps a dense far view a haze rather than a smear of
    // half-legible text.
    private static final int FALLOFF_POWER = 2;

    private static final float FULLY_SOLID = 1f;

    // A radius either side of the centre makes a box's full width.
    private static final int HALVES = 2;

    private NamedRegions() {
    }

    /**
     * Writes each name over the region it belongs to.
     *
     * @param g2            what to draw with, untransformed - this is screen space
     * @param worldToScreen the transform the map was drawn under, to place each name where its
     *                      region ended up
     * @param regions       the regions to name
     * @param colour        what to write them in
     */
    static void paintNames(
            Graphics2D g2,
            AffineTransform worldToScreen,
            List<NamedRegion> regions,
            Color colour) {

        if (regions.isEmpty()) {
            return;
        }

        // A copy, because the font is changed for the names and whatever draws next - the
        // cursor readout - measures its own text with whatever font it is handed.
        var text = (Graphics2D) g2.create();

        text.setFont(text.getFont().deriveFont(LABEL_POINT_SIZE));

        var metrics = text.getFontMetrics();
        var at = new Point2D.Double();

        for (var region : regions) {

            var width = metrics.stringWidth(region.name());

            if (width <= 0) {
                continue;
            }

            worldToScreen.transform(
                new Point2D.Double(region.anchor()[0], region.anchor()[1]), at);

            paintName(
                text,
                region.name(),
                findBackdrop(at, width, metrics),
                metrics,
                new UiElementPaint(
                    colour,
                    readOpacity(measureScreenWidth(region, worldToScreen) / width)));
        }
        text.dispose();
    }

    /**
     * Which region a point is in, or null where none of them holds it.
     *
     * <p>First match rather than smallest or nearest: the sets asked about here do not overlap
     * each other - one partition of cells, one division of the void - so a point is in at most
     * one of any set handed in, and a rule for choosing between two would be answering a
     * question that cannot arise.
     *
     * @param regions the regions to search, in the order they should be tried
     * @param x       where to look
     * @param y       the same
     * @return the region holding the point, or null
     */
    static NamedRegion findRegionAt(List<NamedRegion> regions, double x, double y) {

        for (var region : regions) {

            if (region.holds(x, y)) {
                return region;
            }
        }
        return null;
    }

    // The box a name is painted on, centred over the point its region anchors it at.
    //
    // A box rather than the four numbers it is made of: they only mean anything together, and
    // handed over loose they were four of a method's seven parameters with nothing but their
    // order saying which was which.
    private static Rectangle findBackdrop(Point2D at, int width, FontMetrics metrics) {

        return new Rectangle(
            (float) at.getX() - width / (float) HALVES - LABEL_PADDING,
            (float) at.getY() - metrics.getAscent() - LABEL_PADDING,
            width + (float) HALVES * LABEL_PADDING,
            metrics.getHeight() + (float) HALVES * LABEL_PADDING);
    }

    // How solidly to draw a name, given how much of its own width its region offers on screen.
    //
    // One at a time and from that ratio alone: a name is faint because its own region is small,
    // not because of what is around it, so nothing here has to know what else is being drawn.
    // What the far view shows is then the sum of those independent decisions, which is what
    // makes a crowd of them read as a crowd.
    private static float readOpacity(double fit) {

        if (fit >= FULLY_SOLID) {
            return FULLY_SOLID;
        }
        return (float) (LEAST_OPACITY
            + (NEAR_FIT_OPACITY - LEAST_OPACITY) * Math.pow(Math.max(0, fit), FALLOFF_POWER));
    }

    // One name on a backdrop, so it stays readable over a fill of any colour.
    //
    // The backdrop fades with the text rather than staying put: a solid box under a whisper of
    // text would make the far view a field of black rectangles, which says nothing about what
    // is under them.
    private static void paintName(
            Graphics2D g2,
            String name,
            Rectangle backdrop,
            FontMetrics metrics,
            UiElementPaint paint) {

        g2.setColor(fade(LABEL_BACKDROP, paint.alpha()));
        g2.fillRect(
            Math.round(backdrop.x()),
            Math.round(backdrop.y()),
            Math.round(backdrop.width()),
            Math.round(backdrop.height()));

        // The baseline sits an ascent below the backdrop's own top, which is what puts the
        // glyphs inside it rather than resting on it.
        g2.setColor(fade(paint.colour(), paint.alpha()));
        g2.drawString(
            name,
            Math.round(backdrop.x()) + LABEL_PADDING,
            Math.round(backdrop.y()) + LABEL_PADDING + metrics.getAscent());
    }

    // A colour at a share of its own opacity, so a colour already part-transparent stays in
    // proportion rather than being reset to the share outright.
    private static Color fade(Color colour, float share) {

        return MapPainting.applyAlpha(colour, Math.round(colour.getAlpha() * share));
    }

    // How wide a region is on screen, taken by putting its box through the same transform the
    // map was drawn under. From the transform rather than from a zoom factor handed in beside
    // it, so there is no second reading of the zoom to fall out of step with the first.
    private static double measureScreenWidth(
            NamedRegion region,
            AffineTransform worldToScreen) {

        var box = region.box();

        var low = worldToScreen.transform(
            new Point2D.Double(box.minX(), box.minY()), null);
        var high = worldToScreen.transform(
            new Point2D.Double(box.maxX(), box.maxY()), null);

        return Math.abs(high.getX() - low.getX());
    }
}
