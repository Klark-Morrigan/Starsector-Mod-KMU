package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.ColourRows;
import kmu.desktop.ui.swing.ControlRows;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import javax.swing.JPanel;

/**
 * Everything a cell is drawn WITH, as against what it is shaped like.
 *
 * <p>The fills and their opacities, then the marks laid over them, then the one opacity that
 * governs the void's fills rather than a cell's. Under one heading because a reader adjusting
 * how the map looks is working across all three, and the splits between them - a cell's own
 * colour, a mark drawn on top of it, a fill belonging to neither - are not ones they are ever
 * making.
 */
final class CellAppearanceSection extends PanelSection {

    // How far brightness may wander either side of the chosen colour when jitter is on, as a
    // percentage of the full range. The default is wide enough to tell two neighbours apart
    // and narrow enough that they still read as one palette; the slider exists because which
    // of those matters depends on what is being looked for.
    private static final double JITTER_MINIMUM = 0;

    private static final double JITTER_MAXIMUM = 100;

    CellAppearanceSection(ViewerSettings settings, ViewerRefreshes refreshes) {
        super(settings, refreshes);
    }

    @Override
    JPanel buildSection() {

        return CollapsibleSection.buildFoldingSection(
            "cellAppearance",
            "Cell appearance",
            SettingRows.buildSectionBody(this::addRows));
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
        controls.add(rows.buildOpacitySlider(
            "voidFillOpacity",
            "Void fill opacity",
            opacity -> settings.voidFillOpacity = (int) opacity));
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

        controls.add(rows.buildOpacitySlider(
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

        controls.add(rows.buildOpacitySlider(
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
        controls.add(rows.buildToggle(
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

        controls.add(rows.buildOpacitySlider(
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
        // Remembered under their own names rather than under their labels, as every other
        // control here is. A label is copy: reworded, a control keyed by one loses whatever was
        // set and comes back at its default, with nothing to say why.
        controls.add(ControlRows.buildToggleRow(
            refreshes::repaintMap,
            new ControlRows.Toggle(
                "jitterOwned",
                "Jitter owned",
                true,
                on -> settings.jitterOwned = on),
            new ControlRows.Toggle(
                "jitterUnowned",
                "Jitter unowned",
                false,
                on -> settings.jitterUnowned = on)));

        controls.add(rows.buildSlider(
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

        controls.add(rows.buildOpacitySlider(
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
}
