package kmu.maplayers.base.geometry;

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
 * <p><b>A name is drawn only where its region is wide enough on screen to hold it.</b> There
 * are several hundred cells and several hundred sections in a sector and their names are long,
 * so at the zoom where the whole map fits they would be one illegible mass laid over the thing
 * they describe. Culling on the region's own on-screen width rather than on a zoom threshold
 * means names appear as one zooms in, and each one that appears has room to be read.
 */
final class NamedRegions {

    private static final float LABEL_POINT_SIZE = 11f;

    // Not a map colour and so not read off the palette: the backdrop exists to keep text
    // legible over whatever it lands on, which is a property of writing on a picture rather
    // than of the thing being written about.
    private static final Color LABEL_BACKDROP = new Color(0x00, 0x00, 0x00, 0xc0);

    private static final int LABEL_PADDING = 3;

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

            if (measureScreenWidth(region, worldToScreen) < width) {
                continue;
            }

            worldToScreen.transform(
                new Point2D.Double(region.anchor()[0], region.anchor()[1]), at);

            paintName(
                text,
                region.name(),
                (int) at.x - width / HALVES,
                (int) at.y,
                width,
                metrics,
                colour);
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

    // One name on a backdrop, so it stays readable over a fill of any colour.
    private static void paintName(
            Graphics2D g2,
            String name,
            int x,
            int y,
            int width,
            FontMetrics metrics,
            Color colour) {

        g2.setColor(LABEL_BACKDROP);
        g2.fillRect(
            x - LABEL_PADDING,
            y - metrics.getAscent() - LABEL_PADDING,
            width + HALVES * LABEL_PADDING,
            metrics.getHeight() + HALVES * LABEL_PADDING);

        g2.setColor(colour);
        g2.drawString(name, x, y);
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
