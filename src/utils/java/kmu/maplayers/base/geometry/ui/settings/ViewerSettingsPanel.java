package kmu.maplayers.base.geometry.ui.settings;

import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.ui.CollapsibleSection;
import kmu.ui.ColourRows;
import kmu.ui.ControlRows;
import kmu.ui.SliderRows;
import kmu.ui.ToggleTree;

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
public final class ViewerSettingsPanel {

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
    private static final String INLAND_BRIDGES = "showInlandBridges";
    private static final String INLAND_FILL = "showInlandFill";
    private static final String INLAND_NAMES = "showInlandNames";
    private static final String COASTLINE = "showCoastline";
    private static final String COASTAL_FILL = "showCoastalFill";
    private static final String COASTAL_NAMES = "showCoastalNames";

    private static final String CONTINENT_COASTS = "showContinentCoasts";
    private static final String CONTINENT_FILL = "showContinentCoastalFill";
    private static final String CONTINENT_BRIDGES = "showContinentBridges";
    private static final String BRIDGE_FRONTAGES = "showBridgeFrontages";

    // What each section remembers its switch and its folded state under. Named for the
    // construction rather than taken from the heading, which is copy and gets reworded.
    private static final CollapsibleSection.SectionKeys SECTOR_VOID_KEYS =
        CollapsibleSection.SectionKeys.forSection("sectorVoid");

    private static final CollapsibleSection.SectionKeys CONTINENT_VOID_KEYS =
        CollapsibleSection.SectionKeys.forSection("continentVoid");

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

    public ViewerSettingsPanel(ViewerSettings settings, ViewerRefreshes refreshes) {
        this.settings = settings;
        this.refreshes = refreshes;
    }

    public JPanel buildRows() {

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

        controls.add(ControlRows.buildDivider());

        // The two rival constructions, each foldable away under a switch of its own. They are
        // the two longest runs in the panel and only one of them is usually being worked on,
        // so a reader who cannot put one down is reading it on the way to the other every time.
        controls.add(CollapsibleSection.buildSection(
            SECTOR_VOID_KEYS,
            "Sector coast void (v2)",
            true,
            on -> settings.showSectorVoid = on,
            this::refreshBothConstructions,
            buildSectionBody(this::addVoidPocketRows)));

        controls.add(ControlRows.buildDivider());

        controls.add(CollapsibleSection.buildSection(
            CONTINENT_VOID_KEYS,
            "Continent coast void (v3)",
            true,
            on -> settings.showContinentVoid = on,
            refreshes::refreshCoastlines,
            buildSectionBody(this::addVoidPocketV3Rows)));

        return controls;
    }

    // A column for one section to hold, filled by whichever run of rows it is the section for.
    // The rows add themselves to whatever they are handed, so a section body is only the
    // container plus the layout the panel itself uses.
    private static JPanel buildSectionBody(Consumer<JPanel> addRows) {

        var body = new JPanel();

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        addRows.accept(body);

        return body;
    }

    // Both, because the settled construction is drawn by two overlays: the bridges and their
    // fills are one, the coast and its pockets the other. Refreshing one would leave the
    // section half-suppressed, which reads as a fault in whichever half was left showing.
    private void refreshBothConstructions() {

        refreshes.refreshVoidBridges();
        refreshes.refreshCoastlines();
    }

