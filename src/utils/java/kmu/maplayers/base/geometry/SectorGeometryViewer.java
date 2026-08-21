package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Limits;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

/**
 * An interactive window over the map's geometry: drag a slider, see the shape change. Run it
 * with {@code gradlew viewSectorGeometry}.
 *
 * <h2>Exactly how much of production this runs</h2>
 *
 * The most important thing to know about this window: it is evidence about the mod only as
 * far as this boundary reaches. A tool that quietly diverges from production is worse than
 * no tool, because it produces confident pictures of something that does not exist. So the
 * boundary is named by method, not by adjective.
 *
 * <p><b>Production code this DOES run</b>, on production defaults, through
 * {@link SectorGeometry#buildSectorGeometry}:
 *
 * <ul>
 *   <li>{@code VoronoiCellBuilder.buildLabelledCell} - per system, via
 *       {@link SectorFixture#buildCellEdgesBySystemId}</li>
 *   <li>{@link CellShaper#shapeCells} - and through it {@link EdgeClassifier} and the kmlib
 *       per-edge inset</li>
 *   <li>{@link SystemClusterBorders#traceBorderRings} - and through it the kmlib chainer and
 *       per-edge miter. This is what {@code ClusterBorderTrace.traceRings} forwards to, at
 *       the same {@code CellShaper.BORDER_INSET_DISTANCE}; only the weld tolerance and
 *       miter limit arrive from {@link SectorGeometryParameters} instead of
 *       {@code KmuMapLayerSettings}.</li>
 * </ul>
 *
 * <p><b>Where it stops.</b> Everything below is production code the map runs and this window
 * does not call at all:
 *
 * <ul>
 *   <li><i>Deciding what exists</i> - {@code MapVisibility.shouldAppearOnMap},
 *       {@code CellGeometryCache.updateFromSector} and
 *       {@code getCellEdgesByCellId}. {@link SectorFixture} rebuilds the cache's private
 *       cell-to-edge translation, so every fixture row is drawn and nothing exercises the
 *       cache, its diffing, or the incremental refresh.</li>
 *   <li><i>Deciding who owns it</i> - {@code SectorPolitics.resolveDominantOwnerBySystemId},
 *       {@code SystemDominance}, {@code DominantOwner.mapFactionIdBySystemId},
 *       {@code DecivilisedMarkets.findRevealedDecivilisedSystemIds},
 *       {@code FilteredPolitics}, {@code FilterSelection}. Ownership is the fixture's
 *       dominant-owner column, whose score is summed market size rather than the real
 *       {@code DominanceRules} weight - so who owns what is approximately, not exactly, what
 *       the game resolves. There is no view switching, filter, or spotlight.</li>
 *   <li><i>Turning rings into a picture</i> - {@code TerritoryBuilder.buildTerritories},
 *       {@code buildStyledCellForSystem}, {@code buildFactionTerritory},
 *       {@code BorderSmoothing.sandBorderSpikes}, {@code roundBorderCorners},
 *       {@code PolygonTessellator.tessellateToBoundaryLoops}, {@code tessellateToTriangles},
 *       {@code Hatching.computeHatchRun}, {@code GlVertexRuns.flattenVertices}.</li>
 *   <li><i>Painting it</i> - {@code RenderStyleReader.readRenderStyle}, {@code MapPalettes},
 *       {@code ClusterRenderer}, the label pass, and {@code KmuPoliticalMapSettings} entirely.
 *       Nothing reads {@code Global}. Colours here are hash-derived hues for telling owners
 *       apart, never the faction palette, and this is Java2D, so no blend mode, line
 *       smoothing, corner rounding, hatching, or layering against vanilla is exercised.</li>
 * </ul>
 *
 * <p>So: the rings on screen are the rings the mod would trace. Everything about how they
 * are painted is invented here. Judge a shape in this window; judge how it reads in game.
 *
 * <h2>Why it exists</h2>
 *
 * The knobs it drives are geometric, and their effect is only legible as a shape. A number in
 * a settings file says nothing about whether a border pinches to a neck or a dead star's
 * pocket closes over, and this map's history is of exactly those being found late, from a
 * screenshot, after a feature had been built on them.
 */
final class SectorGeometryViewer implements ViewerRefreshes {

    private static final Path SVG_DIRECTORY = Path.of("build", "reports", "political-map");

    private static final String WINDOW_TITLE = "KMU political map";
    private static final String CSV_EXTENSION = ".csv";
    private static final String SVG_EXTENSION = ".svg";

