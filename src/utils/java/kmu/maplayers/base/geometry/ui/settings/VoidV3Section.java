package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.SliderRows;
import kmu.desktop.ui.swing.ToggleTree;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.JPanel;

/**
 * The v3 void construction: what it draws, what it draws it with, and what it did not draw.
 *
 * <p>By far the longest run in the panel, which is most of why it is a class. It is also the
 * one whose contents are scheduled to go: when v4 wins, this is what gets deleted, and a
 * section that is one file is deleted by deleting one file.
 */
final class VoidV3Section
        extends PanelSection {

    // What the section remembers its switch and its folded state under. Named for the
    // construction rather than taken from the heading, which is copy and gets reworded.
    private static final String SECTION_NAME = "continentVoid";

    // How far apart two cells may sit and still be taken to hold the void between them, in
    // cell radii from centre to centre. Four is the width at which a whole further cell
    // would fit in the gap, which is the point past which the void between two cells stops
    // being theirs.
    //
    // Stepped in hundredths, so the reach moves by a fraction of a cell radius rather than
    // jumping a whole one.
    private static final double BRIDGE_REACH_MINIMUM = 2;
    private static final double BRIDGE_REACH_MAXIMUM = 10;

    private static final SliderRows.SliderRange BRIDGE_REACH_RANGE = new SliderRows.SliderRange(
        BRIDGE_REACH_MINIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
        BRIDGE_REACH_MAXIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE);

    // How far off a wall already down a span may run and still count as running along it, in
    // map units. Zero asks for lines that coincide exactly, which catches only the spans that
    // repeat a wall end for end. The ceiling is several stroke widths - past that the rule is
    // discarding spans that are visibly clear of anything and merely heading the same way.
    private static final SliderRows.SliderRange COAST_SLACK_RANGE =
        new SliderRows.SliderRange(0, 500);

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
    private static final SliderRows.SliderRange ANCHOR_SEPARATION_RANGE =
        new SliderRows.SliderRange(0, 2000);

    private static final SwitchKnob MASTER = new SwitchKnob(
        CollapsibleSection.SectionKeys.forSection(SECTION_NAME).switchKey(),
        "Void pockets - v3",
        true,
        (settings, isOn) -> settings.showContinentVoid = isOn);

    // The switches of the layer tree. Each knob's key is named once, here, and the roll-ups
    // read it off the knob: a roll-up that named a key the switch does not would cover nothing,
    // silently.
    private static final SwitchKnob PUDDLE_BRIDGES = new SwitchKnob(
        "showContinentPuddleBridges", "Bridges", false,
        (settings, isOn) -> settings.showContinentPuddleBridges = isOn);

    private static final SwitchKnob PUDDLE_FILL = new SwitchKnob(
        "showContinentPuddleFill", "Fill", false,
        (settings, isOn) -> settings.showContinentPuddleFill = isOn);

    private static final SwitchKnob PUDDLE_NAMES = new SwitchKnob(
        "showContinentPuddleNames", "Names", false,
        (settings, isOn) -> settings.showContinentPuddleNames = isOn);

    private static final SwitchKnob LAKE_COASTLINE = new SwitchKnob(
        "showContinentLakeCoastline", "Coastline", false,
        (settings, isOn) -> settings.showContinentLakeCoastline = isOn);

    private static final SwitchKnob LAKE_FILL = new SwitchKnob(
        "showContinentLakeFill", "Fill", false,
        (settings, isOn) -> settings.showContinentLakeFill = isOn);

    private static final SwitchKnob LAKE_FRONTAGES = new SwitchKnob(
        "showContinentLakeFrontages", "Bridgeable frontage", false,
        (settings, isOn) -> settings.showContinentLakeFrontages = isOn);

    private static final SwitchKnob LAKE_NAMES = new SwitchKnob(
        "showContinentLakeNames", "Names", false,
        (settings, isOn) -> settings.showContinentLakeNames = isOn);

    private static final SwitchKnob CONTINENT_COASTLINE = new SwitchKnob(
        "showContinentCoastline", "Coastline", false,
        (settings, isOn) -> settings.showContinentCoastline = isOn);

    private static final SwitchKnob CONTINENT_FILL = new SwitchKnob(
        "showContinentCoastFill", "Fill", false,
        (settings, isOn) -> settings.showContinentCoastFill = isOn);

    private static final SwitchKnob CONTINENT_FRONTAGES = new SwitchKnob(
        "showContinentCoastFrontages", "Bridgeable frontage", false,
        (settings, isOn) -> settings.showContinentCoastFrontages = isOn);

    private static final SwitchKnob COAST_NAMES = new SwitchKnob(
        "showContinentCoastNames", "Names", false,
        (settings, isOn) -> settings.showContinentCoastNames = isOn);

    private static final SwitchKnob LAKE_BRIDGES = new SwitchKnob(
        "showContinentLakeBridges", "Bridges", false,
        (settings, isOn) -> settings.showContinentLakeBridges = isOn);

    private static final SwitchKnob LAKE_POCKET_FILL = new SwitchKnob(
        "showContinentLakePocketFill", "Fill", false,
        (settings, isOn) -> settings.showContinentLakePocketFill = isOn);

    private static final SwitchKnob LAKE_POCKET_NAMES = new SwitchKnob(
        "showContinentLakePocketNames", "Names", false,
        (settings, isOn) -> settings.showContinentLakePocketNames = isOn);

    private static final SwitchKnob CONTINENT_BRIDGES = new SwitchKnob(
        "showContinentBridges", "Bridges", false,
        (settings, isOn) -> settings.showContinentBridges = isOn);

    private static final SwitchKnob INLET_FILL = new SwitchKnob(
        "showContinentInletFill", "Fill", false,
        (settings, isOn) -> settings.showContinentInletFill = isOn);

    private static final SwitchKnob INLET_NAMES = new SwitchKnob(
        "showContinentInletNames", "Names", false,
        (settings, isOn) -> settings.showContinentInletNames = isOn);

    private static final SwitchKnob INTERCONTINENTAL_BRIDGES = new SwitchKnob(
        "showIntercontinentalBridges", "Bridges", false,
        (settings, isOn) -> settings.showIntercontinentalBridges = isOn);

    private static final SwitchKnob INTERCONTINENTAL_FILL = new SwitchKnob(
        "showIntercontinentalFill", "Fill", false,
        (settings, isOn) -> settings.showIntercontinentalFill = isOn);

    // Its own switch beside the span rather than under it, because the two are opposite
    // readings of one line: the span says a link is a stroke over the void, the shores say it
    // is land with water either side. A reader judging either wants the other out of the way.
    private static final SwitchKnob INTERCONTINENTAL_SHORES = new SwitchKnob(
        "showIntercontinentalShores", "Coastline", false,
        (settings, isOn) -> settings.showIntercontinentalShores = isOn);

    private static final SwitchKnob INTERCONTINENTAL_ENCLOSED_FILL = new SwitchKnob(
        "showIntercontinentalEnclosedFill", "Enclosed water", false,
        (settings, isOn) -> settings.showIntercontinentalEnclosedFill = isOn);

    private static final SwitchKnob INTERCONTINENTAL_NAMES = new SwitchKnob(
        "showIntercontinentalNames", "Names", false,
        (settings, isOn) -> settings.showIntercontinentalNames = isOn);

    private static final ColourKnob CONTINENT_COAST_COLOUR = new ColourKnob(
        "continentCoastColour",
        "Continent coasts",
        ViewerSettings.CONTINENT_COAST_DEFAULT,
        (settings, colour) -> settings.continentCoastColour = colour);

    private static final ColourKnob COASTAL_VOID_COLOUR = new ColourKnob(
        "continentCoastalVoidColour",
        "Continent coastal fill",
        ViewerSettings.CONTINENT_COASTAL_VOID_DEFAULT,
        (settings, colour) -> {
            settings.continentCoastalVoidColour = colour;
            settings.continentCoastalVoidEdge = colour;
        });

    // How far a span may reach to cross a hole too small to have been drawn a shore. Which
    // holes those are is the puddle floor, up under Global geometry with the frontage floor
    // it works with; this is the other half of the same decision, and stays here because
    // the reach is asked of the cells ALONE, with no coastline consulted.
    private static final SliderKnob BRIDGE_REACH = createBridgeReachKnob(
        "bridgeReachMultiple",
        ViewerSettings.BRIDGE_REACH_DEFAULT,
        (settings, multiple) -> settings.bridgeReachMultiple = multiple);

    private static final ColourKnob CONTINENT_BRIDGE_COLOUR = new ColourKnob(
        "continentBridgeColour",
        "Inlet bridges",
        ViewerSettings.CONTINENT_BRIDGE_DEFAULT,
        (settings, colour) -> settings.continentBridgeColour = colour);

    // Beside the bridges rather than with the coast it is drawn on, because what it explains is
    // where a bridge was allowed to start - it is read against the spans.
    private static final ColourKnob BRIDGE_FRONTAGE_COLOUR = new ColourKnob(
        "bridgeFrontageColour",
        "Bridgeable frontage",
        ViewerSettings.BRIDGE_FRONTAGE_DEFAULT,
        (settings, colour) -> settings.bridgeFrontageColour = colour);

    private static final SliderKnob CONTINENT_BRIDGE_REACH = createBridgeReachKnob(
        "continentBridgeReach",
        ViewerSettings.CONTINENT_BRIDGE_REACH_DEFAULT,
        (settings, multiple) -> settings.continentBridgeReachMultiple = multiple);

    private static final SliderKnob COAST_SLACK = new SliderKnob(
        "continentBridgeCoastSlack",
        "Off a wall still counted as along it",
        COAST_SLACK_RANGE,
        ViewerSettings.CONTINENT_BRIDGE_COAST_SLACK_DEFAULT,
        (settings, slack) -> settings.continentBridgeCoastSlack = slack);

    private static final SliderKnob ANCHOR_SEPARATION = new SliderKnob(
        "spanAnchorSeparation",
        "Least space between two bridge feet",
        ANCHOR_SEPARATION_RANGE,
        ViewerSettings.SPAN_ANCHOR_SEPARATION_DEFAULT,
        (settings, separation) -> settings.spanAnchorSeparation = separation);

    // The label says bridge where the key says span: the key is what saved windows hold, and
    // the label uses v4's word for these lines.
    private static final SwitchKnob THIN_SPAN_FORMATIONS = new SwitchKnob(
        "shouldThinSpanFormations",
        "Thin shared-anchor bridges",
        true,
        (settings, isOn) -> settings.shouldThinSpanFormations = isOn);

    private static final ColourKnob INTERCONTINENTAL_BRIDGE_COLOUR = new ColourKnob(
        "intercontinentalBridgeColour",
        "Intercontinental bridges",
        ViewerSettings.INTERCONTINENTAL_BRIDGE_DEFAULT,
        (settings, colour) -> settings.intercontinentalBridgeColour = colour);

    private static final SwitchKnob SHOW_DROPPED_STRETCHES = new SwitchKnob(
        "showDroppedStretches",
        "Dropped stretches",
        false,
        (settings, isOn) -> settings.showDroppedStretches = isOn);

    private static final ColourKnob DROPPED_STRETCH_COLOUR = new ColourKnob(
        "droppedStretchColour",
        "Dropped stretches",
        ViewerSettings.DROPPED_STRETCH_DEFAULT,
        (settings, colour) -> settings.droppedStretchColour = colour);

    private static final SwitchKnob SHOW_LANDABLE_FRONTAGE = new SwitchKnob(
        "showLandableFrontage",
        "Landable frontage",
        false,
        (settings, isOn) -> settings.showLandableFrontage = isOn);

    private static final ColourKnob LANDABLE_FRONTAGE_COLOUR = new ColourKnob(
        "landableFrontageColour",
        "Landable frontage",
        ViewerSettings.LANDABLE_FRONTAGE_DEFAULT,
        (settings, colour) -> settings.landableFrontageColour = colour);

    VoidV3Section(ViewerSettings settings, ViewerRefreshes refreshes) {
        super(settings, refreshes);
    }

    /**
     * @return every setting this section offers
     */
    static List<ViewerKnob> listKnobs() {

        return List.of(
            MASTER,
            PUDDLE_BRIDGES, PUDDLE_FILL, PUDDLE_NAMES,
            LAKE_COASTLINE, LAKE_FILL, LAKE_FRONTAGES, LAKE_NAMES,
            CONTINENT_COASTLINE, CONTINENT_FILL, CONTINENT_FRONTAGES, COAST_NAMES,
            LAKE_BRIDGES, LAKE_POCKET_FILL, LAKE_POCKET_NAMES,
            CONTINENT_BRIDGES, INLET_FILL, INLET_NAMES,
            INTERCONTINENTAL_BRIDGES, INTERCONTINENTAL_FILL, INTERCONTINENTAL_SHORES,
            INTERCONTINENTAL_ENCLOSED_FILL, INTERCONTINENTAL_NAMES,
            CONTINENT_COAST_COLOUR,
            COASTAL_VOID_COLOUR,
            BRIDGE_REACH,
            CONTINENT_BRIDGE_COLOUR,
            BRIDGE_FRONTAGE_COLOUR,
            CONTINENT_BRIDGE_REACH,
            COAST_SLACK,
            ANCHOR_SEPARATION,
            THIN_SPAN_FORMATIONS,
            INTERCONTINENTAL_BRIDGE_COLOUR,
            SHOW_DROPPED_STRETCHES,
            DROPPED_STRETCH_COLOUR,
            SHOW_LANDABLE_FRONTAGE,
            LANDABLE_FRONTAGE_COLOUR);
    }

    @Override
    JPanel buildSection() {

        return coastRows.buildSwitchedSection(
            SECTION_NAME,
            MASTER,
            SettingRows.buildSectionBody(this::addRows));
    }

    // How far apart two cells may sit and still be bridged, in cell radii.
    //
    // One shape over every search that asks the question, because the answers are read against
    // each other: offered over different ranges or stepped differently, a difference between two
    // sets of spans would be partly a difference between the sliders that set them. Only the
    // default and where the value lands differ, which is what the searches genuinely disagree
    // about.
    private static SliderKnob createBridgeReachKnob(
            String key,
            double defaultMultiple,
            BiConsumer<ViewerSettings, Double> apply) {

        return new SliderKnob(
            key,
            "Bridge reach, in cell radii (x100)",
            BRIDGE_REACH_RANGE,
            defaultMultiple * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
            (settings, stepped) ->
                apply.accept(settings, stepped / ViewerSettings.BRIDGE_REACH_STEP_SCALE));
    }

    // A switch's key, for a roll-up to name.
    private static String[] listKeys(SwitchKnob... knobs) {

        var keys = new String[knobs.length];

        for (var index = 0; index < knobs.length; index++) {
            keys[index] = knobs[index].key();
        }

        return keys;
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
    // The flat roll-ups after them cut across the branches on purpose: they answer questions
    // about a KIND OF THING rather than about a branch - "every wall", "every fill", "every
    // bridge" - and a roll-up is what keeps the nested switch and the global answer synced,
    // since it reads as on, mixed or off over its keys and writes all of them.
    //
    // Every one retraces the coasts rather than repainting, because each layer is built only
    // while it is wanted: switching one on is what makes it exist, not merely what shows it.
    private JPanel buildContinentVoidToggles() {

        var rows = new ArrayList<ToggleTree.Row>();

        rows.add(ToggleTree.Row.ofRollUp(
            TreeDepths.ROOT,
            "allVoidLayers",
            "Continent void",
            listKeys(
                PUDDLE_BRIDGES, PUDDLE_FILL, PUDDLE_NAMES,
                LAKE_COASTLINE, LAKE_FILL, LAKE_FRONTAGES, LAKE_NAMES,
                CONTINENT_COASTLINE, CONTINENT_FILL, CONTINENT_FRONTAGES, COAST_NAMES,
                LAKE_BRIDGES, LAKE_POCKET_FILL, LAKE_POCKET_NAMES,
                CONTINENT_BRIDGES, INLET_FILL, INLET_NAMES,
                INTERCONTINENTAL_BRIDGES, INTERCONTINENTAL_FILL, INTERCONTINENTAL_SHORES,
                INTERCONTINENTAL_NAMES)));

        rows.addAll(buildContinentBranchRows());
        rows.addAll(buildContinentGlobalRows());

        return coastRows.buildToggleTree(rows.toArray(ToggleTree.Row[]::new));
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
                TreeDepths.BRANCH,
                "puddlePockets",
                "Puddle pockets",
                listKeys(PUDDLE_BRIDGES, PUDDLE_FILL, PUDDLE_NAMES)),
            buildSwitchPairRow(PUDDLE_BRIDGES, PUDDLE_FILL),
            buildSwitchRow(PUDDLE_NAMES),
            ToggleTree.Row.ofRollUp(
                TreeDepths.BRANCH,
                "interiorCoastlines",
                "Interior coastlines",
                listKeys(LAKE_COASTLINE, LAKE_FILL, LAKE_FRONTAGES, LAKE_NAMES)),
            buildSwitchPairRow(LAKE_COASTLINE, LAKE_FILL),
            buildSwitchPairRow(LAKE_FRONTAGES, LAKE_NAMES),
            ToggleTree.Row.ofRollUp(
                TreeDepths.BRANCH,
                "exteriorCoastlines",
                "Exterior coastlines",
                listKeys(CONTINENT_COASTLINE, CONTINENT_FILL, CONTINENT_FRONTAGES, COAST_NAMES)),
            buildSwitchPairRow(CONTINENT_COASTLINE, CONTINENT_FILL),
            buildSwitchPairRow(CONTINENT_FRONTAGES, COAST_NAMES),
            ToggleTree.Row.ofRollUp(
                TreeDepths.BRANCH,
                "lakeBridges",
                "Lake bridges",
                listKeys(LAKE_BRIDGES, LAKE_POCKET_FILL, LAKE_POCKET_NAMES)),
            buildSwitchPairRow(LAKE_BRIDGES, LAKE_POCKET_FILL),
            buildSwitchRow(LAKE_POCKET_NAMES),
            ToggleTree.Row.ofRollUp(
                TreeDepths.BRANCH,
                "inletBridges",
                "Inlet bridges",
                listKeys(CONTINENT_BRIDGES, INLET_FILL, INLET_NAMES)),
            buildSwitchPairRow(CONTINENT_BRIDGES, INLET_FILL),
            buildSwitchRow(INLET_NAMES),

            // Last, because it is the only set laid against everything above rather than
            // against the coasts alone.
            ToggleTree.Row.ofRollUp(
                TreeDepths.BRANCH,
                "intercontinentalBridges",
                "Intercontinental bridges",
                listKeys(
                    INTERCONTINENTAL_BRIDGES, INTERCONTINENTAL_FILL, INTERCONTINENTAL_SHORES,
                    INTERCONTINENTAL_ENCLOSED_FILL, INTERCONTINENTAL_NAMES)),
            buildSwitchPairRow(INTERCONTINENTAL_BRIDGES, INTERCONTINENTAL_FILL),
            buildSwitchRow(INTERCONTINENTAL_SHORES),

            // Last of the branch, because the enclosed water is defined by what the rest leave:
            // what the links shut in that no other layer paints.
            buildSwitchPairRow(INTERCONTINENTAL_ENCLOSED_FILL, INTERCONTINENTAL_NAMES));
    }

    // The globals: one per KIND of thing, cutting across every branch above. No switches of
    // their own - a roll-up reads as on, mixed or off over the keys it names and writes all
    // of them, which is what keeps a nested switch and its global answer synced.
    private List<ToggleTree.Row> buildContinentGlobalRows() {

        return List.of(
            ToggleTree.Row.ofPair(
                TreeDepths.BRANCH,
                ToggleTree.Row.ofRollUp(
                    TreeDepths.BRANCH,
                    "everyWall",
                    "Walls",
                    listKeys(
                        LAKE_COASTLINE, CONTINENT_COASTLINE,
                        LAKE_BRIDGES, CONTINENT_BRIDGES, PUDDLE_BRIDGES,
                        INTERCONTINENTAL_BRIDGES)),
                ToggleTree.Row.ofRollUp(
                    TreeDepths.BRANCH,
                    "everyCoastline",
                    "Coastline",
                    listKeys(LAKE_COASTLINE, CONTINENT_COASTLINE, INTERCONTINENTAL_SHORES))),
            ToggleTree.Row.ofPair(
                TreeDepths.BRANCH,
                ToggleTree.Row.ofRollUp(
                    TreeDepths.BRANCH,
                    "everyFrontage",
                    "Bridgeable frontage",
                    listKeys(LAKE_FRONTAGES, CONTINENT_FRONTAGES)),
                ToggleTree.Row.ofRollUp(
                    TreeDepths.BRANCH,
                    "everyBridge",
                    "Bridges",
                    listKeys(
                        PUDDLE_BRIDGES, LAKE_BRIDGES, CONTINENT_BRIDGES,
                        INTERCONTINENTAL_BRIDGES))),
            ToggleTree.Row.ofPair(
                TreeDepths.BRANCH,
                ToggleTree.Row.ofRollUp(
                    TreeDepths.BRANCH,
                    "everyFill",
                    "Fill",
                    listKeys(
                        LAKE_FILL, CONTINENT_FILL, PUDDLE_FILL, LAKE_POCKET_FILL, INLET_FILL,
                        INTERCONTINENTAL_FILL, INTERCONTINENTAL_ENCLOSED_FILL)),
                ToggleTree.Row.ofRollUp(
                    TreeDepths.BRANCH,
                    "everyName",
                    "Names",
                    listKeys(
                        PUDDLE_NAMES, LAKE_NAMES, COAST_NAMES, LAKE_POCKET_NAMES, INLET_NAMES,
                        INTERCONTINENTAL_NAMES))));
    }

    private ToggleTree.Row buildSwitchRow(SwitchKnob knob) {
        return ToggleTree.Row.ofSwitch(TreeDepths.LEAF, knob.bindSwitch(settings));
    }

    private ToggleTree.Row buildSwitchPairRow(SwitchKnob left, SwitchKnob right) {

        return ToggleTree.Row.ofSwitchPair(
            TreeDepths.LEAF, left.bindSwitch(settings), right.bindSwitch(settings));
    }

    // What the coastlines are and how they are drawn, which is what everything below is
    // laid over.
    private void addContinentCoastRows(JPanel controls) {

        controls.add(repaintRows.buildColour(CONTINENT_COAST_COLOUR));
        controls.add(repaintRows.buildColour(COASTAL_VOID_COLOUR));
        controls.add(coastRows.buildSlider(BRIDGE_REACH));
    }

    // The spans laid across the inlets those coastlines leave, and the rules deciding which
    // of them survive.
    private void addInletBridgeRows(JPanel controls) {

        controls.add(repaintRows.buildColour(CONTINENT_BRIDGE_COLOUR));
        controls.add(repaintRows.buildColour(BRIDGE_FRONTAGE_COLOUR));
        controls.add(coastRows.buildSlider(CONTINENT_BRIDGE_REACH));

        // The two knobs that place a span once it has been offered, on one line: how far off a
        // wall it may run and still count as running along it, and how close two feet may stand
        // before one of them moves. Both in map units, and what each means is documented at the
        // field it writes.
        //
        // Read beside the thinning below, because the three answer one crowded anchor between
        // them: the thinning drops the spans buying nothing, and these two decide what the ones
        // that stay are measured by. Over every span the construction lays, links included.
        controls.add(coastRows.buildSliderPair(COAST_SLACK, ANCHOR_SEPARATION));

        // A rule rather than a layer, so it sits with the sliders that decide which spans exist
        // rather than among the switches that decide what is drawn.
        controls.add(coastRows.buildToggle(THIN_SPAN_FORMATIONS));
    }

    // The links between the continents. A colour and nothing else: they are offered under the
    // reach and judged under the slack the inlet spans are, which is deliberate - the two sets
    // are laid over one sector and read together, so a second reach here would turn a
    // difference between them into a difference between two sliders.
    private void addIntercontinentalBridgeRows(JPanel controls) {
        controls.add(repaintRows.buildColour(INTERCONTINENTAL_BRIDGE_COLOUR));
    }

    // What the construction above did not draw, and where it could have drawn.
    //
    // Inside this section rather than loose below it, because both are read off v3's own
    // coastlines: switched off, there are no dropped stretches to show and no frontage was
    // measured. A diagnostic of a construction is scoped to it, and one sitting outside every
    // heading reads as though it answered for the map rather than for one construction's
    // account of it.
    private void addCoastDiagnosticRows(JPanel controls) {

        // What the smoothing left out, on whichever coasts are drawn. A diagnostic rather than
        // a layer: it answers "what did the rules take" - the one thing a finished coast cannot
        // be asked, since a stretch a rule threw away and one the walk never offered are both
        // simply missing from the line.
        controls.add(repaintRows.buildToggle(SHOW_DROPPED_STRETCHES));
        controls.add(repaintRows.buildColour(DROPPED_STRETCH_COLOUR));

        // Where a straight line could arrive from the open void. v3's answer to that, and v3's
        // alone; v4 has one of its own built over its own pieces.
        //
        // The coasts are traced again rather than merely repainted, because the answer is
        // measured on acceptance rather than while painting - switching it on has nothing to
        // draw until the trace is taken again.
        controls.add(coastRows.buildToggle(SHOW_LANDABLE_FRONTAGE));
        controls.add(repaintRows.buildColour(LANDABLE_FRONTAGE_COLOUR));
    }
}
