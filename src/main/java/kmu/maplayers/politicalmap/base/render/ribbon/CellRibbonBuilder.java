package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolylineBands;
import kmlib.math.geometry.RingPath;
import kmlib.opengl.GlVertexRuns;

import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays one cell's planned band around that cell's own ring: the path it runs along, how long a
 * width is worth on it, and the triangles each run comes out as.
 *
 * <p>Everything the plan states is proportional - a run is so many widths long - and everything
 * this settles is the cell's own: where the ring lets a band run, and how much of that ring one
 * width may take before the band would outrun it. Splitting the two is what lets the counting
 * rule be stated over hand-built standings while the geometry is stated over hand-built rings,
 * with neither needing the other's inputs.
 *
 * <p>Pure over a ring and a plan - no sector, no settings read, no GL - so a corner split, a
 * compressed band, and a cell too small to hold one are all posed directly.
 */
public final class CellRibbonBuilder {

    private CellRibbonBuilder() {
    }

    /**
     * Builds one cell's band, or nothing where the cell cannot carry one.
     *
     * <p>A cell drops out for either of two reasons, and both are the design's answer rather
     * than a failure: it plans no band at all (nothing but the bloc it is painted for is present
     * in it), or its ring has no room to hold one clear of its own border - a cell smaller than
     * the pad and width together, or one narrowed to a neck somewhere along it.
     *
     * @param ring       the cell's painted outline, the shape the band runs inside
     * @param topAnchor  the {x, y} the band's start is found above - the cell's own site, so
     *                   every cell begins its band at its top centre and runs clockwise from
     *                   there
     * @param plan       the cell's runs in draw order
     * @param style      the sizes the band is drawn at
     * @return the baked band, or {@link CellRibbon#NONE} where the cell draws none
     */
    public static CellRibbon buildCellRibbon(
            List<double[]> ring,
            double[] topAnchor,
            RibbonPlan plan,
            RibbonStyle style) {

        var totalLengthUnits = plan.sumLengthUnits();
        if (totalLengthUnits <= 0) {
            return CellRibbon.NONE;
        }
        var path = RingPath.traceInsetRing(
            ring,
            style.computeCentrelineInset(),
            style.miterSpikeLimit(),
            topAnchor);

        if (path.isEmpty()) {
            return CellRibbon.NONE;
        }
        var lengthUnitWorld = computeLengthUnit(path.getPerimeter(), totalLengthUnits, style);

        // A unit compressed to nothing is a cell whose ring is too short to state its own
        // contents at any size, so it says nothing rather than drawing a smear of colour where
        // the runs would have been.
        if (lengthUnitWorld < Limits.MIN_EDGE_LENGTH) {
            return CellRibbon.NONE;
        }
        return new CellRibbon(strokeSegments(path, plan, lengthUnitWorld, style));
    }

    // How much of the ring one width is worth on this cell: the authored width, unless the whole
    // band would then outrun the ring, in which case exactly as much as makes it fit.
    //
    // Compressing the length while leaving the width alone is deliberate. The band says its piece
    // through the proportions between its runs, and shortening every run by one factor keeps all
    // of them - a crowded system reads as crowded, with each bloc's share intact. Thinning the
    // band instead would fade the whole readout on precisely the cells carrying the most to say,
    // and would leave the path traced at an inset the band no longer matches.
    private static double computeLengthUnit(
            double perimeter,
            int totalLengthUnits,
            RibbonStyle style) {

        return Math.min(style.widthWorld(), perimeter / totalLengthUnits);
    }

    // Each run stroked over its own stretch of the path, laid end to end from the start.
    //
    // A run is stroked from the corners the path turns at within its stretch, not from its two
    // ends, so a run spanning a corner of the cell turns with it. Consecutive runs butt at the
    // distance they share rather than being joined: they are different colours, so there is no
    // join to make - what would be one band's mitre is two bands' shared edge.
    private static List<RibbonBand> strokeSegments(
            RingPath path,
            RibbonPlan plan,
            double lengthUnitWorld,
            RibbonStyle style) {

        var bands = new ArrayList<RibbonBand>(plan.segments().size());
        var arcLength = 0.0;

        for (var segment : plan.segments()) {
            var span = segment.lengthUnits() * lengthUnitWorld;
            var triangles = PolylineBands.strokeToTriangles(
                path.collectPointsBetween(arcLength, arcLength + span),
                style.widthWorld(),
                style.miterSpikeLimit());

            // A run that came out with no area - a zero-length segment in the plan, or a stretch
            // the path collapsed - is left out rather than kept as an empty draw.
            if (!triangles.isEmpty()) {
                bands.add(new RibbonBand(
                    segment.colour(),
                    GlVertexRuns.flattenVertices(triangles)));
            }
            arcLength += span;
        }
        return bands;
    }
}
