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

    // What each void switch is remembered under. Named here rather than written at the two
    // places each of them appears - once as the switch, once in whichever roll-ups cover it -
    // because a roll-up that named a key the switch does not would cover nothing, silently.
    private static final String INLAND_BRIDGES = "Inland void bridges";
    private static final String INLAND_FILL = "Inland void fill";
    private static final String INLAND_NAMES = "Inland void names";
    private static final String COASTLINE = "Coastline";
    private static final String COASTAL_FILL = "Coastal void fill";
    private static final String COASTAL_NAMES = "Coastal void names";

    // How far apart two cells may sit and still be taken to hold the void between them, in
    // cell radii from centre to centre. Four is the width at which a whole further cell
    // would fit in the gap, which is the point past which the void between two cells stops
    // being theirs.
    private static final double BRIDGE_REACH_MINIMUM = 2;

    private static final double BRIDGE_REACH_MAXIMUM = 10;

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

        // Zoom in to read the names: one is only drawn once its region is wide enough on
        // screen to hold it, so at the zoom the map opens on none of them appear.
        controls.add(ViewerControls.buildToggle(
            "Show cell names",
            "Cell names",
            false,
            on -> settings.showCellNames = on,
            refreshes::repaintMap));

        controls.add(ViewerControls.buildColour(
            "Region names",
            "Region names",
            ViewerSettings.REGION_NAME_DEFAULT,
            colour -> settings.regionNameColour = colour,
            refreshes::repaintMap));

        controls.add(ViewerControls.buildDivider());

        addVoidPocketRows(controls);

        return controls;
    }

    // Everything about the void, under one rule and in the order the toggles above it read:
    // what to show, then the inland knobs, then the coastal ones, then the one knob both
    // kinds share. Below a divider because the rest of the panel is about CELLS, and a reader
    // hunting for a void knob was otherwise reading forty identical rows to find it.
    private void addVoidPocketRows(JPanel controls) {

        controls.add(buildVoidPocketToggles());

        // Keyed as bridges rather than as cuts, which is what this swatch has actually
        // coloured all along. A saved value under the old key belongs to the line it was
        // chosen for, and letting it carry over would silently colour the cuts with it.
        controls.add(ViewerControls.buildColour(
            "Void bridges",
            "Void bridges",
            ViewerSettings.VOID_BRIDGE_DEFAULT,
            colour -> settings.voidBridgeColour = colour,
            refreshes::repaintMap));

        // Stepped in hundredths, so the reach can be moved by a fraction of a cell radius
        // rather than jumping a whole one at a time.
        //
        // Recomputed rather than merely repainted, unlike every other knob down here, because
        // this one decides where the walls go, and a wall is what shuts one piece of void off
        // from the next.
        controls.add(ViewerControls.buildSlider(
            "Bridge reach multiple",
            "Bridge reach, in cell radii (x100)",
            BRIDGE_REACH_MINIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
            BRIDGE_REACH_MAXIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
            ViewerSettings.BRIDGE_REACH_DEFAULT * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
            multiple -> settings.bridgeReachMultiple = multiple / ViewerSettings.BRIDGE_REACH_STEP_SCALE,
            refreshes::refreshVoidBridges,
            () -> { }));

        controls.add(ViewerControls.buildColourPair(
            "Inland void fill",
            "Inland void fill",
            ViewerSettings.INLAND_VOID_DEFAULT, ViewerSettings.INLAND_VOID_DEFAULT,
            colour -> settings.inlandVoidColour = colour,
            colour -> settings.inlandVoidEdge = colour,
            refreshes::repaintMap));

        controls.add(ViewerControls.buildColour(
            "Smoothed outer edge",
            "Smoothed outer edge",
            ViewerSettings.COASTLINE_DEFAULT,
            colour -> settings.coastlineColour = colour,
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

        // Last because it is the one knob that reaches both kinds, so it belongs to neither
        // group above it.
        controls.add(buildOpacitySlider(
            "Void fill opacity",
            opacity -> settings.voidFillOpacity = (int) opacity));
    }

    // Everything there is to see of the void, under one head.
    //
    // Two kinds of pocket, three things to see of each, and two roll-ups cutting the other way
    // for the questions that are about a KIND OF THING rather than about a kind of pocket -
    // "show me every wall", "take every name off". Those two cross the branches on purpose:
    // a bridge and a reach of coast are the same kind of proposal seen in two places, and
    // comparing them means having them under one switch.
    //
    // Rebuilt rather than repainted, because both overlays are built only while something of
    // theirs is on screen - so turning one on is what makes it exist, not merely what shows it.
    private JPanel buildVoidPocketToggles() {

        return ViewerToggleTree.buildToggleTree(
            () -> {
                refreshes.refreshVoidBridges();
                refreshes.refreshCoastlines();
            },
            ViewerToggleTree.Row.ofRollUp(
                0,
                "Void pockets",
                INLAND_BRIDGES, INLAND_FILL, INLAND_NAMES,
                COASTLINE, COASTAL_FILL, COASTAL_NAMES),
            ViewerToggleTree.Row.ofRollUp(
                1, "Inland void pockets", INLAND_BRIDGES, INLAND_FILL, INLAND_NAMES),
            ViewerToggleTree.Row.ofSwitch(2, new ViewerToggleTree.Switch(
                INLAND_BRIDGES, "Bridges", true, on -> settings.showInlandBridges = on)),
            ViewerToggleTree.Row.ofSwitch(2, new ViewerToggleTree.Switch(
                INLAND_FILL, "Fill", true, on -> settings.showInlandFill = on)),
            ViewerToggleTree.Row.ofSwitch(2, new ViewerToggleTree.Switch(
                INLAND_NAMES, "Pocket names", false, on -> settings.showInlandNames = on)),
            ViewerToggleTree.Row.ofRollUp(
                1, "Coastal void pockets", COASTLINE, COASTAL_FILL, COASTAL_NAMES),
            ViewerToggleTree.Row.ofSwitch(2, new ViewerToggleTree.Switch(
                COASTLINE, "Coastline", true, on -> settings.showCoastline = on)),
            ViewerToggleTree.Row.ofSwitch(2, new ViewerToggleTree.Switch(
                COASTAL_FILL, "Fill", true, on -> settings.showCoastalFill = on)),
            ViewerToggleTree.Row.ofSwitch(2, new ViewerToggleTree.Switch(
                COASTAL_NAMES, "Pocket names", false, on -> settings.showCoastalNames = on)),
            ViewerToggleTree.Row.ofRollUp(1, "Pocket borders", INLAND_BRIDGES, COASTLINE),
            ViewerToggleTree.Row.ofRollUp(1, "Pocket names", INLAND_NAMES, COASTAL_NAMES));
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
