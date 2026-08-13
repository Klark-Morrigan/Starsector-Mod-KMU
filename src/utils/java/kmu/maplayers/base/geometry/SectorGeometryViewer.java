package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolygonOffsets;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.prefs.Preferences;

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
final class SectorGeometryViewer {

    private static final Path SVG_DIRECTORY = Path.of("build", "reports", "political-map");

    private static final String CSV_EXTENSION = ".csv";
    private static final String SVG_EXTENSION = ".svg";

    private static final int WINDOW_WIDTH = 1500;
    private static final int WINDOW_HEIGHT = 1000;
    private static final int CONTROL_WIDTH = 300;

    // Narrow enough to push the knobs aside for a good look at the map, wide enough that
    // the divider is still findable.
    private static final int CONTROL_MINIMUM_WIDTH = 80;

    // Keys the remembered layout is stored under. Named rather than inlined because the
    // save and the restore have to agree, and a typo in one of them fails silently.
    private static final String WINDOW_WIDTH_KEY = "windowWidth";
    private static final String WINDOW_HEIGHT_KEY = "windowHeight";
    private static final String CONTROL_WIDTH_KEY = "controlWidth";

    private static final double ZOOM_PER_NOTCH = 1.15;
    private static final double INITIAL_MARGIN = 1.05;

    private static final float SITE_RADIUS = 120f;
    // The mark standing in for a pocket with no room to draw. Fixed in world units like the
    // site dots, so it reads as a pin on the map rather than as a shape of that size.
    private static final float VOID_MARK_RADIUS = 200f;
    // Screen pixels, not world units: the readout keeps its size at every zoom, because a
    // label that shrank with the map would be unreadable at exactly the zoom where a
    // coordinate is wanted.
    private static final int READOUT_OFFSET_X = 14;
    private static final int READOUT_OFFSET_Y = 20;
    private static final int READOUT_PADDING = 4;
    // Generous enough to cover the drawn label whatever the coordinates are, so the repaint
    // it asks for erases the old one without measuring text twice.
    private static final int READOUT_BOX_WIDTH = 240;
    private static final int READOUT_BOX_HEIGHT = 40;
    private static final Color READOUT_TEXT = new Color(0xff, 0xff, 0xff);
    private static final Color READOUT_BACKDROP = new Color(0x00, 0x00, 0x00, 0xc0);
    private static final float CELL_STROKE = 30f;
    private static final float RING_STROKE = 90f;

    private static final int HUE_RANGE = 360;

    private static final float OWNER_SATURATION = 0.8f;
    private static final float OWNER_BRIGHTNESS = 0.55f;

    private static final int OWNER_FILL_ALPHA = 90;
    private static final int OPAQUE_ALPHA = 255;
    private static final int PANEL_PADDING = 8;

    // A slider row is about this tall, so one wheel notch moves the control panel by roughly
    // one knob rather than by one pixel.
    private static final int SCROLL_UNIT_INCREMENT = 16;
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
    private static final Color OWNED_CELL_DEFAULT = new Color(0x4a, 0x8a, 0xd0);
    private static final Color UNOWNED_CELL_DEFAULT = new Color(0x55, 0x55, 0x55);
    private static final Color UNBOUNDED_CELL_DEFAULT = new Color(0x30, 0x30, 0x38);
    private static final Color VOID_CELL_DEFAULT = new Color(0xb0, 0x8a, 0x30);
    private static final Color WIDE_VOID_DEFAULT = new Color(0x30, 0xa0, 0xb0);
    private static final Color CHANNEL_DEFAULT = new Color(0x22, 0x22, 0x26);

    // The line down the middle of a channel: the true border two neighbouring cells share,
    // which each of them insets away from by the same distance. Its own colour because it is
    // its own thing - not the edge of anything drawn, but the line those edges were measured
    // from, and the only place the partition itself is visible once the fills are in.
    private static final Color CENTRELINE_DEFAULT = new Color(0x50, 0x50, 0x58);

