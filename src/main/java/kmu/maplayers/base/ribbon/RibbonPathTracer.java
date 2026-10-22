package kmu.maplayers.base.ribbon;

import kmlib.math.geometry.RingPath;
import kmlib.math.geometry.RingStretch;
import kmlib.opengl.GlVertexRuns;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.ribbon.overlay.CellRibbonPath;
import kmu.maplayers.base.ribbon.overlay.RibbonPathVerdict;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the ring a cell's band runs along - the cell's own outline walked at the inset the sizes
 * ask for, or at the shallowest one a band still fits inside.
 *
 * <p>The one place that ladder is stated, because two callers walk it for different reasons and a
 * diagnostic that traced a cell any differently from the pass it is meant to check would agree
 * with it only by coincidence. What they ask for differs and that is all: the band pass wants the
 * path it may lay on, while the overlay wants the path and what the cell's room lets a band do
 * with it - including the fall the style switches off, which is the whole of why that cell draws
 * nothing.
 *
 * <p>Each rung is asked of the cell's kept rings before it is walked, and kept once it is. So the
 * overlay reads the rings the band pass already walked rather than walking them a second time, and
 * a re-bake walks only the cells a change re-shaped.
 *
 * <p>Pure geometry otherwise - no sector, no settings read, no GL - so a cell roomy enough for the
 * pad, one narrowed to a neck in one place, one too narrow for the pad everywhere, and one narrower
 * than the band itself are each settled by its ring, its anchor and the sizes alone.
 */
public final class RibbonPathTracer {

    // Where a ring is walked from, which is zero by the path's own definition. Named because the
    // overlay marks that point and a bare 0 there reads as an index rather than as a distance.
    private static final double PATH_START = 0.0;

    // Traces only; never instantiated.
    private RibbonPathTracer() {
    }

    /**
     * The same path the band pass lays on, with the reading the overlay colours by, and traced
     * whether or not a band would be laid on it.
     *
     * <p>The shallower inset is walked here even where the style switches the fall off, which
     * is the one way this differs from the band pass and the reason the overlay is worth having:
     * a cell refused for want of the pad and a cell with nothing to report both draw nothing, and
     * only this can tell them apart.
     *
     * @param cellKey       the cell being traced, the key its rings are kept under
     * @param ring          the cell's painted outline, the shape the path runs inside
     * @param topAnchor     the {x, y} the path's start is found above
     * @param style         the sizes the band is drawn at
     * @param ringPathCache the rings already walked inside the cells' current shapes, asked before a
     *                      rung is walked and written back when one is
     * @return the path flattened for the overlay, or {@link CellRibbonPath#NONE} where the cell
     *         held no stretch of path at either inset
     */
    public static CellRibbonPath traceInspectedRibbonPath(
            SystemKey cellKey,
            List<double[]> ring,
            double[] topAnchor,
            RibbonStyle style,
            CellRingPathCache ringPathCache) {

        var rung = walkInsetLadder(
            new CellRings(cellKey, ring, topAnchor, style, ringPathCache),
            true);

        return rung.path().hasStretchHoldingItsInset()
            ? flattenTracedPath(rung.path(), rung.verdict())
            : CellRibbonPath.NONE;
    }

    /**
     * The path a band is laid on, or one holding no stretch a band could go on where the cell has
     * no room for it.
     *
     * <p>The pad is what gives way on a narrow cell, never the half width: the pad is a look,
     * while the half width is what puts the band's near edge on the border rather than over it. A
     * cell narrower than the band is wide holds nothing however the setting stands - there is no
     * tighter band to fall back to, only a thinner one, and thinning is a different design.
     *
     * <p>The fall is taken on a cell whose ring held the inset <em>nowhere</em>, not on one pinched
     * somewhere along it: a pinch costs its own stretch and the rest of the ring is untouched, so
     * dropping the pad there would move the whole band onto the border to rescue a stretch that was
     * never the problem.
     *
     * @param cellKey       the cell being traced, the key its rings are kept under
     * @param ring          the cell's painted outline, the shape the path runs inside
     * @param topAnchor     the {x, y} the path's start is found above
     * @param style         the sizes the band is drawn at, which settle both insets and whether the
     *                      shallower one may be fallen to at all
     * @param ringPathCache the rings already walked inside the cells' current shapes
     * @return the traced path, holding no stretch where the cell holds no band
     */
    static RingPath traceLaidRibbonPath(
            SystemKey cellKey,
            List<double[]> ring,
            double[] topAnchor,
            RibbonStyle style,
            CellRingPathCache ringPathCache) {

        return walkInsetLadder(
            new CellRings(cellKey, ring, topAnchor, style, ringPathCache),
            false).path();
    }

