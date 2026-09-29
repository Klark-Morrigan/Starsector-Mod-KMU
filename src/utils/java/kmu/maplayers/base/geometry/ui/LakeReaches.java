package kmu.maplayers.base.geometry.ui;

import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.v3.Coastlines;

import java.util.ArrayList;
import java.util.List;

/**
 * v3's traced lake coasts, read as the reaches v4 lays.
 *
 * <p>Where the two constructions touch. v4 divides the void along lines and does not trace any;
 * v3 traces coasts and is where the smoothing lives that a coastline is for. So v4's lake coast
 * is v3's, and something has to hand it over - and that something is the window that draws
 * both, because it already depends on both and nothing below it may. Put in either version it
 * would make that version a layer on top of the other rather than a construction beside it;
 * put in the package both versions build on, it would make that package depend on its own
 * dependents.
 *
 * <p>What crosses is the least that can: a reach as the {@link CellGap} it is, two points and
 * two cells. Fillets do not cross at all - along a fillet the shore already is the coast - and
 * which steps are reaches is v3's own answer, not re-derived here. The lake bridges need no
 * reading of their own: v3 already finds them as gaps.
 */
public final class LakeReaches {

    private LakeReaches() {
    }

    /**
     * Every reach of every lake's coast in a trace.
     *
     * @param traced  the coasts as v3 traced them, whose lakes are the ones read
     * @param channel the width below which a step between two touching cells is a handover
     *                rather than a reach. Handed in rather than read off the trace, because a
     *                continent trace is made with no walls and the channel it carries is
     *                zero - under which every handover counts as a reach
     * @return one gap per reach, in the order the coasts are walked
     */
    public static List<CellGap> collectLakeReaches(
            Coastlines.TracedCoasts traced,
            double channel) {

        var shores = traced.lakes().stream().map(Coastlines.Lake::shore).toList();
        var reaches = new ArrayList<CellGap>();

        for (var reach : Coastlines.collectStraightReaches(shores, channel)) {

            var from = reach.from().point();
            var to = reach.to().point();

            reaches.add(new CellGap(
                reach.from().circle(),
                reach.to().circle(),
                from,
                to,
                Points.computeDistance(from, to)));
        }
        return List.copyOf(reaches);
    }
}