    // A bound pocket smaller than this share of a normal cell is one void cell rather than
    // something to divide. Measured against a whole cell's area because that is the unit the
    // map is already read in - "half a system's worth of gap" means something on sight, where
    // a number of square units does not.
    private static final double VOID_SPAN_MINIMUM = 0;
    private static final double VOID_SPAN_MAXIMUM = 6;

    // One cell across. A pocket no wider than a single cell has no two sides far enough
    // apart for anything to reach between them, so there is nothing in it to divide.
    private static final double VOID_SPAN_DEFAULT = 2;
    private static final double VOID_SPAN_STEP_SCALE = 100.0;

    // How far brightness may wander either side of the chosen colour when jitter is on, as a
    // percentage of the full range. The default is wide enough to tell two neighbours apart
    // and narrow enough that they still read as one palette; the slider exists because which
    // of those matters depends on what is being looked for.
    private static final double JITTER_MINIMUM = 0;
    private static final double JITTER_MAXIMUM = 100;
    private static final float JITTER_DEFAULT = 35;
    private static final double JITTER_SCALE = 100.0;

    // Alpha runs the full byte, so a fill can be turned off entirely or made solid without
    // touching the colour it was chosen as.
    private static final double OPACITY_MINIMUM = 0;
    private static final double OPACITY_MAXIMUM = 255;
    private static final Color SITE_COLOUR = new Color(0x88, 0x88, 0x88);

    // How finely each of a pocket's bounding arcs is sampled. The arcs are exact; this only
    // decides how smooth they look, and matching the cells' own bound segment count keeps a
    // pocket's edge as faceted as the cell it runs against rather than visibly rounder.
    private static final int POCKET_ARC_SEGMENTS = 12;

    private final String sectorName;
    private final SectorFixture fixture;
    private final MapCanvas canvas = new MapCanvas();

    private Color ownedCellColour = OWNED_CELL_DEFAULT;
    private Color ownedCellEdge = OWNED_CELL_DEFAULT;
    private Color unownedCellColour = UNOWNED_CELL_DEFAULT;
    private Color unownedCellEdge = UNOWNED_CELL_DEFAULT;
    private Color unboundedCellColour = UNBOUNDED_CELL_DEFAULT;
    private Color unboundedCellEdge = UNBOUNDED_CELL_DEFAULT;
    private Color voidCellColour = VOID_CELL_DEFAULT;
    private Color voidCellEdge = VOID_CELL_DEFAULT;

    private int voidCellOpacity = OWNER_FILL_ALPHA;
    private double voidSpanMultiple = VOID_SPAN_DEFAULT;
    private boolean shouldUnownedBlockAbsorption = true;

    private Color wideVoidColour = WIDE_VOID_DEFAULT;
    private Color wideVoidEdge = WIDE_VOID_DEFAULT;
    private Color siteColour = SITE_COLOUR;
    private Color centrelineColour = CENTRELINE_DEFAULT;
    private Color channelColour = CHANNEL_DEFAULT;
    private Color channelEdge = CHANNEL_DEFAULT;
    
    private int channelOpacity = OWNER_FILL_ALPHA;
    private List<VoidPockets.VoidPocket> voidPockets = List.of();
    private int ownedCellOpacity = OWNER_FILL_ALPHA;
    private int unownedCellOpacity = OWNER_FILL_ALPHA;
    private int unboundedCellOpacity = OWNER_FILL_ALPHA;
    private boolean jitterOwned = true;
    private boolean jitterUnowned;
    private float jitterStrength = JITTER_DEFAULT;
    private boolean showUnboundedCells;
    private List<List<double[]>> unboundedCells = List.of();
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

        var sectorName = args.length > 0
            ? args[0]
            : SectorFixture.listSectorNames().get(0);

