package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.Chord;
import kmu.maplayers.base.geometry.WallKind;
import kmu.maplayers.base.geometry.walls.Walls;

import java.util.ArrayList;
import java.util.List;

/**
 * The water of every lake, as the one walk that divides it: the pockets inside the shore, and
 * the band the shore concedes outside it.
 *
 * <p>A lake is one big hole the cells close around, and its shore is drawn a channel inside the
 * cells. The lake spans cut the water inside that shore into the smaller, deeper pockets a
 * reader sees, and the water outside it - the bays the shore cut straight across - is the band
 * the shore conceded to the cells. Both are wanted, and both are wanted from ONE walk.
 *
 * <p><b>The shore's own reaches are laid as walls, exactly as the outer coast's are.</b> Walked
 * against the cells alone, a span pocket runs out to the cells' edge and takes the band along
 * with the middle - two layers over one piece of water. With the reaches laid, a pocket stops a
 * channel inside the line that closes it, and what lies beyond that line is a hole of its own.
 *
 * <p><b>The band is a hole of the same walk, never a ring difference.</b> Two holes of one walk
 * share no point, so pockets and band are disjoint however the corners fall. Taken as the
 * water's edge minus the shore instead, the band claimed a wedge at every corner where two
 * reaches meet - a wall's side is held off its own line by the channel, and on the circle that
 * lands past the neighbouring reach - and it was the one fill on the map with no channel
 * against the cells.
 *
 * <p>Which holes are which is decided by where each lies, because a shore has water on BOTH
 * sides and the wall a hole closed on cannot say. Its middle can: no hole straddles a wall, and
 * a hole's arcs bow into it from cells outside it, so its middle sits inside it whichever side
 * of the shore it is on. Of the middle and not of any vertex - at the cells' own reach a hole's
 * arcs lie exactly on the shore's fillets, and a point on a ring's edge is on neither side of
 * it.
 */
public final class LakePockets {

    private LakePockets() {
    }

    /**
     * One walk's division of the lakes.
     *
     * @param pockets the water inside every shore, one ring per pocket the spans cut
     * @param bands   the water outside every shore and inside its lake, one ring per bay a
     *                reach cut across
     */
    public record LakeWater(
        List<List<double[]>> pockets,
        List<List<double[]>> bands) {
    }

    /**
     * Finds every piece of water in every lake, on either side of its shore.
     *
     * @param traced the coast, whose lakes carry the shores and which carries the sites
     *               everything here is measured against
     * @param spans  the lake spans, which cut the water inside the shores into the pockets a
     *               reader sees
     * @param rules  the knobs to build them under
     * @return the pockets and the bands, over every lake
     */
    public static LakeWater findLakeWater(
            Coastlines.TracedCoasts traced,
            List<CellGap> spans,
            VoidPockets.PocketRules rules) {

        var shores = Coastlines.collectLakeOutlines(traced);

        if (shores.isEmpty()) {
            return new LakeWater(List.of(), List.of());
        }

        var parameters = rules.parameters();
        var edges = collectWaterEdges(traced);

        var chords = new ArrayList<>(Chord.buildChordsFrom(
            spans, WallKind.LAKE_SPAN));

        chords.addAll(CoastPockets.buildLakeShoreWalls(traced, parameters.borderInset()));

        // The shores keep the channel every other wall keeps. Laying them at no width was
        // tried, to close the gutter the stroke sits in: a wall with no mouth is not a wall to
        // the walk, and the pockets then ran out past the shore they were meant to stop at.
        var walls = new Walls(chords, parameters.borderInset());

        var pockets = new ArrayList<List<double[]>>();
        var bands = new ArrayList<List<double[]>>();

        for (var hole : WalledVoid.traceVoidAcrossWalls(
                traced.union().sites(), walls, parameters, rules.shaping())) {

            var middle = Points.computeMean(hole.boundary());

            if (isInsideAny(shores, middle)) {
                pockets.add(hole.boundary());
            } else if (isInsideAny(edges, middle)) {
                bands.add(hole.boundary());
            }
        }
        return new LakeWater(List.copyOf(pockets), List.copyOf(bands));
    }

    // The cells' own arcs round each lake, which is the outer limit of what any lake water can
    // be: a hole outside every shore but inside one of these is a band.
    private static List<List<double[]>> collectWaterEdges(Coastlines.TracedCoasts traced) {

        var edges = new ArrayList<List<double[]>>(traced.lakes().size());

        for (var lake : traced.lakes()) {
            edges.add(lake.waterEdge());
        }
        return edges;
    }

    private static boolean isInsideAny(List<List<double[]>> rings, double[] at) {

        for (var ring : rings) {

            if (PolygonRegions.isPointInsideRing(ring, at[0], at[1])) {
                return true;
            }
        }
        return false;
    }
}
