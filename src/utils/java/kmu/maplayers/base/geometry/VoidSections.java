package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Where a pocket of void too long to be one thing is cut across, so it reads as a run of
 * sections instead.
 *
 * <p>A pocket the size of a cell is one piece of space: it has one middle, and whatever
 * happens to it happens to all of it. A pocket several cells long is not - it is a corridor,
 * and its far end is as far from its near end as two systems are from each other. Handing
 * that to one owner in a single step would move a border across the map at once, which is
 * the behaviour the whole consolidation design exists to avoid, so it is divided first and
 * the pieces are decided separately.
 *
 * <p>How many pieces is arithmetic: a section is meant to be about a cell across, so a
 * pocket spanning {@code span} wants {@code ceil(span / sectionLength)} of them, and one
 * fewer cuts than that. Rounding up rather than down because the remainder is a piece of
 * void like any other and has to belong to some section.
 *
 * <p>Where to cut is geometry. A cut wants to land where the pocket is already nearly
 * pinched - where two of the cells around it almost meet - because that is the one place a
 * division reads as something rather than as a line drawn across open space. It is also the
 * shortest thing to draw, and the least of the pocket to misattribute if the division turns
 * out to be in slightly the wrong place.
 *
 * <p>Finding those needs no search over the outline. Every cell reaches the same distance,
 * so the narrowest crossing between two of them is along the line joining their sites, and
 * its width is their separation less two reaches - a closed form per pair, from data the
 * pocket already carries. Cells that are neighbours around the pocket fall out of it on
 * their own: their discs overlap, which is what made them neighbours, so their crossing has
 * negative width and never becomes a candidate. There is no adjacency test.
 *
 * <p>A crossing is only a cut if it stays in the void the whole way, which {@link CellGaps}
 * answers - a third cell can sit across the line between two that are close, and the space
 * between them then belongs to that cell rather than to this pocket.
 *
 * <p><b>Narrowest is not, on its own, the right cut.</b> The narrowest place in a pocket is
 * almost never its waist - it is the tip of one of its arms, where two cells run nearly
 * together and the void between them tapers to nothing. Taking crossings shortest-first and
 * only keeping them apart from each other spends every cut a pocket is owed on shaving
 * slivers off its extremities and leaves the body of it in one piece, which is the opposite
 * of dividing it.
 *
 * <p>So a cut is judged by what it leaves. The pocket is carried as a set of pieces,
 * starting as one, and a cut is taken only when neither of the two pieces it would make is
 * too small to be a section. Narrowest-first then decides only the order candidates are
 * offered in, which is what it is good for: among the cuts that genuinely divide, take the
 * one at the tightest pinch.
 *
 * <p><b>Too small is measured by area, not by span.</b> A span cannot see a bad cut at all:
 * a chord through a pocket's two most distant corners leaves both halves reaching between
 * those same two corners, so both measure the full span and a cut that halved the pocket
 * looks identical to one that took nothing. Area answers the question actually being asked -
 * how much of the pocket is on each side - and it is a shoelace sum over a piece the
 * construction already holds.
 *
 * <p>Pieces also settle whether two cuts can coexist, at no extra cost. A cut that crossed an
 * earlier one would have its two ends on the outlines of two different pieces, so "both ends
 * on one piece" is the test, and there is no segment intersection to get wrong on two cuts
 * that nearly touch.
 */
final class VoidSections {

    // A pocket that wants one section is the pocket. Nothing to cut.
    private static final int UNDIVIDED = 1;

    private VoidSections() {
    }

    /**
     * What a pocket came out divided into.
     *
     * <p>The sections come back alongside the cuts because they are the only thing that says
     * whether the division worked. The cut count is not: a pocket can take every cut it was
     * owed and still be badly divided, if what those cuts left is one large piece and a row
     * of small ones.
     *
     * @param cuts     the corridors it was cut across, in the order the cuts were taken
     * @param sections what those cuts left, as closed outlines at the reach that defines the
     *                 void, largest first - one entry holding the whole pocket when it was
     *                 not cut at all
     */
    record VoidDivision(
        List<CellGap> cuts,
        List<List<double[]>> sections) {
    }

    /**
     * The two numbers that decide how a pocket divides.
     *
     * <p>One record rather than two parameters because neither means anything without the
     * other: a cut width is only wide or narrow against the section it would separate, and a
     * section length only produces sensible sections if what may be cut is bounded against
     * it. Passed apart, the pair can be handed a length from one setting and a width from
     * another and still compile.
     *
     * @param sectionLength    how long a section should be - a cell's width, so a section is
     *                         about the size of the thing it will end up beside
     * @param minSectionShare  the least a cut may leave on either side of it, as a share of
     *                         one section's worth of area. How evenly the pocket comes out
     *                         divided, in one number: at one, only a cut leaving a full
     *                         section either side is allowed and almost nothing qualifies;
     *                         near zero, any cut is, and the narrowest crossings win - which
     *                         are the tips, so the pocket is shaved rather than divided.
     *                         Between those, the higher it is set the more the cuts are
     *                         pushed away from the extremities and towards the middle
     */
    record SectionRules(
        double sectionLength,
        double minSectionShare) {
    }

