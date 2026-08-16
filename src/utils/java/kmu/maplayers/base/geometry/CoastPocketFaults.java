package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a coast pocket has strayed to the wrong side of the coast, which is the one way its
 * outline can be wrong.
 *
 * <p>A reach of coast has cells on one side and open sea on the other, and the void it shut in
 * is on the cells' side by definition. So a run of a pocket's outline that has crossed to the
 * seaward side of the very reach that closed it is not a matter of taste - it is void being
 * claimed where there is nothing to claim it.
 *
 * <p><b>Only the offending run, not the pocket it belongs to.</b> A pocket with a sliver
 * shooting off one corner is almost entirely right, and marking the whole shape says the
 * opposite - it points at the part that is correct as loudly as at the part that is not, and
 * gives a reader nothing to aim at. The runs handed back are the maximal stretches of outline
 * that are actually over the line.
 *
 * <p>Kept apart from {@link CoastPockets} for the same reason {@link CoastCrossings} is kept
 * apart from {@link Coastlines}: the construction's job is to build the best shape it can, and
 * this one's job is to say whether it managed. One class doing both grades its own work.
 */
final class CoastPocketFaults {

    // A pocket's outline runs ALONG the reach that closed it for most of its length, held off
    // by the channel, so it sits about a channel inside the line by design. Only a run that
    // has crossed the line itself is over the edge, and the slack is what stops the sampling
    // of a run lying flat against it reading as a fault every other vertex.
    private static final double OVER_THE_LINE = 1;

    // Two, so a run is something drawable rather than a lone sample. One vertex over
    // the line is a rounding at a corner the outline is already turning on.
    private static final int MIN_RUN_VERTICES = 2;

    private CoastPocketFaults() {
    }

    /**
     * How far a point lies on the cells' side of one reach of coast.
     *
     * <p>The one reading of which side is which. A reach has cells on one side and open sea on
     * the other, and three things need to know which: the check that finds outline over the
     * line, the cut that removes it, and the wall placement that tries not to produce it. Read
     * three times it is three chances to disagree about one line - and the check and the cut
     * agreeing is the whole reason the cut can be trusted to remove exactly what the check
     * would otherwise report.
     *
     * <p>Which side the cells are on is read off the reach's own cell rather than assumed, so
     * a reach running the other way round the coast is judged like any other.
     *
     * @param point the point to place
     * @param reach the reach to place it against
     * @param union the discs the reach was laid across
     * @return how far landward the point is - negative out to sea, zero on the line
     */
    static double measureLandwardOffset(
            double[] point,
            DiscUnionBoundary.Chord reach,
            DiscUnion union) {

        var line = reach.line().toUnitLine();

        if (line == null) {
            return 0;
        }
        var normalX = -line.directionY();
        var normalY = line.directionX();

        var centre = union.sites().get(reach.fromCircle());

        var landward = Math.signum(
            (centre[0] - line.originX()) * normalX + (centre[1] - line.originY()) * normalY);

        return landward
            * ((point[0] - line.originX()) * normalX + (point[1] - line.originY()) * normalY);
    }

    /**
     * One run of a pocket's outline that has crossed to the seaward side of a coast reach.
     *
     * @param run   the offending stretch, in the order the outline is drawn
     * @param depth how far past the line the worst of it reaches. Carried because a run that
     *              has grazed the line by a unit and a sliver shooting a cell's width out to
     *              sea look identical to a count, and are not the same fault
     */
    record Spill(
        List<double[]> run,
        double depth) {
    }

    /**
     * Finds every run of coast pocket outline lying seaward of the reach that closed it.
     *
     * <p>Judged against the reaches THIS pocket closes on rather than against every reach on
     * the map. A reach's line is unbounded, so a pocket half a sector away sits on one side of
     * it or the other for no reason worth reporting; what makes the test mean anything is that
     * the pocket and the line are two edges of one shape.
     *
     * @param pockets   the pockets, each paired with the reaches that walled it
     * @param union     the discs the pockets were traced against, to say which side the cells
     *                  are on
     * @return one entry per offending run, deepest first
     */
    static List<Spill> findSpills(List<WalledPocket> pockets, DiscUnion union) {

        var found = new ArrayList<Spill>();

        for (var walled : pockets) {
            for (var outline : walled.pocket().outlines()) {
                for (var reach : walled.reaches()) {

                    collectSpillsAlong(found, outline, reach, union);
                }
            }
        }
        found.sort(java.util.Comparator.comparingDouble(Spill::depth).reversed());

        return found;
    }

    /**
     * One coast pocket and the reaches of coast that closed it.
     *
     * <p>Paired because neither judges the other on its own: a run of outline is only over the
     * line when it is over the line THIS pocket closes on, and a reach only accuses the pocket
     * it actually walled.
     *
     * @param pocket  the pocket
     * @param reaches the coast reaches it closes on
     */
    record WalledPocket(
        VoidPockets.VoidPocket pocket,
        List<DiscUnionBoundary.Chord> reaches) {
    }

