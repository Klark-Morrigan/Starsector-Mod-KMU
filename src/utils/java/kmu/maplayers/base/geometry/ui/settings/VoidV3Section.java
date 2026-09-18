package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.ColourRows;
import kmu.desktop.ui.swing.ControlRows;
import kmu.desktop.ui.swing.SliderRows;
import kmu.desktop.ui.swing.ToggleTree;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

import javax.swing.JPanel;

/**
 * The v3 void construction: what it draws, what it draws it with, and what it did not draw.
 *
 * <p>By far the longest run in the panel, which is most of why it is a class. It is also the
 * one whose contents are scheduled to go: when v4 wins, this is what gets deleted, and a
 * section that is one file is deleted by deleting one file.
 */
final class VoidV3Section extends PanelSection {

    // What each void switch is remembered under. Named here rather than written at the two
    // places each of them appears - once as the switch, once in whichever roll-ups cover it -
    // because a roll-up that named a key the switch does not would cover nothing, silently.
    private static final String PUDDLE_BRIDGES = "showContinentPuddleBridges";

    private static final String PUDDLE_FILL = "showContinentPuddleFill";

    private static final String LAKE_COASTLINE = "showContinentLakeCoastline";

    private static final String LAKE_FILL = "showContinentLakeFill";

    private static final String LAKE_FRONTAGES = "showContinentLakeFrontages";

    private static final String CONTINENT_COASTLINE = "showContinentCoastline";

    private static final String CONTINENT_FILL = "showContinentCoastFill";

    private static final String CONTINENT_FRONTAGES = "showContinentCoastFrontages";

    private static final String CONTINENT_BRIDGES = "showContinentBridges";

    private static final String INLET_FILL = "showContinentInletFill";

    private static final String LAKE_BRIDGES = "showContinentLakeBridges";

    private static final String LAKE_POCKET_FILL = "showContinentLakePocketFill";

    private static final String INTERCONTINENTAL_BRIDGES = "showIntercontinentalBridges";

    private static final String INTERCONTINENTAL_FILL = "showIntercontinentalFill";

    private static final String INTERCONTINENTAL_SHORES = "showIntercontinentalShores";

    private static final String INTERCONTINENTAL_ENCLOSED_FILL = "showIntercontinentalEnclosedFill";

    // One name switch per kind of piece, under the layer that shuts that kind in.
    private static final String PUDDLE_NAMES = "showContinentPuddleNames";

    private static final String LAKE_NAMES = "showContinentLakeNames";

    private static final String LAKE_POCKET_NAMES = "showContinentLakePocketNames";

    private static final String COAST_NAMES = "showContinentCoastNames";

    private static final String INLET_NAMES = "showContinentInletNames";

    private static final String INTERCONTINENTAL_NAMES = "showIntercontinentalNames";

    // What the section remembers its switch and its folded state under. Named for the
    // construction rather than taken from the heading, which is copy and gets reworded.
    private static final CollapsibleSection.SectionKeys CONTINENT_VOID_KEYS =
        CollapsibleSection.SectionKeys.forSection("continentVoid");

    // How far apart two cells may sit and still be taken to hold the void between them, in
    // cell radii from centre to centre. Four is the width at which a whole further cell
    // would fit in the gap, which is the point past which the void between two cells stops
    // being theirs.
    private static final double BRIDGE_REACH_MINIMUM = 2;

    private static final double BRIDGE_REACH_MAXIMUM = 10;

    // How far off a wall already down a span may run and still count as running along it, in
    // map units. Zero asks for lines that coincide exactly, which catches only the spans that
    // repeat a wall end for end. The ceiling is several stroke widths - past that the rule is
    // discarding spans that are visibly clear of anything and merely heading the same way.
    private static final double COAST_SLACK_MINIMUM = 0;

    private static final double COAST_SLACK_MAXIMUM = 500;

