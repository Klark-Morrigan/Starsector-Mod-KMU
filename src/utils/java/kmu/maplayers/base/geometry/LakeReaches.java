package kmu.maplayers.base.geometry;

import kmu.maplayers.base.geometry.v3.Coastlines;
import kmu.maplayers.base.geometry.v4.ReachLine;

import java.util.ArrayList;
import java.util.List;

/**
 * v3's traced lake coasts, read as the reaches v4 lays.
 *
 * <p>The one place the two constructions touch. v4 divides the void along lines and does not
 * trace any; v3 traces coasts and is where the smoothing lives that a coastline is for. So
 * v4's lake coast is v3's, and something has to hand it over - and that something sits above
 * both rather than inside either, because a version that imports the other is no longer a
 * version beside it but a layer on top of it. Here, v3 stays a tracer that knows nothing of
 * the walk, and v4 stays a walk that knows nothing of who traced its lines.
 *
 * <p>What crosses is the least that can: a reach as two points and two cells. Fillets do not
 * cross at all - along a fillet the shore already is the coast - and which steps are reaches is
 * v3's own answer, not re-derived here.
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
     * @return one entry per reach, in the order the coasts are walked
     */
    public static List<ReachLine> collectLakeReaches(
            Coastlines.TracedCoasts traced, double channel) {

        var reaches = new ArrayList<ReachLine>();

        for (var lake : traced.lakes()) {
            for (var reach : Coastlines.collectStraightReaches(List.of(lake.shore()), channel)) {

                reaches.add(new ReachLine(
                    reach.from().point(),
                    reach.to().point(),
                    reach.from().circle(),
                    reach.to().circle()));
            }
        }
        return List.copyOf(reaches);
    }
}
