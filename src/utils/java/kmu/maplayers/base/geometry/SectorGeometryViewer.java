package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Limits;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.List;
import java.util.function.DoubleConsumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
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
 *       {@code KmuLunaSettings}.</li>
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
 *       {@code Hatching.computeHatchSegments}, {@code GlVertexRuns.flattenVertices}.</li>
 *   <li><i>Painting it</i> - {@code RenderStyleReader.readRenderStyle}, {@code MapPalettes},
 *       {@code TerritoryRenderer}, the label pass, and {@code KmuLunaSettings} entirely.
 *       Nothing reads {@code Global}. Colours here are hash-derived hues for telling blocs
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
final class SectorGeometryViewer {
    private static final Path SVG_DIRECTORY = Path.of("build", "reports", "political-map");
    private static final String CSV_EXTENSION = ".csv";
    private static final String SVG_EXTENSION = ".svg";
    private static final int WINDOW_WIDTH = 1500;
    private static final int WINDOW_HEIGHT = 1000;
    private static final int CONTROL_WIDTH = 300;
    private static final double ZOOM_PER_NOTCH = 1.15;
    private static final double INITIAL_MARGIN = 1.05;
    private static final int SLIDER_STEPS = 1000;
    private static final float SITE_RADIUS = 120f;
    private static final float CELL_STROKE = 30f;
    private static final float RING_STROKE = 90f;
    private static final int HUE_RANGE = 360;
    private static final float BLOC_SATURATION = 0.8f;
    private static final float BLOC_BRIGHTNESS = 0.55f;
    private static final int BLOC_FILL_ALPHA = 90;
    private static final int OPAQUE_ALPHA = 255;
    private static final int PANEL_PADDING = 8;
    private static final int SLIDER_ROW_PADDING = 4;
    private static final int SLIDER_ROWS = 2;
    private static final int SLIDER_COLUMNS = 1;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    // Slider ranges: wide enough either side of the shipped defaults to see a knob's effect
    // break down, not just vary. The reach floor sits below any real system spacing and the
    // ceiling well past it, so both "cells never meet" and "cells swallow the sector" are
    // reachable; the weld range spans the chord sagitta that makes clusters chain or not.
    private static final double REACH_MINIMUM = 500;
    private static final double REACH_MAXIMUM = 12000;
    private static final double INSET_MINIMUM = 0;
    private static final double INSET_MAXIMUM = 800;
    private static final double WELD_MINIMUM = 0;
    private static final double WELD_MAXIMUM = 400;
    private static final double MITER_MINIMUM = 1;
    private static final double MITER_MAXIMUM = 12;
    private static final double SEGMENTS_MINIMUM = 3;
    private static final double SEGMENTS_MAXIMUM = 96;
    private static final Color BACKGROUND = new Color(0x11, 0x11, 0x11);
    private static final Color CELL_COLOUR = new Color(0x2a, 0x2a, 0x2a);
    private static final Color NEUTRAL_COLOUR = new Color(0x55, 0x55, 0x55);
    private static final Color SITE_COLOUR = new Color(0x88, 0x88, 0x88);

    private final String sectorName;
    private final SectorFixture fixture;
    private final MapCanvas canvas = new MapCanvas();
    private SectorGeometryParameters parameters = SectorGeometryParameters.createDefaults();
    private SectorGeometry geometry;
    private long lastBuildMillis;

    private SectorGeometryViewer(String sectorName) {
        this.sectorName = sectorName;
        this.fixture = SectorFixture.loadSector(sectorName);
        rebuildGeometry();
    }

    /**
     * Opens the viewer.
     *
     * @param args optionally the fixture to open, as named under the political-map test
     *             resources; the first one found otherwise
     */
    public static void main(String[] args) {
        var sectorName = args.length > 0 ? args[0] : SectorFixture.listSectorNames().get(0);
        SwingUtilities.invokeLater(() -> new SectorGeometryViewer(sectorName).showWindow());
    }

