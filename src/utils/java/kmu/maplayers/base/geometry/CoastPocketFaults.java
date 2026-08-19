package kmu.maplayers.base.geometry;

import kmlib.math.geometry.HalfPlane;
import kmlib.math.geometry.LabelledPolygon;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a coast pocket has strayed outside the reaches that closed it.
 *
 * <p>A reach of coast has cells on one side and open sea on the other, and the void it shut in
 * is on the cells' side by definition. So a run of a pocket's outline that has crossed to the
 * seaward side of the very reach that closed it is not a matter of taste - it is void being
 * claimed where there is nothing to claim it.
 *
 * <p>Two faults, kept apart. A reach is a SEGMENT, so an outline can be on the wrong side of
 * its line, or on the right side of it and past one of its ends. Only the first is void
 * claimed out at sea; the second says a pocket is longer than the piece of coast that closed
 * it, which is a fault where the outline was cut to that span and nothing at all where it was
 * not. Reported as one number they cannot be told apart, and the harmless one is much the
 * commoner - so the count that has to be zero stops meaning anything.
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
    // by the channel, so it sits about a channel inside each bound by design. Only a run that
    // has crossed one is outside it, and the slack is what stops the sampling of a run lying
    // flat against a bound reading as a fault every other vertex.
    private static final double PAST_A_BOUND = 1;

    // Two, so a run is something drawable rather than a lone sample. One vertex over
    // the line is a rounding at a corner the outline is already turning on.
    private static final int MIN_RUN_VERTICES = 2;

    private CoastPocketFaults() {
    }

    /**
     * How far a point lies on the cells' side of one reach of coast.
     *
     * <p>The signed distance itself, for anything wanting to know how far rather than whether
     * - which is what says the inset against the coast is there at all. Whether a point is
     * ALLOWED where it is comes from {@link #buildBounds}, since a reach bounds a pocket past
     * its ends as well as across its line.
     *
     * @param point the point to place
     * @param reach the reach to place it against
     * @param sites the sites, to find the cell the reach leaves from
     * @return how far landward the point is - negative out to sea, zero on the line
     */
    static double measureLandwardOffset(
            double[] point,
            DiscUnionBoundary.Chord reach,
            List<double[]> sites) {

        var landward = buildLandwardNormal(reach, sites);

        if (landward == null) {
            return 0;
        }
        return measureOffsetFrom(point, new HalfPlane(
            reach.line().originX(), reach.line().originY(), landward[0], landward[1]));
    }

    /**
     * How close the pockets come to the reaches that closed them.
     *
     * <p>The number that says whether the inset against the COAST is there at all. A gap
     * against the cells is visible on its own - the cells are drawn - but the coast is one
     * line, and a fill stopping exactly on it looks the same as a fill stopping a channel
     * short of it until they are measured apart.
     *
     * @param pockets the pockets, each paired with the reaches that walled it
     * @param sites   the sites, to say which side of each reach the cells are on
     * @return the closest any outline comes to a reach it closes on, or zero when there are
     *         no pockets - which should be the channel, and is nothing when the inset is
     *         missing
     */
    static double measureClosestApproach(List<WalledPocket> pockets, List<double[]> sites) {

        var closest = Double.MAX_VALUE;

        for (var walled : pockets) {
            for (var outline : walled.pocket().outlines()) {
                for (var reach : walled.reaches()) {

                    var bounds = buildBounds(reach, sites, 0);

                    if (bounds == null) {
                        continue;
                    }

                    for (var point : outline) {

                        if (measureExcursionFrom(point, bounds.toList()) <= 0) {
                            closest = Math.min(
                                closest, measureLandwardOffset(point, reach, sites));
                        }
                    }
                }
            }
        }
        return closest == Double.MAX_VALUE ? 0 : closest;
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
     * One run of a pocket's outline that stays landward of a reach and runs past its END.
     *
     * <p>Its own kind, not a spill. A reach is a segment, so a pocket that reaches beyond
     * where it stops is on the right side of the coast the whole way and is simply longer
     * than the piece of coast that closed it - which is only a fault where the outline was
     * cut to the reach's span and evidently was not. Counted together with the spills, the
     * one number that has to be zero stops meaning anything, because the commonest way to
     * make it non-zero is the harmless one.
     *
     * @param run   the offending stretch, in the order the outline is drawn
     * @param depth how far past the nearer end the worst of it reaches
     */
    record Overrun(
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
     * <p>Against the LINE alone. Where a pocket ends up relative to a reach's two ends is
     * {@link #findOverruns}' question, and a check that answered both at once would report
     * void claimed out at sea and void running past the end of a short reach as one fault.
     *
     * @param pockets the pockets, each paired with the reaches that walled it
     * @param sites   the sites, to say which side of each reach the cells are on
     * @return one entry per offending run, deepest first
     */
    static List<Spill> findSpills(List<WalledPocket> pockets, List<double[]> sites) {

        var found = new ArrayList<Spill>();

        for (var walled : pockets) {
            for (var outline : walled.pocket().outlines()) {
                for (var reach : walled.reaches()) {

                    var bounds = buildBounds(reach, sites, 0);

                    if (bounds != null) {
                        collectRunsOutside(outline, List.of(bounds.landward()), (run, depth) ->
                            found.add(new Spill(run, depth)));
                    }
                }
            }
        }
        found.sort(java.util.Comparator.comparingDouble(Spill::depth).reversed());

        return found;
    }

    /**
     * Finds every run of coast pocket outline that runs past one of its reach's ends.
     *
     * <p>Only a fault where the outline was held to the reach's span in the first place. At a
     * pocket's true extent nothing cuts it, and a hole is under no obligation to stop where a
     * reach does - so a caller asking there is asking about the cut it did not run.
     *
     * @param pockets the pockets, each paired with the reaches that walled it
     * @param sites   the sites, to say which side of each reach the cells are on
     * @return one entry per offending run, deepest first
     */
    static List<Overrun> findOverruns(List<WalledPocket> pockets, List<double[]> sites) {

        var found = new ArrayList<Overrun>();

        for (var walled : pockets) {
            for (var outline : walled.pocket().outlines()) {
                for (var reach : walled.reaches()) {

                    var bounds = buildBounds(reach, sites, 0);

                    if (bounds != null) {
                        collectRunsOutside(
                            outline,
                            List.of(bounds.afterStart(), bounds.beforeEnd()),
                            (run, depth) -> found.add(new Overrun(run, depth)));
                    }
                }
            }
        }
        found.sort(java.util.Comparator.comparingDouble(Overrun::depth).reversed());

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
     * the map at once; the half-planes the pocket must stay inside are a few facts about one
     * line, true whatever the wall did. So the shape is built as well as it can be and then
     * held to the rule, instead of the rule being something the construction is trusted to
     * have met.
     *
     * <p>One bound at a time, each an exact half-plane clip. Cutting against all three at
     * once would mean interpolating a crossing from whichever of them is nearest, and the
     * edge the outline actually leaves through changes partway along wherever two bounds
     * take over from each other.
     *
     * <p>Cut BEFORE the channel and the fill, so what the reader sees is inset from the legal
     * edge rather than from an edge that was never allowed. Cutting afterwards would leave the
     * fill correct and the outline it was measured against wrong.
     *
     * @param outline the pocket outline as traced
     * @param reaches the coast reaches it closes on
     * @param sites   the sites, to say which side of each reach the cells are on
     * @param channel how far the pocket holds back from a reach, so the cut lands where the
     *                fill should stop rather than on the line itself
     * @return what is left, which is empty when the whole outline was over the line
     */
    static List<double[]> cutToLandward(
            List<double[]> outline,
            List<DiscUnionBoundary.Chord> reaches,
            List<double[]> sites,
            double channel) {

        var kept = outline;

        for (var reach : reaches) {

            var bounds = buildBounds(reach, sites, channel);

            if (bounds == null) {
                continue;
            }

            for (var bound : bounds.toList()) {

                if (kept.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                    return List.of();
                }
                // Driven with one throwaway label, as any clip wanting no per-edge
                // distinction is: a label says which cut made an edge, and nothing here asks.
                kept = LabelledPolygon
                    .fromLabelledEdges(kept, new int[kept.size()])
                    .clipToHalfPlane(bound, 0)
                    .getVertices();
            }
        }
        return kept.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA ? List.of() : kept;
    }

    /**
     * The three half-planes one reach of coast bounds a pocket with, each named.
     *
     * <p>Named rather than listed, because two of them mean something different from the
     * third and the difference decides whether a breach is a fault. Handed round as a list,
     * the only thing telling the line from the end caps is its position in it, and a check
     * that wanted one of them had no way to say which without counting.
     *
     * @param landward   the cells' side of the reach's line, held off by the channel
     * @param afterStart everything from the reach's start onward
     * @param beforeEnd  everything up to the reach's end
     */
    private record ReachBounds(
        HalfPlane landward,
        HalfPlane afterStart,
        HalfPlane beforeEnd) {

        // All three as one list, for the callers that hold a pocket to a reach entirely
        // rather than asking about one bound of it.
        List<HalfPlane> toList() {
            return List.of(landward, afterStart, beforeEnd);
        }
    }

    // What to do with one run found outside a set of bounds: the run itself and how far the
    // worst of it went. A run is the same walk whichever fault it turns out to be, so the two
    // finders share the walk and differ only in what they build from it.
    private interface RunHandler {
        void acceptRun(List<double[]> run, double depth);
    }

    /**
     * The part of a pocket outline that is landward of the reaches that closed it, and
     * nothing else - no channel taken off, no holding it within a reach's span.
     *
     * <p>What a pocket at its TRUE extent is held to. It is meant to run right up to the line
     * that closed it, so there is no channel to cut back to; and it has every right to reach
     * past where that line stops, since a reach is one straight piece of a coast that goes on
     * either side of it. The one thing it may not do is cross to the seaward side, which is
     * void that nothing shut in.
     *
     * <p>That case arises where two reaches meet on one cell and their lines cross: each wall
     * is walked out to its own mouth, so each overshoots the other by the wedge between them.
     * The overshoot is not pocket - it is the other reach's sea - and the half-plane is what
     * says so.
     *
     * @param outline the pocket outline as traced
     * @param reaches the coast reaches it closes on
     * @param sites   the sites, to say which side of each reach the cells are on
     * @return what is left, which is empty when the whole outline was over the line
     */
    static List<double[]> cutBehindReaches(
            List<double[]> outline,
            List<DiscUnionBoundary.Chord> reaches,
            List<double[]> sites) {

        var kept = outline;

        for (var reach : reaches) {

            var bounds = buildBounds(reach, sites, 0);

            if (bounds == null) {
                continue;
            }

            if (kept.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
                return List.of();
            }
            // Driven with one throwaway label, as any clip wanting no per-edge distinction
            // is: a label says which cut made an edge, and nothing here asks.
            kept = LabelledPolygon
                .fromLabelledEdges(kept, new int[kept.size()])
                .clipToHalfPlane(bounds.landward(), 0)
                .getVertices();
        }
        return kept.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA ? List.of() : kept;
    }

    /**
     * The three half-planes one reach of coast bounds a pocket with.
     *
     * <p>The one statement of what a reach bounds, and both things that need it read it from
     * here: the cut, which clips the outline to each of them in turn, and the check, which
     * reports how far outside them a point strayed. Written twice they can disagree, and the
     * two ways of disagreeing are a detector that condemns what the cut has already dealt
     * with, or one that stays silent about what it leaves behind.
     *
     * <p>Three, because a reach is a SEGMENT. Landward of its line is the obvious one; past
     * either of its two ends is the one a single side test misses - a pair of near-parallel
     * reaches bounds a slab, and a slab is not a shape.
     *
     * <p>The channel holds off the line only, not the ends. A pocket stops a channel short of
     * the coast because the coast is a border it must not touch; at a reach's ends it meets
     * the cells' own arcs, which the reach it was traced at already holds it off.
     *
     * @param reach   the reach
     * @param sites   the sites, to find the cell it leaves from
     * @param channel how far the pocket holds back from its line
     * @return the three bounds, or null where the reach is too short to have a direction
     */
    private static ReachBounds buildBounds(
            DiscUnionBoundary.Chord reach,
            List<double[]> sites,
            double channel) {

        var landward = buildLandwardNormal(reach, sites);

        if (landward == null) {
            return null;
        }
        var line = reach.line();
        var unit = line.toUnitLine();

        return new ReachBounds(
            new HalfPlane(
                line.originX() + channel * landward[0],
                line.originY() + channel * landward[1],
                landward[0],
                landward[1]),
            new HalfPlane(
                line.originX(), line.originY(), unit.directionX(), unit.directionY()),
            new HalfPlane(
                line.originX() + line.directionX(),
                line.originY() + line.directionY(),
                -unit.directionX(),
                -unit.directionY()));
    }

    // Which way is landward from one reach, as a unit normal. The single reading of which
    // side is which - taken from the reach's own cell rather than assumed, so a reach running
    // the other way round the coast is judged like any other.
    private static double[] buildLandwardNormal(
            DiscUnionBoundary.Chord reach,
            List<double[]> sites) {

        var line = reach.line().toUnitLine();

        if (line == null) {
            return null;
        }
        var normalX = -line.directionY();
        var normalY = line.directionX();

        var towardsCells = Math.signum(measureOffsetFrom(
            sites.get(reach.fromCircle()),
            new HalfPlane(line.originX(), line.originY(), normalX, normalY)));

        return new double[] {towardsCells * normalX, towardsCells * normalY};
    }

    // How far outside the bounds a point lies, at most zero while it is within all of them.
    private static double measureExcursionFrom(double[] point, List<HalfPlane> bounds) {

        if (bounds.isEmpty()) {
            return 0;
        }
        var worst = -Double.MAX_VALUE;

        for (var bound : bounds) {
            worst = Math.max(worst, -measureOffsetFrom(point, bound));
        }
        return worst;
    }

    // How far a point sits on the kept side of one bound, negative out past it.
    private static double measureOffsetFrom(double[] point, HalfPlane bound) {

        return Points.projectPointOnto(
            point[0] - bound.pointX(),
            point[1] - bound.pointY(),
            bound.normalX(),
            bound.normalY());
    }

    // Every maximal stretch of one outline lying outside the given bounds. Walked as runs
    // rather than reported per vertex, because a spike is one fault however many samples it
    // took - and shared by both faults, since which bounds are asked about is the only thing
    // that differs between them and a second walk could disagree about where a run began.
    private static void collectRunsOutside(
            List<double[]> outline,
            List<HalfPlane> bounds,
            RunHandler handler) {

        var run = new ArrayList<double[]>();
        var deepest = 0.0;

        for (var point : outline) {

            var past = measureExcursionFrom(point, bounds);

            if (past > PAST_A_BOUND) {

                run.add(point);
                deepest = Math.max(deepest, past);
                continue;
            }
            deepest = closeRun(handler, run, deepest);
        }
        closeRun(handler, run, deepest);
    }

    // Ends a run and hands back the depth to carry into the next one, which is none. Given
    // back rather than reset by the caller so that closing a run and forgetting how deep it
    // went are one statement and cannot come apart.
    private static double closeRun(RunHandler handler, List<double[]> run, double deepest) {

        if (run.size() >= MIN_RUN_VERTICES) {
            handler.acceptRun(List.copyOf(run), deepest);
        }
        run.clear();

        return 0;
    }
}
