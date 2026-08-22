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

    // How little of its own border a cell may face the void with and still be walked through,
    // as a percentage of the whole turn. Zero is the bottom because it asks nothing, which is
    // the rule switched off and what every other knob is judged against.
    //
    // The ceiling sits at about the median stretch: half of what a sector offers is narrower
    // than a quarter turn, so at the top of this range half the coast is dropped outright and
    // what that costs is on screen rather than merely describable. The useful band is the
    // bottom fifth - a sliver worth losing is a few percent.
    private static final double MIN_FRONTAGE_MINIMUM = 0;

    private static final double MIN_FRONTAGE_MAXIMUM = 25;

    // How far off a wall already down a span may run and still count as running along it, in
    // map units. Zero asks for lines that coincide exactly, which catches only the spans that
    // repeat a wall end for end. The ceiling is several stroke widths - past that the rule is
    // discarding spans that are visibly clear of anything and merely heading the same way.
    private static final double COAST_SLACK_MINIMUM = 0;

    private static final double COAST_SLACK_MAXIMUM = 500;

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

    // Held rather than passed to each row: every knob writes one and refreshes through the
    // other, so threading them through would put the same two arguments on every call in the
    // panel.
    private final ViewerSettings settings;
    private final ViewerRefreshes refreshes;

    ViewerSettingsPanel(ViewerSettings settings, ViewerRefreshes refreshes) {
        this.settings = settings;
        this.refreshes = refreshes;
    }

    JPanel buildRows() {

        var controls = new JPanel();

        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(
            PANEL_PADDING,
            PANEL_PADDING,
            PANEL_PADDING,
            PANEL_PADDING));

        addCellGeometryRows(controls);
        addCellPaintRows(controls);
        addMapChromeRows(controls);

        controls.add(ViewerControls.buildDivider());

        addVoidPocketRows(controls);

        controls.add(ViewerControls.buildDivider());

        addVoidPocketV3Rows(controls);

        return controls;
    }

    // The five knobs that decide what shape the cells are. Every one of them rebuilds the
    // partition, which is what makes them geometry rather than paint.
    private void addCellGeometryRows(JPanel controls) {

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
    }

    // What the cells are painted with, and how solidly.
    private void addCellPaintRows(JPanel controls) {

        controls.add(ViewerSwatches.buildColourPair(
            "Owned cells",
            "Owned cells",
            new ViewerSwatches.Choice(
                ViewerSettings.OWNED_CELL_DEFAULT,
                colour -> settings.ownedCellColour = colour),
            new ViewerSwatches.Choice(
                ViewerSettings.OWNED_CELL_DEFAULT,
                colour -> settings.ownedCellEdge = colour),
            refreshes::repaintMap));

        controls.add(buildOpacitySlider(
            "Owned opacity",
            opacity -> settings.ownedCellOpacity = (int) opacity));

        controls.add(ViewerSwatches.buildColourPair(
            "Unowned cells",
            "Unowned cells",
            new ViewerSwatches.Choice(
                ViewerSettings.UNOWNED_CELL_DEFAULT,
                colour -> settings.unownedCellColour = colour),
            new ViewerSwatches.Choice(
                ViewerSettings.UNOWNED_CELL_DEFAULT,
                colour -> settings.unownedCellEdge = colour),
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

        controls.add(ViewerSwatches.buildColourPair(
            "Unbounded cells",
            "Unbounded cells",
            new ViewerSwatches.Choice(
                ViewerSettings.UNBOUNDED_CELL_DEFAULT,
                colour -> settings.unboundedCellColour = colour),
            new ViewerSwatches.Choice(
                ViewerSettings.UNBOUNDED_CELL_DEFAULT,
                colour -> settings.unboundedCellEdge = colour),
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
            strength -> settings.jitterStrength =
                (float) (strength / ViewerSettings.JITTER_SCALE)));
    }

    // The marks that are neither cell nor void: the sites, the borders the insets were measured
    // from, the channel between two fills, and the names written over any of it.
    private void addMapChromeRows(JPanel controls) {

        controls.add(ViewerSwatches.buildColour(
            "Site dots",
            "Site dots",
            ViewerSettings.SITE_COLOUR,
            colour -> settings.siteColour = colour,
            refreshes::repaintMap));

        controls.add(ViewerSwatches.buildColour(
            "Cell centrelines",
            "Cell centrelines",
            ViewerSettings.CENTRELINE_DEFAULT,
            colour -> settings.centrelineColour = colour,
            refreshes::repaintMap));

        controls.add(ViewerSwatches.buildColourPair(
            "Inset channels",
            "Inset channels",
            new ViewerSwatches.Choice(
                ViewerSettings.CHANNEL_DEFAULT,
                colour -> settings.channelColour = colour),
            new ViewerSwatches.Choice(
                ViewerSettings.CHANNEL_DEFAULT,
                colour -> settings.channelEdge = colour),
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

        controls.add(ViewerSwatches.buildColour(
            "Region names",
            "Region names",
            ViewerSettings.REGION_NAME_DEFAULT,
            colour -> settings.regionNameColour = colour,
            refreshes::repaintMap));
    }

    // Everything about the void as v2 builds it, under one rule and in the order the toggles
    // above it read: what to show, then the inland knobs, then the coastal ones, then the one
    // knob both kinds share. Below a divider because the rest of the panel is about CELLS, and
    // a reader hunting for a void knob was otherwise reading forty identical rows to find it.
    private void addVoidPocketRows(JPanel controls) {

        controls.add(buildVoidPocketToggles());

        // Keyed as bridges rather than as cuts, which is what this swatch has actually
        // coloured all along. A saved value under the old key belongs to the line it was
        // chosen for, and letting it carry over would silently colour the cuts with it.
        controls.add(ViewerSwatches.buildColour(
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
        controls.add(ViewerSliders.buildSlider(
            "Bridge reach multiple",
            "Bridge reach, in cell radii (x100)",
            new ViewerSliders.SliderRange(
                BRIDGE_REACH_MINIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
                BRIDGE_REACH_MAXIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
                ViewerSettings.BRIDGE_REACH_DEFAULT * ViewerSettings.BRIDGE_REACH_STEP_SCALE),
            new ViewerSliders.SliderWork(
                multiple -> settings.bridgeReachMultiple =
                    multiple / ViewerSettings.BRIDGE_REACH_STEP_SCALE,
                refreshes::refreshVoidBridges,
                () -> { })));

        controls.add(ViewerSwatches.buildColourPair(
            "Inland void fill",
            "Inland void fill",
            new ViewerSwatches.Choice(
                ViewerSettings.INLAND_VOID_DEFAULT,
                colour -> settings.inlandVoidColour = colour),
            new ViewerSwatches.Choice(
                ViewerSettings.INLAND_VOID_DEFAULT,
                colour -> settings.inlandVoidEdge = colour),
            refreshes::repaintMap));

        controls.add(ViewerSwatches.buildColour(
            "Coastline",
            "Coastline",
            ViewerSettings.COASTLINE_DEFAULT,
            colour -> settings.coastlineColour = colour,
            refreshes::repaintMap));

        // Its own colour rather than the coastline's, so the two kinds of pocket read the same
        // way: a wall colour and a fill colour each. Sharing one made the coastal fill the only
        // fill on the map painted in the colour of the line that closed it.
        controls.add(ViewerSwatches.buildColourPair(
            "Coastal void fill",
            "Coastal void fill",
            new ViewerSwatches.Choice(
                ViewerSettings.COASTAL_VOID_DEFAULT,
                colour -> settings.coastalVoidColour = colour),
            new ViewerSwatches.Choice(
                ViewerSettings.COASTAL_VOID_DEFAULT,
                colour -> settings.coastalVoidEdge = colour),
            refreshes::repaintMap));

        // The whole of how the settled coast is smoothed: a stretch is dropped for what it
        // offers on its own, so what goes does not depend on which cells came before it.
        controls.add(ViewerSliders.buildSlider(
            "Coast least frontage",
            "Least frontage faced, in % of a cell",
            new ViewerSliders.SliderRange(
                MIN_FRONTAGE_MINIMUM,
                MIN_FRONTAGE_MAXIMUM,
                ViewerSettings.COAST_MIN_FRONTAGE_DEFAULT
                    * ViewerSettings.FRONTAGE_PERCENT_SCALE),
            new ViewerSliders.SliderWork(
                percent -> settings.coastMinFrontageShare =
                    percent / ViewerSettings.FRONTAGE_PERCENT_SCALE,
                refreshes::refreshCoastlines,
                () -> { })));

        // What the smoothing left out, on whichever coasts are drawn. A diagnostic rather
        // than a layer: it answers "what did the rules take" - the one thing a finished coast
        // cannot be asked, since a stretch a rule threw away and one the walk never offered
        // are both simply missing from the line.
        //
        // One row for both constructions, and here rather than in either section, because
        // the question is the same of each and the answer is told apart by the coast each
        // mark sits beside.
        controls.add(ViewerControls.buildToggle(
            "Dropped stretches",
            "Dropped stretches",
            false,
            on -> settings.showDroppedStretches = on,
            refreshes::repaintMap));

        controls.add(ViewerSwatches.buildColour(
            "Dropped stretches colour",
            "Dropped stretches",
            ViewerSettings.DROPPED_STRETCH_DEFAULT,
            colour -> settings.droppedStretchColour = colour,
            refreshes::repaintMap));

        // A pair rather than one, because the two say different halves of the same thing:
        // which run went where it should not, and which cell it went into. Nothing is drawn
        // in either once the construction stops crossing anything.
        controls.add(ViewerSwatches.buildColourPair(
            "Coast crossings",
            "Coast crossing / crossed cell",
            new ViewerSwatches.Choice(
                ViewerSettings.COAST_CROSSING_DEFAULT,
                colour -> settings.coastCrossingColour = colour),
            new ViewerSwatches.Choice(
                ViewerSettings.PIERCED_CELL_DEFAULT,
                colour -> settings.piercedCellColour = colour),
            refreshes::repaintMap));

        // Last because it is the one knob that reaches both kinds, so it belongs to neither
        // group above it.
        controls.add(buildOpacitySlider(
            "Void fill opacity",
            opacity -> settings.voidFillOpacity = (int) opacity));
    }

    // Void pockets v3: the per-continent construction, behind its own divider.
    //
    // Its own section rather than more rows among the v2 knobs because it is a SEPARATE
    // construction, not another thing to see of the settled one. Every knob down here reads
    // only v3 - which is what makes the divider honest, and what lets a reader turn one
    // without wondering whether the map above just moved.
    //
    // Grown a control at a time as v3 acquires them, in the pattern the section already
    // reads in: what to show, what to draw it with, then the knobs that decide its shape.
    private void addVoidPocketV3Rows(JPanel controls) {

        // Outside the v2 toggle tree on purpose - that tree's switches show parts of the one
        // settled construction, and a roll-up that could hide or show this too would misstate
        // what "all of it" means.
        controls.add(ViewerControls.buildToggle(
            "Continent coasts preview",
            "Continent coasts preview",
            false,
            on -> settings.showContinentCoasts = on,
            refreshes::refreshCoastlines));

        controls.add(ViewerSwatches.buildColour(
            "Continent coasts",
            "Continent coasts",
            ViewerSettings.CONTINENT_COAST_DEFAULT,
            colour -> settings.continentCoastColour = colour,
            refreshes::repaintMap));

        // Asked as a percentage because that is how anyone reading a map thinks about how far
        // a cell sticks out; what the floor means is documented at the field it writes.
        controls.add(ViewerSliders.buildSlider(
            "Continent least frontage",
            "Least frontage faced, in % of a cell",
            new ViewerSliders.SliderRange(
                MIN_FRONTAGE_MINIMUM,
                MIN_FRONTAGE_MAXIMUM,
                ViewerSettings.CONTINENT_MIN_FRONTAGE_DEFAULT
                    * ViewerSettings.FRONTAGE_PERCENT_SCALE),
            new ViewerSliders.SliderWork(
                percent -> settings.continentMinFrontageShare =
                    percent / ViewerSettings.FRONTAGE_PERCENT_SCALE,
                refreshes::refreshCoastlines,
                () -> { })));

        // Named for the inlets they close rather than for the continents they sit on, which
        // read as bridges BETWEEN continents and are nothing of the kind.
        //
        // Rebuilt rather than repainted: the coasts have to be traced for the surviving set
        // to be answerable at all, so switching this on is what makes the set exist.
        controls.add(ViewerControls.buildToggle(
            "Inlet bridges",
            "Inlet bridges",
            false,
            on -> settings.showContinentBridges = on,
            refreshes::refreshCoastlines));

        controls.add(ViewerSwatches.buildColour(
            "Inlet bridges",
            "Inlet bridges",
            ViewerSettings.CONTINENT_BRIDGE_DEFAULT,
            colour -> settings.continentBridgeColour = colour,
            refreshes::repaintMap));

        // Scaled up for a slider that deals in whole steps; what the reach means is
        // documented at the field it writes.
        controls.add(ViewerSliders.buildSlider(
            "Inlet bridge reach",
            "Bridge reach, in cell radii (x100)",
            new ViewerSliders.SliderRange(
                BRIDGE_REACH_MINIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
                BRIDGE_REACH_MAXIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
                ViewerSettings.CONTINENT_BRIDGE_REACH_DEFAULT
                    * ViewerSettings.BRIDGE_REACH_STEP_SCALE),
            new ViewerSliders.SliderWork(
                multiple -> settings.continentBridgeReachMultiple =
                    multiple / ViewerSettings.BRIDGE_REACH_STEP_SCALE,
                refreshes::refreshCoastlines,
                () -> { })));

        // In map units, so the slider needs no scaling; what the slack means is documented at
        // the field it writes.
        controls.add(ViewerSliders.buildSlider(
            "Inlet bridge coast slack",
            "Off a wall still counted as along it",
            new ViewerSliders.SliderRange(
                COAST_SLACK_MINIMUM,
                COAST_SLACK_MAXIMUM,
                ViewerSettings.CONTINENT_BRIDGE_COAST_SLACK_DEFAULT),
            new ViewerSliders.SliderWork(
                slack -> settings.continentBridgeCoastSlack = slack,
                refreshes::refreshCoastlines,
                () -> { })));
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
        return ViewerSliders.buildSlider(
            title,
            title,
            new ViewerSliders.SliderRange(minimum, maximum, initial),
            new ViewerSliders.SliderWork(apply, refreshes::rebuildGeometry, () -> { }));
    }

    private JPanel buildOpacitySlider(String title, DoubleConsumer apply) {
        return buildSlider(
            title, OPACITY_MINIMUM, OPACITY_MAXIMUM, ViewerSettings.OWNER_FILL_ALPHA, apply);
    }

    private JPanel buildToggle(String title, boolean initial, Consumer<Boolean> apply) {
        return ViewerControls.buildToggle(
            title, title, initial, apply, refreshes::rebuildGeometry);
    }
}
