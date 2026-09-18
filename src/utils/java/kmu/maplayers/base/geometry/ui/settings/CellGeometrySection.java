package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.ControlRows;
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
final class CellGeometrySection {

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

    private final ViewerSettings settings;

    private final ViewerRefreshes refreshes;

    private final SettingRows rows;

    CellGeometrySection(ViewerSettings settings, ViewerRefreshes refreshes) {

        this.settings = settings;
        this.refreshes = refreshes;
        this.rows = new SettingRows(refreshes);
    }

    /**
     * @return the section, ready to put in the column
     */
    JPanel buildSection() {

        return CollapsibleSection.buildFoldingSection(
            "cellGeometry",
            "Cell geometry",
            SettingRows.buildSectionBody(this::addRows));
    }

    // The five knobs that decide what shape the cells are. Every one of them rebuilds the
    // partition, which is what makes them geometry rather than paint.
    private void addRows(JPanel controls) {

        // How far a cell reaches, and how deep the channel cut into its border is. Paired
        // because they are the two lengths the whole partition is measured in, and a reader
        // setting either is judging it against the other.
        controls.add(SliderRows.buildSliderPair(
            rows.buildSliderSpec(
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
                    settings.parameters.miterSpikeLimit())),
            rows.buildSliderSpec(
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
                    settings.parameters.miterSpikeLimit()))));

        // Which edges the channel above is actually cut into. Directly under the row carrying
        // the depth because the two are one statement between them - how deep, and where - and
        // a reader who has just moved the depth and seen nothing move is a reader whose edges
        // are all seams.
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

        // The two tolerances the ring tracer is held to, on one line: what counts as one corner
        // rather than two, and how far a join may run out before it is cut back. Both are about
        // what the trace forgives, and neither is read without the other.
        controls.add(SliderRows.buildSliderPair(
            rows.buildSliderSpec(
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
                    settings.parameters.miterSpikeLimit())),
            rows.buildSliderSpec(
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
                    value))));

        controls.add(rows.buildSlider(
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
}
