package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.SliderRows;
import kmu.maplayers.base.geometry.EdgeInsetRule;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.List;

import javax.swing.JPanel;

/**
 * The knobs that decide what shape the cells are.
 *
 * <p>Every one of them rebuilds the partition, which is what makes them geometry rather than
 * paint - and what separates this from the appearance beside it. Always answered, so the
 * section folds but does not switch: there is no state in which the map has no cells.
 */
final class CellGeometrySection
        extends PanelSection {

    // Slider ranges: wide enough either side of the shipped defaults to see a knob's effect
    // break down, not just vary. The reach floor sits below any real system spacing and the
    // ceiling well past it, so both "cells never meet" and "cells swallow the sector" are
    // reachable; the weld range spans the chord sagitta that makes clusters chain or not.
    private static final SliderRows.SliderRange REACH_RANGE =
        new SliderRows.SliderRange(500, 12000);

    private static final SliderRows.SliderRange INSET_RANGE =
        new SliderRows.SliderRange(0, 800);

    private static final SliderRows.SliderRange WELD_RANGE =
        new SliderRows.SliderRange(0, 400);

    private static final SliderRows.SliderRange MITER_RANGE =
        new SliderRows.SliderRange(1, 12);

    private static final SliderRows.SliderRange SEGMENTS_RANGE =
        new SliderRows.SliderRange(3, 96);

    // What every knob here opens on: the geometry the mod ships.
    private static final SectorGeometryParameters SHIPPED =
        SectorGeometryParameters.createDefaults();

    private static final SliderKnob CELL_RADIUS = new SliderKnob(
        "cellRadius",
        "Cell reach (cell radius)",
        REACH_RANGE,
        SHIPPED.cellRadius(),
        (settings, value) -> settings.parameters = settings.parameters.withCellRadius(value));

    private static final SliderKnob BORDER_INSET = new SliderKnob(
        "borderInset",
        "Border channel (inset)",
        INSET_RANGE,
        SHIPPED.borderInset(),
        (settings, value) -> settings.parameters = settings.parameters.withBorderInset(value));

    // The map's own rule to open on, so the window opens on the map.
    private static final RadioKnob<EdgeInsetRule> CELL_INSET_RULE = new RadioKnob<>(
        "cellInsetRule",
        "Cell insets",
        EdgeInsetPicks.INSET_RULE_PICKS,
        EdgeInsetRule.AT_EVERY_BORDER,
        (settings, rule) -> settings.cellInsetRule = rule);

    private static final SliderKnob WELD_TOLERANCE = new SliderKnob(
        "weldTolerance",
        "Weld tolerance",
        WELD_RANGE,
        SHIPPED.weldTolerance(),
        (settings, value) -> settings.parameters = settings.parameters.withWeldTolerance(value));

    private static final SliderKnob MITER_SPIKE_LIMIT = new SliderKnob(
        "miterSpikeLimit",
        "Miter spike limit",
        MITER_RANGE,
        SHIPPED.miterSpikeLimit(),
        (settings, value) -> settings.parameters =
            settings.parameters.withMiterSpikeLimit(value));

    private static final SliderKnob BOUND_SEGMENTS = new SliderKnob(
        "cellBoundSegments",
        "Cell bound segments",
        SEGMENTS_RANGE,
        SHIPPED.boundSegments(),
        (settings, value) -> settings.parameters =
            settings.parameters.withBoundSegments((int) Math.round(value)));

    CellGeometrySection(ViewerSettings settings, ViewerRefreshes refreshes) {
        super(settings, refreshes);
    }

    /**
     * @return every setting this section offers
     */
    static List<ViewerKnob> listKnobs() {

        return List.of(
            CELL_RADIUS,
            BORDER_INSET,
            CELL_INSET_RULE,
            WELD_TOLERANCE,
            MITER_SPIKE_LIMIT,
            BOUND_SEGMENTS);
    }

    @Override
    JPanel buildSection() {

        return CollapsibleSection.buildFoldingSection(
            "cellGeometry",
            "Cell geometry",
            SettingRows.buildSectionBody(this::addRows));
    }

    private void addRows(JPanel controls) {

        // How far a cell reaches, and how deep the channel cut into its border is. Paired
        // because they are the two lengths the whole partition is measured in, and a reader
        // setting either is judging it against the other.
        controls.add(rebuildRows.buildSliderPair(CELL_RADIUS, BORDER_INSET));

        // Which edges the channel above is actually cut into. Directly under the row carrying
        // the depth because the two are one statement between them - how deep, and where - and
        // a reader who has just moved the depth and seen nothing move is a reader whose edges
        // are all seams.
        //
        // A rebuild rather than a repaint: the rule decides the fill polygon, not its colour.
        controls.add(rebuildRows.buildRadio(CELL_INSET_RULE));

        // The two tolerances the ring tracer is held to, on one line: what counts as one corner
        // rather than two, and how far a join may run out before it is cut back. Both are about
        // what the trace forgives, and neither is read without the other.
        controls.add(rebuildRows.buildSliderPair(WELD_TOLERANCE, MITER_SPIKE_LIMIT));
        controls.add(rebuildRows.buildSlider(BOUND_SEGMENTS));
    }
}