        SwingUtilities.invokeLater(() -> new SectorGeometryViewer(sectorName).showWindow());
    }

    private void showWindow() {

        var frame = new JFrame("KMU political map - " + sectorName);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // A split rather than a fixed east panel, so the knob column can be widened when a
        // long label needs reading and narrowed when the map does. The canvas takes the
        // slack on resize, since the controls have a natural width and the map does not.
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, buildControls());

        split.setResizeWeight(1.0);
        split.setContinuousLayout(true);

        frame.add(split, BorderLayout.CENTER);
        frame.add(canvas.cursorBar, BorderLayout.SOUTH);

        restoreWindowLayout(frame, split);

        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                saveWindowLayout(frame, split);
            }
        });

        frame.setVisible(true);
    }

    // Window size and divider, kept across runs. A geometry knob is only worth anything at a
    // particular zoom and a particular amount of screen, and having to re-establish both
    // before every session is enough friction to stop someone checking a shape they would
    // otherwise have checked.
    private void restoreWindowLayout(JFrame frame, JSplitPane split) {

        var saved = Preferences.userNodeForPackage(SectorGeometryViewer.class);

        frame.setSize(
            saved.getInt(WINDOW_WIDTH_KEY, WINDOW_WIDTH),
            saved.getInt(WINDOW_HEIGHT_KEY, WINDOW_HEIGHT));

        frame.setLocationRelativeTo(null);

        // After the size, because a divider is positioned within the split's current width
        // and setting it first would place it against the default.
        split.setDividerLocation(frame.getWidth()
            - saved.getInt(CONTROL_WIDTH_KEY, CONTROL_WIDTH));
    }

    private void saveWindowLayout(JFrame frame, JSplitPane split) {

        var saved = Preferences.userNodeForPackage(SectorGeometryViewer.class);

        saved.putInt(WINDOW_WIDTH_KEY, frame.getWidth());
        saved.putInt(WINDOW_HEIGHT_KEY, frame.getHeight());

        // Stored as the control column's width rather than the divider's position, so
        // reopening at a different window size keeps the knobs the size they were set to
        // instead of the map the size it happened to be.
        saved.putInt(CONTROL_WIDTH_KEY, frame.getWidth() - split.getDividerLocation());
    }

    private JScrollPane buildControls() {

        var controls = new JPanel();

        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(
            PANEL_PADDING,
            PANEL_PADDING,
            PANEL_PADDING,
            PANEL_PADDING));

        controls.add(buildSlider(
            "Cell reach (cell radius)",
            REACH_MINIMUM,
            REACH_MAXIMUM,
            parameters.cellRadius(),
            value -> parameters = new SectorGeometryParameters(
                value,
                parameters.boundSegments(),
                parameters.borderInset(),
                parameters.weldTolerance(),
                parameters.miterSpikeLimit())));

        controls.add(buildSlider(
            "Border channel (inset)",
            INSET_MINIMUM,
            INSET_MAXIMUM,
            parameters.borderInset(),
            value -> parameters = new SectorGeometryParameters(
                parameters.cellRadius(),
                parameters.boundSegments(),
                value,
                parameters.weldTolerance(),
                parameters.miterSpikeLimit())));

        controls.add(buildSlider(
            "Weld tolerance",
            WELD_MINIMUM,
            WELD_MAXIMUM,
            parameters.weldTolerance(),
            value -> parameters = new SectorGeometryParameters(
                parameters.cellRadius(),
                parameters.boundSegments(),
                parameters.borderInset(),
                value,
                parameters.miterSpikeLimit())));

        controls.add(buildSlider(
            "Miter spike limit",
            MITER_MINIMUM,
            MITER_MAXIMUM,
            parameters.miterSpikeLimit(),
            value -> parameters = new SectorGeometryParameters(
                parameters.cellRadius(),
                parameters.boundSegments(),
                parameters.borderInset(),
                parameters.weldTolerance(),
                value)));

        controls.add(buildSlider(
            "Cell bound segments",
            SEGMENTS_MINIMUM,
            SEGMENTS_MAXIMUM,
            parameters.boundSegments(),
            value -> parameters = new SectorGeometryParameters(
                parameters.cellRadius(),
                (int) Math.round(value),
                parameters.borderInset(),
                parameters.weldTolerance(),
                parameters.miterSpikeLimit())));

        controls.add(ViewerControls.buildColourPair(
            "Owned cells",
            "Owned cells",
            OWNED_CELL_DEFAULT,
            OWNED_CELL_DEFAULT,
            colour -> ownedCellColour = colour,
            colour -> ownedCellEdge = colour,
            canvas::repaint));

        controls.add(buildOpacitySlider(
            "Owned opacity",
            opacity -> ownedCellOpacity = (int) opacity));

        controls.add(ViewerControls.buildColourPair(
            "Unowned cells",
            "Unowned cells",
            UNOWNED_CELL_DEFAULT,
            UNOWNED_CELL_DEFAULT,
            colour -> unownedCellColour = colour,
            colour -> unownedCellEdge = colour,
            canvas::repaint));

        controls.add(buildOpacitySlider(
            "Unowned opacity",
            opacity -> unownedCellOpacity = (int) opacity));

        controls.add(buildToggle(
            "Trace unbounded cells",
            false,
            on -> {
                showUnboundedCells = on;
                refreshUnboundedCells();
                refreshVoidPockets();
            }));

        controls.add(ViewerControls.buildColourPair(
            "Unbounded cells",
            "Unbounded cells",
            UNBOUNDED_CELL_DEFAULT,
            UNBOUNDED_CELL_DEFAULT,
            colour -> unboundedCellColour = colour,
            colour -> unboundedCellEdge = colour,
            canvas::repaint));

        controls.add(buildOpacitySlider(
            "Unbounded opacity",
            opacity -> unboundedCellOpacity = (int) opacity));

        controls.add(ViewerControls.buildToggleRow(
            canvas::repaint,
            new ViewerControls.Toggle(
                "Jitter owned",
                "Jitter owned",
                true,
                on -> jitterOwned = on),
            new ViewerControls.Toggle(
                "Jitter unowned",
                "Jitter unowned",
                false,
                on -> jitterUnowned = on)));

        controls.add(buildSlider(
            "Jitter strength",
            JITTER_MINIMUM,
            JITTER_MAXIMUM,
            JITTER_DEFAULT,
            strength -> jitterStrength = (float) strength));

        controls.add(ViewerControls.buildColourPair(
            "Void cells",
            "Void within a cell",
            VOID_CELL_DEFAULT,
            VOID_CELL_DEFAULT,
            colour -> voidCellColour = colour,
            colour -> voidCellEdge = colour,
            canvas::repaint));

        controls.add(ViewerControls.buildToggle(
            "Unowned blocks absorption",
            "Unowned blocks void absorption",
            true,
            on -> shouldUnownedBlockAbsorption = on,
            this::refreshVoidPockets));

        controls.add(ViewerControls.buildColourPair(
            "Wide void",
            "Void wider than that",
            WIDE_VOID_DEFAULT, WIDE_VOID_DEFAULT,
            colour -> wideVoidColour = colour,
            colour -> wideVoidEdge = colour,
            canvas::repaint));

        controls.add(ViewerControls.buildColour(
            "Site dots",
            "Site dots",
            SITE_COLOUR,
            colour -> siteColour = colour,
            canvas::repaint));

        controls.add(ViewerControls.buildColour(
            "Cell centrelines",
            "Cell centrelines",
            CENTRELINE_DEFAULT,
            colour -> centrelineColour = colour,
            canvas::repaint));

        controls.add(ViewerControls.buildColourPair(
            "Inset channels",
            "Inset channels",
            CHANNEL_DEFAULT,
            CHANNEL_DEFAULT,
            colour -> channelColour = colour,
            colour -> channelEdge = colour,
            canvas::repaint));

        controls.add(buildOpacitySlider(
            "Channel opacity",
            opacity -> channelOpacity = (int) opacity));

        controls.add(buildOpacitySlider(
            "Void cell opacity",
            opacity -> voidCellOpacity = (int) opacity));

        // Stepped in hundredths, so the threshold can be moved by a fraction of a cell
        // radius rather than jumping a whole one at a time.
        controls.add(ViewerControls.buildSlider(
            "Void span multiple",
            "Void span, in cell radii (x100)",
            VOID_SPAN_MINIMUM * VOID_SPAN_STEP_SCALE,
            VOID_SPAN_MAXIMUM * VOID_SPAN_STEP_SCALE,
            VOID_SPAN_DEFAULT * VOID_SPAN_STEP_SCALE,
            multiple -> voidSpanMultiple = multiple / VOID_SPAN_STEP_SCALE,
            canvas::repaint,
            () -> { }));

        controls.add(buildSaveSvgButton());
        controls.add(canvas.statusLabel);

        // Scrolled, because the knob count now exceeds a window height and a control that has
        // fallen off the bottom of a fixed panel is a control nobody knows exists.
        var scroller = new JScrollPane(controls);

        // A minimum rather than a fixed size now that the split decides the width: without
        // one the divider can be dragged past the knobs and they vanish with no way back.
        scroller.setMinimumSize(new Dimension(CONTROL_MINIMUM_WIDTH, 0));
        scroller.getVerticalScrollBar().setUnitIncrement(SCROLL_UNIT_INCREMENT);

        return scroller;
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

    // The cell geometry is cheap enough to rebuild on every pixel of drag; the void overlay
    // is not, so it settles when the handle is released. Both hang off the same knobs,
    // because the cells the overlay finds corridors between are these cells.
    // The title doubles as the key. A knob's label is the one thing about it that is already
    // unique and already meaningful, so keying on it means a knob cannot be added without
    // being remembered - which is how the last panel ended up with several that were not.
    private JPanel buildSlider(
            String title,
            double minimum,
            double maximum,
            double initial,
            DoubleConsumer apply) {
        return ViewerControls.buildSlider(
            title,
            title,
            minimum,
            maximum,
            initial,
            apply,
            () -> {
                rebuildGeometry();
                canvas.repaint();
            },
            () -> { });
    }

    private JPanel buildOpacitySlider(String title, DoubleConsumer apply) {
        return buildSlider(title, OPACITY_MINIMUM, OPACITY_MAXIMUM, OWNER_FILL_ALPHA, apply);
    }

    private JPanel buildToggle(String title, boolean initial, Consumer<Boolean> apply) {
        return ViewerControls.buildToggle(title, title, initial, apply, () -> {
            rebuildGeometry();
            canvas.repaint();
        });
    }

    // Built only while they are on screen: the partition is over every site at once, so it
    // costs about what the clipped build costs, and nothing else in the window needs it.
    private void refreshUnboundedCells() {

        unboundedCells = showUnboundedCells
            ? UnboundedCells.buildUnboundedCells(
                fixture.getSites(),
                parameters.boundSegments())
            : List.of();

        canvas.repaint();
    }

    // Nothing to build. A site's void is what is left showing when its clipped cell is
    // drawn over its unclipped one, so the apron appears from draw order alone - which is
    // why the void colour is applied to the unbounded cells rather than to a shape of its
    // own.
    // The pockets of void the cells trap, as their own outlines. Not the same black as the
    // border channel, which is what made the classification impossible to check by eye: a
    // channel is two touching cells leaving room for a border, a pocket is space no cell
    // reaches, and they are only the same colour by accident of both being unpainted.
    private void refreshVoidPockets() {

        voidPockets = VoidPockets.findVoidPockets(
            fixture.getSites(),
            fixture.getOwnerBySite(),
            parameters,
            POCKET_ARC_SEGMENTS,
            shouldUnownedBlockAbsorption);

        canvas.repaint();
    }

    private void rebuildGeometry() {

        var start = System.nanoTime();
        geometry = SectorGeometry.buildSectorGeometry(fixture, parameters);

        if (showUnboundedCells) {

            unboundedCells = UnboundedCells.buildUnboundedCells(
                fixture.getSites(),
                parameters.boundSegments());
        }
        refreshVoidPockets();
        lastBuildMillis = (System.nanoTime() - start) / NANOS_PER_MILLI;
        refreshStatus();
    }

    private void refreshStatus() {

        canvas.statusLabel.setText(String.format(
            "<html>%d systems, %d owners<br>rebuilt in %d ms<br><br>"
                + "drag to pan, wheel to zoom</html>",
            fixture.getSystemIds().size(),
            geometry.ringsByOwner().size(),
            lastBuildMillis));
    }

    // One chosen colour for every owner, optionally spread in brightness so neighbours can
    // still be told apart. Brightness rather than hue on purpose: a hue jitter makes each
    // owner look like a different faction, which is what the shipped palette means, while a
    // brightness jitter reads as one thing seen in several places.
    private Color resolveOwnedColour(String ownerId) {
        return jitterOwned
            ? jitterBrightness(ownedCellColour, ownerId.hashCode(), jitterStrength)
            : ownedCellColour;
    }

    private static Color jitterBrightness(Color base, int seed, float strength) {

        var hsb = Color.RGBtoHSB(base.getRed(), base.getGreen(), base.getBlue(), null);

        // Hashes cluster in their low bits, so the spread is taken from a well-mixed value
        // rather than from the seed itself - otherwise consecutive ids come out identical.
        var mixed = Math.floorMod(Integer.reverse(seed * 0x9E3779B9), HUE_RANGE) / (float) HUE_RANGE;
        var brightness = Math.max(0f, Math.min(
            1f,
            hsb[2] + (mixed - 0.5f) * (float) (strength / JITTER_SCALE)));

        return Color.getHSBColor(hsb[0], hsb[1], brightness);
    }

    // The one way anything filled is drawn here: a translucent body under an opaque outline.
    // Shared rather than repeated per layer so an owner's cell, an unowned cell and the
    // partition underneath read as the same kind of thing in different colours - which is the
    // only reason it is possible to tell at a glance which of them a shape belongs to.
    private static void paintFilledShape(
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

    private static Color applyAlpha(Color colour, int alpha) {
        return new Color(
            colour.getRed(),
            colour.getGreen(),
            colour.getBlue(),
            alpha);
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
        private final JLabel cursorBar = new JLabel(" ");
        private Point cursorPoint;
        private double scale;
        private double offsetX;
        private double offsetY;
        private Point2D dragAnchor;

        private MapCanvas() {

            setBackground(BACKGROUND);
            cursorBar.setBorder(BorderFactory.createEmptyBorder(
                    READOUT_PADDING, READOUT_PADDING, READOUT_PADDING, READOUT_PADDING));
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
                    cursorBar.setText(describeCursor());

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

            var g2 = (Graphics2D) g.create();

            g2.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            if (scale == 0) {
                fitToSector();
            }

            // World y grows upward and screen y downward, so the drawing is flipped once here
            // rather than at every vertex - a sector drawn upside down would read as a bug.
            g2.translate(offsetX, offsetY);
            g2.scale(scale, -scale);

            paintGeometry(g2);

            g2.dispose();

            paintCursorReadout((Graphics2D) g);
        }

        private void trackCursor(Point point) {

            var previous = cursorPoint;

            cursorPoint = point;
            cursorBar.setText(describeCursor());

            if (previous != null) {
                repaintAround(previous);
            }
            if (point != null) {
                repaintAround(point);
            }
        }

        private void repaintAround(Point point) {
            repaint(
                point.x - READOUT_BOX_WIDTH,
                point.y - READOUT_BOX_HEIGHT,
                READOUT_BOX_WIDTH * 2,
                READOUT_BOX_HEIGHT * 2);
        }

        // In screen space, on purpose: the world transform is scaled and y-flipped, so text
        // drawn through it would come out mirrored and sized by the zoom.
        private void paintCursorReadout(Graphics2D g2) {

            if (cursorPoint == null || scale == 0) {
                return;
            }

            var text = describeCursor();
            var metrics = g2.getFontMetrics();
            var width = metrics.stringWidth(text);
            var x = cursorPoint.x + READOUT_OFFSET_X;
            var y = cursorPoint.y + READOUT_OFFSET_Y;

            // Flipped to the near side at the canvas edge, so the label stays readable
            // rather than running off where the pointer is most likely to be.
            if (x + width + READOUT_PADDING > getWidth()) {
                x = cursorPoint.x - READOUT_OFFSET_X - width;
            }

            if (y + READOUT_PADDING > getHeight()) {
                y = cursorPoint.y - READOUT_OFFSET_Y;
            }

            g2.setColor(READOUT_BACKDROP);
            g2.fillRect(
                x - READOUT_PADDING,
                y - metrics.getAscent() - READOUT_PADDING,
                width + 2 * READOUT_PADDING,
                metrics.getHeight() + 2 * READOUT_PADDING);
            g2.setColor(READOUT_TEXT);
            g2.drawString(text, x, y);
        }

        private String describeCursor() {

            if (cursorPoint == null || scale == 0) {
                return " ";
            }
            return String.format(
                Locale.ROOT,
                "%.0f, %.0f",
                (cursorPoint.x - offsetX) / scale,
                (offsetY - cursorPoint.y) / scale);
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

            for (var cell : unboundedCells) {
                paintFilledShape(
                    g2,
                    buildPath(cell),
                    unboundedCellColour,
                    unboundedCellOpacity,
                    unboundedCellEdge);
            }

            // Split on the threshold at paint time rather than at rebuild, so moving the
            // slider recolours without recomputing any geometry.
            var wideEnough = voidSpanMultiple * parameters.cellRadius();

            for (var pocket : voidPockets) {

                var isWide = pocket.span() > wideEnough;

                // Nothing to draw means the channel closed the pocket over, and drawing it
                // flush against the cells instead would break the one rule every other shape
                // here keeps. The mark says it is there without claiming an extent it has
                // not got.
                if (pocket.outlines().isEmpty()) {

                    paintVoidMark(
                        g2,
                        pocket.centre(),
                        isWide ? wideVoidColour : voidCellColour);

                    continue;
                }

                for (var outline : pocket.outlines()) {

                    var path = buildPath(outline);

                    if (pocket.absorbingOwner() != null) {

                        var fill = resolveOwnedColour(pocket.absorbingOwner());
                        paintFilledShape(g2, path, fill, ownedCellOpacity, fill);
                        continue;
                    }
                    paintFilledShape(
                        g2,
                        path,
                        isWide ? wideVoidColour : voidCellColour,
                        voidCellOpacity,
                        isWide ? wideVoidEdge : voidCellEdge);
                }
            }
            // The channel is the ring a cell leaves between its true edge and its inset
            // fill, so painting the whole true cell and letting the fill cover the middle
            // leaves exactly that ring showing - no second shape to build, and it cannot
            // disagree with where the fill actually stops.
            //
            // Filled only. The outline of this shape is the shared cell boundary, which is
            // the centreline rather than the channel's own border; the border is the inset
            // contour, stroked below once the fills are down.
            for (var cell : geometry.cellEdgesByCellId().values()) {

                g2.setColor(applyAlpha(channelColour, channelOpacity));
                g2.fill(buildPath(toRing(cell)));
            }

            g2.setStroke(new BasicStroke(RING_STROKE));

            // Unowned per the geometry's own keys, not the fixture's: a cell the build
            // grouped or unowned must be drawn as the build left it.
            for (var entry : geometry.shapedCellByCellId().entrySet()) {

                if (geometry.ownerByCellId().containsKey(entry.getKey())
                        || entry.getValue().fillPolygon().size()
                            < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                    continue;
                }
                paintFilledShape(
                    g2,
                    buildPath(entry.getValue().fillPolygon()),
                    jitterUnowned
                        ? jitterBrightness(unownedCellColour,
                            entry.getKey().hashCode(),
                            jitterStrength)
                        : unownedCellColour,
                    unownedCellOpacity,
                    unownedCellEdge);
            }
            // One path per owner, filled even-odd, so a ring wound against the rest cuts a hole in
            // it - an enclave - instead of painting over it solid. Filling each ring on its own
            // paints an enclave as another island of the owner's colour, which is the opposite of
            // what it means.
            for (var entry : geometry.ringsByOwner().entrySet()) {

                var cluster = new Path2D.Double(Path2D.WIND_EVEN_ODD);

                for (var ring : entry.getValue()) {
                    cluster.append(buildPath(ring), false);
                }
                paintFilledShape(
                    g2,
                    cluster,
                    resolveOwnedColour(entry.getKey()),
                    ownedCellOpacity,
                    ownedCellEdge);
            }

            // Both passes go after the fills, not before. A cluster fuses its cells into
            // one fill, so a line drawn first is painted over by the very shape it divides.
            g2.setStroke(new BasicStroke(CELL_STROKE));

            paintFillContours(g2);
            paintCentrelines(g2);

            g2.setColor(siteColour);

            for (var site : fixture.getSites()) {

                g2.fill(new java.awt.geom.Ellipse2D.Double(
                    site[0] - SITE_RADIUS,
                    site[1] - SITE_RADIUS,
                    SITE_RADIUS * 2,
                    SITE_RADIUS * 2));
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
        // A diamond rather than a disc, so a pocket that could not be drawn is not mistaken
        // for one of the round site dots at a glance.
        private void paintVoidMark(Graphics2D g2, double[] centre, Color colour) {

            var mark = new Path2D.Double();

            mark.moveTo(centre[0], centre[1] + VOID_MARK_RADIUS);
            mark.lineTo(centre[0] + VOID_MARK_RADIUS, centre[1]);
            mark.lineTo(centre[0], centre[1] - VOID_MARK_RADIUS);
            mark.lineTo(centre[0] - VOID_MARK_RADIUS, centre[1]);
            mark.closePath();

            g2.setColor(applyAlpha(colour, voidCellOpacity));
            g2.fill(mark);
        }

        private void paintFillContours(Graphics2D g2) {

            for (var entry : geometry.shapedCellByCellId().entrySet()) {

                var shaped = entry.getValue();
                var trueEdges = geometry.cellEdgesByCellId().get(entry.getKey());

                if (trueEdges == null
                        || shaped.fillPolygon().size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                    continue;
                }

                var cellEdge = geometry.ownerByCellId().containsKey(entry.getKey())
                    ? ownedCellEdge
                    : unownedCellEdge;

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

                    g2.setColor(applyAlpha(
                        facesAnotherCell(trueEdges, from, to)
                            ? channelEdge
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

            g2.setColor(applyAlpha(centrelineColour, OPAQUE_ALPHA));

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

        private static boolean facesAnotherCell(
                List<CellEdge> trueEdges,
                double[] from,
                double[] to) {

            var nearest = CellEdges.findNearestEdge(
                trueEdges,
                (from[0] + to[0]) / 2.0,
                (from[1] + to[1]) / 2.0);

            return nearest != null && nearest.target() instanceof EdgeTarget.AcrossSystem;
        }

        private void fitToSector() {

            var bounds = SiteBounds.measureAround(fixture.getSites());

            var span = bounds.measureWidestSpan() * INITIAL_MARGIN;

            scale = Math.min(getWidth(), getHeight()) / span;
            offsetX = getWidth() / 2.0 - bounds.findCentreX() * scale;
            offsetY = getHeight() / 2.0 + bounds.findCentreY() * scale;
        }

        private List<double[]> toRing(List<CellEdge> edges) {
            return edges.stream()
                .map(edge -> new double[] {edge.x1(), edge.y1()})
                .toList();
        }
    }
}