    private void showWindow() {
        var frame = new JFrame("KMU political map - " + sectorName);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(canvas, BorderLayout.CENTER);
        frame.add(buildControls(), BorderLayout.EAST);
        frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildControls() {
        var controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setPreferredSize(new Dimension(CONTROL_WIDTH, 0));
        controls.setBorder(BorderFactory.createEmptyBorder(
                PANEL_PADDING, PANEL_PADDING, PANEL_PADDING, PANEL_PADDING));
        controls.add(buildSlider("Territory reach (cell radius)", REACH_MINIMUM, REACH_MAXIMUM,
                parameters.cellRadius(),
                value -> parameters = new SectorGeometryParameters(value,
                        parameters.boundSegments(), parameters.borderInset(),
                        parameters.weldTolerance(), parameters.miterSpikeLimit())));
        controls.add(buildSlider("Border channel (inset)", INSET_MINIMUM, INSET_MAXIMUM,
                parameters.borderInset(),
                value -> parameters = new SectorGeometryParameters(parameters.cellRadius(),
                        parameters.boundSegments(), value,
                        parameters.weldTolerance(), parameters.miterSpikeLimit())));
        controls.add(buildSlider("Weld tolerance", WELD_MINIMUM, WELD_MAXIMUM,
                parameters.weldTolerance(),
                value -> parameters = new SectorGeometryParameters(parameters.cellRadius(),
                        parameters.boundSegments(), parameters.borderInset(), value,
                        parameters.miterSpikeLimit())));
        controls.add(buildSlider("Miter spike limit", MITER_MINIMUM, MITER_MAXIMUM,
                parameters.miterSpikeLimit(),
                value -> parameters = new SectorGeometryParameters(parameters.cellRadius(),
                        parameters.boundSegments(), parameters.borderInset(),
                        parameters.weldTolerance(), value)));
        controls.add(buildSlider("Cell bound segments", SEGMENTS_MINIMUM, SEGMENTS_MAXIMUM,
                parameters.boundSegments(),
                value -> parameters = new SectorGeometryParameters(parameters.cellRadius(),
                        (int) Math.round(value), parameters.borderInset(),
                        parameters.weldTolerance(), parameters.miterSpikeLimit())));
        controls.add(buildSaveSvgButton());
        controls.add(canvas.statusLabel);
        return controls;
    }

    // Writing the SVG is an action the operator asks for, not something that happens to them.
    // It captures whatever the sliders are currently showing, which is the point: the file is
    // for keeping an interesting shape - to attach to a plan, or to diff against a later run -
    // and only the person looking at it knows when it has become interesting.
    private JButton buildSaveSvgButton() {
        var button = new JButton("Save SVG of current shape");
        button.addActionListener(event -> {
            var target = SVG_DIRECTORY.resolve(
                    sectorName.replace(CSV_EXTENSION, SVG_EXTENSION));
            SectorSvgWriter.writeSectorSvg(target, fixture, geometry);
            canvas.statusLabel.setText("<html>wrote<br>" + target.toAbsolutePath() + "</html>");
        });
        return button;
    }

    // A slider over a real-valued knob. Swing sliders are integral, so the range is mapped
    // onto fixed steps and back - the knobs are continuous and quantising them to whole world
    // units would make the fine end of the reach slider unreachable.
    private JPanel buildSlider(
            String title, double minimum, double maximum, double initial, DoubleConsumer apply) {
        var label = new JLabel();
        var slider = new JSlider(0, SLIDER_STEPS,
                (int) Math.round((initial - minimum) / (maximum - minimum) * SLIDER_STEPS));
        Runnable refresh = () -> {
            var value = minimum + (maximum - minimum) * slider.getValue() / (double) SLIDER_STEPS;
            label.setText(String.format("%s: %.0f", title, value));
            apply.accept(value);
        };
        refresh.run();
        slider.addChangeListener(event -> {
            refresh.run();
            rebuildGeometry();
            canvas.repaint();
        });
        var panel = new JPanel(new GridLayout(SLIDER_ROWS, SLIDER_COLUMNS));
        panel.add(label);
        panel.add(slider);
        panel.setBorder(BorderFactory.createEmptyBorder(SLIDER_ROW_PADDING, 0,
                SLIDER_ROW_PADDING, 0));
        return panel;
    }

    private void rebuildGeometry() {
        var start = System.nanoTime();
        geometry = SectorGeometry.buildSectorGeometry(fixture, parameters);
        lastBuildMillis = (System.nanoTime() - start) / NANOS_PER_MILLI;
        canvas.statusLabel.setText(String.format(
                "<html>%d systems, %d blocs<br>rebuilt in %d ms<br><br>"
                        + "drag to pan, wheel to zoom</html>",
                fixture.getSystemIds().size(), geometry.ringsByGroupKey().size(), lastBuildMillis));
    }

    private static Color pickBlocColour(String blocId, int alpha) {
        var hue = Math.floorMod(blocId.hashCode(), HUE_RANGE) / (float) HUE_RANGE;
        var opaque = Color.getHSBColor(hue, BLOC_SATURATION, BLOC_BRIGHTNESS);
        return new Color(opaque.getRed(), opaque.getGreen(), opaque.getBlue(), alpha);
    }

    private static Path2D buildPath(List<double[]> ring) {
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
    private final class MapCanvas extends JPanel {
        private final JLabel statusLabel = new JLabel();
        private double scale;
        private double offsetX;
        private double offsetY;
        private Point2D dragAnchor;

        private MapCanvas() {
            setBackground(BACKGROUND);
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
                }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseDragged(java.awt.event.MouseEvent event) {
                    offsetX += event.getX() - dragAnchor.getX();
                    offsetY += event.getY() - dragAnchor.getY();
                    dragAnchor = event.getPoint();
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            var g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (scale == 0) {
                fitToSector();
            }
            // World y grows upward and screen y downward, so the drawing is flipped once here
            // rather than at every vertex - a sector drawn upside down would read as a bug.
            g2.translate(offsetX, offsetY);
            g2.scale(scale, -scale);
            paintGeometry(g2);
            g2.dispose();
        }

        private void paintGeometry(Graphics2D g2) {
            g2.setStroke(new BasicStroke(CELL_STROKE));
            g2.setColor(CELL_COLOUR);
            for (var edges : geometry.cellEdgesByCellId().values()) {
                g2.draw(buildPath(toRing(edges)));
            }
            g2.setStroke(new BasicStroke(RING_STROKE));
            // Ungrouped per the geometry's own keys, not the fixture's: a cell the build
            // grouped or ungrouped must be drawn as the build left it.
            for (var entry : geometry.shapedCellByCellId().entrySet()) {
                if (geometry.groupKeyByCellId().containsKey(entry.getKey())
                        || entry.getValue().fillPolygon().size()
                            < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                    continue;
                }
                g2.setColor(NEUTRAL_COLOUR);
                g2.draw(buildPath(entry.getValue().fillPolygon()));
            }
            // One path per bloc, filled even-odd, so a ring wound against the rest cuts a hole in
            // it - an enclave - instead of painting over it solid. Filling each ring on its own
            // paints an enclave as another island of the bloc's colour, which is the opposite of
            // what it means.
            for (var entry : geometry.ringsByGroupKey().entrySet()) {
                var region = new Path2D.Double(Path2D.WIND_EVEN_ODD);
                for (var ring : entry.getValue()) {
                    region.append(buildPath(ring), false);
                }
                g2.setColor(pickBlocColour(entry.getKey(), BLOC_FILL_ALPHA));
                g2.fill(region);
                g2.setColor(pickBlocColour(entry.getKey(), OPAQUE_ALPHA));
                g2.draw(region);
            }
            g2.setColor(SITE_COLOUR);
            for (var site : fixture.getSites()) {
                g2.fill(new java.awt.geom.Ellipse2D.Double(site[0] - SITE_RADIUS,
                        site[1] - SITE_RADIUS, SITE_RADIUS * 2, SITE_RADIUS * 2));
            }
        }

        private void fitToSector() {
            var minX = Double.MAX_VALUE;
            var minY = Double.MAX_VALUE;
            var maxX = -Double.MAX_VALUE;
            var maxY = -Double.MAX_VALUE;
            for (var site : fixture.getSites()) {
                minX = Math.min(minX, site[0]);
                minY = Math.min(minY, site[1]);
                maxX = Math.max(maxX, site[0]);
                maxY = Math.max(maxY, site[1]);
            }
            var span = Math.max(maxX - minX, maxY - minY) * INITIAL_MARGIN;
            scale = Math.min(getWidth(), getHeight()) / span;
            offsetX = getWidth() / 2.0 - (minX + maxX) / 2.0 * scale;
            offsetY = getHeight() / 2.0 + (minY + maxY) / 2.0 * scale;
        }

        private List<double[]> toRing(List<CellEdge> edges) {
            return edges.stream().map(edge -> new double[] {edge.x1(), edge.y1()}).toList();
        }
    }
}
