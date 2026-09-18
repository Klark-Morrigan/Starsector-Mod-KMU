package kmu.maplayers.base.geometry.ui.settings;

import kmu.desktop.ui.swing.CollapsibleSection;
import kmu.desktop.ui.swing.SliderRows;
import kmu.maplayers.base.geometry.settings.ViewerSettings;

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
final class GlobalGeometrySection extends PanelSection {

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

    GlobalGeometrySection(ViewerSettings settings, ViewerRefreshes refreshes) {
        super(settings, refreshes);
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
        controls.add(rows.buildSlider(
            "roundBelowDegrees",
            "Round corners sharper than, in degrees",
            ROUND_BELOW_MINIMUM,
            ROUND_BELOW_MAXIMUM,
            ViewerSettings.ROUND_BELOW_DEGREES_DEFAULT,
            degrees -> settings.roundBelowDegrees = degrees));

        // The two that say how a corner is drawn once it has been judged one, on a line of
        // their own: neither means anything without the other, and a reader setting a radius
        // is already looking at the segment count it will be drawn with.
        controls.add(SliderRows.buildSliderPair(
            rows.buildSliderSpec(
                "roundingRadius",
                "Corner rounding radius, in map units",
                ROUNDING_RADIUS_MINIMUM,
                ROUNDING_RADIUS_MAXIMUM,
                ViewerSettings.ROUNDING_RADIUS_DEFAULT,
                radius -> settings.roundingRadius = radius),
            rows.buildSliderSpec(
                "roundingSegments",
                "Segments per rounded corner",
                ROUNDING_SEGMENTS_MINIMUM,
                ROUNDING_SEGMENTS_MAXIMUM,
                ViewerSettings.ROUNDING_SEGMENTS_DEFAULT,
                segments -> settings.roundingSegments = (int) segments)));

        // The pass that runs BEFORE the three above, and so is read after them: what it takes
        // out is what the rounding would otherwise be handed and be unable to fix.
        //
        // Paired for the reason the two above are - a spike is what it is by BOTH its angle and
        // its height, and either alone decides nothing.
        controls.add(SliderRows.buildSliderPair(
            rows.buildSliderSpec(
                "spikeBelowDegrees",
                "Sand spikes sharper than, in degrees",
                SPIKE_BELOW_MINIMUM,
                SPIKE_BELOW_MAXIMUM,
                ViewerSettings.SPIKE_BELOW_DEGREES_DEFAULT,
                degrees -> settings.spikeBelowDegrees = degrees),
            rows.buildSliderSpec(
                "spikeHeight",
                "Tallest spike sanded, in map units",
                SPIKE_HEIGHT_MINIMUM,
                SPIKE_HEIGHT_MAXIMUM,
                ViewerSettings.SPIKE_HEIGHT_DEFAULT,
                height -> settings.spikeHeight = height)));
    }

    // Where a straight run is allowed to land on the cell it reaches, which every coast on the
    // map is placed by.
    //
    // Global for the reason the smoothing knobs are: it decides how a coast is PLACED rather
    // than which coast is being traced, so it reaches every line on the map alike.
    private void addRunPlacementRows(JPanel controls) {

        // Off, because the map is deliberately placed the other way - the row is here to put
        // the two placements side by side, not to offer a correction someone should leave on.
        controls.add(rows.buildToggle(
            "shouldLandWhereVisible",
            "Land runs only where the far cell is visible",
            false,
            on -> settings.shouldLandWhereVisible = on));
    }

    // How much water there has to be before it is drawn a shore at all.
    //
    // Global rather than v3's, though only v3 reads them today. They are the terms a shore is
    // judged in - how far a cell must face the water, how much water there must be - and the
    // one way to tell two constructions apart is to build both under the same terms. Left in
    // the v3 section they would be v3's own dial, and a v4 built to different floors could
    // differ from it for that reason alone with nothing on screen saying so.
    private void addShoreFloorRows(JPanel controls) {

        // On one line because they work as a pair: the frontage one judges a cell's stretch of
        // shore, the puddle one a whole lake. Both asked as a percentage because that is how
        // anyone reading a map thinks about how far a cell sticks out, and both at the same
        // scale; what each floor means is documented at the field it writes.
        controls.add(SliderRows.buildSliderPair(
            new SliderRows.SliderSpec(
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
                    () -> { })),
            new SliderRows.SliderSpec(
                "minLakeShare",
                "Least lake, in % of a cell",
                new SliderRows.SliderRange(
                    MIN_LAKE_MINIMUM,
                    MIN_LAKE_MAXIMUM,
                    ViewerSettings.MIN_LAKE_SHARE_DEFAULT
                        * ViewerSettings.FRONTAGE_PERCENT_SCALE),
                new SliderRows.SliderWork(
                    percent -> settings.minLakeShare =
                        percent / ViewerSettings.FRONTAGE_PERCENT_SCALE,
                    refreshes::refreshCoastlines,
                    () -> { }))));
    }
}
