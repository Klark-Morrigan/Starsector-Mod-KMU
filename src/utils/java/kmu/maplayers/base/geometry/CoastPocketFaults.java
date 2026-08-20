package kmu.maplayers.base.geometry;

import kmlib.math.geometry.HalfPlane;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
 * <p>Running past a reach's END is not a fault, and nothing here reports it. A pocket walled
 * by one reach and a bridge runs past that reach's ends by however far the bridge takes it,
 * which is exactly where it belongs; a cut that held such a pocket to one reach's span clipped
 * it out of existence, and both that cut and the measure of whether it had run are gone.
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

    // A pocket closed by a reach of coast runs ALONG that piece of coast for most of its
    // length, a channel inside it. Only a run that has crossed to the far side is at fault,
    // and this slack is what stops the sampling of an outline lying flat against the line
    // reading as a fault every other vertex.
    private static final double PAST_THE_COAST = 1;

    // Two, so a run is something drawable rather than a lone sample. One vertex outside the
    // coast is a rounding at a corner the outline is already turning on.
    private static final int MIN_RUN_VERTICES = 2;

    private CoastPocketFaults() {
    }

    /**
     * How far a point lies on the cells' side of one reach of coast.
     *
     * <p>The signed distance itself, which is what says the inset against the coast is there
     * at all. Only meaningful alongside the reach - a pocket past a reach's end is not near
     * that piece of coast at all - so {@link #isBesideReach} is asked first.
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
                    for (var point : outline) {

                        var landward = measureLandwardOffset(point, reach, sites);

                        // Beside the reach, and on the cells' side of it. A point out past
                        // either end is not near this piece of coast at all, and one on the
                        // seaward side has not left a gap against it - it has crossed it,
                        // which is a spill and is measured against the coast rather than here.
                        if (landward >= 0 && isBesideReach(point, reach)) {
                            closest = Math.min(closest, landward);
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
                Locale.ROOT,
                "%.0f out to sea over %d points from %.0f,%.0f",
                depth,
                run.size(),
                run.get(0)[0],
                run.get(0)[1]);
        }
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
        found.sort(Comparator.comparingDouble(Spill::depth).reversed());

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

            if (out > PAST_THE_COAST) {

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

    // Whether a point lies alongside a reach rather than off one of its ends.
    //
    // A reach is a SEGMENT. The channel it holds a pocket off by means nothing out past where
    // it stops: a pocket walled by this reach at one end and a bridge at the other runs on out
    // there legitimately, and measuring its distance from this reach's LINE out there would
    // report the gap against a piece of coast that is not beside it.
    private static boolean isBesideReach(double[] point, DiscUnionBoundary.Chord reach) {

        var unit = reach.line().toUnitLine();

        if (unit == null) {
            return false;
        }
        var start = reach.findStart();

        var along = Points.projectPointOnto(
            point[0] - start[0],
            point[1] - start[1],
            unit.directionX(),
            unit.directionY());

        return along >= 0 && along <= Points.computeDistance(start, reach.findEnd());
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

    // How far a point sits on the kept side of one bound, negative out past it.
    private static double measureOffsetFrom(double[] point, HalfPlane bound) {

        return Points.projectPointOnto(
            point[0] - bound.pointX(),
            point[1] - bound.pointY(),
            bound.normalX(),
            bound.normalY());
    }
}
