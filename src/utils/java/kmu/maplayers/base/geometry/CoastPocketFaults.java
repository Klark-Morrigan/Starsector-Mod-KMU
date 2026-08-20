package kmu.maplayers.base.geometry;

import kmlib.math.geometry.HalfPlane;
import kmlib.math.geometry.LabelledPolygon;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a coast pocket has strayed outside the coast that shut it in.
 *
 * <p>The map draws one line round the settled space, and the void a coast shuts in is inside
 * it. So a run of a pocket's outline lying OUTSIDE that line is not a matter of taste - it is
 * void being claimed where there is nothing to claim it.
 *
 * <p><b>Against the drawn coast, never against one reach's line.</b> A reach is one straight
 * piece of that coast and its line runs on forever; a pocket closed by a reach at one end and
 * a BRIDGE at the other legitimately lies past that reach's line, out where the bridge shuts
 * the space. Judged per reach, such a pocket is condemned for being exactly where it belongs -
 * and a correction applied on that verdict deletes it. The coast is the only line that answers
 * the question actually being asked.
 *
 * <p>A second fault is kept apart from it: an outline past a reach's END. That one IS per
 * reach, because it is about the cut that holds a pocket within a reach's span, and it says a
 * pocket is longer than the piece of coast that closed it rather than that it is out at sea.
 * Counted together, the harmless one is much the commoner and the number that has to be zero
 * stops meaning anything.
 *
 * <p><b>Only the offending run, not the pocket it belongs to.</b> A pocket with a sliver
 * shooting off one corner is almost entirely right, and marking the whole shape says the
 * opposite - it points at the part that is correct as loudly as at the part that is not, and
 * gives a reader nothing to aim at. The runs handed back are the maximal stretches of outline
 * that are actually outside.
 *
 * <p>Kept apart from {@link CoastPockets} for the same reason {@link CoastCrossings} is kept
 * apart from {@link Coastlines}: the construction's job is to build the best shape it can, and
 * this one's job is to say whether it managed. One class doing both grades its own work.
 */
final class CoastPocketFaults {

    // A pocket's outline runs ALONG the line that closed it for most of its length, held off
    // by the channel, so it sits about a channel inside by design. Only a run that has crossed
    // to the far side is at fault, and this slack is what stops the sampling of a run lying
    // flat against that line reading as a fault every other vertex.
    private static final double PAST_A_LINE = 1;

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
     * One run of a pocket's outline lying outside the drawn coast.
     *
     * @param run   the offending stretch, in the order the outline is drawn
     * @param depth how far out the worst of it reaches. Carried because a run that has grazed
     *              the line by a unit and a sliver shooting a cell's width out to sea look
     *              identical to a count, and are not the same fault
     */
    record Spill(
        List<double[]> run,
        double depth) {

        @Override
        public String toString() {
            return String.format(
                java.util.Locale.ROOT,
                "%.0f out to sea over %d points from %.0f,%.0f",
                depth,
                run.size(),
                run.get(0)[0],
                run.get(0)[1]);
        }
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
     * Finds every run of coast pocket outline lying outside the drawn coast.
     *
     * <p>The whole of the test. Inside that line the void may be shut in by a reach, by a
     * bridge, or by the cells themselves, and which of them did it is no business of this
     * check; outside it there is nothing to shut anything in, so any outline out there is
     * wrong however it got there.
     *
     * @param pockets the pockets, each paired with the reaches that walled it
     * @param coasts  the drawn coast, as the rings the map puts on screen
     * @return one entry per offending run, deepest first
     */
    static List<Spill> findSpills(
            List<WalledPocket> pockets,
            List<List<double[]>> coasts) {

        var found = new ArrayList<Spill>();

        for (var walled : pockets) {
            for (var outline : walled.pocket().outlines()) {

                collectRunsAtSea(outline, coasts, found);
            }
        }
        found.sort(java.util.Comparator.comparingDouble(Spill::depth).reversed());

        return found;
    }

    // Every maximal stretch of one outline lying outside the drawn coast, with how far out the
    // worst of it went.
    private static void collectRunsAtSea(
            List<double[]> outline,
            List<List<double[]>> coasts,
            List<Spill> found) {

        var run = new ArrayList<double[]>();
        var deepest = 0.0;

        for (var point : outline) {

            var out = measureDepthAtSea(point, coasts);

            if (out > PAST_A_LINE) {

                run.add(point);
                deepest = Math.max(deepest, out);
                continue;
            }

            if (run.size() >= MIN_RUN_VERTICES) {
                found.add(new Spill(List.copyOf(run), deepest));
            }
            run.clear();
            deepest = 0;
        }

        if (run.size() >= MIN_RUN_VERTICES) {
            found.add(new Spill(List.copyOf(run), deepest));
        }
    }

    // How far outside the drawn coast a point lies, which is nothing while it is inside any
    // of the coast's rings.
    private static double measureDepthAtSea(double[] point, List<List<double[]>> coasts) {

        var nearest = Double.MAX_VALUE;

        for (var coast : coasts) {

            if (PolygonRegions.isPointInsideRing(coast, point[0], point[1])) {
                return 0;
            }
            nearest = Math.min(nearest, PolygonRegions.computeDistanceToBoundary(coast, point));
        }
        return nearest == Double.MAX_VALUE ? 0 : nearest;
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
                        collectRunsPastEnds(outline, bounds, found);
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

        return clipToBounds(outline, reaches, sites, channel);
    }

    // One outline held inside whichever bounds a caller names, clipped against each in turn.
    // Both cuts are this walk at a different choice of bounds and a different channel, and a
    // second copy of it is a second answer about how a pocket is held.
    private static List<double[]> clipToBounds(
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

    // How far past one of a reach's ends a point is, which is what the cut holds a pocket
    // within wherever a cut runs at all.
    private static double measurePastEndDepth(double[] point, ReachBounds bounds) {

        return Math.max(
            -measureOffsetFrom(point, bounds.afterStart()),
            -measureOffsetFrom(point, bounds.beforeEnd()));
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

    // Every maximal stretch of one outline lying past a reach's ends. Walked as runs rather
    // than reported per vertex, because a spike is one fault however many samples it took.
    private static void collectRunsPastEnds(
            List<double[]> outline,
            ReachBounds bounds,
            List<Overrun> found) {

        var run = new ArrayList<double[]>();
        var deepest = 0.0;

        for (var point : outline) {

            var past = measurePastEndDepth(point, bounds);

            if (past > PAST_A_LINE) {

                run.add(point);
                deepest = Math.max(deepest, past);
                continue;
            }
            deepest = closeRun(found, run, deepest);
        }
        closeRun(found, run, deepest);
    }

    // Ends a run and hands back the depth to carry into the next one, which is none. Given
    // back rather than reset by the caller so that closing a run and forgetting how deep it
    // went are one statement and cannot come apart.
    private static double closeRun(List<Overrun> found, List<double[]> run, double deepest) {

        if (run.size() >= MIN_RUN_VERTICES) {
            found.add(new Overrun(List.copyOf(run), deepest));
        }
        run.clear();

        return 0;
    }
}
