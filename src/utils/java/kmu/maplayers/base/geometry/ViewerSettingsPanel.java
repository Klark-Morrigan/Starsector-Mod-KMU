package kmu.maplayers.base.geometry;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;

/**
 * The rows that edit {@link ViewerSettings}, in the order they are read down the panel.
 *
 * <p>Separate from the settings themselves because the two change for different reasons: a
 * new thing to draw adds a field there, while moving a knob, relabelling it or changing what
 * it costs to turn changes only this. Held together they made one class where the list of
 * what the map can be drawn with was buried in the wiring that chooses it.
 *
 * <p>Each row states its own cost by which refresh it is wired to - a colour asks for a
 * repaint, a void knob for its own layer, a geometry knob for the whole rebuild. That is the
 * one thing worth checking when a row is added, so it is the one thing written at the point
 * the row is declared.
 *
 * <p>The ranges are here rather than with the settings: how far a slider may travel is a
 * property of the widget, where the value it starts at is a property of the setting.
 */
final class ViewerSettingsPanel {

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

    // A bound pocket smaller than this share of a normal cell is one void cell rather than
    // something to divide. Measured against a whole cell's area because that is the unit the
    // map is already read in - "half a system's worth of gap" means something on sight, where
    // a number of square units does not.
    private static final double VOID_SPAN_MINIMUM = 0;

    private static final double VOID_SPAN_MAXIMUM = 6;

    // How much of a section a cut has to leave on either side of it, as a percentage. The
    // knob that decides how evenly a pocket comes out divided, and the one worth sweeping:
    // the two ends of its range are two different wrong answers - slivers shaved off the
    // tips at the bottom, chords thrown across open void at the top - and where the good
    // answers sit between them is a question about a shape rather than about a number.
    // How far apart two cells may sit and still be taken to hold the void between them, in
    // cell radii from centre to centre. Four is the width at which a whole further cell
    // would fit in the gap, which is the point past which the void between two cells stops
    // being theirs.
    private static final double BRIDGE_REACH_MINIMUM = 2;

    private static final double BRIDGE_REACH_MAXIMUM = 10;

    private static final double MIN_SECTION_MINIMUM = 0;

    private static final double MIN_SECTION_MAXIMUM = 100;

    // How near the last kept point a cell's frontage has to be before it is dropped from the
    // smoothed edge, in cell radii. Zero keeps every cell and reproduces the scallop exactly,
    // which is the useful bottom end of the range: it is what the smoothing is judged
    // against. The top is wide enough to cut a coast down to its corners.
    private static final double COAST_SKIP_MINIMUM = 0;

    private static final double COAST_SKIP_MAXIMUM = 4;

    // How many cells may be dropped in a row. At zero nothing is skipped whatever the
    // distance; the ceiling is past the point where a dense coast collapses to a few points,
    // so what that failure looks like is reachable rather than merely describable.
    private static final double COAST_MAX_SKIPS_MINIMUM = 0;

    private static final double COAST_MAX_SKIPS_MAXIMUM = 20;

    // How far brightness may wander either side of the chosen colour when jitter is on, as a
    // percentage of the full range. The default is wide enough to tell two neighbours apart
    // and narrow enough that they still read as one palette; the slider exists because which
    // of those matters depends on what is being looked for.
    private static final double JITTER_MINIMUM = 0;

    private static final double JITTER_MAXIMUM = 100;

    // Alpha runs the full byte, so a fill can be turned off entirely or made solid without
    // touching the colour it was chosen as.
    private static final double OPACITY_MINIMUM = 0;

    private static final double OPACITY_MAXIMUM = 255;

    private static final int PANEL_PADDING = 8;

    private final ViewerSettings settings;
    private final ViewerRefreshes refreshes;

    ViewerSettingsPanel(ViewerSettings settings, ViewerRefreshes refreshes) {
        this.settings = settings;
        this.refreshes = refreshes;
    }

    // Held rather than passed to each row: every knob needs it, and threading it through the
    // three wrappers below would put the same argument on every call in the panel.




