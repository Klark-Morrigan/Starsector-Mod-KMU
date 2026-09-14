package kmu.maplayers.base.geometry.ui.settings;

import kmu.maplayers.base.geometry.EdgeInsetRule;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.settings.ViewerSettings;
import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.ColourRows;
import kmu.desktop.ui.swing.ControlRows;
import kmu.desktop.ui.swing.SliderRows;
import kmu.desktop.ui.swing.ToggleTree;

import java.util.ArrayList;
import java.util.List;
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

    // How much water a hole must hold to be drawn as a lake, in percent of one cell's area.
    // Zero is the floor switched off - every puddle kept - and the ceiling is a hundred
    // times the shipped floor: half a cell of water, which discards most of what a real
    // sector holds, so culling whole lakes is an experiment the slider can actually run.
    private static final double MIN_LAKE_MINIMUM = 0;

    private static final double MIN_LAKE_MAXIMUM = 50;

    // How every rounded line on the map is rounded where it turns sharply - the coasts and
    // the cluster borders alike.
    //
    // The radius is how far back along each arm of a corner the arc starts, in map units.
    // Zero is the pass switched off, which is what every setting of it is judged against;
    // the ceiling is a quarter of a cell radius, past which the rounding stops touching only
    // the tip of a corner and begins reshaping the runs either side of it.
    //
    // The segment count decides how smooth each rounded corner comes out. One is the
    // coarsest thing that is still a cut rather than a point; the ceiling is past where more
    // segments stop being visible at the zoom a sector is read at.
    //
    // The threshold is how sharply a line has to turn to be rounded at all, in degrees.
    // Zero rounds nothing whatever the radius says.
    //
    // Its ceiling stops just short of where a line's own sampled arcs begin. Both kinds of
    // line here are cell arcs sampled at the same fixed angles joined by straight runs, and
    // those samples meet at about 173 degrees: a threshold past them rounds every one, which
    // moves the drawn line nowhere it was not already going - the same 28 units off the
    // traced coast at 174 as at 170 - while tripling the vertex count, 2637 to 7737 on the
    // larger fixture. Below that the pass finds the joins BETWEEN runs, which is what it is
    // for.
    //
    // Tied to how finely arcs are sampled rather than to a round number, so it wants
    // re-measuring if that changes.
    private static final double ROUNDING_RADIUS_MINIMUM = 0;

    private static final double ROUNDING_RADIUS_MAXIMUM = 1000;

    private static final double ROUNDING_SEGMENTS_MINIMUM = 1;

    private static final double ROUNDING_SEGMENTS_MAXIMUM = 16;

    private static final double ROUND_BELOW_MINIMUM = 0;

    private static final double ROUND_BELOW_MAXIMUM = 172;

    // The spike-sanding pass ahead of the rounding, which splices out a protrusion both
    // sharper than its angle and shallower than its height. Either at zero switches it off.
    //
    // The height ceiling is half a cell radius, past which what is being spliced out is a
    // peninsula rather than a needle. The angle ceiling is a right angle: a corner blunter
    // than that is shape, whatever its height, and sanding it would flatten real geometry.
    private static final double SPIKE_HEIGHT_MINIMUM = 0;

    private static final double SPIKE_HEIGHT_MAXIMUM = 2000;

    private static final double SPIKE_BELOW_MINIMUM = 0;

    private static final double SPIKE_BELOW_MAXIMUM = 90;

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

    /**
     * Every control the viewer offers, in one column.
     *
     * <p>Built once, when the window is. Nothing here is rebuilt as settings change - a
     * control writes its setting and asks for whatever redraw that costs, and the panel itself
     * never has to know what changed.
     *
     * @return the column
     */
    public JPanel buildRows() {

        var controls = new JPanel();

        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createEmptyBorder(
            PANEL_PADDING,
            PANEL_PADDING,
            PANEL_PADDING,
            PANEL_PADDING));

        // Foldable but not switchable, unlike the void section below. What a cell is shaped
        // like and what it is drawn in are always answered - there is no state in which the
        // map has no cells - so a switch over either would be a control with nothing to mean.
        controls.add(CollapsibleSection.buildFoldingSection(
            "cellGeometry",
            "Cell geometry",
            buildSectionBody(this::addCellGeometryRows)));

        controls.add(CollapsibleSection.buildFoldingSection(
            "cellAppearance",
            "Cell appearance",
            buildSectionBody(this::addCellAppearanceRows)));

        controls.add(ControlRows.buildDivider());

        // The void construction, foldable away under a switch of its own. It is by far the
        // longest run in the panel, and a reader working on the cells above is reading it on
        // the way to nothing.
        controls.add(CollapsibleSection.buildSection(
            CONTINENT_VOID_KEYS,
            "Void pockets",
            new CollapsibleSection.MasterSwitch(
                true, on -> settings.showContinentVoid = on, refreshes::refreshCoastlines),
            buildSectionBody(this::addVoidPocketRows)));

        controls.add(ControlRows.buildDivider());

        addSharedKnobRows(controls);

        return controls;
    }

    // The knobs that reach across the sections, outside every one of them.
    //
    // Inside a section, a knob governing what the others show is unreachable exactly when that
    // section is folded away or switched off - and worse, drawn as disabled while still
    // deciding what the rest of the map looks like. A knob that looks dead and is not is the
    // one kind of control a reader cannot recover from by looking harder.
    private void addSharedKnobRows(JPanel controls) {

        // What the smoothing left out, on whichever coasts are drawn. A diagnostic rather
        // than a layer: it answers "what did the rules take" - the one thing a finished coast
        // cannot be asked, since a stretch a rule threw away and one the walk never offered
        // are both simply missing from the line.
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

        // Where a straight line could arrive from the open void, which is answered off the
        // discs and so says the same thing under either construction - one row, like the drops
        // above, and for the same reason.
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

        // Read by every fill on the map - the coasts' pockets, the spans' and the bridges' own
        // - so it belongs to no one section.
        controls.add(buildOpacitySlider(
            "voidFillOpacity",
            "Void fill opacity",
            opacity -> settings.voidFillOpacity = (int) opacity));

        addLineSmoothingRows(controls);
        addRunPlacementRows(controls);
    }

    // Where a straight run is allowed to land on the cell it reaches, which every coast on the
    // map is placed by.
    //
    // Among the shared knobs for the reason the smoothing ones are: it decides how a coast is
    // PLACED rather than which coast is being traced, so it reaches every line on the map
    // alike.
    private void addRunPlacementRows(JPanel controls) {

        // Off, because the map is deliberately placed the other way - the row is here to put
        // the two placements side by side, not to offer a correction someone should leave on.
        controls.add(buildToggle(
            "shouldLandWhereVisible",
            "Land runs only where the far cell is visible",
            false,
            on -> settings.shouldLandWhereVisible = on));
    }

    // What becomes of a drawn line where it turns sharply: which turns are rounded and to
    // what, then which protrusions are spliced out ahead of that rounding.
    //
    // Both passes here rather than the rounding alone, because the pair is one answer: the
    // sanding exists to hand the rounding geometry it can work on, and reading either without
    // the other says nothing about the line that comes out.
    //
    // Among the shared knobs because smoothing is about how a LINE is drawn rather than about
    // how anything is traced - it reaches the cluster borders as readily as the coasts, and
    // those belong to no section at all.
    //
    // Every one rebuilds rather than repaints. Each smoothed line is worked out once when its
    // geometry is built and carried on it, so moving any of these is a change to the geometry
    // the frame is drawn from rather than to the way that geometry is painted.
    private void addLineSmoothingRows(JPanel controls) {

        // First of the three, because it is the one that decides whether the others do
        // anything: at the bottom of its range nothing is rounded whatever they say, and at
        // the top every join between two runs of line is.
        controls.add(buildSlider(
            "roundBelowDegrees",
            "Round corners sharper than, in degrees",
            ROUND_BELOW_MINIMUM,
            ROUND_BELOW_MAXIMUM,
            ViewerSettings.ROUND_BELOW_DEGREES_DEFAULT,
            degrees -> settings.roundBelowDegrees = degrees));

        controls.add(buildSlider(
            "roundingRadius",
            "Corner rounding radius, in map units",
            ROUNDING_RADIUS_MINIMUM,
            ROUNDING_RADIUS_MAXIMUM,
            ViewerSettings.ROUNDING_RADIUS_DEFAULT,
            radius -> settings.roundingRadius = radius));

        controls.add(buildSlider(
            "roundingSegments",
            "Segments per rounded corner",
            ROUNDING_SEGMENTS_MINIMUM,
            ROUNDING_SEGMENTS_MAXIMUM,
            ViewerSettings.ROUNDING_SEGMENTS_DEFAULT,
            segments -> settings.roundingSegments = (int) segments));

        // The pass that runs BEFORE the three above, and so is read after them: what it takes
        // out is what the rounding would otherwise be handed and be unable to fix.
        controls.add(buildSlider(
            "spikeBelowDegrees",
            "Sand spikes sharper than, in degrees",
            SPIKE_BELOW_MINIMUM,
            SPIKE_BELOW_MAXIMUM,
            ViewerSettings.SPIKE_BELOW_DEGREES_DEFAULT,
            degrees -> settings.spikeBelowDegrees = degrees));

        controls.add(buildSlider(
            "spikeHeight",
            "Tallest spike sanded, in map units",
            SPIKE_HEIGHT_MINIMUM,
            SPIKE_HEIGHT_MAXIMUM,
            ViewerSettings.SPIKE_HEIGHT_DEFAULT,
            height -> settings.spikeHeight = height));
    }

    // Everything a cell is drawn WITH, as against what it is shaped like: the fills and their
    // opacities, then the marks laid over them. Two runs under one heading because a reader
    // adjusting how the map looks is working across both, and the split between a cell's own
    // colour and a mark drawn on top of it is not one they are ever making.
    private void addCellAppearanceRows(JPanel controls) {

        addCellPaintRows(controls);
        addMapChromeRows(controls);
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

        // Which edges the channel above is actually cut into. Directly under the depth slider
        // because the two are one statement between them - how deep, and where - and a reader
        // who has just moved the depth and seen nothing move is a reader whose edges are all
        // seams.
        //
        // A rebuild rather than a repaint: the rule decides the fill polygon, not its colour.
        controls.add(ControlRows.buildRadio(
            "cellInsetRule",
            "Cell insets",
            List.of(
                new ControlRows.Pick<>("By ownership", EdgeInsetRule.AT_EVERY_BORDER),
                new ControlRows.Pick<>("None", EdgeInsetRule.NOWHERE),
                new ControlRows.Pick<>("All", EdgeInsetRule.EVERYWHERE)),
            rule -> settings.cellInsetRule = rule,
            refreshes::rebuildGeometry));

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

        addOwnedCellRows(controls);
        addUnboundedCellRows(controls);
        addJitterRows(controls);
    }

    // What an owned cell and an unowned one are drawn in, each as a fill under an edge with
    // an opacity of its own. The two together because the whole point of the pair is that a
    // reader can tell at a glance which of them a cell is.
    private void addOwnedCellRows(JPanel controls) {

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
    }

    // The cells the partition could not bound, traced separately and drawn over the rest.
    //
    // The switch rebuilds rather than repaints: an unbounded cell is found by tracing, so
    // asking for them is what makes them exist. The coasts go with it, since a cell that was
    // not bounded is a cell no gap was measured against.
    private void addUnboundedCellRows(JPanel controls) {
        controls.add(buildToggle(
            "showUnboundedCells",
            "Trace unbounded cells",
            false,
            on -> {
                settings.showUnboundedCells = on;
                refreshes.refreshUnboundedCells();
                refreshes.refreshCoastlines();
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
    }

    // How far each cell's fill wanders from the colour it was chosen as, and which kinds do.
    //
    // Its own run because it is about telling neighbours apart rather than about what either
    // is: the colours above say what a cell IS, and this says how hard the map works to stop
    // two of them reading as one shape.
    private void addJitterRows(JPanel controls) {
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

    // Everything about the void, behind its own divider because the rest of the panel is about
    // CELLS, and a reader hunting for a void knob was otherwise reading forty identical rows
    // to find it.
    //
    // In the order the construction builds in, which is the order a reader follows it: what
    // there is to see, then the coasts everything else is laid against, then the spans across
    // the inlets those coasts leave, then the links between one continent and the next.
    private void addVoidPocketRows(JPanel controls) {

        controls.add(buildContinentVoidToggles());

        addContinentCoastRows(controls);
        addInletBridgeRows(controls);
        addIntercontinentalBridgeRows(controls);
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
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                PUDDLE_BRIDGES, "Bridges", false,
                on -> settings.showContinentPuddleBridges = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
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
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                LAKE_COASTLINE, "Coastline", false,
                on -> settings.showContinentLakeCoastline = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                LAKE_FILL, "Fill", false,
                on -> settings.showContinentLakeFill = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                LAKE_FRONTAGES, "Bridgeable frontage", false,
                on -> settings.showContinentLakeFrontages = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                LAKE_NAMES, "Names", false,
                on -> settings.showContinentLakeNames = on)),
            ToggleTree.Row.ofRollUp(
                1,
                "exteriorCoastlines",
                "Exterior coastlines",
                CONTINENT_COASTLINE, CONTINENT_FILL, CONTINENT_FRONTAGES, COAST_NAMES),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                CONTINENT_COASTLINE, "Coastline", false,
                on -> settings.showContinentCoastline = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                CONTINENT_FILL, "Fill", false,
                on -> settings.showContinentCoastFill = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                CONTINENT_FRONTAGES, "Bridgeable frontage", false,
                on -> settings.showContinentCoastFrontages = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                COAST_NAMES, "Names", false,
                on -> settings.showContinentCoastNames = on)),
            ToggleTree.Row.ofRollUp(
                1,
                "lakeBridges", "Lake bridges", LAKE_BRIDGES, LAKE_POCKET_FILL, LAKE_POCKET_NAMES),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                LAKE_BRIDGES, "Bridges", false,
                on -> settings.showContinentLakeBridges = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                LAKE_POCKET_FILL, "Fill", false,
                on -> settings.showContinentLakePocketFill = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                LAKE_POCKET_NAMES, "Names", false,
                on -> settings.showContinentLakePocketNames = on)),
            ToggleTree.Row.ofRollUp(
                1,
                "inletBridges", "Inlet bridges", CONTINENT_BRIDGES, INLET_FILL, INLET_NAMES),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                CONTINENT_BRIDGES, "Bridges", false,
                on -> settings.showContinentBridges = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
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
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                INTERCONTINENTAL_BRIDGES, "Bridges", false,
                on -> settings.showIntercontinentalBridges = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                INTERCONTINENTAL_FILL, "Fill", false,
                on -> settings.showIntercontinentalFill = on)),

            // Its own switch beside the span rather than under it, because the two are opposite
            // readings of one line: the span says a link is a stroke over the void, the shores
            // say it is land with water either side. A reader judging either wants the other
            // out of the way.
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                INTERCONTINENTAL_SHORES, "Coastline", false,
                on -> settings.showIntercontinentalShores = on)),

            // Last of the branch, because it is defined by what the rest leave: the water the
            // links enclose that no other layer paints.
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                INTERCONTINENTAL_ENCLOSED_FILL, "Enclosed water", false,
                on -> settings.showIntercontinentalEnclosedFill = on)),
            ToggleTree.Row.ofSwitch(2, new ToggleTree.Switch(
                INTERCONTINENTAL_NAMES, "Names", false,
                on -> settings.showIntercontinentalNames = on)));
    }

    // The globals: one per KIND of thing, cutting across every branch above. No switches of
    // their own - a roll-up reads as on, mixed or off over the keys it names and writes all
    // of them, which is what keeps a nested switch and its global answer synced.
    private List<ToggleTree.Row> buildContinentGlobalRows() {

        return List.of(
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
                LAKE_COASTLINE, CONTINENT_COASTLINE, INTERCONTINENTAL_SHORES),
            ToggleTree.Row.ofRollUp(
                1,
                "everyFrontage", "Bridgeable frontage", LAKE_FRONTAGES, CONTINENT_FRONTAGES),
            ToggleTree.Row.ofRollUp(
                1,
                "everyBridge",
                "Bridges",
                PUDDLE_BRIDGES, LAKE_BRIDGES, CONTINENT_BRIDGES, INTERCONTINENTAL_BRIDGES),
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
                INTERCONTINENTAL_NAMES));
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

        // The frontage floor: how little of its own border a cell may face the void with and
        // still be walked through. Asked as a percentage because that is how anyone reading a
        // map thinks about how far a cell sticks out; what the floor means is documented at
        // the field it writes.
        controls.add(SliderRows.buildSlider(
            "continentLeastFrontage",
            "Least frontage faced, in % of a cell",
            new SliderRows.SliderRange(
                MIN_FRONTAGE_MINIMUM,
                MIN_FRONTAGE_MAXIMUM,
                ViewerSettings.CONTINENT_MIN_FRONTAGE_DEFAULT
                    * ViewerSettings.FRONTAGE_PERCENT_SCALE),
            new SliderRows.SliderWork(
                percent -> settings.continentMinFrontageShare =
                    percent / ViewerSettings.FRONTAGE_PERCENT_SCALE,
                refreshes::refreshCoastlines,
                () -> { })));

        // The puddle floor, beside the frontage floor it works with: that one judges a
        // cell's stretch of shore, this one a whole lake. Asked for in percent the way the
        // frontage share is, and at the same scale.
        controls.add(SliderRows.buildSlider(
            "minLakeShare",
            "Least lake, in % of a cell",
            new SliderRows.SliderRange(
                MIN_LAKE_MINIMUM,
                MIN_LAKE_MAXIMUM,
                ViewerSettings.MIN_LAKE_SHARE_DEFAULT * ViewerSettings.FRONTAGE_PERCENT_SCALE),
            new SliderRows.SliderWork(
                percent -> settings.minLakeShare =
                    percent / ViewerSettings.FRONTAGE_PERCENT_SCALE,
                refreshes::refreshCoastlines,
                () -> { })));

        // Beside the puddle floor, because the two decide one thing between them: that one says
        // which holes are too small for a shore, and this one how far a span may reach to cross
        // one. The reach of the search asked of the cells ALONE, with no coastline consulted -
        // which is why it sits here rather than among the spans laid against the coastlines.
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

        // Beside the thinning, because the two answer one crowded anchor: that one drops the
        // spans buying nothing, this moves the feet of the ones that stay. Over every span the
        // construction lays, links included, so it is read here whichever set is on screen.
        controls.add(SliderRows.buildSlider(
            "spanAnchorSeparation",
            "Least space between two span feet",
            new SliderRows.SliderRange(
                ANCHOR_SEPARATION_MINIMUM,
                ANCHOR_SEPARATION_MAXIMUM,
                ViewerSettings.SPAN_ANCHOR_SEPARATION_DEFAULT),
            new SliderRows.SliderWork(
                separation -> settings.spanAnchorSeparation = separation,
                refreshes::refreshCoastlines,
                () -> { })));

        // A rule rather than a layer, so it sits with the sliders that decide which spans
        // exist rather than among the switches that decide what is drawn. Rebuilds, because
        // what it changes is the set itself.
        controls.add(buildToggle(
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
