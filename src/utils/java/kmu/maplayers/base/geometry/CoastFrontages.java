package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Angles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which stretches of a cell's border a wall may be anchored on.
 *
 * <p>A wall laid onto a coast that is already drawn has to end ON that line, since one ending
 * short of it closes nothing off. So the places a wall may start and finish are the places the
 * coast runs along a cell - and that is a fraction of each cell rather than all of it, because
 * the rest of a cell's border either faces land or faces water some other line has closed.
 *
 * <p>Its own class because that question is asked by two quite different things. What lays the
 * walls asks it to choose where to anchor them; the viewer asks it to draw the answer, so that
 * a wall which looks as though it ignored a nearer cell can be seen never to have been offered
 * anywhere nearer. Left inside the search, the second caller would be reaching into it for a
 * step that has nothing to do with searching.
 *
 * <p>Read off the drawn ring rather than off the arcs the walk found uncovered. The two differ:
 * smoothing passes through only part of what a cell exposes, and it is the drawn line a wall
 * has to meet.
 *
 * <p><b>Except where there is no drawn line.</b> A cell alone in the void is on no coast at all,
 * having none to be on, and its border is nonetheless a border a span may reach - so an island
 * is answered from its own rim. The exception is narrow and stays narrow: it applies to cells
 * the walk itself set aside, and to no others.
 */
public final class CoastFrontages {

    // No cell yet, while a walk is between one frontage and the next. Named rather than left
    // as a bare -1 because it is compared against real cell numbers.
    private static final int NO_CELL = -1;

    // How many half turns a whole one is, which is what turns the arc density the coasts are
    // sampled at into the step count a full circle wants.
    private static final int WHOLE_TURNS_OF_HALF_TURNS = 2;

    private CoastFrontages() {
    }

    /**
     * Which of a construction's two coastlines a frontage is read off.
     *
     * <p>The same line seen from opposite sides: an exterior shore has the void outside it and
     * the cells within, an interior one has the water within and the cells around. So the same
     * question is asked of both - where may a wall be anchored - and both answer it the same
     * way, which is why one walk serves them and the difference is only which rings it is
     * offered.
     *
     * <p>Named rather than left as a choice of method, because what is anchored on which shore
     * is a decision a caller makes and then has to keep: a search handed one construction's
     * frontages and another's coast would compile and describe a map nobody drew. Asking the
     * shore for them off a trace makes the pair impossible to get wrong.
     */
    public enum Shore {

        /** The outer coastlines, whose spans cross the void between and around continents. */
        EXTERIOR {
            @Override
            public Map<Integer, List<List<double[]>>> collectFrontages(
                    Coastlines.TracedCoasts traced) {

                return collectBridgeFrontages(traced);
            }
        },

        /** The lake shores, whose spans cross water the cells closed around unaided. */
        INTERIOR {
            @Override
            public Map<Integer, List<List<double[]>>> collectFrontages(
                    Coastlines.TracedCoasts traced) {

                return collectLakeFrontages(traced);
            }
        };

        /**
         * Every stretch of this shore a wall may be anchored on, by cell.
         *
         * @param traced the coast to read them off
         * @return each cell's own stretches, in walk order; a cell no line of this shore runs
         *         along is absent
         */
        public abstract Map<Integer, List<List<double[]>>> collectFrontages(
                Coastlines.TracedCoasts traced);
    }

    /**
     * Every point the traced coastline passes through on a cell, gathered by the cell.
     *
     * <p><b>The whole frontage, not merely its two ends.</b> A span is anchored wherever the
     * coast comes closest to the cell across the gap, and that is generally somewhere along a
     * frontage rather than at a corner of one. Offered only the corners, every span leaving a
     * cell has to start at one of two places, so several of them start at the SAME place and
     * leave in a fan - which is a picture of what the anchors allowed rather than of where the
     * void is narrow.
     *
     * <p><b>Points of the traced line, rather than places computed on the cell's arc.</b> Two
     * reasons, and the second is the one that matters. The line is sampled, so a point worked
     * out on the true arc sits off its chord by the sagitta - close, but not ON the line the
     * pieces are cut against. And a span landing between two of its points forces that
     * stretch of coast to be split there, which where it lands near an existing point leaves a
     * sliver of coast shorter than anything else on the map. Anchoring on a point the line
     * already has costs a little precision - the anchor is quantised to the sampling, a
     * fraction of a cell radius - and buys exactness against the line every wall of this
     * construction is measured on. Not the ROUNDED ring the map draws: that is presentation,
     * and it leaves the vertices wherever it cuts a corner.
     *
     * @param traced the coast
     * @return each cell's own stretch of traced coast, in walk order. A cell the coast never
     *         runs along is absent rather than present under an empty list, since it offers
     *         nowhere to anchor at all
     */
    public static Map<Integer, List<List<double[]>>> collectBridgeFrontages(
            Coastlines.TracedCoasts traced) {

        return collectFrontagesAlong(traced.coasts());
    }

    /**
     * The cells whose whole bridgeable frontage is a single point.
     *
     * <p>Such a cell offers a wall exactly one place to land, and a wall of any width there
     * buries it: the mouth takes the border either side of the anchor and a coast running up
     * to the wall stops a channel short of the point it was offered. So these are the cells a
     * wall is laid on with no width at all - the one exception to the channel - and they are
     * named here because this is where the frontage that decides it is read.
     *
     * @param traced the coast
     * @return the cells, empty where every eligible cell offers a stretch
     */
    public static Set<Integer> collectPinchedCells(Coastlines.TracedCoasts traced) {

        var pinched = new LinkedHashSet<Integer>();

        for (var entry : collectBridgeFrontages(traced).entrySet()) {

            var isPoint = true;

            for (var run : entry.getValue()) {
                if (run.size() > 1) {
                    isPoint = false;
                }
            }
            if (isPoint) {
                pinched.add(entry.getKey());
            }
        }
        return Set.copyOf(pinched);
    }

