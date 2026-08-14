package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Segments;

import java.util.List;

/**
 * The corridor of void between two cells, and whether anything is in the way of it.
 *
 * <p>Shared because two constructions ask the same pair of questions about the same pair of
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
 * <p>The corridor's own two cells need no exempting from that, which is worth stating
 * because it looks like an omission. The corridor begins and ends exactly one reach from
 * each of them, along the line joining them, so the nearest point on it to either site is
 * that site's own end of it at exactly the reach. Neither can come in under.
 */
final class CellGaps {

    // Slack on "is this site far enough from the corridor", in world units. A site whose
    // circle grazes it is on the boundary of the test, and the sector is measured in tens of
    // thousands, so this is small enough to change no real answer and large enough to stop a
    // grazing site being read as a blocking one by a rounding.
    private static final double COVER_TOLERANCE = 1e-6;

    private CellGaps() {
    }

    /**
     * One corridor of void between two cells.
     *
     * <p>The single currency for a straight run across void between two cells, whatever is
     * being done with it - offered as somewhere to divide a pocket, kept as void a pair
     * holds, or drawn. Those are the same line measured the same way, so they are the same
     * value; naming them apart per construction only invites two of them to drift.
     *
     * @param fromSite which cell it leaves
     * @param toSite   which cell it meets
     * @param start    where it meets the first cell's reach
     * @param end      where it meets the second's
     * @param width    how far apart the two cells are across it, at the reach that defines
     *                 the void rather than any reach something is drawn at, so two gaps stay
     *                 comparable when the channel width changes
     */
    record CellGap(
        int fromSite,
        int toSite,
        double[] start,
        double[] end,
        double width) {
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

        // Plain arithmetic rather than kmlib's Points.computeDistance: that is overloaded on
        // an LWJGL vector type the tooling has no classpath for, so the call will not resolve
        // here.
        var separation = Math.hypot(to[0] - from[0], to[1] - from[1]);
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
     * Whether a corridor runs clear of every cell, or whether one lies across it.
     *
     * <p>Two cells being close says nothing on its own: the space between them can belong to
     * a third cell sitting between them rather than to the void, and anything drawn there
     * would run straight through that cell.
     *
     * @param gap   the corridor to test
     * @param sites every site, because a cell nowhere near either end can still lie across
     *              the middle
     * @param reach how far a cell reaches from its site
     * @return whether nothing is in the way
     */
    static boolean isGapClear(CellGap gap, List<double[]> sites, double reach) {

        for (var site : sites) {

            if (Segments.computeDistanceToPoint(gap.start(), gap.end(), site)
                    < reach - COVER_TOLERANCE) {
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