    // How close two span feet may stand before one of them moves, in map units. Zero switches
    // the spreading off, which is what the pass reads a non-positive setting as.
    //
    // The whole of the construction's gain sits at the very bottom of this range, where merely
    // coincident feet are separated. What the rest of the range does is spread feet that are
    // already distinct, which is a legibility trade paid for in pockets - so the range runs up
    // to about a third of a cell's frontage, far enough to see what the trade looks like and
    // short of the settings where every foot ends up at the end of its stretch.
    //
    // A slider rather than a switch even so, because how much of that trade is worth making is
    // a matter of taste about the map, and taste needs somewhere to be exercised.
    private static final double ANCHOR_SEPARATION_MINIMUM = 0;

    private static final double ANCHOR_SEPARATION_MAXIMUM = 2000;

    VoidV3Section(ViewerSettings settings, ViewerRefreshes refreshes) {
        super(settings, refreshes);
    }

    @Override
    JPanel buildSection() {

        return CollapsibleSection.buildSection(
            CONTINENT_VOID_KEYS,
            "Void pockets - v3",
            new CollapsibleSection.MasterSwitch(
                true, on -> settings.showContinentVoid = on, refreshes::refreshCoastlines),
            SettingRows.buildSectionBody(this::addRows));
    }

    // In the order the construction builds in, which is the order a reader follows it: what
    // there is to see, then the coasts everything else is laid against, then the spans across
    // the inlets those coasts leave, then the links between one continent and the next - and
    // last what the whole of it did not draw.
    private void addRows(JPanel controls) {

        controls.add(buildContinentVoidToggles());

        addContinentCoastRows(controls);
        addInletBridgeRows(controls);
        addIntercontinentalBridgeRows(controls);
        addCoastDiagnosticRows(controls);
    }

    // What there is to see of the continent construction, in the order water gets smaller:
    // puddle pockets, then the two symmetric shores - interior and exterior, with the same
    // three switches each, because the two are the same kind of line seen from opposite
    // sides - then the spans laid over each of those shores, in the same order.
    //
    // The flat roll-ups after them cut across the branches on purpose: they
    // answer questions about a KIND OF THING rather than about a branch - "every wall",
    // "every fill", "every bridge" - and a roll-up is what keeps the nested switch and the
    // global answer synced, since it reads as on, mixed or off over its keys and writes all
    // of them.
    //
    // Every one rebuilds rather than repaints, because each is built only while it is wanted:
    // switching one on is what makes it exist, not merely what shows it.
    private JPanel buildContinentVoidToggles() {

        var rows = new ArrayList<ToggleTree.Row>();

        rows.add(ToggleTree.Row.ofRollUp(
            0,
            "allVoidLayers",
            "Continent void",
            PUDDLE_BRIDGES, PUDDLE_FILL, PUDDLE_NAMES,
            LAKE_COASTLINE, LAKE_FILL, LAKE_FRONTAGES, LAKE_NAMES,
            CONTINENT_COASTLINE, CONTINENT_FILL, CONTINENT_FRONTAGES, COAST_NAMES,
            LAKE_BRIDGES, LAKE_POCKET_FILL, LAKE_POCKET_NAMES,
            CONTINENT_BRIDGES, INLET_FILL, INLET_NAMES,
            INTERCONTINENTAL_BRIDGES, INTERCONTINENTAL_FILL, INTERCONTINENTAL_SHORES,
            INTERCONTINENTAL_NAMES));

        rows.addAll(buildContinentBranchRows());
        rows.addAll(buildContinentGlobalRows());

        return ToggleTree.buildToggleTree(
            refreshes::refreshCoastlines, rows.toArray(ToggleTree.Row[]::new));
    }

