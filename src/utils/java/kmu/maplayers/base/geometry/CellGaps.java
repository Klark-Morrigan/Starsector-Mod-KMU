package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import java.util.List;

/**
 * The corridor of void between two cells, and whether anything is in the way of it.
 *
 * <p>Shared because two searches ask the same pair of questions about the same pair of
 * cells - one cutting a pocket of trapped void into sections, the other looking for pairs
 * that trap void between them at all - and each would otherwise carry its own arithmetic
 * for it.
 *
 * <p>Every cell reaches the same distance, and that is what makes both answers closed form
 * rather than a search. The narrowest place between two cells is on the line joining their
 * sites, so the corridor is that line with one reach taken off each end and its width is
 * their separation less two reaches. Neither cell's outline is consulted.
 *
 * <p>Whether the corridor is open is the same shape of question. A point lies inside some
 * cell exactly when it is within reach of its nearest site, so "does anything cross this
 * corridor" is "does any site come within a reach of it" - again no polygon, and which cell
 * an intruding site's disc belongs to never has to be settled, only that some cell covers
 * that stretch.
 *
 * <p>A line's own two cells need no exempting from that, which is worth stating because it
 * looks like an omission. Each end sits one reach from its own site, so the nearest point of
 * the line to that site is that end, at exactly the reach, and the test is a strict one.
 *
 * <p>Which also settles the case where a line does NOT leave along the joining line - an end
 * anchored somewhere else on a cell's rim. The end is still one reach out, so it still cannot
 * come in under on its own account; but a line leaving at an angle steep enough to cut back
 * through its own cell dips below the reach part way along and is refused, which is right.
 * That is a real fault rather than an artefact of the test, and it is one a line sampled at a
 * few points can slip past entirely.
 */
public final class CellGaps {

    // Slack on "is this site far enough from the corridor", in world units. A site whose
    // circle grazes it is on the boundary of the test, and the sector is measured in tens of
    // thousands, so this is small enough to change no real answer and large enough to stop a
    // grazing site being read as a blocking one by a rounding.
    private static final double COVER_TOLERANCE = 1e-6;

    private CellGaps() {
    }

    /**
     * The corridor between two cells, at their common reach.
     *
     * @param sites    the sites
     * @param fromSite the first cell
     * @param toSite   the second
     * @param reach    how far either cell reaches from its site
     * @return the corridor, or null when the two cells overlap and there is none - which is
     *         what makes them neighbours, so a caller sweeping every pair gets the pairs
     *         with void between them and no adjacency test of its own
     */
    static CellGap findGapBetween(
            List<double[]> sites,
            int fromSite,
            int toSite,
            double reach) {

        var from = sites.get(fromSite);
        var to = sites.get(toSite);

        var separation = Points.computeDistance(from, to);
        var width = separation - 2 * reach;

        if (width <= 0) {
            return null;
        }
        return new CellGap(
            fromSite,
            toSite,
            projectAlong(from, to, reach, separation),
            projectAlong(to, from, reach, separation),
            width);
    }

    /**
     * Whether a line runs clear of every cell, or whether one lies across it.
     *
     * <p>Two cells being close says nothing on its own: the space between them can belong to
     * a third cell sitting between them rather than to the void, and anything drawn there
     * would run straight through that cell.
     *
     * <p>Asked of the whole line at once rather than of places along it. A cell covers the
     * line exactly when its site comes within a reach of the nearest point of it, and that
     * point is one subtraction away - so the answer is exact, where sampling a handful of
     * places lets a line clipping a corner between two of them through.
     *
     * @param start one end
     * @param end   the other
     * @param sites every site, because a cell nowhere near either end can still lie across
     *              the middle
     * @param reach how far a cell reaches from its site
     * @return whether nothing is in the way
     */
    static boolean isLineClearOfCells(
            double[] start,
            double[] end,
            List<double[]> sites,
            double reach) {

        for (var site : sites) {

            if (Segments.computeDistanceToPoint(start, end, site) < reach - COVER_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    // The point that far from one site along the line towards another.
    private static double[] projectAlong(
            double[] from,
            double[] to,
            double distance,
            double separation) {

        return new double[] {
            from[0] + (to[0] - from[0]) * distance / separation,
            from[1] + (to[1] - from[1]) * distance / separation};
    }
}
