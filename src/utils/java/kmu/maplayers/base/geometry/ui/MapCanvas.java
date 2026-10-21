package kmu.maplayers.base.geometry.ui;

import kmlib.math.geometry.Bounds;

import kmu.maplayers.base.geometry.NamedRegion;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The view onto the sector: where it sits on screen, what a drag, the wheel and a right click
 * do, and what the pointer reads.
 *
 * <p>Apart from the viewer because the two answer to different things. The viewer decides what
 * is drawn and in what order; this decides where the drawing lands and how a reader moves
 * about it. Held together, the panning and the refresh contract would share one class and
 * neither could be read without the other.
 */
final class MapCanvas
        extends JPanel {

    private static final double ZOOM_PER_NOTCH = 1.15;
    private static final double INITIAL_MARGIN = 1.05;

    // The margin round the line of text along the bottom of the window.
    private static final int CURSOR_BAR_PADDING = 4;

    private static final Color BACKGROUND = new Color(0x11, 0x11, 0x11);

    private final CanvasContent content;
    private final CursorReadout readout = new CursorReadout(this::findWorldPoint);

    private final JLabel cursorBar = new JLabel(" ");

    private Point cursorPoint;
    private Point2D dragAnchor;

    private double offsetX;
    private double offsetY;

    // Zero until the view is fitted to the sector, which the first paint does once the canvas
    // has a size to fit into.
    private double scale;

    /**
     * What the canvas draws and reports, as the viewer supplies it.
     *
     * @param paintWorld  draws the map, under the transform from the sector to the screen
     * @param paintNames  writes the names, in screen space, given that transform
     * @param readSites   the sites the view is fitted round
     * @param recordPick  writes down a point in the sector and the colour drawn at it
     */
    record CanvasContent(
        Consumer<Graphics2D> paintWorld,
        BiConsumer<Graphics2D, AffineTransform> paintNames,
        Supplier<List<double[]>> readSites,
        BiConsumer<double[], Color> recordPick) {
    }

    MapCanvas(CanvasContent content) {

        this.content = content;

        setBackground(BACKGROUND);
        cursorBar.setBorder(BorderFactory.createEmptyBorder(
            CURSOR_BAR_PADDING,
            CURSOR_BAR_PADDING,
            CURSOR_BAR_PADDING,
            CURSOR_BAR_PADDING));

        bindZoom();
        bindPointer();
    }

    /**
     * @return the line along the bottom of the window that says what the pointer is over
     */
    JLabel getCursorBar() {
        return cursorBar;
    }

    /**
     * Puts the view back to "not yet fitted", so the next paint fits it to the sector again.
     *
     * <p>For a new sector: two sectors do not occupy the same coordinates, and keeping the old
     * pan would open the new one off screen.
     */
    void markForRefit() {
        scale = 0;
    }

    /**
     * Hands the readout the regions it names, once each rebuild.
     *
     * @param cells    the cells, asked first
     * @param sections the pieces of void
     */
    void nameRegionsFrom(List<NamedRegion> cells, List<NamedRegion> sections) {
        readout.nameRegionsFrom(cells, sections);
    }

    @Override
    protected void paintComponent(Graphics g) {

        super.paintComponent(g);
        paintMap((Graphics2D) g);

        if (scale != 0) {
            content.paintNames().accept((Graphics2D) g, buildWorldToScreen());
        }

        if (cursorPoint != null && scale != 0) {
            readout.paintAt((Graphics2D) g, cursorPoint, getSize());
        }
    }

    // Zoom about the cursor rather than the origin, so the feature being inspected stays under
    // the pointer instead of sliding off.
    private void bindZoom() {

        addMouseWheelListener(event -> {

            var factor = Math.pow(ZOOM_PER_NOTCH, -event.getWheelRotation());

            offsetX = event.getX() - (event.getX() - offsetX) * factor;
            offsetY = event.getY() - (event.getY() - offsetY) * factor;
            scale *= factor;

            repaint();
        });
    }

    private void bindPointer() {

        addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent event) {

                dragAnchor = event.getPoint();

                if (SwingUtilities.isRightMouseButton(event)) {
                    writePick(event.getPoint());
                }
            }

            @Override
            public void mouseExited(MouseEvent event) {
                trackCursor(null);
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {

            @Override
            public void mouseDragged(MouseEvent event) {

                offsetX += event.getX() - dragAnchor.getX();
                offsetY += event.getY() - dragAnchor.getY();
                dragAnchor = event.getPoint();
                cursorPoint = event.getPoint();
                cursorBar.setText(readout.describeOnOneLine(cursorPoint));

                repaint();
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                trackCursor(event.getPoint());
            }
        });
    }

    // The one statement of where the sector sits on the canvas. The map is drawn through it and
    // the names are placed by it, so a name landing somewhere other than on its own region is
    // impossible rather than merely unlikely.
    private AffineTransform buildWorldToScreen() {

        var transform = new AffineTransform();

        transform.translate(offsetX, offsetY);

        // World y grows upward and screen y downward, so the drawing is flipped once here
        // rather than at every vertex - a sector drawn upside down would read as a bug.
        transform.scale(scale, -scale);

        return transform;
    }

    // The map itself, without the readout drawn over it. Apart from the readout because a pick
    // has to be able to paint the map alone: a label following the pointer around is the one
    // thing certain to be near wherever a colour is being read.
    private void paintMap(Graphics2D g) {

        var g2 = (Graphics2D) g.create();

        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);

        if (scale == 0) {
            fitToSector();
        }

        g2.transform(buildWorldToScreen());

        content.paintWorld().accept(g2);

        g2.dispose();
    }

    // What was under the pointer, written down. Painted afresh into an image rather than grabbed
    // off the screen, so what is read is this canvas at this moment - a screen grab reads
    // whatever window happens to be over it, and a cached frame reads whatever the knobs were
    // before the last move.
    private void writePick(Point point) {

        if (scale == 0 || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }

        var image = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
        var g2 = image.createGraphics();

        g2.setColor(getBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());

        paintMap(g2);

        g2.dispose();

        content.recordPick().accept(findWorldPoint(point), new Color(image.getRGB(point.x, point.y)));
    }

    private void trackCursor(Point point) {

        var previous = cursorPoint;

        cursorPoint = point;
        cursorBar.setText(readout.describeOnOneLine(point));

        if (previous != null) {
            repaintAround(previous);
        }

        if (point != null) {
            repaintAround(point);
        }
    }

    private void repaintAround(Point point) {

        if (scale == 0) {
            return;
        }

        var box = readout.findEraseBox(point, getFontMetrics(getFont()), getSize());

        repaint(box.x, box.y, box.width, box.height);
    }

    // Where a point on the canvas is in the sector's own coordinates - the inverse of the
    // transform the map is drawn under, in one place, so what a pick writes down and what the
    // readout says cannot come to disagree.
    private double[] findWorldPoint(Point point) {

        return new double[] {
            (point.x - offsetX) / scale,
            (offsetY - point.y) / scale};
    }

    private void fitToSector() {

        var bounds = Bounds.computeEnclosingBounds(content.readSites().get());

        var span = Math.max(
            bounds.maxX() - bounds.minX(),
            bounds.maxY() - bounds.minY()) * INITIAL_MARGIN;

        scale = Math.min(getWidth(), getHeight()) / span;
        offsetX = getWidth() / 2.0 - (bounds.minX() + bounds.maxX()) / 2 * scale;
        offsetY = getHeight() / 2.0 + (bounds.minY() + bounds.maxY()) / 2 * scale;
    }
}
