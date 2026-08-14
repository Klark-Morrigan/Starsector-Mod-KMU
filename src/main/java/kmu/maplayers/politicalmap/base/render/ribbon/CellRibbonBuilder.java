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

    // The whole band stroked in one piece and cut into its runs afterwards, rather than a run
    // at a time.
    //
    // The band is one shape whatever it is coloured in: stroking each run on its own leaves
    // every boundary between two runs a pair of square ends butted together, which opens a wedge
    // wherever that boundary lands on a corner of the path - and a cell's ring is rounded, so
    // most boundaries land on one. Stroked once, a boundary is a point the band turns at like
    // any other, and only the band's two outer ends are left square.
    private static List<RibbonBand> strokeSegments(
            RingPath path,
            RibbonPlan plan,
            double lengthUnitWorld,
            RibbonStyle style) {

        var strokedSpans = PolylineBands.strokeSpansToTriangles(
            collectSegmentSpans(path, plan, lengthUnitWorld),
            style.widthWorld(),
            style.miterSpikeLimit());

        var bands = new ArrayList<RibbonBand>(strokedSpans.size());

        for (var segment = 0; segment < strokedSpans.size(); segment++) {

            var triangles = strokedSpans.get(segment);

            // A run that came out with no area - a zero-length segment in the plan, or a stretch
            // the path collapsed - is left out rather than kept as an empty draw. The stroker
            // keeps such a run in place rather than dropping it, so a run and its colour are
            // still read off each other by position here.
            if (!triangles.isEmpty()) {
                bands.add(new RibbonBand(
                    plan.segments().get(segment).colour(),
                    GlVertexRuns.flattenVertices(triangles)));
            }
        }
        return bands;
    }

    // Each run's own stretch of the path, laid end to end from the start.
    //
    // A stretch carries the corners the path turns at within it, not just its two ends, so a run
    // spanning a corner of the cell turns with it rather than cutting across.
    private static List<List<double[]>> collectSegmentSpans(
            RingPath path,
            RibbonPlan plan,
            double lengthUnitWorld) {

        var spans = new ArrayList<List<double[]>>(plan.segments().size());
        var arcLength = 0.0;

        for (var segment : plan.segments()) {

            var span = segment.lengthUnits() * lengthUnitWorld;

            spans.add(path.collectPointsBetween(arcLength, arcLength + span));
            arcLength += span;
        }
        return spans;
    }
}