    JPanel buildRows() {

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
            settings.parameters.cellRadius(),
            value -> settings.parameters = new SectorGeometryParameters(
                value,
                settings.parameters.boundSegments(),
                settings.parameters.borderInset(),
                settings.parameters.weldTolerance(),
                settings.parameters.miterSpikeLimit())));

        controls.add(buildSlider(
            "Border channel (inset)",
            INSET_MINIMUM,
            INSET_MAXIMUM,
            settings.parameters.borderInset(),
            value -> settings.parameters = new SectorGeometryParameters(
                settings.parameters.cellRadius(),
                settings.parameters.boundSegments(),
                value,
                settings.parameters.weldTolerance(),
                settings.parameters.miterSpikeLimit())));

        controls.add(buildSlider(
            "Weld tolerance",
            WELD_MINIMUM,
            WELD_MAXIMUM,
            settings.parameters.weldTolerance(),
            value -> settings.parameters = new SectorGeometryParameters(
                settings.parameters.cellRadius(),
                settings.parameters.boundSegments(),
                settings.parameters.borderInset(),
                value,
                settings.parameters.miterSpikeLimit())));

        controls.add(buildSlider(
            "Miter spike limit",
            MITER_MINIMUM,
            MITER_MAXIMUM,
            settings.parameters.miterSpikeLimit(),
            value -> settings.parameters = new SectorGeometryParameters(
                settings.parameters.cellRadius(),
                settings.parameters.boundSegments(),
                settings.parameters.borderInset(),
                settings.parameters.weldTolerance(),
                value)));

        controls.add(buildSlider(
            "Cell bound segments",
            SEGMENTS_MINIMUM,
            SEGMENTS_MAXIMUM,
            settings.parameters.boundSegments(),
            value -> settings.parameters = new SectorGeometryParameters(
                settings.parameters.cellRadius(),
                (int) Math.round(value),
                settings.parameters.borderInset(),
                settings.parameters.weldTolerance(),
                settings.parameters.miterSpikeLimit())));

        controls.add(ViewerControls.buildColourPair(
            "Owned cells",
            "Owned cells",
            ViewerSettings.OWNED_CELL_DEFAULT,
            ViewerSettings.OWNED_CELL_DEFAULT,
            colour -> settings.ownedCellColour = colour,
            colour -> settings.ownedCellEdge = colour,
            refreshes::repaintMap));

        controls.add(buildOpacitySlider(
            "Owned opacity",
            opacity -> settings.ownedCellOpacity = (int) opacity));

        controls.add(ViewerControls.buildColourPair(
            "Unowned cells",
            "Unowned cells",
            ViewerSettings.UNOWNED_CELL_DEFAULT,
            ViewerSettings.UNOWNED_CELL_DEFAULT,
            colour -> settings.unownedCellColour = colour,
            colour -> settings.unownedCellEdge = colour,
            refreshes::repaintMap));

        controls.add(buildOpacitySlider(
            "Unowned opacity",
            opacity -> settings.unownedCellOpacity = (int) opacity));

        controls.add(buildToggle(
            "Trace unbounded cells",
            false,
            on -> {
                settings.showUnboundedCells = on;
                refreshes.refreshUnboundedCells();
                refreshes.refreshVoidBridges();
            }));

        controls.add(ViewerControls.buildColourPair(
            "Unbounded cells",
            "Unbounded cells",
            ViewerSettings.UNBOUNDED_CELL_DEFAULT,
            ViewerSettings.UNBOUNDED_CELL_DEFAULT,
            colour -> settings.unboundedCellColour = colour,
            colour -> settings.unboundedCellEdge = colour,
            refreshes::repaintMap));

        controls.add(buildOpacitySlider(
            "Unbounded opacity",
            opacity -> settings.unboundedCellOpacity = (int) opacity));

        controls.add(ViewerControls.buildToggleRow(
            refreshes::repaintMap,
            new ViewerControls.Toggle(
                "Jitter owned",
                "Jitter owned",
                true,
                on -> settings.jitterOwned = on),
            new ViewerControls.Toggle(
                "Jitter unowned",
                "Jitter unowned",
                false,
                on -> settings.jitterUnowned = on)));

        controls.add(buildSlider(
            "Jitter strength",
            JITTER_MINIMUM,
            JITTER_MAXIMUM,
            ViewerSettings.JITTER_DEFAULT,
            strength -> settings.jitterStrength = (float) (strength / ViewerSettings.JITTER_SCALE)));

        controls.add(ViewerControls.buildColourPair(
            "Void cells",
            "Void within a cell",
            ViewerSettings.VOID_CELL_DEFAULT,
            ViewerSettings.VOID_CELL_DEFAULT,
            colour -> settings.voidCellColour = colour,
            colour -> settings.voidCellEdge = colour,
            refreshes::repaintMap));

        controls.add(ViewerControls.buildToggle(
            "Show void bridges",
            "Void pockets",
            true,
            on -> settings.showVoidBridges = on,
            refreshes::refreshVoidBridges));

        // Redoes both constructions over the void, because each answers to it and a frame with
        // one of them moved is a map neither of them describes.
        controls.add(ViewerControls.buildToggle(
            "Draw void at its true extent",
            "Void at its true extent (no channel)",
            false,
            on -> settings.showPocketsAtTrueExtent = on,
            () -> {
                refreshes.refreshVoidBridges();
                refreshes.refreshCoastlines();
            }));

        controls.add(ViewerControls.buildColourPair(
            "Wide void",
            "Void wider than that",
            ViewerSettings.WIDE_VOID_DEFAULT, ViewerSettings.WIDE_VOID_DEFAULT,
            colour -> settings.wideVoidColour = colour,
            colour -> settings.wideVoidEdge = colour,
            refreshes::repaintMap));

        controls.add(ViewerControls.buildColour(
            "Site dots",
            "Site dots",
            ViewerSettings.SITE_COLOUR,
            colour -> settings.siteColour = colour,
            refreshes::repaintMap));

        controls.add(ViewerControls.buildColour(
            "Cell centrelines",
            "Cell centrelines",
            ViewerSettings.CENTRELINE_DEFAULT,
            colour -> settings.centrelineColour = colour,
            refreshes::repaintMap));

        controls.add(ViewerControls.buildColourPair(
            "Inset channels",
            "Inset channels",
            ViewerSettings.CHANNEL_DEFAULT,
            ViewerSettings.CHANNEL_DEFAULT,
            colour -> settings.channelColour = colour,
            colour -> settings.channelEdge = colour,
            refreshes::repaintMap));

        controls.add(buildOpacitySlider(
            "Channel opacity",
            opacity -> settings.channelOpacity = (int) opacity));

        controls.add(buildOpacitySlider(
            "Void cell opacity",
            opacity -> settings.voidCellOpacity = (int) opacity));

        controls.add(ViewerControls.buildColour(
            "Void section cuts",
            "Void section cuts",
            ViewerSettings.SECTION_CUT_DEFAULT,
            colour -> settings.sectionCutColour = colour,
            refreshes::repaintMap));

        // Stepped in hundredths, so the threshold can be moved by a fraction of a cell
        // radius rather than jumping a whole one at a time.
        //
        // Recomputed rather than merely repainted, unlike every other knob down here, because
        // this one no longer only decides a colour: the same length is what a pocket is cut
        // into sections of, and those are geometry.
        controls.add(ViewerControls.buildSlider(
            "Void span multiple",
            "Void span, in cell radii (x100)",
            VOID_SPAN_MINIMUM * ViewerSettings.VOID_SPAN_STEP_SCALE,
            VOID_SPAN_MAXIMUM * ViewerSettings.VOID_SPAN_STEP_SCALE,
            ViewerSettings.VOID_SPAN_DEFAULT * ViewerSettings.VOID_SPAN_STEP_SCALE,
            multiple -> settings.voidSpanMultiple = multiple / ViewerSettings.VOID_SPAN_STEP_SCALE,
            refreshes::refreshVoidBridges,
            () -> { }));

        controls.add(ViewerControls.buildSlider(
            "Bridge reach multiple",
            "Bridge reach, in cell radii (x100)",
            BRIDGE_REACH_MINIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
            BRIDGE_REACH_MAXIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
            ViewerSettings.BRIDGE_REACH_DEFAULT * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
            multiple -> settings.bridgeReachMultiple = multiple / ViewerSettings.BRIDGE_REACH_STEP_SCALE,
            refreshes::refreshVoidBridges,
            () -> { }));

        controls.add(ViewerControls.buildSlider(
            "Min section share",
            "Least a cut leaves, as % of a section",
            MIN_SECTION_MINIMUM,
            MIN_SECTION_MAXIMUM,
            ViewerSettings.MIN_SECTION_DEFAULT,
            share -> settings.minSectionShare = share / ViewerSettings.MIN_SECTION_SCALE,
            refreshes::refreshVoidBridges,
            () -> { }));

        controls.add(ViewerControls.buildToggle(
            "Show coastlines",
            "Smoothed outer edge",
            true,
            on -> settings.showCoastlines = on,
            refreshes::refreshCoastlines));

        controls.add(ViewerControls.buildColour(
            "Smoothed outer edge",
            "Smoothed outer edge",
            ViewerSettings.COASTLINE_DEFAULT,
            colour -> settings.coastlineColour = colour,
            refreshes::repaintMap));

        // A pair rather than one, because the two say different halves of the same thing:
        // which run went where it should not, and which cell it went into. Nothing is drawn
        // in either once the construction stops crossing anything.
        controls.add(ViewerControls.buildColourPair(
            "Coast crossings",
            "Coast crossing / crossed cell",
            ViewerSettings.COAST_CROSSING_DEFAULT,
            ViewerSettings.PIERCED_CELL_DEFAULT,
            colour -> settings.coastCrossingColour = colour,
            colour -> settings.piercedCellColour = colour,
            refreshes::repaintMap));

        controls.add(ViewerControls.buildSlider(
            "Coast skip distance",
            "Coast skip distance, in cell radii (x100)",
            COAST_SKIP_MINIMUM * ViewerSettings.COAST_SKIP_STEP_SCALE,
            COAST_SKIP_MAXIMUM * ViewerSettings.COAST_SKIP_STEP_SCALE,
            ViewerSettings.COAST_SKIP_DEFAULT * ViewerSettings.COAST_SKIP_STEP_SCALE,
            multiple -> settings.coastSkipMultiple =
                multiple / ViewerSettings.COAST_SKIP_STEP_SCALE,
            refreshes::refreshCoastlines,
            () -> { }));

        controls.add(ViewerControls.buildSlider(
            "Coast max skips",
            "Most cells skipped in a row",
            COAST_MAX_SKIPS_MINIMUM,
            COAST_MAX_SKIPS_MAXIMUM,
            ViewerSettings.COAST_MAX_SKIPS_DEFAULT,
            skips -> settings.coastMaxSkips = (int) Math.round(skips),
            refreshes::refreshCoastlines,
            () -> { }));

        return controls;
    }

    // Every geometry knob rebuilds; that is what makes it a geometry knob rather than a
    // colour. Bound once here so a new one cannot be added that quietly only repaints.
    //
    // The title doubles as the key it is remembered under. A knob's label is the one thing
    // about it already unique and already meaningful, so keying on it means a knob cannot be
    // added without being remembered - which is how the last panel ended up with several
    // that were not.
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
            refreshes::rebuildGeometry,
            () -> { });
    }

    private JPanel buildOpacitySlider(String title, DoubleConsumer apply) {
        return buildSlider(title, OPACITY_MINIMUM, OPACITY_MAXIMUM, ViewerSettings.OWNER_FILL_ALPHA, apply);
    }

    private JPanel buildToggle(String title, boolean initial, Consumer<Boolean> apply) {
        return ViewerControls.buildToggle(
            title, title, initial, apply, refreshes::rebuildGeometry);
    }
}
