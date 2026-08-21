package kmu.maplayers.base.geometry;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * What the pointer is over, said twice: in a box beside the pointer and along the status bar.
 *
 * <p>Its own class because it is the one thing on the canvas that is not about the map. It has
 * a position, a size, an edge to keep clear of and a repaint region to invalidate - none of
 * which the shapes underneath care about - and it grew until reading the canvas meant reading
 * a hundred lines of text placement before reaching anything geometric.
 *
 * <p><b>Placed once, drawn and erased from that one placement.</b> A readout that measured
 * itself when drawing and guessed when erasing leaves the tail of a long name smeared behind
 * the pointer, so {@link #findBox} is what both go through.
 *
 * <p>In screen space throughout, deliberately: the map is drawn under a scaled and y-flipped
 * transform, and text put through that comes out mirrored and sized by the zoom.
 */
final class CursorReadout {

    private static final int OFFSET_X = 14;
    private static final int OFFSET_Y = 20;
    private static final int PADDING = 4;

    // A pixel out on each side, so an antialiased glyph edge sitting on the boundary is erased
    // along with the rest of it.
    private static final int ERASE_MARGIN = 1;

    private static final Color TEXT = new Color(0xff, 0xff, 0xff);
    private static final Color BACKDROP = new Color(0x00, 0x00, 0x00, 0xc0);

    // What a readout says when there is nothing to say: one blank line, so the status bar keeps
    // its height instead of collapsing every time the pointer leaves the canvas.
    private static final List<String> NOTHING = List.of(" ");

    // Where a point on the canvas is in the sector's own coordinates. Handed in rather than
    // worked out here, because the canvas owns the transform the map is drawn under and a
    // second inverse of it is a second answer about where the pointer is.
    private final Function<Point, double[]> findWorldPoint;

    private List<NamedRegion> cells = List.of();
    private List<NamedRegion> sections = List.of();

    CursorReadout(Function<Point, double[]> findWorldPoint) {
        this.findWorldPoint = findWorldPoint;
    }

    /**
     * Hands it the regions it names, once each rebuild.
     *
     * @param cells    the cells, asked first
     * @param sections the pieces of void
     */
    void nameRegionsFrom(List<NamedRegion> cells, List<NamedRegion> sections) {

        this.cells = cells;
        this.sections = sections;
    }

    /**
     * What the pointer is over, as the lines the box shows.
     *
     * <p>Lines rather than one string with a break in it, because the box measures and places
     * each of them and the status bar wants them on a single line instead.
     *
     * @param at where the pointer is, or null where it is off the canvas
     * @return the coordinates, and the name of whatever is there
     */
    List<String> describeLines(Point at) {

        if (at == null) {
            return NOTHING;
        }

        var world = findWorldPoint.apply(at);
        var coordinates = String.format(Locale.ROOT, "%.0f, %.0f", world[0], world[1]);
        var named = findNameAt(world[0], world[1]);

        return named == null ? List.of(coordinates) : List.of(coordinates, named);
    }

    /**
     * The same reading on one line, which is what the status bar has room for.
     *
     * @param at where the pointer is, or null
     * @return the coordinates with the name to the RIGHT of them rather than under them
     */
    String describeOnOneLine(Point at) {
        return String.join("   ", describeLines(at));
    }

    /**
     * Where the readout sits for one pointer position.
     *
     * @param at      where the pointer is
     * @param metrics the font it will be drawn in
     * @param canvas  how big the canvas is, to keep the box on it
     * @return the box it covers
     */
    Rectangle findBox(Point at, FontMetrics metrics, Dimension canvas) {

        var lines = describeLines(at);
        var width = measureWidestLine(lines, metrics) + 2 * PADDING;
        var height = metrics.getHeight() * lines.size() + 2 * PADDING;

        var x = at.x + OFFSET_X;
        var y = at.y + OFFSET_Y - metrics.getAscent() - PADDING;

        // Flipped to the near side at the canvas edge, so the readout stays on screen rather
        // than running off where the pointer is most likely to be.
        if (x + width > canvas.width) {
            x = at.x - OFFSET_X - width;
        }

        if (y + height > canvas.height) {
            y = at.y - OFFSET_Y - height;
        }
        return new Rectangle(x, y, width, height);
    }

    /**
     * The region to invalidate so that a readout drawn at that position is erased.
     *
     * @param at      where the pointer was
     * @param metrics the font it was drawn in
     * @param canvas  how big the canvas is
     * @return the box to repaint
     */
    Rectangle findEraseBox(Point at, FontMetrics metrics, Dimension canvas) {

        var box = findBox(at, metrics, canvas);

        return new Rectangle(
            box.x - ERASE_MARGIN,
            box.y - ERASE_MARGIN,
            box.width + 2 * ERASE_MARGIN,
            box.height + 2 * ERASE_MARGIN);
    }

    /**
     * Draws it.
     *
     * @param g2     what to draw with, untransformed - this is screen space
     * @param at     where the pointer is
     * @param canvas how big the canvas is
     */
    void paintAt(Graphics2D g2, Point at, Dimension canvas) {

        var metrics = g2.getFontMetrics();
        var lines = describeLines(at);
        var box = findBox(at, metrics, canvas);

        g2.setColor(BACKDROP);
        g2.fillRect(box.x, box.y, box.width, box.height);
        g2.setColor(TEXT);

        for (var line = 0; line < lines.size(); line++) {

            g2.drawString(
                lines.get(line),
                box.x + PADDING,
                box.y + PADDING + metrics.getAscent() + line * metrics.getHeight());
        }
    }

    // Whichever named region the pointer is in. The cells are asked first because a cell is the
    // more definite answer: void is defined as what the cells do not reach, so the two only
    // ever overlap by the sliver between a cell's straight bound and the circle it is inscribed
    // under, and inside that sliver the cell is what a reader is pointing at.
    private String findNameAt(double worldX, double worldY) {

        var cell = NamedRegions.findRegionAt(cells, worldX, worldY);

        if (cell != null) {
            return cell.name();
        }

        var section = NamedRegions.findRegionAt(sections, worldX, worldY);

        return section == null ? null : section.name();
    }

    private static int measureWidestLine(List<String> lines, FontMetrics metrics) {

        var widest = 0;

        for (var line : lines) {
            widest = Math.max(widest, metrics.stringWidth(line));
        }
        return widest;
    }
}
