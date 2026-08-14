package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.PolylineBands;
import kmlib.math.geometry.RingPath;
import kmlib.opengl.GlVertexRuns;

import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays one cell's planned band around that cell's own ring: the path it runs along, how much of
 * that path the cluster names leave it, how long a width is worth on what remains, and the
 * triangles each run comes out as.
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
     * @param topAnchor    the {x, y} the band's start is found above - the cell's own site, so
     *                     every cell begins its band at its top centre and runs clockwise from
     *                     there
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
        var path = RingPath.traceInsetRing(
            ring,
            style.computeCentrelineInset(),
            style.miterSpikeLimit(),
            topAnchor);

        if (path.isEmpty()) {
            return CellRibbon.NONE;
        }

        // The names are carved off the path rather than off the plan, and that is the whole of
        // why a band and a name no longer share room. Cutting the plan instead would leave a run
        // partly eaten, and a run's length is the readout - a colony drawn two thirds as long as
        // its neighbours says something false about the system. Carved off the path, what a name
        // costs is room, which the clamp below already knows how to answer.
        var clearArcs = path.findClearArcs(nameBoxes);
        var lengthUnitWorld = computeLengthUnit(
            sumArcLengths(clearArcs),
            totalLengthUnits,
            style);

        // A unit compressed to nothing is a cell with too little ring left to state its own
        // contents at any size - too small to begin with, or too much of it under a name - so it
        // says nothing rather than drawing a smear of colour where the runs would have been.
        if (lengthUnitWorld < Limits.MIN_EDGE_LENGTH) {
            return CellRibbon.NONE;
        }
        return new CellRibbon(strokeSegments(path, plan, clearArcs, lengthUnitWorld, style));
    }

    // How much of the ring one width is worth on this cell: the authored width, unless the whole
    // band would then outrun the room the ring has left, in which case exactly as much as makes
    // it fit.
    //
    // Compressing the length while leaving the width alone is deliberate. The band says its piece
    // through the proportions between its runs, and shortening every run by one factor keeps all
    // of them - a crowded system reads as crowded, with each bloc's share intact. Thinning the
    // band instead would fade the whole readout on precisely the cells carrying the most to say,
    // and would leave the path traced at an inset the band no longer matches.
    private static double computeLengthUnit(
            double clearLength,
            int totalLengthUnits,
            RibbonStyle style) {

        return Math.min(style.widthWorld(), clearLength / totalLengthUnits);
    }

    // How much band the cell's ring has left to offer, across every stretch of it no name covers.
    private static double sumArcLengths(List<double[]> clearArcs) {

        var length = 0.0;

        for (var arc : clearArcs) {
            length += arc[1] - arc[0];
        }
        return length;
    }

    // The band stroked one clear stretch of ring at a time, each stretch in one piece and cut
    // into its runs afterwards.
    //
    // The band is one shape whatever it is coloured in: stroking each run on its own leaves
    // every boundary between two runs a pair of square ends butted together, which opens a wedge
    // wherever that boundary lands on a corner of the path - and a cell's ring is rounded, so
    // most boundaries land on one. Stroked once, a boundary is a point the band turns at like
    // any other. Where a name interrupts the ring the band genuinely stops and starts again, so
    // each stretch is its own stroke and its two ends are square - which is what an interruption
    // should look like.
    private static List<RibbonBand> strokeSegments(
            RingPath path,
            RibbonPlan plan,
            List<double[]> clearArcs,
            double lengthUnitWorld,
            RibbonStyle style) {

        var bands = new ArrayList<RibbonBand>(plan.segments().size());

        for (var pieces : collectPiecesByArc(plan, clearArcs, lengthUnitWorld)) {
            appendStrokedPieces(bands, path, plan, pieces, style);
        }
        return bands;
    }

    // The plan laid end to end along the clear stretches, as the pieces each stretch carries.
    //
    // A run reaching the end of one stretch carries on at the start of the next rather than
    // being cut short there: the plan is a proportional readout, so a run interrupted by a name
    // has to keep its whole length or it says the wrong thing about the system. It draws as two
    // pieces of one colour, which is what the interruption looks like from the reader's side.
    private static List<List<RibbonPiece>> collectPiecesByArc(
            RibbonPlan plan,
            List<double[]> clearArcs,
            double lengthUnitWorld) {

        var piecesByArc = new ArrayList<List<RibbonPiece>>(clearArcs.size());

        for (var arc = 0; arc < clearArcs.size(); arc++) {
            piecesByArc.add(new ArrayList<>());
        }
        var arcIndex = 0;
        var cursor = clearArcs.isEmpty() ? 0.0 : clearArcs.get(0)[0];

        for (var segment = 0; segment < plan.segments().size(); segment++) {

            var remaining = plan.segments().get(segment).lengthUnits() * lengthUnitWorld;

            while (remaining > Limits.MIN_EDGE_LENGTH && arcIndex < clearArcs.size()) {

                var arcEnd = clearArcs.get(arcIndex)[1];
                var taken = Math.min(remaining, arcEnd - cursor);

                if (taken > Limits.MIN_EDGE_LENGTH) {
                    piecesByArc
                        .get(arcIndex)
                        .add(new RibbonPiece(segment, cursor, cursor + taken));
                }
                cursor += taken;
                remaining -= taken;

                // The stretch is spent, so the rest of this run - and everything after it -
                // continues on the next one. The clamp above already made the plan fit the
                // stretches together, so running out of them altogether is rounding rather
                // than a plan that was too long, and the walk simply stops.
                if (arcEnd - cursor <= Limits.MIN_EDGE_LENGTH) {
                    arcIndex++;

                    if (arcIndex < clearArcs.size()) {
                        cursor = clearArcs.get(arcIndex)[0];
                    }
                }
            }
        }
        return piecesByArc;
    }

    // One clear stretch's pieces stroked as the single band they are, each piece coming back out
    // of it in its own run's colour.
    private static void appendStrokedPieces(
            List<RibbonBand> bands,
            RingPath path,
            RibbonPlan plan,
            List<RibbonPiece> pieces,
            RibbonStyle style) {

        if (pieces.isEmpty()) {
            return;
        }
        var spans = new ArrayList<List<double[]>>(pieces.size());

        // A piece's stretch carries the corners the path turns at within it, not just its two
        // ends, so a run spanning a corner of the cell turns with it rather than cutting across.
        for (var piece : pieces) {
            spans.add(path.collectPointsBetween(piece.startArcLength(), piece.endArcLength()));
        }
        var strokedSpans = PolylineBands.strokeSpansToTriangles(
            spans,
            style.widthWorld(),
            style.miterSpikeLimit());

        for (var piece = 0; piece < strokedSpans.size(); piece++) {

            var triangles = strokedSpans.get(piece);

            // A piece that came out with no area - a stretch the path collapsed - is left out
            // rather than kept as an empty draw. The stroker keeps such a piece in place rather
            // than dropping it, so a piece and its colour are still read off each other by
            // position here.
            if (!triangles.isEmpty()) {
                bands.add(new RibbonBand(
                    plan.segments().get(pieces.get(piece).segmentIndex()).colour(),
                    GlVertexRuns.flattenVertices(triangles)));
            }
        }
    }

    /**
     * One run's share of one clear stretch of ring: which run of the plan it belongs to, and
     * where along the path it begins and ends.
     *
     * <p>A run and a piece are not the same thing once the names have cut the ring up - a run
     * interrupted by one is two pieces, and a stretch of ring carries pieces of several runs -
     * so the piece carries the run it came from rather than the two being read off each other
     * by position.
     */
    private record RibbonPiece(
        int segmentIndex,
        double startArcLength,
        double endArcLength) {
    }
}
