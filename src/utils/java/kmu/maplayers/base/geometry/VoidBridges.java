package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Segments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Void trapped between two cells that face each other across a short gap, found by the gap
 * rather than by the shape it belongs to.
 *
 * <p>The other way at the same problem as {@link VoidSections}, and worth stating as such
 * because the two answer the same question from opposite ends. That one starts from a
 * pocket - void the cells have already closed around - and asks where to divide it. This one
 * starts from a pair of cells and asks whether the void between them is narrow enough to
 * count as theirs, with no notion of a pocket at all. Void that no ring of cells encloses is
 * invisible to the first and ordinary to the second.
 *
 * <p>A pair qualifies when its two cells are close enough to trap something and nothing is
 * in the way. Close enough is four reaches centre to centre: the gap is then at most two
 * reaches, a whole cell's width, so a whole further system could sit in it. Wider than that
 * and what lies between them is not a gap they hold but open void that happens to be
 * between them.
 *
 * <p>Nothing in the way is {@link CellGaps}' clearance test - a third cell across the line
 * means the space between the two belongs to it, and there is nothing for the pair to trap.
 * Cells that overlap drop out on their own, having no gap at all, so a sweep of every pair
 * needs no adjacency test and no "does this cell touch void" pass either: a pair with a
 * clear gap between its reaches has void between them by construction, because every point
 * along it is further than a reach from every site.
 *
 * <p>What is left can still cross itself, and two bridges over the same stretch of void are
 * two claims on it. Where two cross, the shorter survives: it spans the tighter gap, and the
 * tighter gap is the more defensible claim on what lies between - the wider one is reaching
 * across void the narrow one already holds. Taking the candidates shortest first and keeping
 * each one that clears what is already kept does exactly that, one loss per crossing.
 *
 * <p>Two bridges from the same cell need no exempting from that, which they would if these
 * ran centre to centre: a shared endpoint counts as a crossing, and every pair of bridges
 * off one site would knock one of themselves out. They run from reach to reach instead, so
 * two leaving the same cell start at two different points on its rim and only register when
 * they genuinely cross.
 */
public final class VoidBridges {

    private VoidBridges() {
    }

    /**
     * Finds every stretch of void two cells hold between them.
     *
     * @param sites          the sites
     * @param reach          how far a cell reaches from its site
     * @param maxSeparation  how far apart two sites may be and still hold the void between
     *                       them, centre to centre
     * @return the bridges, narrowest first, none of them crossing another
     */
    public static List<CellGap> findVoidBridges(
            List<double[]> sites,
            double reach,
            double maxSeparation) {

        var candidates = findHeldGaps(sites, reach, maxSeparation - 2 * reach);

        // Narrowest first, which is the whole selection rule. The sites break ties, and only
        // so that two equally narrow gaps are always offered in the same order.
        candidates.sort(Comparator
            .comparingDouble(CellGap::width)
            .thenComparingInt(CellGap::fromSite)
            .thenComparingInt(CellGap::toSite));

        var kept = new ArrayList<CellGap>();

        for (var candidate : candidates) {

            if (!doesCrossAny(candidate, kept)) {
                kept.add(candidate);
            }
        }
        return kept;
    }

    private static List<CellGap> findHeldGaps(
            List<double[]> sites,
            double reach,
            double widestGap) {

        var held = new ArrayList<CellGap>();

        for (var first = 0; first < sites.size(); first++) {
            for (var second = first + 1; second < sites.size(); second++) {

                var gap = CellGaps.findGapBetween(sites, first, second, reach);

                // Capped before the clearance test rather than after, because that test walks
                // every site and the cap throws out most pairs on a subtraction.
                if (gap == null || gap.width() > widestGap) {
                    continue;
                }

                if (CellGaps.isGapClear(gap, sites, reach)) {
                    held.add(gap);
                }
            }
        }
        return held;
    }

    private static boolean doesCrossAny(
            CellGap bridge,
            List<CellGap> kept) {

        for (var held : kept) {

            if (Segments.intersectSegments(
                    bridge.start(),
                    bridge.end(),
                    held.start(),
                    held.end()) != null) {

                return true;
            }
        }
        return false;
    }
}
