package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Segments;
import kmlib.math.geometry.UniformGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * The coastline the links added, drawn by the walk that draws every other coastline.
 *
 * <p><b>The links are laid as walls and the sector is traced again.</b> That is the whole method.
 * A link laid as a wall is boundary rather than a stroke over the void, so the walk runs out
 * along one of its sides, round whatever it reaches and back along the other. Handed the links,
 * the same walk gives an isthmus with an edge on each side, absorbs a
 * cell that was alone in the void into the shape a link joins it to, protects the cells a link
 * lands on from the frontage floor, and rounds the joins - all of which is what a coastline is,
 * and none of which is worth reproducing beside it.
 *
 * <p><b>Then only what the first line does not already carry.</b> Tracing again redraws the whole
 * sector, and most of that is the continent coasts already on screen. So each ring the second
 * trace hands back is cut into the runs whose points no drawn line has, and those are what this
 * construction is: the stretches where the outer border sweeps over a link or an island the
 * first trace could not reach, and the little the walk moved elsewhere because a link changed
 * what a cell has to be smoothed against.
 *
 * <p><b>Matched against the drawn line, not against its points.</b> The two traces sample one
 * border at one set of angles, so most of a shared stretch comes out of them point for point -
 * but not all of it. Move a corner and the stretch that ran into it is re-sampled from a new end,
 * so it lies exactly along the first line while sharing none of its points. Asked point against
 * point, every one of those reads as new coastline and the map gets a second copy of the first
 * drawn over it. So the question is the one a reader would ask: does this point lie ON the line
 * already drawn.
 *
 * <p><b>A run keeps the drawn point at each of its ends.</b> That point is on both lines, which is
 * what makes the new stretch join the old one rather than start a sampling step away from it. One
 * shared POINT is not a duplicated stretch: nothing is drawn twice, and nothing is left with a
 * hairline gap where the two meet.
 *
 * <p><b>The OUTER border, and nothing inside it.</b> What this layer is for is rounding the
 * sector up: the one line a reader traces to see where the settled sector ends, drawn where the
 * composite's outer silhouette runs. The seas the links wall in are deliberately not shored by
 * it - the water inside them is the fills' subject and the land around them already carries the
 * continent coasts, so a line there would say a third time what two layers already say. The
 * second trace still keeps those holes' shores ({@link Coastlines.TracedCoasts#walledShores}),
 * because the walk needs the walls laid to close the outer silhouette at all and other readers
 * want the border facing that water; this layer just does not draw them.
 */
public final class IntercontinentalCoasts {

    // How far off a drawn line a point may sit and still be on it, in map units. Small against
    // everything this keeps - an isthmus edge stands half a channel off a link's line, and a
    // link is only laid where it runs clear of the coast by more than that - and large enough to
    // absorb two traces arriving at one stretch by different arithmetic.
    private static final double SAME_LINE = 25;

    // How wide a cell of the lookup grid is, in map units. Near the length of one sampled step,
    // so a cell holds few lines. Any width finds every line within the tolerance, since a lookup
    // reads every cell that far round its point.
    private static final double LOOKUP_CELL_SIDE = 500;

    // Where a segment's two ends sit within the pair each is carried as.
    private static final int SEGMENT_FROM = 0;
    private static final int SEGMENT_TO = 1;

    private IntercontinentalCoasts() {
    }

    /**
     * Finds the coastline the links added.
     *
     * @param traced the continent coasts as they are drawn, which are what the second trace is
     *               cut against
     * @param linked the sector traced again with the links laid, which has to have been traced
     *               under the same rules as the drawn line: two coasts smoothed differently
     *               agree nowhere, and the cut would then keep the whole sector
     * @return one open run per stretch no drawn coastline carries. A ring the drawn coasts have
     *         no point of at all comes back whole, ending where it began
     */
    public static List<List<double[]>> findLinkedShores(
            Coastlines.TracedCoasts traced,
            Coastlines.TracedCoasts linked) {

        // Nothing was laid, so the second trace is the first and every stretch of it is already
        // drawn.
        if (linked == traced) {
            return List.of();
        }

        // Every line the first trace drew, since all of them are already on the map: a stretch
        // this pass would lay over a lake shore is as duplicated as one over an outer coast.
        var drawn = indexDrawnLines(gatherEveryShore(traced));
        var runs = new ArrayList<List<double[]>>();

        // Only the second trace's OUTER silhouettes. Its walled holes' shores face water the
        // fills and the continent coasts already account for - see the class note - and its
        // lakes are the first trace's lakes, which the cut would take away anyway.
        for (var ring : Coastlines.collectCoastOutlines(linked)) {
            runs.addAll(cutUndrawnRuns(ring, drawn));
        }
        return List.copyOf(runs);
    }