    // The five knobs that decide what shape the cells are. Every one of them rebuilds the
    // partition, which is what makes them geometry rather than paint.
    private void addCellGeometryRows(JPanel controls) {

        controls.add(buildSlider(
            "cellRadius",
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
            "borderInset",
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
            "weldTolerance",
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
            "miterSpikeLimit",
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
            "cellBoundSegments",
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

        controls.add(ColourRows.buildColourPair(
            "ownedCell",
            "Owned cells",
            new ColourRows.Choice(
                ViewerSettings.OWNED_CELL_DEFAULT,
                colour -> settings.ownedCellColour = colour),
            new ColourRows.Choice(
                ViewerSettings.OWNED_CELL_DEFAULT,
                colour -> settings.ownedCellEdge = colour),
            refreshes::repaintMap));

        controls.add(buildOpacitySlider(
            "ownedCellOpacity",
            "Owned opacity",
            opacity -> settings.ownedCellOpacity = (int) opacity));

        controls.add(ColourRows.buildColourPair(
            "unownedCell",
            "Unowned cells",
            new ColourRows.Choice(
                ViewerSettings.UNOWNED_CELL_DEFAULT,
                colour -> settings.unownedCellColour = colour),
            new ColourRows.Choice(
                ViewerSettings.UNOWNED_CELL_DEFAULT,
                colour -> settings.unownedCellEdge = colour),
            refreshes::repaintMap));

        controls.add(buildOpacitySlider(
            "unownedCellOpacity",
            "Unowned opacity",
            opacity -> settings.unownedCellOpacity = (int) opacity));

        controls.add(buildToggle(
            "showUnboundedCells",
            "Trace unbounded cells",
            false,
            on -> {
                settings.showUnboundedCells = on;
                refreshes.refreshUnboundedCells();
                refreshes.refreshVoidBridges();
            }));

        controls.add(ColourRows.buildColourPair(
            "unboundedCell",
            "Unbounded cells",
            new ColourRows.Choice(
                ViewerSettings.UNBOUNDED_CELL_DEFAULT,
                colour -> settings.unboundedCellColour = colour),
            new ColourRows.Choice(
                ViewerSettings.UNBOUNDED_CELL_DEFAULT,
                colour -> settings.unboundedCellEdge = colour),
            refreshes::repaintMap));

        controls.add(buildOpacitySlider(
            "unboundedCellOpacity",
            "Unbounded opacity",
            opacity -> settings.unboundedCellOpacity = (int) opacity));

        controls.add(ControlRows.buildToggleRow(
            refreshes::repaintMap,
            new ControlRows.Toggle(
                "Jitter owned",
                "Jitter owned",
                true,
                on -> settings.jitterOwned = on),
            new ControlRows.Toggle(
                "Jitter unowned",
                "Jitter unowned",
                false,
                on -> settings.jitterUnowned = on)));

        controls.add(buildSlider(
            "jitterStrength",
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

        controls.add(ColourRows.buildColour(
            "siteDots",
            "Site dots",
            ViewerSettings.SITE_COLOUR,
            colour -> settings.siteColour = colour,
            refreshes::repaintMap));

        controls.add(ColourRows.buildColour(
            "cellCentrelines",
            "Cell centrelines",
            ViewerSettings.CENTRELINE_DEFAULT,
            colour -> settings.centrelineColour = colour,
            refreshes::repaintMap));

        controls.add(ColourRows.buildColourPair(
            "insetChannel",
            "Inset channels",
            new ColourRows.Choice(
                ViewerSettings.CHANNEL_DEFAULT,
                colour -> settings.channelColour = colour),
            new ColourRows.Choice(
                ViewerSettings.CHANNEL_DEFAULT,
                colour -> settings.channelEdge = colour),
            refreshes::repaintMap));

        controls.add(buildOpacitySlider(
            "channelOpacity",
            "Channel opacity",
            opacity -> settings.channelOpacity = (int) opacity));

        // Zoom in to read the names: one is only drawn once its region is wide enough on
        // screen to hold it, so at the zoom the map opens on none of them appear.
        controls.add(ControlRows.buildToggle(
            "showCellNames",
            "Cell names",
            false,
            on -> settings.showCellNames = on,
            refreshes::repaintMap));

        controls.add(ColourRows.buildColour(
            "regionNames",
            "Region names",
            ViewerSettings.REGION_NAME_DEFAULT,
            colour -> settings.regionNameColour = colour,
            refreshes::repaintMap));
    }

    // Everything about the void as v2 builds it, in the order the toggles above it read: what
    // to show, then the inland knobs, then the coastal ones, then the knobs both kinds share.
    // Below a divider because the rest of the panel is about CELLS, and a reader hunting for a
    // void knob was otherwise reading forty identical rows to find it.
    //
    // Split the way the v3 section is, so the two constructions can be read side by side: a
    // reader comparing them is comparing two runs of rows, not one short method against one
    // long one.
    private void addVoidPocketRows(JPanel controls) {

        controls.add(buildVoidPocketToggles());

        addInlandVoidRows(controls);
        addCoastalVoidRows(controls);
        addSharedVoidRows(controls);
    }

    // The void held between two cells facing each other, and how far apart they may be.
    private void addInlandVoidRows(JPanel controls) {

        // Keyed as bridges rather than as cuts, which is what this swatch has actually
        // coloured all along. A saved value under the old key belongs to the line it was
        // chosen for, and letting it carry over would silently colour the cuts with it.
        controls.add(ColourRows.buildColour(
            "voidBridgeColour",
            "Void bridges",
            ViewerSettings.VOID_BRIDGE_DEFAULT,
            colour -> settings.voidBridgeColour = colour,
            refreshes::repaintMap));

        // Recomputed rather than merely repainted, unlike the colours down here, because this
        // one decides where the walls go, and a wall is what shuts one piece of void off from
        // the next.
        controls.add(buildBridgeReachSlider(
            "bridgeReachMultiple",
            ViewerSettings.BRIDGE_REACH_DEFAULT,
            multiple -> settings.bridgeReachMultiple = multiple,
            refreshes::refreshVoidBridges));

        controls.add(ColourRows.buildColourPair(
            "inlandVoidFill",
            "Inland void fill",
            new ColourRows.Choice(
                ViewerSettings.INLAND_VOID_DEFAULT,
                colour -> settings.inlandVoidColour = colour),
            new ColourRows.Choice(
                ViewerSettings.INLAND_VOID_DEFAULT,
                colour -> settings.inlandVoidEdge = colour),
            refreshes::repaintMap));
    }

    // The line round the sector's outer shape, the void it shuts in, and how it is smoothed.
    private void addCoastalVoidRows(JPanel controls) {

        controls.add(ColourRows.buildColour(
            "coastlineColour",
            "Coastline",
            ViewerSettings.COASTLINE_DEFAULT,
            colour -> settings.coastlineColour = colour,
            refreshes::repaintMap));

        // Its own colour rather than the coastline's, so the two kinds of pocket read the same
        // way: a wall colour and a fill colour each. Sharing one made the coastal fill the only
        // fill on the map painted in the colour of the line that closed it.
        controls.add(ColourRows.buildColourPair(
            "coastalVoidFill",
            "Coastal void fill",
            new ColourRows.Choice(
                ViewerSettings.COASTAL_VOID_DEFAULT,
                colour -> settings.coastalVoidColour = colour),
            new ColourRows.Choice(
                ViewerSettings.COASTAL_VOID_DEFAULT,
                colour -> settings.coastalVoidEdge = colour),
            refreshes::repaintMap));

        // The whole of how the settled coast is smoothed: a stretch is dropped for what it
        // offers on its own, so what goes does not depend on which cells came before it.
        controls.add(buildLeastFrontageSlider(
            "coastLeastFrontage",
            ViewerSettings.COAST_MIN_FRONTAGE_DEFAULT,
            share -> settings.coastMinFrontageShare = share));
    }

    // The knobs that reach both kinds of void, and both constructions, so they belong to
    // neither group above them.
    private void addSharedVoidRows(JPanel controls) {

        // What the smoothing left out, on whichever coasts are drawn. A diagnostic rather
        // than a layer: it answers "what did the rules take" - the one thing a finished coast
        // cannot be asked, since a stretch a rule threw away and one the walk never offered
        // are both simply missing from the line.
        //
        // One row for both constructions because the question is the same of each, and the
        // answer is told apart by the coast each mark sits beside.
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

        // A pair rather than one, because the two say different halves of the same thing:
        // which run went where it should not, and which cell it went into. Nothing is drawn
        // in either once the construction stops crossing anything.
        controls.add(ColourRows.buildColourPair(
            "coastCrossings",
            "Coast crossing / crossed cell",
            new ColourRows.Choice(
                ViewerSettings.COAST_CROSSING_DEFAULT,
                colour -> settings.coastCrossingColour = colour),
            new ColourRows.Choice(
                ViewerSettings.PIERCED_CELL_DEFAULT,
                colour -> settings.piercedCellColour = colour),
            refreshes::repaintMap));

        controls.add(buildOpacitySlider(
            "voidFillOpacity",
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

        controls.add(buildContinentVoidToggles());

        addContinentCoastRows(controls);
        addInletBridgeRows(controls);
    }

    // What there is to see of the continent construction, gathered the way the settled one's
    // switches are. Loose beside the colour and the slider each belonged to, the four read as
    // four unrelated knobs; together they read as the one question they answer - how much of
    // this proposal is on screen - and the roll-up over them can then say "all of it".
    //
    // Every one rebuilds rather than repaints, because each is built only while it is wanted:
    // switching one on is what makes it exist, not merely what shows it.
    private JPanel buildContinentVoidToggles() {

        return ToggleTree.buildToggleTree(
            refreshes::refreshCoastlines,
            ToggleTree.Row.ofRollUp(
                0,
                "Continent void",
                CONTINENT_COASTS, CONTINENT_FILL, CONTINENT_BRIDGES, BRIDGE_FRONTAGES),
            ToggleTree.Row.ofSwitch(1, new ToggleTree.Switch(
                CONTINENT_COASTS, "Coasts", false,
                on -> settings.showContinentCoasts = on)),
            ToggleTree.Row.ofSwitch(1, new ToggleTree.Switch(
                CONTINENT_FILL, "Coastal fill", false,
                on -> settings.showContinentCoastalFill = on)),
            ToggleTree.Row.ofSwitch(1, new ToggleTree.Switch(
                CONTINENT_BRIDGES, "Inlet bridges", false,
                on -> settings.showContinentBridges = on)),
            ToggleTree.Row.ofSwitch(1, new ToggleTree.Switch(
                BRIDGE_FRONTAGES, "Bridgeable frontage", false,
                on -> settings.showBridgeFrontages = on)),
            ToggleTree.Row.ofRollUp(
                1, "Walls", CONTINENT_COASTS, CONTINENT_BRIDGES));
    }

    // What the v3 coastlines are and how they are drawn, which is what everything below is
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

        controls.add(buildLeastFrontageSlider(
            "continentLeastFrontage",
            ViewerSettings.CONTINENT_MIN_FRONTAGE_DEFAULT,
            share -> settings.continentMinFrontageShare = share));
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

        // In map units, so the slider needs no scaling; what the slack means is documented at
        // the field it writes.
        controls.add(SliderRows.buildSlider(
            "continentBridgeCoastSlack",
            "Off a wall still counted as along it",
            new SliderRows.SliderRange(
                COAST_SLACK_MINIMUM,
                COAST_SLACK_MAXIMUM,
                ViewerSettings.CONTINENT_BRIDGE_COAST_SLACK_DEFAULT),
            new SliderRows.SliderWork(
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

        return ToggleTree.buildToggleTree(
            () -> {
                refreshes.refreshVoidBridges();
                refreshes.refreshCoastlines();
            },
            ToggleTree.Row.ofRollUp(
                0,
                "Void pockets",
                INLAND_BRIDGES, INLAND_FILL, INLAND_NAMES,
                COASTLINE, COASTAL_FILL, COASTAL_NAMES),
            ToggleTree.Row.ofRollUp(
                1, "Inland void pockets", INLAND_BRIDGES, INLAND_FILL, INLAND_NAMES),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                INLAND_BRIDGES, "Bridges", true, on -> settings.showInlandBridges = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                INLAND_FILL, "Fill", true, on -> settings.showInlandFill = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                INLAND_NAMES, "Pocket names", false, on -> settings.showInlandNames = on)),
            ToggleTree.Row.ofRollUp(
                1, "Coastal void pockets", COASTLINE, COASTAL_FILL, COASTAL_NAMES),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                COASTLINE, "Coastline", true, on -> settings.showCoastline = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                COASTAL_FILL, "Fill", true, on -> settings.showCoastalFill = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                COASTAL_NAMES, "Pocket names", false, on -> settings.showCoastalNames = on)),
            ToggleTree.Row.ofRollUp(1, "Pocket borders", INLAND_BRIDGES, COASTLINE),
            ToggleTree.Row.ofRollUp(1, "Pocket names", INLAND_NAMES, COASTAL_NAMES));
    }

    // Every geometry knob rebuilds; that is what makes it a geometry knob rather than a
    // colour. Bound once here so a new one cannot be added that quietly only repaints.
    //
    // The title doubles as the key it is remembered under. A knob's label is the one thing
    // about it already unique and already meaningful, so keying on it means a knob cannot be
    // added without being remembered - which is how the last panel ended up with several
    // that were not.
    private JPanel buildSlider(
            String key,
            String title,
            double minimum,
            double maximum,
            double initial,
            DoubleConsumer apply) {
        return SliderRows.buildSlider(
            key,
            title,
            new SliderRows.SliderRange(minimum, maximum, initial),
            new SliderRows.SliderWork(apply, refreshes::rebuildGeometry, () -> { }));
    }

    /**
     * How far apart two cells may sit and still be bridged, in cell radii.
     *
     * <p>One row shape for both constructions, because the two are on screen to be compared:
     * asked over different ranges or stepped differently, a difference between their bridges
     * would be partly a difference between the sliders that set them. Only the default and
     * where the value lands differ, which is what the two searches genuinely disagree about.
     *
     * <p>Stepped in hundredths, so the reach moves by a fraction of a cell radius rather than
     * jumping a whole one.
     *
     * @param key            what to remember it under
     * @param defaultMultiple the reach this construction opens at
     * @param apply          records the new reach, already back in cell radii
     * @param onChange       what to rebuild once it moves, which differs by construction
     * @return the row
     */
    private JPanel buildBridgeReachSlider(
            String key,
            double defaultMultiple,
            DoubleConsumer apply,
            Runnable onChange) {

        return SliderRows.buildSlider(
            key,
            "Bridge reach, in cell radii (x100)",
            new SliderRows.SliderRange(
                BRIDGE_REACH_MINIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
                BRIDGE_REACH_MAXIMUM * ViewerSettings.BRIDGE_REACH_STEP_SCALE,
                defaultMultiple * ViewerSettings.BRIDGE_REACH_STEP_SCALE),
            new SliderRows.SliderWork(
                stepped -> apply.accept(stepped / ViewerSettings.BRIDGE_REACH_STEP_SCALE),
                onChange,
                () -> { }));
    }

    /**
     * How little of its own border a cell may face the void with and still be walked through.
     *
     * <p>One row shape for both coasts, for the reason the reach is: the floor is the knob the
     * two constructions are most often compared across, so it has to ask them the same
     * question over the same range.
     *
     * <p>Asked as a percentage because that is how anyone reading a map thinks about how far a
     * cell sticks out; what the floor means is documented at the field it writes.
     *
     * @param key          what to remember it under
     * @param defaultShare the floor this coast opens at, as a share of a turn
     * @param apply        records the new floor, already back to a share
     * @return the row
     */
    private JPanel buildLeastFrontageSlider(
            String key,
            double defaultShare,
            DoubleConsumer apply) {

        return SliderRows.buildSlider(
            key,
            "Least frontage faced, in % of a cell",
            new SliderRows.SliderRange(
                MIN_FRONTAGE_MINIMUM,
                MIN_FRONTAGE_MAXIMUM,
                defaultShare * ViewerSettings.FRONTAGE_PERCENT_SCALE),
            new SliderRows.SliderWork(
                percent -> apply.accept(percent / ViewerSettings.FRONTAGE_PERCENT_SCALE),
                refreshes::refreshCoastlines,
                () -> { }));
    }

    private JPanel buildOpacitySlider(String key, String title, DoubleConsumer apply) {
        return buildSlider(
            key,
            title,
            OPACITY_MINIMUM,
            OPACITY_MAXIMUM,
            ViewerSettings.OWNER_FILL_ALPHA,
            apply);
    }

    private JPanel buildToggle(
            String key, String title, boolean initial, Consumer<Boolean> apply) {

        return ControlRows.buildToggle(
            key, title, initial, apply, refreshes::rebuildGeometry);
    }
}
