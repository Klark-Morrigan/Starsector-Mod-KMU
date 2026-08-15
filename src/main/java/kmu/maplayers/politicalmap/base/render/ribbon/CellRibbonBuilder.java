package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolylineBands;
import kmlib.math.geometry.RingPath;
import kmlib.math.geometry.RingStretch;
import kmlib.opengl.GlVertexRuns;

import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays one cell's planned band around that cell's own ring: the path it runs along, the longest
 * stretch of that path the cluster names leave it, how long a width is worth on that stretch,
 * where along it the band sits, and the triangles each run comes out as.
 *
 * <p>Everything the plan states is proportional - a run is so many widths long - and everything
 * this settles is the cell's own: where the ring lets a band run, and how much of that ring one
 * width may take before the band would outrun it. Splitting the two is what lets the counting
 * rule be stated over hand-built standings while the geometry is stated over hand-built rings,
 * with neither needing the other's inputs.
 *
 * <p>Pure over a ring, a plan and the names' boxes - no sector, no settings read, no GL - so a
 * corner split, a compressed band, a name lying across the ring, and a cell too small to hold a
 * band at all are all posed directly.
 */
public final class CellRibbonBuilder {

    private CellRibbonBuilder() {
    }

    /**
     * Builds one cell's band, or nothing where the cell cannot carry one.
     *
     * <p>A cell drops out for either of two reasons, and both are the design's answer rather
     * than a failure: it plans no band at all (nothing but the bloc it is painted for is present
     * in it), or its ring has no room to hold one - a cell smaller than the pad and width
     * together, one narrowed to a neck somewhere along it, or one whose ring the names cover so
     * far round that what is left could not state the plan at any size.
     *
     * @param ring         the cell's painted outline, the shape the band runs inside
     * @param topAnchor    the {x, y} the path's start is found above - the cell's own site, so
     *                     every cell begins its band as near its top centre as the names leave
     *                     room for, and runs clockwise from there
     * @param plan         the cell's runs in draw order
     * @param style        the sizes the band is drawn at
     * @param nameBoxes    the room the drawn cluster names take up, as world rings the band
     *                     keeps out of; the whole map's, since a name sits where its own
     *                     cluster is roomiest and that can be over this cell
     * @return the baked band, or {@link CellRibbon#NONE} where the cell draws none
     */
    public static CellRibbon buildCellRibbon(
            List<double[]> ring,
            double[] topAnchor,
            RibbonPlan plan,
            RibbonStyle style,
            List<List<double[]>> nameBoxes) {

        var totalLengthUnits = plan.sumLengthUnits();
        if (totalLengthUnits <= 0) {
            return CellRibbon.NONE;
        }
        var path = RibbonPathTracer.traceLaidRibbonPath(ring, topAnchor, style);

        if (path.isEmpty()) {
            return CellRibbon.NONE;
        }

        // The names are carved off the path rather than off the plan, and that is the whole of
        // why a band and a name no longer share room. Cutting the plan instead would leave a run
        // partly eaten, and a run's length is the readout - a colony drawn two thirds as long as
        // its neighbours says something false about the system. Carved off the path, what a name
        // costs is room, which the clamp below already knows how to answer.
        var clearArcs = path.findClearArcs(nameBoxes);

        // A ring wholly under the names is the one case where keeping the band clear of them and
        // drawing it at all cannot both be honoured, so one of the two gives way and which is the
        // player's answer. By default the clearance does: a cell reporting nothing and a cell
        // refused the room to report look identical to a reader, and only one of them is true.
        if (clearArcs.isEmpty() && !style.isBandAlwaysDrawn()) {
            return CellRibbon.NONE;
        }
        var stretch = clearArcs.isEmpty()
            ? new RingStretch(0.0, path.getPerimeter())
            : selectLongestClearStretch(path.fuseStretchAcrossStart(clearArcs));

        var lengthUnitWorld = computeLengthUnit(stretch.computeLength(), totalLengthUnits, style);

        // A unit compressed to nothing is a cell with too little ring left to state its own
        // contents at any size - too small to begin with, or too much of it under a name - so it
        // says nothing rather than drawing a smear of colour where the runs would have been.
        if (lengthUnitWorld < Limits.MIN_EDGE_LENGTH) {
            return CellRibbon.NONE;
        }
        // As near the cell's top centre as the stretch allows, rather than wherever the ring
        // the names left happens to open. Every cell's band is read from that landmark - the
        // dominant bloc first, running clockwise - so a band that could begin there and does
        // not costs the reader the one thing every cell's band has in common.
        var ribbonStart = path.placeSpanNearestStart(stretch, totalLengthUnits * lengthUnitWorld);

        return new CellRibbon(strokeSegments(path, plan, ribbonStart, lengthUnitWorld, style));
    }

