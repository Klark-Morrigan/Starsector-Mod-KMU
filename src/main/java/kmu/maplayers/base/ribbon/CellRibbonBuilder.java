package kmu.maplayers.base.ribbon;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolylineBands;
import kmlib.math.geometry.RingPath;
import kmlib.math.geometry.RingStretch;
import kmlib.opengl.GlVertexRuns;
import kmlib.profiling.IterationScope;
import kmlib.starsector.systems.SystemKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays one cell's planned band along the path traced round that cell: the longest stretch of that
 * path the cluster names and the cell's own narrow places leave it, how long a width is worth on
 * that stretch, where along it the band sits, and the triangles each run comes out as.
 *
 * <p>Everything the plan states is proportional - a run is so many widths long - and everything
 * this settles is the cell's own: where the path lets a band run, and how much of it one width may
 * take before the band would outrun it. Splitting the two keeps the counting rule free of rings
 * and the geometry free of counts, with neither needing the other's inputs.
 *
 * <p>The one entry point a layer lays a cell's band through. A layer settles which cells are offered
 * a band and plans each by its own rule; everything from the plan onwards is the same for every
 * layer, so it is stated here once rather than by each layer's bake: the empty plan answered before
 * any ring is walked, the ring reached for through the cells' kept rings, and the carve and stroke.
 *
 * <p>The ring is reached for rather than traced afresh, because a path outlives the bake that
 * traced it - a ring moves when its cell is re-shaped, while a bake runs whenever a cluster name may
 * have moved. What is left to do per bake is the part that genuinely differs: the names have moved,
 * so the carve and everything after it is done again.
 *
 * <p>Pure geometry past that ring - no sector, no settings read, no GL - so a corner split, a
 * compressed band, a name lying across the ring, a ring narrowed to a neck in one place, and a cell
 * too small to hold a band at all are each settled by the path, the plan and the names' boxes alone.
 * The pass's scope is marked and never read, so what it records cannot change the band.
 *
 * <p>Three of a bake's four phases are marked here, and separately: what a cell spends reaching for
 * its ring, carving the names off it and stroking the result grow on different axes, so a bake that
 * slowed down is answered here rather than guessed at. The fourth, planning, is the layer's.
 */
public final class CellRibbonBuilder {

    private CellRibbonBuilder() {
    }

    /**
     * Lays one cell's planned band inside the ring that cell holds, or nothing where it draws none.
     *
     * <p>A cell comes back bare two ways. It planned no band at all, the layer finding nothing in
     * it to report; that is answered first, since such a cell must not pay for a ring walk nothing
     * would be laid on. Or its ring has no room to hold the band, which is the design's answer
     * rather than a failure.
     *
     * @param cellKey       the cell being laid, the key its traced rings are kept under
     * @param fillPolygon   the cell's painted outline, the ring the band runs inside
     * @param site          the {x, y} the band's start is found above
     * @param plan          the cell's runs in draw order, as the layer's rule planned them
     * @param style         the sizes every band in the pass is laid at
     * @param nameBoxes     the room the drawn cluster names take up, as world rings the band keeps
     *                      out of; the whole map's, since a name sits where its own cluster is
     *                      roomiest and that can be over this cell
     * @param ringPathCache the rings already walked inside the cells' current shapes, asked before
     *                      this cell's ring is walked and written back when it is
     * @param bakeScope     the pass's open scope, whose turn is this cell and on which this cell's
     *                      trace, carve and stroke are marked
     * @param bakeTally     the bake call's counts, to which this cell is added by what it came back
     *                      as and whether its ring had to be walked
     * @return the baked band, or {@link CellRibbon#NONE} where the cell draws none
     */
    public static CellRibbon layCellRibbon(
            SystemKey cellKey,
            List<double[]> fillPolygon,
            double[] site,
            RibbonPlan plan,
            RibbonStyle style,
            List<List<double[]>> nameBoxes,
            CellRingPathCache ringPathCache,
            IterationScope bakeScope,
            RibbonBakeTally bakeTally) {

        if (!plan.hasLength()) {

            bakeTally.countUnplannedCell();
            return CellRibbon.NONE;
        }

        var ringPath = RibbonPathTracer.traceLaidRibbonPath(
            cellKey,
            fillPolygon,
            site,
            style,
            ringPathCache,
            bakeTally);

        // What the phase covers is the whole reach for the ring rather than the walk alone, so a
        // bake over cells whose rings already stood reports the near-nothing a cache read costs -
        // which is the difference the kept rings are there to make, as the phase's own number
        // rather than a gap in somebody else's.
        bakeScope.markPhase(RibbonBakePhases.TRACE_PHASE);

        var ribbon = buildCellRibbon(
            ringPath,
            plan,
            style,
            nameBoxes,
            bakeScope);

        bakeTally.countLaidCell(ribbon);

        return ribbon;
    }