    // Narrow enough to push the knobs aside for a good look at the map, wide enough that
    // the divider is still findable.
    private static final int CONTROL_MINIMUM_WIDTH = 80;
    
    // A slider row is about this tall, so one wheel notch moves the control panel by roughly
    // one knob rather than by one pixel.
    private static final int SCROLL_UNIT_INCREMENT = 16;

    private static final double ZOOM_PER_NOTCH = 1.15;
    private static final double INITIAL_MARGIN = 1.05;

    private static final float SITE_RADIUS = 120f;

    // The margin round the line of text along the bottom of the window.
    private static final int STATUS_BAR_PADDING = 4;

    private static final float CELL_STROKE = 30f;

    private static final int OPAQUE_ALPHA = 255;

    private static final long NANOS_PER_MILLI = 1_000_000L;

    private static final Color BACKGROUND = new Color(0x11, 0x11, 0x11);

    // Not final: the fixture is picked from a dropdown, so a session can move between
    // sectors without restarting - a shape only worth judging is one that holds on more
    // than one sector, and reopening the tool to find out is enough friction to skip it.
    private String sectorName;
    private SectorFixture fixture;
    private final MapCanvas canvas = new MapCanvas();

    private List<List<double[]>> unboundedCells = List.of();
    private SectorGeometry geometry;
    private long lastBuildMillis;
    private final ViewerSettings settings = new ViewerSettings();

    // Opened with the window and emptied there, so a session's picks are its own.
    private final PickLog picks = PickLog.startPickLog();
    private final VoidBridgesOverlay voidBridges = new VoidBridgesOverlay(settings);
    private final CoastlinesOverlay coastlines = new CoastlinesOverlay(settings);
    private final ContinentCoastsOverlay continentCoasts = new ContinentCoastsOverlay(settings);
    private final VoidSectionsOverlay voidSections = new VoidSectionsOverlay(settings);

    // The cells as named regions, so the pointer can be told which one it is over and a name
    // can be written on each. Built with the geometry rather than on a toggle of their own,
    // because the readout names a cell whether or not the names are being drawn.
    private List<NamedRegion> cellNames = List.of();

    private final CursorReadout readout =
        new CursorReadout(point -> canvas.findWorldPoint(point));

    private SectorGeometryViewer() {
    }

