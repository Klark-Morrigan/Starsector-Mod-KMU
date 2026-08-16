package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Segments;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a smoothed coast passes inside a cell, which is the one way it can be wrong.
 *
 * <p>{@link Coastlines} may not put a straight run through a cell. Everything else about a
 * coast is a matter of taste - how smooth, how many corners, how far it bulges over a notch -
 * and this is the part with a right answer, so it is asked and answered on its own rather
 * than among the measurements that merely describe a shape.
 *
 * <p>Two thresholds, deliberately different. {@link #findPenetrations} answers the geometric
 * question down to a unit, because that is what the construction's own clearance test needs.
 * {@link #findVisibleCrossings} answers what a reader could actually see, which is a question
 * about how wide a line the borders are drawn with. Folding them together gets one of the two
 * wrong: loosen the first and the construction stops trying to do better, tighten the second
 * and the map fills with marks over nothing, which teaches its reader to stop looking.
 */
final class CoastCrossings {

    private CoastCrossings() {
    }

    // A stroked line reaches half its width either side of the edge it draws.
    private static final double DRAWN_EITHER_SIDE = 2;

    /**
     * The crossings deep enough to be seen, which are the only ones worth marking.
     *
     * <p>{@link #findPenetrations} answers a question about the geometry and answers it down
     * to a unit, because that is what the construction's own clearance test needs. Marking
     * what it finds is a different question: a cell's border is drawn as a line of some width,
     * so a run less than half of that inside the border is UNDER the line that draws it, and
     * there is no pixel anywhere showing it on the wrong side of anything.
     *
     * <p>Kept apart rather than folded into one threshold. Loosening the construction's test
     * would stop it trying to do better; marking at the construction's threshold puts marks on
     * a map where nothing can be seen, and a reader who checks two of those stops checking the
     * third. Both are wrong in the same way and only one of them is visible.
     *
     * @param traced       what {@link #traceSectorCoasts} handed back
     * @param borderStroke how wide a cell's border is drawn
     * @return the crossings that show, deepest first
     */
    static List<Penetration> findVisibleCrossings(Coastlines.TracedCoasts traced, double borderStroke) {

        var visible = new ArrayList<Penetration>();

        for (var crossing : findPenetrations(traced)) {

            if (crossing.depth() > borderStroke / DRAWN_EITHER_SIDE) {
                visible.add(crossing);
            }
        }
        visible.sort(java.util.Comparator.comparingDouble(Penetration::depth).reversed());

        return visible;
    }

    /**
     * Every straight run that passes inside a cell, and which cells each one is inside.
     *
     * <p>What a count cannot be acted on without. Nineteen crossings somewhere is a number to
     * argue with; nineteen crossings drawn on the map, each with the cell it goes through
     * marked, is a thing to look at - and looking is how the last several of these were
     * actually found.
     *
     * <p><b>Every site, including the two the run departs from and lands on.</b> Leaving
     * those out looks reasonable - a run touches both by construction, so they read as false
     * positives - but they are exactly the cells a run cuts through when the sliding fails,
     * and excluding them made this report zero while chain ends were being crossed end to
     * end. A run that leaves both borders outwards touches them and no more, so a real
     * incursion there is as real as any other.
     *
     * <p>Fillets are left out, because a fillet is drawn ON a cell's border and its chord
     * dips inside that cell by the sagitta of its own sampling, which is not the same thing
     * as a coast crossing one.
     *
     * @param traced what {@link Coastlines#traceSectorCoasts} handed back
     * @return one entry per offending run, in the order they are drawn
     */
    static List<Penetration> findPenetrations(Coastlines.TracedCoasts traced) {

        var found = new ArrayList<Penetration>();

        for (var coast : traced.coasts()) {
            for (var index = 0; index < coast.size(); index++) {

                var from = coast.get(index);
                var to = coast.get((index + 1) % coast.size());

                if (from.circle() == to.circle()) {
                    continue;
                }

                var pierced = findPiercedCells(traced.union(), from, to);

                if (pierced.isEmpty()) {
                    continue;
                }

                var circles = new ArrayList<Integer>(pierced.size());

                for (var pierce : pierced) {
                    circles.add(pierce.circle());
                }
                found.add(new Penetration(from, to, circles, pierced.get(0).depth()));
            }
        }
        return found;
    }

    /**
     * One straight run of coast that goes inside a cell, and the cells it goes inside.
     *
     * @param from    where the run starts, and whose cell it starts on
     * @param to      where it ends, and whose cell
     * @param circles the cells it passes inside, deepest first
     * @param depth   how far inside the worst of them it reaches. Carried because the two
     *                kinds look identical to a count and are not the same failure: a run
     *                grazing a border it is already leaving from is worth a unit or two, and
     *                a run cutting a cell in half is worth hundreds
     */
    record Penetration(
        Coastlines.CoastVertex from,
        Coastlines.CoastVertex to,
        List<Integer> circles,
        double depth) {
    }

    // Which cells one straight run is inside and how far into each, worst first. Depths are
    // kept rather than measured and dropped, because the deepest of them is the number that
    // says whether this is a graze or a crossing and recomputing it invites the two answers
    // to differ.
    private static List<Pierce> findPiercedCells(
            DiscUnion union,
            Coastlines.CoastVertex from,
            Coastlines.CoastVertex to) {

        var pierced = new ArrayList<Pierce>();

        for (var site = 0; site < union.sites().size(); site++) {

            var depth = union.reach() - Segments.computeDistanceToPoint(
                from.point(), to.point(), union.sites().get(site));

            if (depth > Coastlines.TOUCHING_TOLERANCE) {
                pierced.add(new Pierce(site, depth));
            }
        }
        pierced.sort(java.util.Comparator.comparingDouble(Pierce::depth).reversed());

        return pierced;
    }

    /**
     * One cell a straight run passes inside, and how far in it reaches.
     *
     * @param circle whose cell it is
     * @param depth  how far inside it the run reaches
     */
    private record Pierce(
        int circle,
        double depth) {
    }

    /**
     * How far the worst straight run of coast reaches inside a cell.
     *
     * <p>The check that governs this. A smoothed edge can look plausible and still cut a cell
     * in half - that is the failure it was built to fix - and the only way to say it has not
     * is to ask every straight reach how near it comes to every site.
     *
     * @param traced what {@link Coastlines#traceSectorCoasts} handed back
     * @return the deepest any straight run of coast reaches inside a cell, which is zero when
     *         none of them enters one
     */
    static double measureDeepestIncursion(Coastlines.TracedCoasts traced) {

        var deepest = 0.0;

        for (var penetration : findPenetrations(traced)) {
            deepest = Math.max(deepest, penetration.depth());
        }
        return deepest;
    }
}