    // The branches: one per thing the construction makes, each with the switches that thing
    // has. In the order water gets smaller, then the spans laid over it.
    //
    // A "Names" row under each, because the pieces of void are named by the layer that shut
    // them in: a reader judging one layer's water wants its names and no other's, and a name
    // switch that sat apart from the layers would put every name on at once.
    private List<ToggleTree.Row> buildContinentBranchRows() {

        return List.of(
            ToggleTree.Row.ofRollUp(
                1,
                "puddlePockets", "Puddle pockets", PUDDLE_BRIDGES, PUDDLE_FILL, PUDDLE_NAMES),
            ToggleTree.Row.ofSwitchPair(
                2,
                new ToggleTree.Switch(
                PUDDLE_BRIDGES, "Bridges", false,
                on -> settings.showContinentPuddleBridges = on),
                new ToggleTree.Switch(
                PUDDLE_FILL, "Fill", false,
                on -> settings.showContinentPuddleFill = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                PUDDLE_NAMES, "Names", false,
                on -> settings.showContinentPuddleNames = on)),
            ToggleTree.Row.ofRollUp(
                1,
                "interiorCoastlines",
                "Interior coastlines",
                LAKE_COASTLINE, LAKE_FILL, LAKE_FRONTAGES, LAKE_NAMES),
            ToggleTree.Row.ofSwitchPair(
                2,
                new ToggleTree.Switch(
                LAKE_COASTLINE, "Coastline", false,
                on -> settings.showContinentLakeCoastline = on),
                new ToggleTree.Switch(
                LAKE_FILL, "Fill", false,
                on -> settings.showContinentLakeFill = on)),
            ToggleTree.Row.ofSwitchPair(
                2,
                new ToggleTree.Switch(
                LAKE_FRONTAGES, "Bridgeable frontage", false,
                on -> settings.showContinentLakeFrontages = on),
                new ToggleTree.Switch(
                LAKE_NAMES, "Names", false,
                on -> settings.showContinentLakeNames = on)),
            ToggleTree.Row.ofRollUp(
                1,
                "exteriorCoastlines",
                "Exterior coastlines",
                CONTINENT_COASTLINE, CONTINENT_FILL, CONTINENT_FRONTAGES, COAST_NAMES),
            ToggleTree.Row.ofSwitchPair(
                2,
                new ToggleTree.Switch(
                CONTINENT_COASTLINE, "Coastline", false,
                on -> settings.showContinentCoastline = on),
                new ToggleTree.Switch(
                CONTINENT_FILL, "Fill", false,
                on -> settings.showContinentCoastFill = on)),
            ToggleTree.Row.ofSwitchPair(
                2,
                new ToggleTree.Switch(
                CONTINENT_FRONTAGES, "Bridgeable frontage", false,
                on -> settings.showContinentCoastFrontages = on),
                new ToggleTree.Switch(
                COAST_NAMES, "Names", false,
                on -> settings.showContinentCoastNames = on)),
            ToggleTree.Row.ofRollUp(
                1,
                "lakeBridges", "Lake bridges", LAKE_BRIDGES, LAKE_POCKET_FILL, LAKE_POCKET_NAMES),
            ToggleTree.Row.ofSwitchPair(
                2,
                new ToggleTree.Switch(
                LAKE_BRIDGES, "Bridges", false,
                on -> settings.showContinentLakeBridges = on),
                new ToggleTree.Switch(
                LAKE_POCKET_FILL, "Fill", false,
                on -> settings.showContinentLakePocketFill = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                LAKE_POCKET_NAMES, "Names", false,
                on -> settings.showContinentLakePocketNames = on)),
            ToggleTree.Row.ofRollUp(
                1,
                "inletBridges", "Inlet bridges", CONTINENT_BRIDGES, INLET_FILL, INLET_NAMES),
            ToggleTree.Row.ofSwitchPair(
                2,
                new ToggleTree.Switch(
                CONTINENT_BRIDGES, "Bridges", false,
                on -> settings.showContinentBridges = on),
                new ToggleTree.Switch(
                INLET_FILL, "Fill", false,
                on -> settings.showContinentInletFill = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                INLET_NAMES, "Names", false,
                on -> settings.showContinentInletNames = on)),

            // Last, because it is the only set laid against everything above rather than
            // against the coasts alone.
            ToggleTree.Row.ofRollUp(
                1,
                "intercontinentalBridges",
                "Intercontinental bridges",
                INTERCONTINENTAL_BRIDGES, INTERCONTINENTAL_FILL, INTERCONTINENTAL_SHORES,
                INTERCONTINENTAL_ENCLOSED_FILL, INTERCONTINENTAL_NAMES),
            ToggleTree.Row.ofSwitchPair(
                2,
                new ToggleTree.Switch(
                INTERCONTINENTAL_BRIDGES, "Bridges", false,
                on -> settings.showIntercontinentalBridges = on),
                new ToggleTree.Switch(
                INTERCONTINENTAL_FILL, "Fill", false,
                on -> settings.showIntercontinentalFill = on)),

            // Its own switch beside the span rather than under it, because the two are opposite
            // readings of one line: the span says a link is a stroke over the void, the shores
            // say it is land with water either side. A reader judging either wants the other
            // out of the way.
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                INTERCONTINENTAL_SHORES, "Coastline", false,
                on -> settings.showIntercontinentalShores = on)),