    // The one stretch of ring the whole band is laid on: the longest the names left, with the
    // rest of the ring staying bare.
    //
    // A band scattered round the ring in pieces is not the readout it looks like: a reader
    // cannot tell one run interrupted by a name from two runs of one colour, so a band split
    // across stretches loses the very proportions the carve was protecting and litters the cell
    // for it. One stretch means one shape, and a cell can be read at a glance again - at the
    // price of spending only part of a ring the names cut into halves, which is the cheaper of
    // the two losses.
    //
    // Taken over the stretches the path has already fused, never the carved ones: a cell whose
    // name sits anywhere but its top centre has its longest run arrive as the two stretches
    // either side of the path's start, and picking between those two judges the cell on
    // whichever half happened to be bigger.
    private static RingStretch selectLongestClearStretch(List<RingStretch> clearStretches) {

        RingStretch longest = null;

        for (var stretch : clearStretches) {
            if (longest == null || stretch.computeLength() > longest.computeLength()) {
                longest = stretch;
            }
        }
        return longest;
    }

    // How much of the ring one width is worth on this cell: the authored width, unless the whole
    // band would then outrun the stretch it is laid on, in which case exactly as much as makes it
    // fit.
    //
    // Measured against that one stretch rather than against every clear stretch together, since
    // the ring the band is not laid on is ring the band does not spend. A cell whose name covers
    // much of its outline therefore compresses harder, which is the honest number.
    //
    // Compressing the length while leaving the width alone is deliberate. The band says its piece
    // through the proportions between its runs, and shortening every run by one factor keeps all
    // of them - a crowded system reads as crowded, with each bloc's share intact. Thinning the
    // band instead would fade the whole readout on precisely the cells carrying the most to say,
    // and would leave the path traced at an inset the band no longer matches.
    private static double computeLengthUnit(
            double stretchLength,
            int totalLengthUnits,
            RibbonStyle style) {

        return Math.min(style.widthWorld(), stretchLength / totalLengthUnits);
    }

    // The plan laid end to end along the chosen stretch and stroked as the one band it is, each
    // run coming back out of the stroke in its own colour.
    //
    // The band is one shape whatever it is coloured in: stroking each run on its own leaves every
    // boundary between two runs a pair of square ends butted together, which opens a wedge
    // wherever that boundary lands on a corner of the path - and a cell's ring is rounded, so
    // most boundaries land on one. Stroked once, a boundary is a point the band turns at like any
    // other, and only the band's own two ends are square.
    private static List<RibbonBand> strokeSegments(
            RingPath path,
            RibbonPlan plan,
            double startArcLength,
            double lengthUnitWorld,
            RibbonStyle style) {

        var spans = new ArrayList<List<double[]>>(plan.segments().size());
        var cursor = startArcLength;

        // A run's stretch carries the corners the path turns at within it, not just its two ends,
        // so a run spanning a corner of the cell turns with it rather than cutting across.
        for (var segment : plan.segments()) {

            var runEnd = cursor + segment.lengthUnits() * lengthUnitWorld;

            spans.add(path.collectPointsBetween(cursor, runEnd));
            cursor = runEnd;
        }
        return collectStrokedBands(plan, PolylineBands.strokeSpansToTriangles(
            spans,
            style.widthWorld(),
            style.miterSpikeLimit()));
    }

    // One band per run that drew something, in the run's own colour. A run of no length strokes
    // to nothing - a plan may carry one - and it is dropped rather than kept as an empty band,
    // since the renderer's work is per band and an empty one is a draw call for no triangles.
    private static List<RibbonBand> collectStrokedBands(
            RibbonPlan plan,
            List<List<double[]>> strokedSpans) {

        var bands = new ArrayList<RibbonBand>(strokedSpans.size());

        for (var segment = 0; segment < strokedSpans.size(); segment++) {

            if (strokedSpans.get(segment).isEmpty()) {
                continue;
            }
            bands.add(new RibbonBand(
                plan.segments().get(segment).colour(),
                GlVertexRuns.flattenVertices(strokedSpans.get(segment))));
        }
        return bands;
    }
}
