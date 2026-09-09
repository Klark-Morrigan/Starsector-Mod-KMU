package kmu.maplayers.base.geometry;

import kmu.maplayers.base.render.clusters.BorderSmoothing;
import kmu.maplayers.base.theme.BorderSmoothingStyle;
import kmu.maplayers.base.theme.CornerRoundingStyle;
import kmu.maplayers.base.theme.SpikeSandingStyle;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One sector as it is currently being drawn: the geometry, the lines smoothing has already
 * been through, and the knobs everything else about the picture follows from.
 *
 * <p>Exists so that two drawings of one map cannot disagree. A drawing handed the raw geometry
 * and left to work the rest out arrives at its own answer - its own knobs, its own trace, its
 * own smoothing - and the result is a picture of a map nobody was looking at, which is exactly
 * what a picture is for ruling out. Handed this, every drawing is working from what the last
 * rebuild actually produced.
 *
 * <p>Carries the KNOBS as well as the shapes because not everything drawn is worth keeping a
 * shape for: the void a construction shut in is cheap to find again and expensive to hold, so
 * a drawing finds it - but finds it under the settings the rest of the picture was built
 * under rather than under the defaults.
 *
 * <p>Deliberately not the window's own settings object. That belongs to the window, is
 * mutable, and says what the map COULD be drawn with; this says what it was drawn with, and
 * is a value a report can be handed without reaching back into a live panel.
 *
 * @param geometry             the cells, their owners and the cluster rings traced from them
 * @param smoothedRingsByOwner each owner's cluster rings after the smoothing passes - the
 *                             line a border is actually stroked along, which the rings on the
 *                             geometry above are not
 * @param coast                the sector coast as traced under the rules below: the border, and
 *                             so what anything measuring against the coast reads
 * @param roundedCoast         that same coast with its sharp joins taken off, which is what a
 *                             drawing strokes. Rounded once here rather than at each drawing,
 *                             so that two pictures of one map cannot round it differently and
 *                             so that a pass over tens of thousands of points is paid for once
 * @param parameters           the geometry knobs it was all built under
 * @param coastRules           the rules the coast was traced under, whose rounding produced the
 *                             line above
 * @param shaping              how far the void shapes are pulled back from what closed them in
 */
public record DrawnSector(
    SectorGeometry geometry,
    Map<String, List<List<double[]>>> smoothedRingsByOwner,
    Coastlines.TracedCoasts coast,
    CoastRounding.RoundedCoasts roundedCoast,
    SectorGeometryParameters parameters,
    Coastlines.CoastRules coastRules,
    VoidPockets.PocketShaping shaping) {

    // How tall a protrusion may be and still be spliced out ahead of the rounding, and how
    // sharp it has to be to count as one. The map offers the same two figures behind its own
    // settings, where nothing outside the game can read them - so these are a second answer
    // by necessity rather than by choice, and are named here so at least every drawing gives
    // the same one.
    private static final double DEFAULT_SPIKE_HEIGHT = 150.0;
    private static final double DEFAULT_SPIKE_BELOW_RADIANS = Math.toRadians(60);

    // How a line is smoothed when nobody has chosen: the coast's own rounding, with the
    // spike pass ahead of it at the numbers above.
    //
    // Here rather than at each drawing, because a drawing that named its own would be a
    // second answer to how the map looks - which is the thing this record exists to stop.
    // Both gates on, with a zero knob standing in for off, so what each pass does is decided
    // by the numbers alone.
    public static final BorderSmoothingStyle DEFAULT_SMOOTHING = new BorderSmoothingStyle(
        new SpikeSandingStyle(true, DEFAULT_SPIKE_HEIGHT, DEFAULT_SPIKE_BELOW_RADIANS),
        new CornerRoundingStyle(
            true,
            Coastlines.DEFAULT_ROUNDING.radius(),
            Coastlines.DEFAULT_ROUNDING.segmentsPerCorner(),
            Coastlines.DEFAULT_ROUNDING.bevelBelowAngleRadians(),
            Coastlines.DEFAULT_ROUNDING.roundBelowAngleRadians()));

    /**
     * Builds the sector as it would be drawn under one set of knobs.
     *
     * <p>The one place geometry becomes a picture. Every drawing goes through here so that
     * none of them can smooth a border its own way, trace a coast under knobs of its own, or
     * quietly draw the vertices under a line rather than the line.
     *
     * @param fixture    the sector to build from
     * @param geometry   the geometry already built from it, under the same parameters
     * @param parameters the knobs that geometry was built under, which the coast is traced
     *                   under too
     * @param smoothing  how the cluster borders are smoothed
     * @param rules      the rules to trace the coast under
     * @param shaping    how far the void shapes are pulled back from what closed them in
     * @return the sector as those knobs draw it
     */
    public static DrawnSector buildDrawnSector(
            SectorFixture fixture,
            SectorGeometry geometry,
            SectorGeometryParameters parameters,
            BorderSmoothingStyle smoothing,
            Coastlines.CoastRules rules,
            VoidPockets.PocketShaping shaping) {

        var smoothed = new LinkedHashMap<String, List<List<double[]>>>();

        for (var byOwner : geometry.ringsByOwner().entrySet()) {
            smoothed.put(
                byOwner.getKey(),
                BorderSmoothing.smoothBorderLoops(byOwner.getValue(), smoothing));
        }

        var coast = SettledCoast.traceAcrossBridges(fixture.getSites(), parameters, rules);

        return new DrawnSector(
            geometry,
            smoothed,
            coast,
            CoastRounding.roundTracedCoasts(coast, rules.rounding()),
            parameters,
            rules,
            shaping);
    }
}