    // The ladder itself: the authored inset, then the pad given up, and what a band does with the
    // rung it stops on.
    //
    // The band pass stops on the padded rung where the style keeps the pad, since a band is never
    // laid on the rung below it then and walking it would be spent on nothing. The overlay walks
    // on regardless, which is the one thing the two callers do differently.
    private static TracedRung walkInsetLadder(CellRings cellRings, boolean isRefusedFallWalked) {

        var style = cellRings.style();
        var paddedPath = cellRings.getOrTraceRingPathAt(RibbonPathInset.PADDED);

        if (paddedPath.hasStretchHoldingItsInset()) {
            return new TracedRung(paddedPath, RibbonPathVerdict.LAID_AT_PAD);
        }

        if (!style.isBandAlwaysDrawn() && !isRefusedFallWalked) {
            return new TracedRung(paddedPath, RibbonPathVerdict.REFUSED);
        }

        var unpaddedPath = cellRings.getOrTraceRingPathAt(RibbonPathInset.UNPADDED);

        // The path stands either way; what the style's answer decides is whether a band goes on
        // it. Reported rather than withheld, so a cell left bare by that answer is visibly a cell
        // the setting emptied rather than a system with nothing to say.
        return new TracedRung(
            unpaddedPath,
            unpaddedPath.hasStretchHoldingItsInset() && style.isBandAlwaysDrawn()
                ? RibbonPathVerdict.LAID_UNPADDED
                : RibbonPathVerdict.REFUSED);
    }

    // The traced ring packed for emission as the two things it is - the stretches a band may lie
    // on and the stretches its own shape denied it - paired with what the ring let a band do.
    //
    // Cut here rather than drawn as the one loop it was traced as, because the two are read
    // differently and one of them is the answer to "why is this cell's band shorter than its
    // outline". A ring folded outside the cell is the sharpest case: drawn whole it shows a line
    // leaving the shape it reports on, where cut it shows that ring as outline no band was ever
    // going to use.
    private static CellRibbonPath flattenTracedPath(RingPath path, RibbonPathVerdict verdict) {

        return new CellRibbonPath(
            collectStretchPolylines(path, path.findStretchesHoldingItsInset()),
            collectStretchPolylines(path, path.findStretchesFailingItsInset()),
            GlVertexRuns.flattenVertices(List.of(path.computePointAt(PATH_START))),
            verdict);
    }

    // One polyline per stretch, each carrying the corners the ring turns at within it so a stretch
    // spanning a corner bends with the cell rather than cutting across it.
    private static List<float[]> collectStretchPolylines(
            RingPath path,
            List<RingStretch> stretches) {

        var polylines = new ArrayList<float[]>(stretches.size());

        for (var stretch : stretches) {

            polylines.add(GlVertexRuns.flattenVertices(
                path.collectPointsBetween(stretch.startArcLength(), stretch.endArcLength())));
        }

        return polylines;
    }

    // One cell's ring as the ladder walks it: everything a rung is traced from, and where the rungs
    // already walked are kept. Held together so a rung is asked for by its inset alone, the five
    // things that settle it being the same for every rung of one cell.
    private record CellRings(
        SystemKey cellKey,
        List<double[]> ring,
        double[] topAnchor,
        RibbonStyle style,
        CellRingPathCache ringPathCache) {

        // The rung kept for this cell at the inset, or a fresh walk of it where none stands, kept
        // for the next bake.
        RingPath getOrTraceRingPathAt(RibbonPathInset inset) {

            var standingPath = ringPathCache.findRingPathOf(cellKey, inset);
            if (standingPath != null) {
                return standingPath;
            }

            var tracedPath = RingPath.traceInsetRing(
                ring,
                inset.computeInsetDistance(style),
                style.miterSpikeLimit(),
                topAnchor);

            ringPathCache.putRingPath(cellKey, inset, tracedPath);

            return tracedPath;
        }
    }

    // The rung the ladder stopped on and what a band does with it. The band pass reads the path
    // alone; the verdict is the overlay's.
    private record TracedRung(
        RingPath path,
        RibbonPathVerdict verdict) {
    }
}