    /**
     * Builds one cell's band along a ring already traced, or nothing where that ring cannot carry
     * one.
     *
     * <p>A cell drops out where its ring has no room to hold the band - a cell smaller than the
     * pad and width together, or one whose ring the names and its own narrow places leave so
     * little of that what remains could not state the plan at any size. A cell narrowed to a neck
     * in one place is not that cell: the neck costs its own stretch of ring and the band goes on
     * the longest of what is left.
     *
     * @param path         the ring traced round the cell, at whichever inset it had room for
     * @param plan         the cell's runs in draw order, at least one of them with length
     * @param style        the sizes the band is drawn at, the same ones the path was traced at
     * @param nameBoxes    the room the drawn cluster names take up, as world rings the band
     *                     keeps out of
     * @param bakeScope    the pass's open scope, on which this cell's carve and stroke are marked
     * @return the baked band, or {@link CellRibbon#NONE} where the cell draws none
     */
    static CellRibbon buildCellRibbon(
            RingPath path,
            RibbonPlan plan,
            RibbonStyle style,
            List<List<double[]>> nameBoxes,
            IterationScope bakeScope) {

        // A cell whose ring held the band's inset nowhere: smaller than the pad and width
        // together, or too narrow for them along the whole of it. A cell pinched in one place
        // is not this cell - it keeps every stretch the pinch does not cost.
        if (!path.hasStretchHoldingItsInset()) {
            return CellRibbon.NONE;
        }

        var totalLengthUnits = plan.sumLengthUnits();
        var layout = layOutBand(path, nameBoxes, totalLengthUnits, style);

        bakeScope.markPhase(RibbonBakePhases.CARVE_PHASE);

        // A cell the carve left nothing to lay a band on - charged for the carve all the same,
        // since what it cost is what the pass spent finding that out.
        if (layout == null) {
            return CellRibbon.NONE;
        }

        var bands = strokeSegments(path, plan, layout, style);

        bakeScope.markPhase(RibbonBakePhases.STROKE_PHASE);

        return new CellRibbon(bands);
    }

    // Where along the cell's ring the band goes and how much of that ring one width is worth
    // there, or nothing at all where what the names and the cell's own shape leave could not
    // state the plan at any size.
    //
    // One block because it is one question - how much room is left, and where - and because it is
    // the span a bake charges its carve over: split across the caller, the phase would be timed
    // in pieces with the refusals falling outside it, which are the cells whose carve is most
    // worth knowing the cost of.
    private static RibbonBandLayout layOutBand(
            RingPath path,
            List<List<double[]>> nameBoxes,
            int totalLengthUnits,
            RibbonStyle style) {

        var stretch = findBandStretch(path, nameBoxes, style);
        return stretch == null
            ? null
            : placeBandOn(path, stretch, totalLengthUnits, style);
    }