    /**
     * Opens the viewer.
     *
     * @param args ignored; the fixture is picked from the dropdown in the window, which
     *             remembers what was last chosen
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SectorGeometryViewer().showWindow());
    }

    // Loading only. The geometry is not rebuilt here because the knobs have not applied their
    // remembered values yet, and rebuilding against the defaults would only be thrown away.
    private void loadSector(String name) {

        sectorName = name;
        fixture = SectorFixture.loadSector(name);
    }

    // Run once the pick has been applied, never alongside it: the loading is the apply, and
    // doing it here as well would parse the fixture twice on every change of sector.
    //
    // Refits as well as rebuilding, because two sectors do not occupy the same coordinates
    // and keeping the old pan would open the new one off screen.
    private void refitToLoadedSector() {

        canvas.markForRefit();
        rebuildGeometry();
    }

    private void showWindow() {

        var frame = new JFrame(WINDOW_TITLE);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // A split rather than a fixed east panel, so the knob column can be widened when a
        // long label needs reading and narrowed when the map does. The canvas takes the
        // slack on resize, since the controls have a natural width and the map does not.
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, buildControlColumn());

        split.setResizeWeight(1.0);
        split.setContinuousLayout(true);

        frame.add(split, BorderLayout.CENTER);
        frame.add(canvas.cursorBar, BorderLayout.SOUTH);

        ViewerWindowLayout.restoreLayout(frame, split);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                ViewerWindowLayout.saveLayout(frame, split);
            }
        });

        frame.setVisible(true);
    }

    // Writing the SVG is an action the operator asks for, not something that happens to them.
    // It captures whatever the sliders are currently showing, which is the point: the file is
    // for keeping an interesting shape - to attach to a plan, or to diff against a later run -
    // and only the person looking at it knows when it has become interesting.
    // The sector picker and the SVG button bracket the knob rows rather than sitting among
    // them: one decides which sector is loaded and the other writes a file, and neither is a
    // setting the map is drawn with.
    private JScrollPane buildControlColumn() {

        var controls = new JPanel();

        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        controls.add(ViewerControls.buildChoice(
            "Sector fixture",
            "Sector",
            SectorFixture.listSectorNames(),
            this::loadSector,
            this::refitToLoadedSector));

        controls.add(new ViewerSettingsPanel(settings, this).buildRows());
        controls.add(buildSaveSvgButton());
        controls.add(canvas.statusLabel);

        // Scrolled, because the knob count now exceeds a window height and a control that has
        // fallen off the bottom of a fixed panel is a control nobody knows exists.
        var scroller = new JScrollPane(controls);

        // A minimum rather than a fixed size now that the split decides the width: without
        // one the divider can be dragged past the knobs and they vanish with no way back.
        scroller.setMinimumSize(new Dimension(CONTROL_MINIMUM_WIDTH, 0));
        scroller.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);

        // Every knob applies its remembered value as it is built, and until they all have,
        // nothing has the settings the session was left on - the reach, the miter limit, the
        // sector. So the one build of the geometry happens here, after the last of them,
        // rather than in the constructor where it would run against the defaults and then sit
        // there looking authoritative until something was touched.
        rebuildGeometry();

        return scroller;
    }

    private JButton buildSaveSvgButton() {

        var button = new JButton("Save SVG of current shape");

        button.addActionListener(event -> {

            var target = SVG_DIRECTORY.resolve(
                sectorName.replace(CSV_EXTENSION, SVG_EXTENSION));

            // At whatever shaping the window is showing, which is what "of current shape"
            // means: a button that saved the other one would hand back a picture of a map
            // nobody was looking at.
            SectorSvgWriter.writeSectorSvg(
                target, fixture, geometry, settings.resolvePocketShaping());

            canvas.statusLabel.setText("<html>wrote<br>" + target.toAbsolutePath() + "</html>");
        });
        return button;
    }

    // Built only while they are on screen: the partition is over every site at once, so it
    // costs about what the clipped build costs, and nothing else in the window needs it.
    @Override
    public void refreshUnboundedCells() {

        unboundedCells = settings.showUnboundedCells
            ? UnboundedCells.buildUnboundedCells(
                fixture.getSites(),
                settings.parameters.boundSegments())
            : List.of();

        canvas.repaint();
    }

    // Nothing to build. A site's void is what is left showing when its clipped cell is
    // drawn over its unclipped one, so the apron appears from draw order alone - which is
    // why the void colour is applied to the unbounded cells rather than to a shape of its
    // own.
    // The pockets of void the cells trap, as their own outlines. Not the same black as the
    // Rebuilt alongside the pockets rather than on its own schedule, because the two are only
    // worth anything side by side and a knob that moved one without the other would be
    // comparing two different maps.
    @Override
    public void refreshVoidBridges() {

        voidBridges.refresh(fixture);
        refreshCoastlines();
    }

    // The sections follow the coast rather than having a schedule of their own: a section is a
    // piece of void the walls helped close, so every knob that moves a wall moves both where
    // the divisions fall and what the pieces are called.
    @Override
    public void refreshCoastlines() {

        coastlines.refresh(fixture);

        // The preview rides the same refresh because it is traced under the same coast knobs:
        // a knob that moved one line without the other would show two coasts that were never
        // traced from the same settings.
        continentCoasts.refresh(fixture);
        refreshVoidSections();
    }

    // Not on the refresh contract: the panel has no knob that moves the sections without
    // moving the walls first, so this is reached through the coast rather than asked for.
    private void refreshVoidSections() {

        voidSections.refresh(fixture);
        readout.nameRegionsFrom(cellNames, voidSections.getSections());
        repaintMap();
    }

    @Override
    public void repaintMap() {
        canvas.repaint();
    }

    @Override
    public void rebuildGeometry() {

        var start = System.nanoTime();
        geometry = SectorGeometry.buildSectorGeometry(fixture, settings.parameters);
        cellNames = buildCellNames();

        readout.nameRegionsFrom(cellNames, voidSections.getSections());

        if (settings.showUnboundedCells) {

            unboundedCells = UnboundedCells.buildUnboundedCells(
                fixture.getSites(),
                settings.parameters.boundSegments());
        }
        refreshVoidBridges();
        lastBuildMillis = (System.nanoTime() - start) / NANOS_PER_MILLI;
        refreshStatus();
    }

    // Each cell as a named region, taken from its TRUE border rather than from its inset fill.
    // What a reader means by "which cell is this" includes the border channel, which the fill
    // has already given up - so a pointer in the channel would be told it is nowhere.
    private List<NamedRegion> buildCellNames() {

        var named = new ArrayList<NamedRegion>(geometry.cellEdgesByCellId().size());

        for (var entry : geometry.cellEdgesByCellId().entrySet()) {

            var ring = CellEdges.convertEdgesToRing(entry.getValue());

            if (ring.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                continue;
            }
            named.add(NamedRegion.nameRegion(entry.getKey(), ring));
        }
        return List.copyOf(named);
    }

    private void refreshStatus() {

        canvas.statusLabel.setText(String.format(
            "<html>%d systems, %d owners<br>rebuilt in %d ms<br><br>"
                + "drag to pan, wheel to zoom<br>right click to note a colour<br>%s</html>",
            fixture.getSystemIds().size(),
            geometry.ringsByOwner().size(),
            lastBuildMillis,
            PickLog.getPickFile()));
    }

    private final class MapCanvas extends JPanel {

        private final JLabel statusLabel = new JLabel();
        private final JLabel cursorBar = new JLabel(" ");
        private Point cursorPoint;
        private double scale;
        private double offsetX;
        private double offsetY;
        private Point2D dragAnchor;

        private MapCanvas() {

            setBackground(BACKGROUND);
            cursorBar.setBorder(BorderFactory.createEmptyBorder(
                    STATUS_BAR_PADDING,
                    STATUS_BAR_PADDING,
                    STATUS_BAR_PADDING,
                    STATUS_BAR_PADDING));
            addMouseWheelListener(event -> {

                var factor = Math.pow(ZOOM_PER_NOTCH, -event.getWheelRotation());

                // Zoom about the cursor rather than the origin, so the feature being
                // inspected stays under the pointer instead of sliding off.
                offsetX = event.getX() - (event.getX() - offsetX) * factor;
                offsetY = event.getY() - (event.getY() - offsetY) * factor;
                scale *= factor;

                repaint();
            });
            addMouseListener(new java.awt.event.MouseAdapter() {

                @Override
                public void mousePressed(java.awt.event.MouseEvent event) {

                    dragAnchor = event.getPoint();

                    if (SwingUtilities.isRightMouseButton(event)) {
                        writePick(event.getPoint());
                    }
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent event) {
                    trackCursor(null);
                }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {

                @Override
                public void mouseDragged(java.awt.event.MouseEvent event) {

                    offsetX += event.getX() - dragAnchor.getX();
                    offsetY += event.getY() - dragAnchor.getY();
                    dragAnchor = event.getPoint();
                    cursorPoint = event.getPoint();
                    cursorBar.setText(readout.describeOnOneLine(cursorPoint));

                    repaint();
                }

                @Override
                public void mouseMoved(java.awt.event.MouseEvent event) {
                    trackCursor(event.getPoint());
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {

            super.paintComponent(g);
            paintMap((Graphics2D) g);

            if (scale != 0) {
                paintNames((Graphics2D) g);
            }
            if (cursorPoint != null && scale != 0) {
                readout.paintAt((Graphics2D) g, cursorPoint, getSize());
            }
        }

        // The cells first, so that where a name of each kind would land on the same place the
        // void's name is the one left readable. A piece of void is the smaller and rarer thing
        // and is what anyone with the void names switched on is looking for.
        private void paintNames(Graphics2D g2) {

            var worldToScreen = buildWorldToScreen();

            if (settings.showCellNames) {
                NamedRegions.paintNames(
                    g2, worldToScreen, cellNames, settings.regionNameColour);
            }
            voidSections.paintNames(g2, worldToScreen);
        }

        // The one statement of where the sector sits on the canvas. The map is drawn through
        // it and the section names are placed by it, so a name landing somewhere other than on
        // its own section is impossible rather than merely unlikely.
        private AffineTransform buildWorldToScreen() {

            var transform = new AffineTransform();

            transform.translate(offsetX, offsetY);

            // World y grows upward and screen y downward, so the drawing is flipped once here
            // rather than at every vertex - a sector drawn upside down would read as a bug.
            transform.scale(scale, -scale);

            return transform;
        }

        // The map itself, without the readout drawn over it. Apart from the readout because a
        // pick has to be able to paint the map alone: a label following the pointer around is
        // the one thing certain to be near wherever a colour is being read.
        private void paintMap(Graphics2D g) {

            var g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            if (scale == 0) {
                fitToSector();
            }

            g2.transform(buildWorldToScreen());

            paintGeometry(g2);

            g2.dispose();
        }

        // What was under the pointer, written down. Painted afresh into an image rather than
        // grabbed off the screen, so what is read is this canvas at this moment - a screen
        // grab reads whatever window happens to be over it, and a cached frame reads whatever
        // the knobs were before the last move.
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

            var world = findWorldPoint(point);

            picks.appendPick(
                sectorName, world[0], world[1], new Color(image.getRGB(point.x, point.y)));
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
        // transform the map is drawn under, in one place, so what a pick writes down and what
        // the readout says cannot come to disagree.
        private double[] findWorldPoint(Point point) {

            return new double[] {
                (point.x - offsetX) / scale,
                (offsetY - point.y) / scale};
        }

        private void paintGeometry(Graphics2D g2) {

            // First, so the cells and rings draw over it. The corridors occupy the space the
            // cells do not, so nothing is actually hidden - but a stray pixel of raster
            // straying over a cell rim should read as the mistake it is rather than paint
            // over the shape it got wrong.
            // Below the clipped cells on purpose: the unclipped partition is the thing the
            // clip cut down, so it reads as what is underneath rather than as another layer
            // laid over the top.
            g2.setStroke(new BasicStroke(CELL_STROKE));

            paintUnderlays(g2);
            paintChannels(g2);

            g2.setStroke(new BasicStroke(MapLook.RING_STROKE));

            paintUnownedCells(g2);
            paintOwnerClusters(g2);

            // Both passes go after the fills, not before. A cluster fuses its cells into
            // one fill, so a line drawn first is painted over by the very shape it divides.
            g2.setStroke(new BasicStroke(CELL_STROKE));

            paintFillContours(g2);
            paintCentrelines(g2);
            voidBridges.paintSpans(g2);
            coastlines.paintCoasts(g2);

            // Topmost of the lines, so where the preview and the settled coast coincide the
            // preview reads unbroken - what is being looked for is where they DIVERGE, and a
            // divergence shows as two colours coming apart.
            continentCoasts.paintCoasts(g2);
            paintSites(g2);
        }

        // The unclipped partition and the two void constructions, all of which go under the
        // cells so that a stray edge reads as the mistake it is rather than painting over the
        // shape it got wrong.
        private void paintUnderlays(Graphics2D g2) {

            for (var cell : unboundedCells) {
                MapPainting.paintFilledShape(
                    g2,
                    MapPainting.buildPath(cell),
                    settings.unboundedCellColour,
                    settings.unboundedCellOpacity,
                    settings.unboundedCellEdge);
            }

            voidBridges.paintFills(g2);
            coastlines.paintPocketFills(g2);
        }

        // The channel is the ring a cell leaves between its true edge and its inset fill, so
        // painting the whole true cell and letting the fill cover the middle leaves exactly
        // that ring showing - no second shape to build, and it cannot disagree with where the
        // fill actually stops.
        //
        // Filled only. The outline of this shape is the shared cell boundary, which is the
        // centreline rather than the channel's own border; the border is the inset contour,
        // stroked later once the fills are down.
        private void paintChannels(Graphics2D g2) {

            g2.setColor(MapPainting.applyAlpha(
                settings.channelColour, settings.channelOpacity));

            for (var cell : geometry.cellEdgesByCellId().values()) {
                g2.fill(MapPainting.buildPath(CellEdges.convertEdgesToRing(cell)));
            }
        }

        // Unowned per the geometry's own keys, not the fixture's: a cell the build grouped or
        // unowned must be drawn as the build left it.
        private void paintUnownedCells(Graphics2D g2) {

            for (var entry : geometry.shapedCellByCellId().entrySet()) {

                if (geometry.ownerByCellId().containsKey(entry.getKey())
                        || entry.getValue().fillPolygon().size()
                            < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                    continue;
                }
                MapPainting.paintFilledShape(
                    g2,
                    MapPainting.buildPath(entry.getValue().fillPolygon()),
                    settings.jitterUnowned
                        ? MapPainting.jitterBrightness(settings.unownedCellColour,
                            entry.getKey().hashCode(),
                            settings.jitterStrength)
                        : settings.unownedCellColour,
                    settings.unownedCellOpacity,
                    settings.unownedCellEdge);
            }
        }

        // One path per owner, filled even-odd, so a ring wound against the rest cuts a hole in
        // it - an enclave - instead of painting over it solid. Filling each ring on its own
        // paints an enclave as another island of the owner's colour, which is the opposite of
        // what it means.
        private void paintOwnerClusters(Graphics2D g2) {

            for (var entry : geometry.ringsByOwner().entrySet()) {

                var cluster = new Path2D.Double(Path2D.WIND_EVEN_ODD);

                for (var ring : entry.getValue()) {
                    cluster.append(MapPainting.buildPath(ring), false);
                }
                MapPainting.paintFilledShape(
                    g2,
                    cluster,
                    settings.resolveOwnedColour(entry.getKey()),
                    settings.ownedCellOpacity,
                    settings.ownedCellEdge);
            }
        }

        private void paintSites(Graphics2D g2) {

            g2.setColor(settings.siteColour);

            for (var site : fixture.getSites()) {
                g2.fill(MapPainting.buildCircle(site, SITE_RADIUS));
            }
        }

        // The fill contour, edge by edge, because one contour carries two different
        // things. A fill edge that was pulled in against ANOTHER CELL is the side of a
        // border channel; a fill edge pulled in at the cell's own reach bound is just where
        // the cell stops, with no channel behind it and nothing on the far side. Colouring
        // the whole contour one way makes one of those two invisible.
        //
        // Which is which comes from the true edge the fill edge was pulled off: the inset is
        // a parallel offset, so the nearest true edge to a fill edge's midpoint is the edge
        // it came from, and that edge is tagged with what lies across it.
        private void paintFillContours(Graphics2D g2) {

            for (var entry : geometry.shapedCellByCellId().entrySet()) {

                var shaped = entry.getValue();
                var trueEdges = geometry.cellEdgesByCellId().get(entry.getKey());

                if (trueEdges == null
                        || shaped.fillPolygon().size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                    continue;
                }

                var cellEdge = geometry.ownerByCellId().containsKey(entry.getKey())
                    ? settings.ownedCellEdge
                    : settings.unownedCellEdge;

                var fill = shaped.fillPolygon();

                for (var index = 0; index < fill.size(); index++) {

                    // A seam left on the true cell border fuses two same-owner fills into
                    // one shape. Stroking it would draw a division that the fill itself
                    // deliberately does not have.
                    if (!shaped.edgeIsBoundary()[index]) {
                        continue;
                    }

                    var from = fill.get(index);
                    var to = fill.get((index + 1) % fill.size());

                    g2.setColor(MapPainting.applyAlpha(
                        doesFaceAnotherCell(trueEdges, from, to)
                            ? settings.channelEdge
                            : cellEdge,
                        OPAQUE_ALPHA));

                    g2.draw(new Line2D.Double(from[0], from[1], to[0], to[1]));
                }
            }
        }

        // The true cell border, but only where another cell is actually across it. That is
        // what makes a line a CENTRELINE: two cells meet along it and each backs off by the
        // same inset, leaving it running down the middle of what opens up. A reach-bound
        // edge has nothing on the far side to be the centre of - it is the cell's outer
        // silhouette, and belongs to the cell's own outline colour.
        private void paintCentrelines(Graphics2D g2) {

            g2.setColor(MapPainting.applyAlpha(settings.centrelineColour, OPAQUE_ALPHA));

            for (var edges : geometry.cellEdgesByCellId().values()) {
                for (var edge : edges) {

                    if (!(edge.target() instanceof EdgeTarget.AcrossSystem)) {
                        continue;
                    }

                    g2.draw(new Line2D.Double(
                        edge.x1(),
                        edge.y1(),
                        edge.x2(),
                        edge.y2()));
                }
            }
        }

        private static boolean doesFaceAnotherCell(
                List<CellEdge> trueEdges,
                double[] from,
                double[] to) {

            var nearest = CellEdges.findNearestEdge(
                trueEdges,
                (from[0] + to[0]) / 2.0,
                (from[1] + to[1]) / 2.0);

            return nearest != null && nearest.target() instanceof EdgeTarget.AcrossSystem;
        }

        // Puts the view back to "not yet fitted", which is what a zero scale means to the
        // paint pass. Named rather than having callers assign zero, because "scale = 0" reads
        // as breaking the transform rather than as asking for it to be worked out again.
        private void markForRefit() {
            scale = 0;
        }

        private void fitToSector() {

            var bounds = Bounds.computeEnclosingBounds(fixture.getSites());

            var span = Math.max(
                bounds.maxX() - bounds.minX(),
                bounds.maxY() - bounds.minY()) * INITIAL_MARGIN;

            scale = Math.min(getWidth(), getHeight()) / span;
            offsetX = getWidth() / 2.0 - (bounds.minX() + bounds.maxX()) / 2 * scale;
            offsetY = getHeight() / 2.0 + (bounds.minY() + bounds.maxY()) / 2 * scale;
        }

    }
}
