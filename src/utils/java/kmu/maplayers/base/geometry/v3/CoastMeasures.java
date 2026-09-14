package kmu.maplayers.base.geometry.v3;

import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.DiscUnionBoundary;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Measurements about a traced coast, for working out why one came out wrong.
 *
 * <p>None of this decides anything. {@link CoastCrossings} says whether a coast is wrong and
 * both drawings act on it; what is here only describes, and exists because "nineteen runs
 * cross a cell" is a number to argue with while "every one of them joins two stretches the
 * walk put side by side" is a place to look. Each of these was written to answer one question
 * about one fault.
 *
 * <p>Separate from the construction so that it is obvious they can all go. They earned their
 * keep - the walk-gap column is what proved the fault was in how a single run resolves rather
 * than in which cells get skipped - but they answer a question that is currently closed, and
 * scaffolding left standing among the load-bearing parts stops looking like scaffolding.
 */
public final class CoastMeasures {

    private CoastMeasures() {
    }

    /**
     * How far apart, along the silhouette, the two stretches each crossing joins are.
     *
     * <p>The one measurement that says where the fault lives. A crossing on a run between two
     * stretches the walk put NEXT to each other has nothing to do with skipping - the clamp
     * produced a bad run from two cells it was always going to join, and the fix is in how a
     * run is resolved. A crossing on a run that jumps over skipped stretches is the skip rule
     * opening a gap the repair pass failed to close, and the fix is there instead.
     *
     * @param traced the coast, as it was traced
     * @return one pair per crossing - how deep it goes, and how many stretches were skipped
     *         across it, where zero means the two were neighbours - deepest first
     */
    static List<double[]> measureCrossingGaps(Coastlines.TracedCoasts traced) {

        var places = mapWalkPlaces(traced);

        return measureAgainstDepth(traced, crossing -> measureWalkGap(
            places.get(crossing.from().circle()), places.get(crossing.to().circle())));
    }

    /**
     * How much border each crossed cell offered the coast, beside how deep the crossing was.
     *
     * <p>A crossed cell holding far less frontage than the coast as a whole would say the
     * arrival failed for want of anywhere to land; ordinary frontage says the fault is
     * elsewhere.
     *
     * @param traced the coast, as it was traced
     * @return one pair per crossing - how deep it goes, and how long the crossed cell's
     *         frontage is - deepest first
     */
    static List<double[]> measureCrossingFrontages(Coastlines.TracedCoasts traced) {

        var marks = mapMarksByCircle(traced);

        return measureAgainstDepth(traced, crossing -> measureFrontage(
            traced.union(), marks.get(crossing.circles().get(0))));
    }

    /**
     * How long every cell's frontage on the void is, as the coast found them.
     *
     * <p>The population a crossed cell's frontage is read against. Without it a small number
     * is just a small number.
     *
     * @param traced the coast, as it was traced
     * @return every stretch of coast, as the length of border it offers
     */
    static List<Double> measureFrontages(Coastlines.TracedCoasts traced) {

        var frontages = new ArrayList<Double>();

        for (var silhouette : traced.silhouettes()) {
            for (var mark : silhouette) {
                frontages.add(measureFrontage(traced.union(), mark));
            }
        }
        return frontages;
    }

    // Every per-crossing measure is the same shape - pair each crossing's depth with one
    // other number and read the deepest first - so the shape is written once and each measure
    // supplies only its own second column.
    private static List<double[]> measureAgainstDepth(
            Coastlines.TracedCoasts traced,
            ToDoubleFunction<CoastCrossings.Penetration> measure) {

        var rows = new ArrayList<double[]>();

        for (var crossing : CoastCrossings.findPenetrations(traced)) {
            rows.add(new double[] {crossing.depth(), measure.applyAsDouble(crossing)});
        }
        rows.sort(Comparator.comparingDouble((double[] row) -> row[0]).reversed());

        return rows;
    }

    // The stretch each cell offers most border on. The most rather than any, because a cell
    // facing the void on two stretches is only badly served if BOTH are slivers.
    private static java.util.Map<Integer, DiscUnionBoundary.CoastMark> mapMarksByCircle(
            Coastlines.TracedCoasts traced) {

        var marks = new HashMap<Integer, DiscUnionBoundary.CoastMark>();

        for (var silhouette : traced.silhouettes()) {
            for (var mark : silhouette) {

                marks.merge(
                    mark.circle(),
                    mark,
                    (held, offered) -> measureFrontage(traced.union(), offered)
                        > measureFrontage(traced.union(), held) ? offered : held);
            }
        }
        return marks;
    }

    // Where each cell sits along its own silhouette: which one, how far round it, and how
    // long that silhouette is.
    private static java.util.Map<Integer, int[]> mapWalkPlaces(Coastlines.TracedCoasts traced) {

        var places = new HashMap<Integer, int[]>();

        for (var silhouette = 0; silhouette < traced.silhouettes().size(); silhouette++) {

            var marks = traced.silhouettes().get(silhouette);

            for (var index = 0; index < marks.size(); index++) {

                places.putIfAbsent(
                    marks.get(index).circle(), new int[] {silhouette, index, marks.size()});
            }
        }
        return places;
    }

    // How many stretches lie between two along their own silhouette, or -1 when they are not
    // on the same one at all - which is itself worth seeing, since a run joining two separate
    // silhouettes is a different animal again.
    private static double measureWalkGap(int[] from, int[] to) {

        if (from == null || to == null || from[0] != to[0]) {
            return -1;
        }
        return Math.floorMod(to[1] - from[1] - 1, from[2]);
    }

    // How much border one stretch of coast offers, as the length of the arc it spans. What a
    // crossing is judged against: the same depth into a cell means one thing on a stretch
    // barely facing the void and another on one facing it for half a turn.
    private static double measureFrontage(DiscUnion union, DiscUnionBoundary.CoastMark mark) {
        return mark == null ? 0 : (mark.toAngle() - mark.fromAngle()) * union.reach();
    }

    /**
     * How many stretches of coast the cells actually make, before any are skipped.
     *
     * <p>The other half of the count that says whether the smoothing is doing anything: a
     * kept count on its own cannot tell a coast that was already smooth from one the skip
     * rules refused to touch.
     *
     * @param traced the coast, as it was traced
     * @return how many stretches there are in total, across every run
     */
    static int countCoastMarks(Coastlines.TracedCoasts traced) {

        var marks = 0;

        for (var silhouette : traced.silhouettes()) {
            marks += silhouette.size();
        }
        return marks;
    }
}
