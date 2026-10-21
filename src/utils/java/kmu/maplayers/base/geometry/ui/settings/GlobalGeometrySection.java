package kmu.maplayers.base.geometry.ui.settings;

import kmlib.math.percentages.Percentages;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.SliderRows;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

import java.util.List;

import javax.swing.JPanel;

/**
 * What every line on the map is drawn by, whichever construction drew it.
 *
 * <p>Its own class because its knobs answer to no section: the smoothing reaches the cluster
 * borders as readily as the coasts, where a run may land is asked of every coast alike, and
 * the two floors are the terms BOTH void constructions judge a shore in. A knob like that
 * kept inside a section is unreachable exactly when that section is folded away, and drawn as
 * disabled while still deciding what the rest of the map looks like.
 */
final class GlobalGeometrySection
        extends PanelSection {

    // How every rounded line on the map is rounded where it turns sharply - the coasts and
    // the cluster borders alike.
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
    private static final SliderRows.SliderRange ROUND_BELOW_RANGE =
        new SliderRows.SliderRange(0, 172);

    // The radius is how far back along each arm of a corner the arc starts, in map units.
    // Zero is the pass switched off, which is what every setting of it is judged against;
    // the ceiling is a quarter of a cell radius, past which the rounding stops touching only
    // the tip of a corner and begins reshaping the runs either side of it.
    private static final SliderRows.SliderRange ROUNDING_RADIUS_RANGE =
        new SliderRows.SliderRange(0, 1000);

    // The segment count decides how smooth each rounded corner comes out. One is the
    // coarsest thing that is still a cut rather than a point; the ceiling is past where more
    // segments stop being visible at the zoom a sector is read at.
    private static final SliderRows.SliderRange ROUNDING_SEGMENTS_RANGE =
        new SliderRows.SliderRange(1, 16);

    // The spike-sanding pass ahead of the rounding, which splices out a protrusion both
    // sharper than its angle and shallower than its height. Either at zero switches it off.
    //
    // The angle ceiling is a right angle: a corner blunter than that is shape, whatever its
    // height, and sanding it would flatten real geometry. The height ceiling is half a cell
    // radius, past which what is being spliced out is a peninsula rather than a needle.
    private static final SliderRows.SliderRange SPIKE_BELOW_RANGE =
        new SliderRows.SliderRange(0, 90);

    private static final SliderRows.SliderRange SPIKE_HEIGHT_RANGE =
        new SliderRows.SliderRange(0, 2000);

    // How little of its own border a cell may face the void with and still be walked through,
    // as a percentage of the whole turn. Zero is the bottom because it asks nothing, which is
    // the rule switched off and what every other knob is judged against.
    //
    // The ceiling sits at about the median stretch: half of what a sector offers is narrower
    // than a quarter turn, so at the top of this range half the coast is dropped outright and
    // what that costs is on screen rather than merely describable. The useful band is the
    // bottom fifth - a sliver worth losing is a few percent.
    private static final SliderRows.SliderRange LEAST_FRONTAGE_RANGE =
        new SliderRows.SliderRange(0, 25);

    // How much water a hole must hold to be drawn as a lake, in percent of one cell's area.
    // Zero is the floor switched off - every puddle kept - and the ceiling is a hundred
    // times the shipped floor: half a cell of water, which discards most of what a real
    // sector holds, so culling whole lakes is an experiment the slider can actually run.
    private static final SliderRows.SliderRange LEAST_LAKE_RANGE =
        new SliderRows.SliderRange(0, 50);

    private static final SliderKnob ROUND_BELOW = new SliderKnob(
        "roundBelowDegrees",
        "Round corners sharper than, in degrees",
        ROUND_BELOW_RANGE,
        ViewerSettings.ROUND_BELOW_DEGREES_DEFAULT,
        (settings, degrees) -> settings.roundBelowDegrees = degrees);

    private static final SliderKnob ROUNDING_RADIUS = new SliderKnob(
        "roundingRadius",
        "Corner rounding radius, in map units",
        ROUNDING_RADIUS_RANGE,
        ViewerSettings.ROUNDING_RADIUS_DEFAULT,
        (settings, radius) -> settings.roundingRadius = radius);

    // Rounded rather than truncated, so a slider standing a hair under a whole count reads as
    // that count rather than the one below it.
    private static final SliderKnob ROUNDING_SEGMENTS = new SliderKnob(
        "roundingSegments",
        "Segments per rounded corner",
        ROUNDING_SEGMENTS_RANGE,
        ViewerSettings.ROUNDING_SEGMENTS_DEFAULT,
        (settings, segments) -> settings.roundingSegments = (int) Math.round(segments));

    private static final SliderKnob SPIKE_BELOW = new SliderKnob(
        "spikeBelowDegrees",
        "Sand spikes sharper than, in degrees",
        SPIKE_BELOW_RANGE,
        ViewerSettings.SPIKE_BELOW_DEGREES_DEFAULT,
        (settings, degrees) -> settings.spikeBelowDegrees = degrees);

    private static final SliderKnob SPIKE_HEIGHT = new SliderKnob(
        "spikeHeight",
        "Tallest spike sanded, in map units",
        SPIKE_HEIGHT_RANGE,
        ViewerSettings.SPIKE_HEIGHT_DEFAULT,
        (settings, height) -> settings.spikeHeight = height);

    // Off, because the map is deliberately placed the other way - the row is here to put the
    // two placements side by side, not to offer a correction someone should leave on.
    private static final SwitchKnob LAND_WHERE_VISIBLE = new SwitchKnob(
        "shouldLandWhereVisible",
        "Land runs only where the far cell is visible",
        false,
        (settings, isOn) -> settings.shouldLandWhereVisible = isOn);

    // Both floors are asked as a percentage, because that is how anyone reading a map thinks
    // about how far a cell sticks out; what each floor means is documented at the field it
    // writes.
    private static final SliderKnob LEAST_FRONTAGE = new SliderKnob(
        "continentLeastFrontage",
        "Least frontage faced, in % of a cell",
        LEAST_FRONTAGE_RANGE,
        Percentages.convertFractionToPercent(ViewerSettings.CONTINENT_MIN_FRONTAGE_DEFAULT),
        (settings, percent) -> settings.continentMinFrontageShare =
            Percentages.convertPercentToFraction(percent));

    private static final SliderKnob LEAST_LAKE = new SliderKnob(
        "minLakeShare",
        "Least lake, in % of a cell",
        LEAST_LAKE_RANGE,
        Percentages.convertFractionToPercent(ViewerSettings.MIN_LAKE_SHARE_DEFAULT),
        (settings, percent) -> settings.minLakeShare =
            Percentages.convertPercentToFraction(percent));

    GlobalGeometrySection(ViewerSettings settings, ViewerRefreshes refreshes) {
        super(settings, refreshes);
    }

    /**
     * @return every setting this section offers
     */
    static List<ViewerKnob> listKnobs() {

        return List.of(
            ROUND_BELOW,
            ROUNDING_RADIUS,
            ROUNDING_SEGMENTS,
            SPIKE_BELOW,
            SPIKE_HEIGHT,
            LAND_WHERE_VISIBLE,
            LEAST_FRONTAGE,
            LEAST_LAKE);
    }

    @Override
    JPanel buildSection() {

        return CollapsibleSection.buildFoldingSection(
            "globalGeometry",
            "Global geometry",
            SettingRows.buildSectionBody(this::addRows));
    }

    private void addRows(JPanel controls) {

        addLineSmoothingRows(controls);
        addRunPlacementRows(controls);
        addShoreFloorRows(controls);
    }

    // What becomes of a drawn line where it turns sharply: which turns are rounded and to
    // what, then which protrusions are spliced out ahead of that rounding.
    //
    // Both passes here rather than the rounding alone, because the pair is one answer: the
    // sanding exists to hand the rounding geometry it can work on, and reading either without
    // the other says nothing about the line that comes out.
    //
    // Every one rebuilds rather than repaints. Each smoothed line is worked out once when its
    // geometry is built and carried on it, so moving any of these is a change to the geometry
    // the frame is drawn from rather than to the way that geometry is painted.
    private void addLineSmoothingRows(JPanel controls) {

        // First of the three, because it is the one that decides whether the others do
        // anything: at the bottom of its range nothing is rounded whatever they say, and at
        // the top every join between two runs of line is.
        controls.add(rebuildRows.buildSlider(ROUND_BELOW));

        // The two that say how a corner is drawn once it has been judged one, on a line of
        // their own: neither means anything without the other.
        controls.add(rebuildRows.buildSliderPair(ROUNDING_RADIUS, ROUNDING_SEGMENTS));

        // The pass that runs BEFORE the three above, and so is read after them: what it takes
        // out is what the rounding would otherwise be handed and be unable to fix. Paired
        // because a spike is what it is by BOTH its angle and its height.
        controls.add(rebuildRows.buildSliderPair(SPIKE_BELOW, SPIKE_HEIGHT));
    }

    // Where a straight run is allowed to land on the cell it reaches, which every coast on the
    // map is placed by. Global for the reason the smoothing knobs are: it decides how a coast is
    // PLACED rather than which coast is being traced.
    private void addRunPlacementRows(JPanel controls) {
        controls.add(rebuildRows.buildToggle(LAND_WHERE_VISIBLE));
    }

    // How much water there has to be before it is drawn a shore at all.
    //
    // Global rather than v3's. They are the terms a shore is judged in - how far a cell must
    // face the water, how much water there must be - and the one way to tell two
    // constructions apart is to build both under the same terms: v3 traces its lakes under
    // them, and v4 lays the coasts of those same lakes, so the lake floor is what decides for
    // both which holes are lakes and which are left for the puddle tier.
    //
    // On one line because they work as a pair: the frontage one judges a cell's stretch of
    // shore, the puddle one a whole lake.
    private void addShoreFloorRows(JPanel controls) {
        controls.add(coastRows.buildSliderPair(LEAST_FRONTAGE, LEAST_LAKE));
    }
}