            // Last of the branch, because the enclosed water is defined by what the rest leave:
            // what the links shut in that no other layer paints.
            ToggleTree.Row.ofSwitchPair(
                2,
                new ToggleTree.Switch(
                    INTERCONTINENTAL_ENCLOSED_FILL, "Enclosed water", false,
                    on -> settings.showIntercontinentalEnclosedFill = on),
                new ToggleTree.Switch(
                    INTERCONTINENTAL_NAMES, "Names", false,
                    on -> settings.showIntercontinentalNames = on)));
    }

    // The globals: one per KIND of thing, cutting across every branch above. No switches of
    // their own - a roll-up reads as on, mixed or off over the keys it names and writes all
    // of them, which is what keeps a nested switch and its global answer synced.
    private List<ToggleTree.Row> buildContinentGlobalRows() {

        return List.of(
            ToggleTree.Row.ofPair(
                1,
                ToggleTree.Row.ofRollUp(
                    1,
                    "everyWall",
                    "Walls",
                    LAKE_COASTLINE, CONTINENT_COASTLINE,
                    LAKE_BRIDGES, CONTINENT_BRIDGES, PUDDLE_BRIDGES,
                    INTERCONTINENTAL_BRIDGES),
                ToggleTree.Row.ofRollUp(
                    1,
                    "everyCoastline",
                    "Coastline",
                    LAKE_COASTLINE, CONTINENT_COASTLINE, INTERCONTINENTAL_SHORES)),
            ToggleTree.Row.ofPair(
                1,
                ToggleTree.Row.ofRollUp(
                    1,
                    "everyFrontage", "Bridgeable frontage", LAKE_FRONTAGES, CONTINENT_FRONTAGES),
                ToggleTree.Row.ofRollUp(
                    1,
                    "everyBridge",
                    "Bridges",
                    PUDDLE_BRIDGES, LAKE_BRIDGES, CONTINENT_BRIDGES, INTERCONTINENTAL_BRIDGES)),
            ToggleTree.Row.ofPair(
                1,
                ToggleTree.Row.ofRollUp(
                    1,
                    "everyFill",
                    "Fill",
                    LAKE_FILL, CONTINENT_FILL, PUDDLE_FILL, LAKE_POCKET_FILL, INLET_FILL,
                    INTERCONTINENTAL_FILL, INTERCONTINENTAL_ENCLOSED_FILL),
                ToggleTree.Row.ofRollUp(
                    1,
                    "everyName",
                    "Names",
                    PUDDLE_NAMES, LAKE_NAMES, COAST_NAMES, LAKE_POCKET_NAMES, INLET_NAMES,
                    INTERCONTINENTAL_NAMES)));
    }

    // What the coastlines are and how they are drawn, which is what everything below is
    // laid over.
    private void addContinentCoastRows(JPanel controls) {

        controls.add(ColourRows.buildColour(
            "continentCoastColour",
            "Continent coasts",
            ViewerSettings.CONTINENT_COAST_DEFAULT,
            colour -> settings.continentCoastColour = colour,
            refreshes::repaintMap));

        controls.add(ColourRows.buildColour(
            "continentCoastalVoidColour",
            "Continent coastal fill",
            ViewerSettings.CONTINENT_COASTAL_VOID_DEFAULT,
            colour -> {
                settings.continentCoastalVoidColour = colour;
                settings.continentCoastalVoidEdge = colour;
            },
            refreshes::repaintMap));

        // How far a span may reach to cross a hole too small to have been drawn a shore. Which
        // holes those are is the puddle floor, up under Global geometry with the frontage floor
        // it works with; this is the other half of the same decision, and stays here because
        // the reach is asked of the cells ALONE, with no coastline consulted.
        controls.add(buildBridgeReachSlider(
            "bridgeReachMultiple",
            ViewerSettings.BRIDGE_REACH_DEFAULT,
            multiple -> settings.bridgeReachMultiple = multiple,
            refreshes::refreshCoastlines));
    }

    // The spans laid across the inlets those coastlines leave, and the rules deciding which
    // of them survive.
    private void addInletBridgeRows(JPanel controls) {

        controls.add(ColourRows.buildColour(
            "continentBridgeColour",
            "Inlet bridges",
            ViewerSettings.CONTINENT_BRIDGE_DEFAULT,
            colour -> settings.continentBridgeColour = colour,
            refreshes::repaintMap));

        // Beside the bridges rather than with the coast it is drawn on, because what it
        // explains is where a bridge was allowed to start - it is read against the spans.
        controls.add(ColourRows.buildColour(
            "bridgeFrontageColour",
            "Bridgeable frontage",
            ViewerSettings.BRIDGE_FRONTAGE_DEFAULT,
            colour -> settings.bridgeFrontageColour = colour,
            refreshes::repaintMap));

        controls.add(buildBridgeReachSlider(
            "continentBridgeReach",
            ViewerSettings.CONTINENT_BRIDGE_REACH_DEFAULT,
            multiple -> settings.continentBridgeReachMultiple = multiple,
            refreshes::refreshCoastlines));

        // The two knobs that place a span once it has been offered, on one line: how far off a
        // wall it may run and still count as running along it, and how close two feet may stand
        // before one of them moves. Both in map units, so neither slider needs scaling, and
        // what each means is documented at the field it writes.
        //
        // Read beside the thinning below, because the three answer one crowded anchor between
        // them: the thinning drops the spans buying nothing, and these two decide what the ones
        // that stay are measured by. Over every span the construction lays, links included.
        controls.add(SliderRows.buildSliderPair(
            new SliderRows.SliderSpec(
                "continentBridgeCoastSlack",
                "Off a wall still counted as along it",
                new SliderRows.SliderRange(
                    COAST_SLACK_MINIMUM,
                    COAST_SLACK_MAXIMUM,
                    ViewerSettings.CONTINENT_BRIDGE_COAST_SLACK_DEFAULT),
                new SliderRows.SliderWork(
                    slack -> settings.continentBridgeCoastSlack = slack,
                    refreshes::refreshCoastlines,
                    () -> { })),
            new SliderRows.SliderSpec(
                "spanAnchorSeparation",
                "Least space between two span feet",
                new SliderRows.SliderRange(
                    ANCHOR_SEPARATION_MINIMUM,
                    ANCHOR_SEPARATION_MAXIMUM,
                    ViewerSettings.SPAN_ANCHOR_SEPARATION_DEFAULT),
                new SliderRows.SliderWork(
                    separation -> settings.spanAnchorSeparation = separation,
                    refreshes::refreshCoastlines,
                    () -> { }))));

        // A rule rather than a layer, so it sits with the sliders that decide which spans
        // exist rather than among the switches that decide what is drawn. Rebuilds, because
        // what it changes is the set itself.
        controls.add(rows.buildToggle(
            "shouldThinSpanFormations",
            "Thin shared-anchor spans",
            true,
            on -> {
                settings.shouldThinSpanFormations = on;
                refreshes.refreshCoastlines();
            }));
    }

    // The links between the continents. A colour and nothing else: they are offered under the
    // reach and judged under the slack the inlet spans are, which is deliberate - the two sets
    // are laid over one sector and read together, so a second reach here would turn a
    // difference between them into a difference between two sliders.
    private void addIntercontinentalBridgeRows(JPanel controls) {

        controls.add(ColourRows.buildColour(
            "intercontinentalBridgeColour",
            "Intercontinental bridges",
            ViewerSettings.INTERCONTINENTAL_BRIDGE_DEFAULT,
            colour -> settings.intercontinentalBridgeColour = colour,
            refreshes::repaintMap));
    }

    // What the construction above did not draw, and where it could have drawn.
    //
    // Inside this section rather than loose below it, because both are read off v3's own
    // coastlines: switched off, there are no dropped stretches to show and no frontage was
    // measured. A diagnostic of a construction is scoped to it, and one sitting outside every
    // heading reads as though it answered for the map rather than for one construction's
    // account of it.
    //
    // Last in the section, after the layers each is a diagnostic OF.
    private void addCoastDiagnosticRows(JPanel controls) {

        // What the smoothing left out, on whichever coasts are drawn. A diagnostic rather than
        // a layer: it answers "what did the rules take" - the one thing a finished coast cannot
        // be asked, since a stretch a rule threw away and one the walk never offered are both
        // simply missing from the line.
        //
        // One row over every coast because the question is the same of each, and the answer is
        // told apart by the coast each mark sits beside.
        controls.add(ControlRows.buildToggle(
            "showDroppedStretches",
            "Dropped stretches",
            false,
            on -> settings.showDroppedStretches = on,
            refreshes::repaintMap));

        controls.add(ColourRows.buildColour(
            "droppedStretchColour",
            "Dropped stretches",
            ViewerSettings.DROPPED_STRETCH_DEFAULT,
            colour -> settings.droppedStretchColour = colour,
            refreshes::repaintMap));

        // Where a straight line could arrive from the open void. v3's answer to that, and v3's
        // alone: this reading is visibly wrong on the map today, so v4 gets one of its own
        // built over its own pockets rather than inheriting this.
        //
        // The coasts are refreshed rather than merely repainted, because the answer is measured
        // on acceptance rather than while painting - switching it on has nothing to draw until
        // the trace is taken again.
        controls.add(ControlRows.buildToggle(
            "showLandableFrontage",
            "Landable frontage",
            false,
            on -> settings.showLandableFrontage = on,
            refreshes::refreshCoastlines));

        controls.add(ColourRows.buildColour(
            "landableFrontageColour",
            "Landable frontage",
            ViewerSettings.LANDABLE_FRONTAGE_DEFAULT,
            colour -> settings.landableFrontageColour = colour,
            refreshes::repaintMap));
    }

    /**
     * How far apart two cells may sit and still be bridged, in cell radii.
     *
     * <p>One row shape over every search that asks the question, because the answers are read
     * against each other: offered over different ranges or stepped differently, a difference
     * between two sets of spans would be partly a difference between the sliders that set
     * them. Only the default and where the value lands differ, which is what the searches
     * genuinely disagree about.
     *
     * <p>Stepped in hundredths, so the reach moves by a fraction of a cell radius rather than
     * jumping a whole one.
     *
     * @param key            what to remember it under
     * @param defaultMultiple the reach this search opens at
     * @param apply          records the new reach, already back in cell radii
     * @param onChange       what to rebuild once it moves, which differs by search
     * @return the row
     */
    private JPanel buildBridgeReachSlider(
            String key,
            double defaultMultiple,
            DoubleConsumer apply,
            Runnable onChange) {

        return SliderRows.buildSlider(new SliderRows.SliderSpec(
            key,
            "Bridge reach, in cell radii (x100)",
            new SliderRows.SliderRange(
                BRIDGE_REACH_MINIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
                BRIDGE_REACH_MAXIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
                defaultMultiple * ViewerSettings.BRIDGE_REACH_STEP_SCALE),
            new SliderRows.SliderWork(
                stepped -> apply.accept(stepped / ViewerSettings.BRIDGE_REACH_STEP_SCALE),
                onChange,
                () -> { })));
    }
}