    /**
     * The part of a pocket outline that is legal - everything landward of the reaches that
     * closed it.
     *
     * <p>Cut rather than corrected. Where the wall a pocket closes on ends up is the result of
     * a chain of angles, and chasing it into place has to be right for every configuration on
     * the map at once; the half-plane the pocket must stay inside is one fact about one line,
     * true whatever the wall did. So the shape is built as well as it can be and then held to
     * the rule, instead of the rule being something the construction is trusted to have met.
     *
     * <p>Cut BEFORE the channel and the fill, so what the reader sees is inset from the legal
     * edge rather than from an edge that was never allowed. Cutting afterwards would leave the
     * fill correct and the outline it was measured against wrong.
     *
     * @param outline the pocket outline as traced
     * @param reaches the coast reaches it closes on
     * @param union   the discs it was traced against, to say which side the cells are on
     * @param channel how far the pocket holds back from a reach, so the cut lands where the
     *                fill should stop rather than on the line itself
     * @return what is left, which is empty when the whole outline was over the line
     */
    static List<double[]> cutToLandward(
            List<double[]> outline,
            List<DiscUnionBoundary.Chord> reaches,
            DiscUnion union,
            double channel) {

        var kept = outline;

        for (var reach : reaches) {

            kept = cutAlong(kept, reach, union, channel);

            // And to the reach's own two ends. A reach is a SEGMENT, and clipping only to the
            // side of its line leaves the pocket free to run off along that line past where
            // the coast actually turned the corner - which is the overhang at the tip of a
            // wedge, on the correct side of every line and outside the coast all the same.
            // Two near-parallel reaches bound a slab, and a slab is not a shape.
            kept = cutBeyondEnd(kept, reach, true);
            kept = cutBeyondEnd(kept, reach, false);
        }
        return kept;
    }

    // One outline held to one end of one reach, by the same clip walk: the half-plane whose
    // edge crosses the reach square-on at that end, keeping the side the reach runs towards.
    private static List<double[]> cutBeyondEnd(
            List<double[]> outline,
            DiscUnionBoundary.Chord reach,
            boolean atStart) {

        var line = reach.line().toUnitLine();

        if (line == null || outline.isEmpty()) {
            return outline;
        }
        var span = Math.hypot(reach.line().directionX(), reach.line().directionY());

        var facing = atStart ? 1 : -1;
        var edge = atStart ? 0 : span;

        return clipTo(outline, point -> facing * (measureAlong(point, line) - edge));
    }

    /**
     * How far outside the stretch one reach bounds a point lies.
     *
     * <p>The same three half-planes the cut holds an outline to, read as one number: seaward
     * of the line, or past either of the reach's two ends. Read the same way by the check and
     * by the cut on purpose - a detector that measured a different region from the one the cut
     * removes would either report faults the cut had already dealt with or, worse, stay silent
     * about the ones it leaves behind. That silence is what let an overhang past a reach's end
     * sit on the map unmarked: side-of-the-line alone cannot bound a slab, and two
     * near-parallel reaches are a slab.
     *
     * @param point the point to place
     * @param reach the reach to place it against
     * @param union the discs the reach was laid across
     * @return how far outside it is, at most zero when it is within the reach's bounds
     */
    private static double measureExcursion(
            double[] point,
            DiscUnionBoundary.Chord reach,
            DiscUnion union) {

        var line = reach.line().toUnitLine();

        if (line == null) {
            return 0;
        }
        var span = Math.hypot(reach.line().directionX(), reach.line().directionY());
        var along = measureAlong(point, line);

        return Math.max(
            -measureLandwardOffset(point, reach, union),
            Math.max(-along, along - span));
    }

    // How far along a reach a point sits, from the end its line begins at.
    private static double measureAlong(double[] point, kmlib.math.geometry.DirectedLine line) {

        return (point[0] - line.originX()) * line.directionX()
            + (point[1] - line.originY()) * line.directionY();
    }

    // The single-plane clip walk itself, told which side is in by a signed reading. A vertex
    // inside survives; an edge crossing contributes the crossing point, so the cut lands on
    // the line rather than on the nearest sample to it.
    private static List<double[]> clipTo(
            List<double[]> outline,
            java.util.function.ToDoubleFunction<double[]> inside) {

        var kept = new ArrayList<double[]>(outline.size());

        for (var index = 0; index < outline.size(); index++) {

            var here = outline.get(index);
            var next = outline.get((index + 1) % outline.size());

            var hereIn = inside.applyAsDouble(here);
            var nextIn = inside.applyAsDouble(next);

            if (hereIn >= 0) {
                kept.add(here);
            }
            if ((hereIn >= 0) != (nextIn >= 0)) {

                var share = hereIn / (hereIn - nextIn);

                kept.add(new double[] {
                    here[0] + (next[0] - here[0]) * share,
                    here[1] + (next[1] - here[1]) * share});
            }
        }
        return kept.size() < MIN_RUN_VERTICES ? List.of() : kept;
    }

    // One outline held to one reach's landward side, by the single-plane clip walk. A vertex
    // inside survives; an edge crossing the line contributes the crossing point, so the cut
    // edge lands exactly on the line rather than on the nearest sample to it.
    private static List<double[]> cutAlong(
            List<double[]> outline,
            DiscUnionBoundary.Chord reach,
            DiscUnion union,
            double channel) {

        if (outline.isEmpty()) {
            return outline;
        }
        return clipTo(outline, point -> measureLandwardOffset(point, reach, union) - channel);
    }

    // Every maximal stretch of one outline lying seaward of one reach. Walked as runs rather
    // than reported per vertex, because a spike is one fault however many samples it took.
    private static void collectSpillsAlong(
            List<Spill> found,
            List<double[]> outline,
            DiscUnionBoundary.Chord reach,
            DiscUnion union) {

        var run = new ArrayList<double[]>();
        var deepest = 0.0;

        for (var point : outline) {

            var past = measureExcursion(point, reach, union);

            if (past > OVER_THE_LINE) {

                run.add(point);
                deepest = Math.max(deepest, past);
                continue;
            }
            deepest = closeRun(found, run, deepest);
        }
        closeRun(found, run, deepest);
    }

    private static double closeRun(List<Spill> found, List<double[]> run, double deepest) {

        if (run.size() >= MIN_RUN_VERTICES) {
            found.add(new Spill(List.copyOf(run), deepest));
        }
        run.clear();

        return 0;
    }
}