    /**
     * How far a set of points reaches across, as the distance between the two furthest apart.
     *
     * @param points the points to measure across, few enough that every pair is compared
     * @return the widest distance between any two, or zero when there are fewer than two
     */
    static double measureWidestSpan(List<double[]> points) {

        var widest = 0.0;

        for (var first = 0; first < points.size(); first++) {
            for (var second = first + 1; second < points.size(); second++) {

                widest = Math.max(
                    widest,
                    Points.computeDistance(points.get(first), points.get(second)));
            }
        }
        return widest;
    }

    /**
     * Cuts a pocket across into roughly cell-sized sections.
     *
     * @param hole          the pocket at the reach that defines the void - what the sections
     *                      are carved out of, whose cells the cuts run between, and whose
     *                      reach a corridor's width is measured against
     * @param sites         every site, because a cell that does not ring the pocket can
     *                      still lie across a candidate cut
     * @param span  how far the pocket reaches across, which decides how many sections it is
     *              owed. Taken from the caller rather than measured here because it is the
     *              pocket's own extent, reported as such and tested against elsewhere, and
     *              two places measuring it apart would let a pocket be called too long while
     *              being cut into one section
     * @param rules how long a section should be, and how much of one a cut has to leave on
     *              either side of it to be worth taking
     * @return how it came out divided, holding no cuts and the whole pocket as its one
     *         section when it is already section-sized or nothing in it can be cut across
     */
    static VoidDivision divideVoidPocket(
            VoidHole hole,
            List<double[]> sites,
            double span,
            SectionRules rules) {

        var wantedSections = (int) Math.ceil(span / rules.sectionLength());

        if (wantedSections <= UNDIVIDED) {
            return new VoidDivision(List.of(), List.of(hole.boundary()));
        }

        var candidates = findCrossings(hole.ringing(), sites, hole.reach());

        // Narrowest first, which decides only what order the candidates are offered in. The
        // sites break ties, and only so that two equally narrow crossings are always offered
        // in the same order.
        candidates.sort(Comparator
            .comparingDouble(CellGap::width)
            .thenComparingInt(CellGap::fromSite)
            .thenComparingInt(CellGap::toSite));

        var pocketArea = measureArea(hole.boundary());

        return takeDividingCuts(
            candidates,
            hole.boundary(),
            wantedSections - 1,
            pocketArea / wantedSections * rules.minSectionShare());
    }

    // Every pair of cells around the pocket that has clear void between them, as the segment
    // spanning it. The pair is the cut: with one reach for every cell, the narrowest crossing
    // between two of them is the one along the line joining their sites, so there is one
    // candidate per pair and no shorter one to look for.
    private static List<CellGap> findCrossings(
            List<Integer> ringing,
            List<double[]> sites,
            double trueReach) {

        var crossings = new ArrayList<CellGap>();

        for (var first = 0; first < ringing.size(); first++) {
            for (var second = first + 1; second < ringing.size(); second++) {

                var gap = CellGaps.findGapBetween(
                    sites,
                    ringing.get(first),
                    ringing.get(second),
                    trueReach);

                if (gap != null && CellGaps.isGapClear(gap, sites, trueReach)) {
                    crossings.add(gap);
                }
            }
        }
        return crossings;
    }

    // Takes cuts in the order offered, keeping the ones that leave a section's worth on both
    // sides of them.
    private static VoidDivision takeDividingCuts(
            List<CellGap> candidates,
            List<double[]> boundary,
            int wantedCuts,
            double leastSectionArea) {

        var pieces = new ArrayList<List<double[]>>();
        pieces.add(boundary);

        var cuts = new ArrayList<CellGap>(wantedCuts);
        var snapDistance = PolygonChords.measureSnapDistance(boundary);

        for (var candidate : candidates) {

            if (cuts.size() == wantedCuts) {
                break;
            }

            var landing = PolygonChords.findLanding(
                pieces, candidate.start(), candidate.end(), snapDistance);

            if (landing == null) {
                continue;
            }

            var halves = PolygonChords.splitPiece(landing, candidate.start(), candidate.end());

            if (halves.isEmpty() || !doHalvesHoldSections(halves, leastSectionArea)) {
                continue;
            }

            pieces.remove(landing.piece());
            pieces.addAll(halves);

            cuts.add(candidate);
        }

        // Largest first, so the piece most likely to still want dividing is the one read
        // first out of a diagnostic.
        pieces.sort(Comparator
            .comparingDouble((List<double[]> piece) -> measureArea(piece))
            .reversed());

        return new VoidDivision(cuts, pieces);
    }

    private static boolean doHalvesHoldSections(
            List<List<double[]>> halves,
            double leastSectionArea) {

        for (var half : halves) {

            if (measureArea(half) < leastSectionArea) {
                return false;
            }
        }
        return true;
    }

    // Unsigned, because a pocket's outline winds the opposite way to a filled shape's and
    // the pieces cut out of it inherit that. Which way a piece is wound says nothing about
    // how much of the pocket it holds, which is the only thing asked of it here.
    private static double measureArea(List<double[]> piece) {
        return Math.abs(PolygonRegions.computeSignedArea(piece));
    }
}