    /**
     * The same question asked of the cells that have no coast at all: an island's whole rim.
     *
     * <p>A cell alone in the void is on no silhouette, because the line round it would be its
     * own border drawn a second time and would enclose nothing the cell does not already claim.
     * That is a good reason not to DRAW it and no reason at all to refuse to reach it: the cell
     * is there, it faces the void the whole way round, and a span laid to it joins it to
     * whatever is on the other end.
     *
     * <p>So the whole turn is offered, which is exactly what "the part of a cell's border that
     * faces the void" comes to for a cell with no neighbours. Sampled at the density the coasts
     * are, so an anchor on an island and an anchor on a continent are places of the same
     * spacing and the rules that measure between them read one scale.
     *
     * <p>Closed, unlike a coast's frontage, since an island's rim has no ends - the run comes
     * back to where it started, and the last point is not repeated.
     *
     * @param traced      the coast, for the islands it found and the cells to measure them on
     * @param arcSegments how many steps to sample a half turn at
     * @return each island's rim, by cell; empty where the sector has no islands
     */
    public static Map<Integer, List<List<double[]>>> collectIslandFrontages(
            Coastlines.TracedCoasts traced,
            int arcSegments) {

        var frontages = new LinkedHashMap<Integer, List<List<double[]>>>();
        var union = traced.union();
        var steps = Math.max(1, arcSegments * WHOLE_TURNS_OF_HALF_TURNS);

        for (var island : traced.islands()) {

            var site = union.sites().get(island);
            var rim = new ArrayList<double[]>(steps);

            for (var step = 0; step < steps; step++) {

                var angle = Angles.FULL_TURN * step / steps;

                rim.add(new double[] {
                    site[0] + union.reach() * Math.cos(angle),
                    site[1] + union.reach() * Math.sin(angle)});
            }
            frontages.put(island, List.of(List.copyOf(rim)));
        }
        return frontages;
    }

    /**
     * The same question asked of the lake shores: every point an interior coastline passes
     * through on a cell, gathered by the cell.
     *
     * @param traced the coast
     * @return each cell's own stretch of drawn lake shore, in walk order; a cell no shore
     *         runs along is absent
     */
    public static Map<Integer, List<List<double[]>>> collectLakeFrontages(
            Coastlines.TracedCoasts traced) {

        return collectFrontagesAlong(
            traced.lakes().stream().map(Coastlines.Lake::shore).toList());
    }

    // The walk itself, over whichever rings were offered - both entries above are one
    // question about two sets of lines, and a second copy of the walk is how the two come to
    // cut frontages differently.
    private static Map<Integer, List<List<double[]>>> collectFrontagesAlong(
            List<Coastlines.Coast> coasts) {

        var frontages = new LinkedHashMap<Integer, List<List<double[]>>>();

        for (var line : coasts) {

            var coast = line.vertices();

            if (coast.isEmpty()) {
                continue;
            }

            // Walked from a place the cell changes rather than from the list's first entry,
            // so a frontage straddling the ring's own start comes back as the one run it is
            // rather than as two ending nowhere.
            var begin = findFrontageStart(coast);
            var run = new ArrayList<double[]>();
            var runCell = NO_CELL;

            for (var step = 0; step < coast.size(); step++) {

                var vertex = coast.get((begin + step) % coast.size());

                if (vertex.circle() != runCell) {

                    addFrontage(frontages, runCell, run);
                    run = new ArrayList<>();
                    runCell = vertex.circle();
                }
                run.add(vertex.point());
            }
            addFrontage(frontages, runCell, run);
        }
        return frontages;
    }

    // Where to start walking a ring so that no frontage is split across the ends of the list.
    // A ring the coast runs round without ever changing cell has no such place, and starting
    // anywhere is as good as anywhere else.
    private static int findFrontageStart(List<Coastlines.CoastVertex> coast) {

        for (var index = 0; index < coast.size(); index++) {

            var previous = coast.get((index + coast.size() - 1) % coast.size());

            if (coast.get(index).circle() != previous.circle()) {
                return index;
            }
        }
        return 0;
    }

    // One run filed under the cell whose border it lies on, dropping what cannot be anchored
    // on: a run belonging to no cell, and an empty one. Both would otherwise be offered to the
    // span search as somewhere to start from.
    private static void addFrontage(
            Map<Integer, List<List<double[]>>> frontages,
            int cell,
            List<double[]> run) {

        if (cell == NO_CELL || run.isEmpty()) {
            return;
        }

        frontages
            .computeIfAbsent(cell, whichever -> new ArrayList<>())
            .add(List.copyOf(run));
    }

    // Every point of a cell's frontages together, which is what choosing an anchor asks for.
    // Which run a point came from matters to a reader looking at the map and not at all to the
    // search, since any point of any of them is somewhere a span may be anchored.
    public static Map<Integer, List<double[]>> gatherFrontagePoints(
            Map<Integer, List<List<double[]>>> frontages) {

        var points = new LinkedHashMap<Integer, List<double[]>>();

        for (var entry : frontages.entrySet()) {
            for (var run : entry.getValue()) {

                points
                    .computeIfAbsent(entry.getKey(), whichever -> new ArrayList<>())
                    .addAll(run);
            }
        }
        return points;
    }
}
