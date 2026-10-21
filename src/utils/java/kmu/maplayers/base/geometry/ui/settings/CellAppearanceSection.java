package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.SliderRows;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.List;
import java.util.function.BiConsumer;

import javax.swing.JPanel;

/**
 * Everything a cell is drawn WITH, as against what it is shaped like.
 *
 * <p>The fills and their opacities, then the marks laid over them, then the one opacity that
 * governs the void's fills rather than a cell's. Under one heading because a reader adjusting
 * how the map looks is working across all three, and the splits between them - a cell's own
 * colour, a mark drawn on top of it, a fill belonging to neither - are not ones they are ever
 * making.
 *
 * <p>Everything here repaints, bar the unbounded cells' switch: what it changes is whether a
 * shape exists rather than how one is drawn.
 */
final class CellAppearanceSection
        extends PanelSection {

    // Alpha runs the full byte, so a fill can be turned off entirely or made solid without
    // touching the colour it was chosen as.
    private static final SliderRows.SliderRange OPACITY_RANGE =
        new SliderRows.SliderRange(0, 255);

    // How far brightness may wander either side of the chosen colour when jitter is on, as a
    // percentage of the full range. The default is wide enough to tell two neighbours apart
    // and narrow enough that they still read as one palette; the slider exists because which
    // of those matters depends on what is being looked for.
    private static final SliderRows.SliderRange JITTER_RANGE =
        new SliderRows.SliderRange(0, 100);

    private static final ColourPairKnob OWNED_CELL = new ColourPairKnob(
        "ownedCell",
        "Owned cells",
        ViewerSettings.OWNED_CELL_DEFAULT,
        (settings, colour) -> settings.ownedCellColour = colour,
        (settings, colour) -> settings.ownedCellEdge = colour);

    private static final SliderKnob OWNED_OPACITY = createOpacityKnob(
        "ownedCellOpacity",
        "Owned opacity",
        (settings, opacity) -> settings.ownedCellOpacity = opacity);

    private static final ColourPairKnob UNOWNED_CELL = new ColourPairKnob(
        "unownedCell",
        "Unowned cells",
        ViewerSettings.UNOWNED_CELL_DEFAULT,
        (settings, colour) -> settings.unownedCellColour = colour,
        (settings, colour) -> settings.unownedCellEdge = colour);

    private static final SliderKnob UNOWNED_OPACITY = createOpacityKnob(
        "unownedCellOpacity",
        "Unowned opacity",
        (settings, opacity) -> settings.unownedCellOpacity = opacity);

    private static final SwitchKnob SHOW_UNBOUNDED = new SwitchKnob(
        "showUnboundedCells",
        "Trace unbounded cells",
        false,
        (settings, isOn) -> settings.showUnboundedCells = isOn);

    private static final ColourPairKnob UNBOUNDED_CELL = new ColourPairKnob(
        "unboundedCell",
        "Unbounded cells",
        ViewerSettings.UNBOUNDED_CELL_DEFAULT,
        (settings, colour) -> settings.unboundedCellColour = colour,
        (settings, colour) -> settings.unboundedCellEdge = colour);

    private static final SliderKnob UNBOUNDED_OPACITY = createOpacityKnob(
        "unboundedCellOpacity",
        "Unbounded opacity",
        (settings, opacity) -> settings.unboundedCellOpacity = opacity);

    private static final SwitchKnob JITTER_OWNED = new SwitchKnob(
        "jitterOwned",
        "Jitter owned",
        true,
        (settings, isOn) -> settings.jitterOwned = isOn);

    private static final SwitchKnob JITTER_UNOWNED = new SwitchKnob(
        "jitterUnowned",
        "Jitter unowned",
        false,
        (settings, isOn) -> settings.jitterUnowned = isOn);

    private static final SliderKnob JITTER_STRENGTH = new SliderKnob(
        "jitterStrength",
        "Jitter strength",
        JITTER_RANGE,
        ViewerSettings.JITTER_DEFAULT,
        (settings, strength) -> settings.jitterStrength =
            (float) (strength / ViewerSettings.JITTER_SCALE));

    private static final ColourKnob SITE_DOTS = new ColourKnob(
        "siteDots",
        "Site dots",
        ViewerSettings.SITE_COLOUR,
        (settings, colour) -> settings.siteColour = colour);

    private static final ColourKnob CENTRELINES = new ColourKnob(
        "cellCentrelines",
        "Cell centrelines",
        ViewerSettings.CENTRELINE_DEFAULT,
        (settings, colour) -> settings.centrelineColour = colour);

    private static final ColourPairKnob INSET_CHANNEL = new ColourPairKnob(
        "insetChannel",
        "Inset channels",
        ViewerSettings.CHANNEL_DEFAULT,
        (settings, colour) -> settings.channelColour = colour,
        (settings, colour) -> settings.channelEdge = colour);

    private static final SliderKnob CHANNEL_OPACITY = createOpacityKnob(
        "channelOpacity",
        "Channel opacity",
        (settings, opacity) -> settings.channelOpacity = opacity);

    // Zoom in to read the names: one is only drawn once its region is wide enough on screen to
    // hold it, so at the zoom the map opens on none of them appear.
    private static final SwitchKnob SHOW_CELL_NAMES = new SwitchKnob(
        "showCellNames",
        "Cell names",
        false,
        (settings, isOn) -> settings.showCellNames = isOn);

    private static final ColourKnob REGION_NAMES = new ColourKnob(
        "regionNames",
        "Region names",
        ViewerSettings.REGION_NAME_DEFAULT,
        (settings, colour) -> settings.regionNameColour = colour);

    private static final SliderKnob VOID_FILL_OPACITY = createOpacityKnob(
        "voidFillOpacity",
        "Void fill opacity",
        (settings, opacity) -> settings.voidFillOpacity = opacity);

    CellAppearanceSection(ViewerSettings settings, ViewerRefreshes refreshes) {
        super(settings, refreshes);
    }

    /**
     * @return every setting this section offers
     */
    static List<ViewerKnob> listKnobs() {

        return List.of(
            OWNED_CELL,
            OWNED_OPACITY,
            UNOWNED_CELL,
            UNOWNED_OPACITY,
            SHOW_UNBOUNDED,
            UNBOUNDED_CELL,
            UNBOUNDED_OPACITY,
            JITTER_OWNED,
            JITTER_UNOWNED,
            JITTER_STRENGTH,
            SITE_DOTS,
            CENTRELINES,
            INSET_CHANNEL,
            CHANNEL_OPACITY,
            SHOW_CELL_NAMES,
            REGION_NAMES,
            VOID_FILL_OPACITY);
    }

    @Override
    JPanel buildSection() {

        return CollapsibleSection.buildFoldingSection(
            "cellAppearance",
            "Cell appearance",
            SettingRows.buildSectionBody(this::addRows));
    }

    // One fill's opacity, rounded rather than truncated to a whole alpha, so a slider standing
    // a hair under a value reads as that value rather than the one below it.
    private static SliderKnob createOpacityKnob(
            String key,
            String title,
            BiConsumer<ViewerSettings, Integer> apply) {

        return new SliderKnob(
            key,
            title,
            OPACITY_RANGE,
            ViewerSettings.OWNER_FILL_ALPHA,
            (settings, opacity) -> apply.accept(settings, (int) Math.round(opacity)));
    }

    private void addRows(JPanel controls) {

        addCellPaintRows(controls);
        addMapChromeRows(controls);

        // The map's fifth opacity, beside the four above it rather than out among the void
        // knobs. It governs void fills - the coasts' pockets, the spans' and the bridges' own -
        // but what a reader does with it is compare it against the cell opacities until the two
        // read apart, and that comparison is made by moving one and then the other.
        //
        // It also has to stay reachable while the void sections are switched off, which is
        // exactly when a reader is setting the cells up to look at.
        controls.add(repaintRows.buildSlider(VOID_FILL_OPACITY));
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

        controls.add(repaintRows.buildColourPair(OWNED_CELL));
        controls.add(repaintRows.buildSlider(OWNED_OPACITY));
        controls.add(repaintRows.buildColourPair(UNOWNED_CELL));
        controls.add(repaintRows.buildSlider(UNOWNED_OPACITY));
    }

    // The cells the partition could not bound, traced separately and drawn under the rest.
    //
    // The switch traces rather than repaints: an unbounded cell is found by tracing, so asking
    // for them is what makes them exist.
    private void addUnboundedCellRows(JPanel controls) {

        controls.add(unboundedRows.buildToggle(SHOW_UNBOUNDED));
        controls.add(repaintRows.buildColourPair(UNBOUNDED_CELL));
        controls.add(repaintRows.buildSlider(UNBOUNDED_OPACITY));
    }

    // How far each cell's fill wanders from the colour it was chosen as, and which kinds do.
    //
    // Its own run because it is about telling neighbours apart rather than about what either
    // is: the colours above say what a cell IS, and this says how hard the map works to stop
    // two of them reading as one shape.
    private void addJitterRows(JPanel controls) {

        controls.add(repaintRows.buildToggleRow(JITTER_OWNED, JITTER_UNOWNED));
        controls.add(repaintRows.buildSlider(JITTER_STRENGTH));
    }

    // The marks that are neither cell nor void: the sites, the borders the insets were measured
    // from, the channel between two fills, and the names written over any of it.
    private void addMapChromeRows(JPanel controls) {

        controls.add(repaintRows.buildColour(SITE_DOTS));
        controls.add(repaintRows.buildColour(CENTRELINES));
        controls.add(repaintRows.buildColourPair(INSET_CHANNEL));
        controls.add(repaintRows.buildSlider(CHANNEL_OPACITY));
        controls.add(repaintRows.buildToggle(SHOW_CELL_NAMES));
        controls.add(repaintRows.buildColour(REGION_NAMES));
    }
}
