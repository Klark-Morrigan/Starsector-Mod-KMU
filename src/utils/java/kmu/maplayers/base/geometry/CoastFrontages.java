package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 */
public final class CoastFrontages {

    // No cell yet, while a walk is between one frontage and the next. Named rather than left
    // as a bare -1 because it is compared against real cell numbers.
    private static final int NO_CELL = -1;

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

            @Override
            public double resolveOfferDistance(double reach, double cellRadius) {
                return reach;
            }

            @Override
            public boolean isSpanWithinReach(CellGap span, double reach) {
                return true;
            }
        },

        /** The lake shores, whose spans cross water the cells closed around unaided. */
        INTERIOR {
            @Override
            public Map<Integer, List<List<double[]>>> collectFrontages(
                    Coastlines.TracedCoasts traced) {

                return collectLakeFrontages(traced);
            }

            @Override
            public double resolveOfferDistance(double reach, double cellRadius) {

                // A span's ends sit on the cells' rims, so its width undershoots the centre
                // distance by up to a diameter - which is exactly how far the offer has to be
                // loosened for the width gate below to see every span it should judge.
                return reach + CELL_DIAMETERS_OF_SLACK * cellRadius;
            }

            @Override
            public boolean isSpanWithinReach(CellGap span, double reach) {
                return span.width() <= reach;
            }
        };

        // How much a cell's own body adds to the centre distance of a pair whose shores touch:
        // one radius each side.
        private static final double CELL_DIAMETERS_OF_SLACK = 2;

        /**
         * Every stretch of this shore a wall may be anchored on, by cell.
         *
         * @param traced the coast to read them off
         * @return each cell's own stretches, in walk order; a cell no line of this shore runs
         *         along is absent
         */
        public abstract Map<Integer, List<List<double[]>>> collectFrontages(
                Coastlines.TracedCoasts traced);

        /**
         * How far apart two cells may sit, centre to centre, and still have a span offered.
         *
         * <p>The exterior shore answers with the reach itself: its water is unbounded, so how
         * far apart the cells sit is the only measure of separation there is, and that is the
         * settled meaning of the knob. The interior shore loosens the offer by a cell's
         * diameter and gates the span itself instead, because its water is bounded and it is
         * the WATER that is bridged: two cells facing each other across a wide lake have near
         * shores and far centres, and gated at the centres they are never offered at all.
         *
         * @param reach      the reach the knob asks for, in map units
         * @param cellRadius one cell's radius
         * @return the centre distance within which a pair is worth offering
         */
        public abstract double resolveOfferDistance(double reach, double cellRadius);

        /**
         * Whether a span found between two offered cells crosses no more water than the reach
         * allows.
         *
         * <p>The second half of the offer gate: where {@link #resolveOfferDistance} loosened
         * the centre distance, this is what holds the reach's actual meaning - always true on
         * the exterior shore, whose gate was the centre distance itself.
         *
         * @param span  the span, at its chosen anchors
         * @param reach the reach the knob asks for, in map units
         * @return whether it is within reach
         */
        public abstract boolean isSpanWithinReach(CellGap span, double reach);
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
