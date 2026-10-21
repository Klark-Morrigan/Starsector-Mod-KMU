package kmu.maplayers.base.geometry.output.v3;

import kmlib.math.geometry.Bounds;

import kmu.maplayers.base.geometry.Chord;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.LineCrossings;
import kmu.maplayers.base.geometry.SquareKey;
import kmu.maplayers.base.geometry.walls.WallLaying;
import kmu.maplayers.base.geometry.walls.Walls;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * A way out of the void a point sits in, if there is one.
 *
 * <p>Proof rather than inference. Whether a patch of void is enclosed is exactly whether
 * something can walk out of it, so this walks: a flood in four directions, each step stopped by
 * a cell it enters or a wall it crosses. A flood that reaches open space is a leak. None getting
 * out says only that this flood did not, so the answer is worded as it is measured.
 *
 * <p>One per reach, since the two can differ: void enclosed at the cells' own reach and open a
 * channel out is exactly the case where a pocket exists and nothing is drawn for it. The walls
 * laid and the open sea depend on the reach alone, so they are worked out once rather than once
 * per point asked about.
 */
final class EscapeFlood {

    // How the void is followed outward: one step of the flood, how far past the cells counts as
    // open sea, and how many places to visit before giving up.
    //
    // The stride is well under a cell, so the flood cannot hop one. The margin is a couple of
    // cells past the outermost site, which is past every wall the map lays. The cap is what
    // stops a pick in genuinely open water from flooding the whole sector: an answer of "still
    // going after this many" is the same news as "it got out", and costs a fraction of the time.
    private static final double FLOOD_STEP = 250;
    private static final double OPEN_SEA_MARGIN = 2;
    private static final int FLOOD_CAP = 40_000;

    // The four ways out of a place. Diagonals are left out on purpose: a diagonal step slips
    // between two cells that meet at a corner, which is land rather than a way through.
    private static final int[][] FLOOD_STEPS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private final DiscUnion union;
    private final List<Chord> laid;

    // Where the cells stop and the open sea begins, as a box the flood is out once it leaves.
    private final Bounds openSea;

    private EscapeFlood(DiscUnion union, List<Chord> laid, Bounds openSea) {

        this.union = union;
        this.laid = laid;
        this.openSea = openSea;
    }

    /**
     * Lays the walls at one reach and measures where open sea begins, ready to flood from any
     * point.
     *
     * @param union the discs at the reach being asked about
     * @param walls the walls on offer, of which only the ones laid at this reach stop a step
     * @return the flood, ready to be asked about any point
     */
    static EscapeFlood prepareAtReach(DiscUnion union, Walls walls) {

        // Past the furthest site by a couple of cells, which is past every wall as well, since a
        // wall runs between two of them.
        var overSites = Bounds.computeEnclosingBounds(union.sites());
        var margin = OPEN_SEA_MARGIN * union.reach();

        return new EscapeFlood(
            union,
            WallLaying.findAttachableChords(union, walls),
            new Bounds(
                overSites.minX() - margin,
                overSites.minY() - margin,
                overSites.maxX() + margin,
                overSites.maxY() + margin));
    }

    /**
     * Floods out from one point and says whether it got away.
     *
     * @param pick where to start
     * @return what the flood found, in words
     */
    String describeEscape(double[] pick) {

        // A pick inside the cells has no void to follow at all, which is a different answer from
        // one whose void is shut in - and at the drawn reach the cells stand a channel wider, so
        // a point in a narrow corridor is inside them there and outside them at their own reach.
        if (union.isPointInside(pick)) {
            return "inside the cells at this reach, so there is no void here to follow";
        }

        // Only places the flood actually reached. Counting the ones it tried and refused says
        // five for a point that never moved, which reads as a tiny pocket rather than as a
        // flood that went nowhere.
        var reached = new HashSet<Long>();
        var queue = new ArrayDeque<double[]>();

        reached.add(packPlaceKey(pick));
        queue.add(pick);

        while (!queue.isEmpty()) {

            if (reached.size() > FLOOD_CAP) {
                return "still spreading after " + FLOOD_CAP + " places, so not shut in";
            }

            var at = queue.poll();

            if (isPastTheCells(at)) {

                return String.format(
                    Locale.ROOT,
                    "reaches open sea, %d places flooded",
                    reached.size());
            }

            for (var step : FLOOD_STEPS) {

                var next = new double[] {
                    at[0] + step[0] * FLOOD_STEP,
                    at[1] + step[1] * FLOOD_STEP};

                // Whether a wall is in the way depends on which side the flood arrives from, so
                // a place refused from here may be reached from elsewhere and is not written off.
                if (reached.contains(packPlaceKey(next))
                        || union.isPointInside(next)
                        || isCrossingAnyWall(at, next)) {

                    continue;
                }

                reached.add(packPlaceKey(next));
                queue.add(next);
            }
        }

        return reached.size() == 1
            ? "boxed in where it stands - every way out is a cell or a wall within one step"
            : "shut in, " + reached.size() + " places flooded";
    }

    // One flooded place as a single key, so the same place is not visited twice. Quantised to
    // the stride, which is what makes the flood a grid rather than a drift.
    private static long packPlaceKey(double[] at) {

        return SquareKey.packSquareKey(
            (int) Math.round(at[0] / FLOOD_STEP),
            (int) Math.round(at[1] / FLOOD_STEP));
    }

    // Strictly outside the box: a place on its edge has not left the cells behind yet.
    private boolean isPastTheCells(double[] at) {

        return at[0] < openSea.minX()
            || at[1] < openSea.minY()
            || at[0] > openSea.maxX()
            || at[1] > openSea.maxY();
    }

    // Whether one step of the flood crosses a laid wall. The wall is the stretch between its two
    // ends, not the line it lies on, because a wall bounds void only where it actually runs.
    //
    // Grazing a wall exactly at one of its ends counts as crossing it. Counted otherwise, the
    // one direction that slips past a wall's end is the one direction the flood believes in,
    // and enclosed void reads as leaking.
    private boolean isCrossingAnyWall(double[] from, double[] to) {

        for (var chord : laid) {

            if (LineCrossings.isCrossing(
                    from,
                    to,
                    chord.findStart(),
                    chord.findEnd(),
                    LineCrossings.SharedAnchor.IS_CROSSING)) {

                return true;
            }
        }

        return false;
    }
}