    // Every line a trace drew, whichever side of the water it was read from.
    //
    // The borders rather than the rounded rings the map puts on screen. Both traces are cut
    // against each other here, and the rounding moves a corner by up to its own radius - so
    // matched against rounded lines, a stretch the two traces agree on reads as new coastline
    // wherever it runs into a corner, and the layer lays a second copy of the first over it.
    private static List<List<double[]>> gatherEveryShore(Coastlines.TracedCoasts traced) {

        var shores = new ArrayList<>(Coastlines.collectCoastOutlines(traced));

        shores.addAll(Coastlines.collectLakeOutlines(traced));
        shores.addAll(Coastlines.collectWalledShoreOutlines(traced));

        return List.copyOf(shores);
    }

    // One ring of the second trace, cut into the stretches the drawn coasts do not carry. Walked
    // round from a point they DO carry, so a stretch straddling the ring's own start comes back
    // as the one run it is rather than as two ending nowhere.
    private static List<List<double[]>> cutUndrawnRuns(
            List<double[]> ring,
            UniformGrid<double[][]> drawn) {

        var begin = findDrawnStart(ring, drawn);

        // Nothing of this ring is on any drawn line, so the whole of it is new. Closed rather
        // than open: it has no ends, and beginning it anywhere would leave a seam.
        if (begin < 0) {
            return List.of(closeRing(ring));
        }

        var runs = new ArrayList<List<double[]>>();
        var run = new ArrayList<double[]>();

        // One step past the ring, so a run still open at the wrap closes against the drawn point
        // it started from.
        for (var step = 1; step <= ring.size(); step++) {

            var index = (begin + step) % ring.size();
            var point = ring.get(index);

            if (!isAlreadyDrawn(point, drawn)) {

                if (run.isEmpty()) {
                    run.add(ring.get((index + ring.size() - 1) % ring.size()));
                }
                run.add(point);

            } else if (!run.isEmpty()) {

                run.add(point);
                runs.add(List.copyOf(run));
                run = new ArrayList<>();
            }
        }
        return runs;
    }

    // Where to start walking a ring so that no run is split across the ends of the list: any
    // point a drawn coastline already carries. A ring with no such point is one the drawn coasts
    // know nothing about.
    private static int findDrawnStart(
            List<double[]> ring,
            UniformGrid<double[][]> drawn) {

        for (var index = 0; index < ring.size(); index++) {

            if (isAlreadyDrawn(ring.get(index), drawn)) {
                return index;
            }
        }
        return -1;
    }

    // A ring given back with its first point repeated at the end, so a run drawn from it is a
    // closed shape rather than one with a gap between its ends where the line was cut open.
    private static List<double[]> closeRing(List<double[]> ring) {

        var closed = new ArrayList<>(ring);

        closed.add(ring.get(0));

        return List.copyOf(closed);
    }

    // The drawn coastlines' own segments, filed under every grid cell they pass through. A sector
    // carries tens of thousands of them and the second trace asks about as many points, so asked
    // by walking the whole line each time this pass would cost more than either trace.
    private static UniformGrid<double[][]> indexDrawnLines(List<List<double[]>> rings) {

        var filed = new UniformGrid<double[][]>(LOOKUP_CELL_SIDE);

        for (var ring : rings) {
            for (var index = 1; index < ring.size(); index++) {

                var from = ring.get(index - 1);
                var to = ring.get(index);

                filed.fileSegment(from, to, new double[][] {from, to});
            }
        }
        return filed;
    }

    // Whether a drawn line runs through a point. Only the lines filed within the tolerance of it
    // are measured, which is every line that could be.
    private static boolean isAlreadyDrawn(double[] point, UniformGrid<double[][]> drawn) {

        for (var segment : drawn.listItemsNear(point[0], point[1], SAME_LINE)) {

            if (Segments.computeDistanceToPoint(
                    segment[SEGMENT_FROM], segment[SEGMENT_TO], point) <= SAME_LINE) {

                return true;
            }
        }
        return false;
    }
}