    // The one stretch of the ring the band goes on, or nothing where the names cover the whole of
    // it and the style keeps them clear rather than drawing the band.
    private static RingStretch findBandStretch(
            RingPath path,
            List<List<double[]>> nameBoxes,
            RibbonStyle style) {

        // The names are carved off the path rather than off the plan, and that is the whole of
        // how a band keeps out of a name's room. Cutting the plan instead would leave a run
        // partly eaten, and a run's length is the readout - a run drawn two thirds as long as the
        // plan laid it says something false about the cell. Carved off the path, what a name
        // costs is room, which the clamp below already knows how to answer.
        var clearArcs = path.findClearArcs(nameBoxes);

        // A ring wholly under the names is the one case where keeping the band clear of them and
        // drawing it at all cannot both be honoured, so one of the two gives way and the style
        // says which. By default the clearance does: a cell reporting nothing and a cell
        // refused the room to report look identical to a reader, and only one of them is true.
        if (clearArcs.isEmpty() && !style.isBandAlwaysDrawn()) {
            return null;
        }

        // Forced, it is the names that give way and only the names: the path's own overrun
        // stretches stay carved, since a band laid across one hangs over the border of the very
        // cell it reports on - which is what the half width exists to prevent.
        //
        // The whole band goes on the one longest stretch, with the rest of the ring staying bare.
        // A band scattered round the ring in pieces is not the readout it looks like: a reader
        // cannot tell one run interrupted by a name from two runs of one colour, so a band split
        // across stretches loses the very proportions the carve was protecting and litters the cell
        // for it. One stretch means one shape, at the price of spending only part of a ring the
        // names cut into halves, which is the cheaper of the two losses.
        //
        // Taken over the fused stretches, never the carved ones: a cell whose name sits anywhere but
        // its top centre has its longest run arrive as the two stretches either side of the path's
        // start, and picking between those two judges the cell on whichever half happened to be
        // bigger.
        return RingStretch.selectLongest(path.fuseStretchAcrossStart(
            clearArcs.isEmpty()
                ? path.findStretchesHoldingItsInset()
                : clearArcs));
    }

    // How much of the stretch one width is worth and where along it the band sits, or nothing
    // where the stretch is too short to state the plan at any size.
    private static RibbonBandLayout placeBandOn(
            RingPath path,
            RingStretch stretch,
            int totalLengthUnits,
            RibbonStyle style) {

        var lengthUnitWorld = computeLengthUnit(stretch.computeLength(), totalLengthUnits, style);

        // A unit compressed to nothing is a cell with too little ring left to state its own
        // contents at any size - too small to begin with, or too much of it under a name - so it
        // says nothing rather than drawing a smear of colour where the runs would have been.
        if (lengthUnitWorld < Limits.MIN_EDGE_LENGTH) {
            return null;
        }

        // As near the cell's top centre as the stretch allows, rather than wherever the ring
        // the names left happens to open. Every cell's band is read from that landmark - the
        // first run first, running clockwise - so a band that could begin there and does
        // not costs the reader the one thing every cell's band has in common.
        return new RibbonBandLayout(
            path.placeSpanNearestStart(stretch, totalLengthUnits * lengthUnitWorld),
            lengthUnitWorld);
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
    // of them - a crowded cell reads as crowded, with each run's share intact. Thinning the
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
            RibbonBandLayout layout,
            RibbonStyle style) {

        var runLengths = new double[plan.segments().size()];

        for (var segment = 0; segment < runLengths.length; segment++) {
            runLengths[segment] = plan.segments().get(segment).lengthUnits() * layout.lengthUnitWorld();
        }

        // A run's stretch carries the corners the path turns at within it, not just its two ends,
        // so a run spanning a corner of the cell turns with it rather than cutting across.
        var spans = path.collectSpansEndToEnd(layout.startArcLength(), runLengths);

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

    // Where one cell's band sits on its own ring and what a width is worth there - the two the
    // carve settles and the stroke then spends. Private because it is how the two halves of this
    // class talk to each other; a band's own shape is what leaves it.
    private record RibbonBandLayout(
        double startArcLength,
        double lengthUnitWorld) {
    }
}
