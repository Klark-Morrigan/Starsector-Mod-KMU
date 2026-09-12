package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.Segments;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The black patch, found rather than clicked on: settled space with nothing drawn in it.
 *
 * <p>The map fills the void layer by layer - the water behind the shores, the bays the spans
 * hold, the lakes and the puddles, the sea between the continents - and each layer reports on
 * its own answer. None of them can report the thing a reader actually sees, because it is
 * precisely the case each believes belongs to another: a patch inside the coast, clear of every
 * cell, that no layer filled.
 *
 * <p>Asked of the PICTURE rather than of the layers. A patch is judged by the same facts that
 * decide what is painted there - inside the drawn coast, clear of the band a fill gives up
 * against that line, clear of every cell at the reach fills are drawn against, and inside no
 * fill - so a patch reported here is black on screen, and a map with none has no black left in
 * it. Matching holes to pockets cannot say that: a pocket is shaped after the hole it came from,
 * so a hole with a pocket to its name can still leave a patch of map uncovered.
 *
 * <p>Read off the same inventory the drawing reads, for the reason above: a report that listed
 * the layers for itself would call a patch bare that the map is plainly painting the moment the
 * two lists came apart.
 *
 * <p>Sampled on a grid, because area is what the question is about. The spacing decides the
 * smallest patch that can be seen and nothing else; a patch worth a reader's attention is
 * thousands of units across, and a run of samples is what says how big the one found is.
 *
 * <p>Companion to {@link PickedPointCheck}, which asks the same question of one point a person
 * put their cursor on. This one asks it of the whole map, so the answer arrives without anyone
 * having to find the patch first.
 */
public final class UndrawnVoid {

    // How far apart the samples are. Well inside the smallest pocket worth drawing, so no
    // patch a reader would notice can fall between two of them, and coarse enough that the
    // whole of a five-hundred cell sector is swept in seconds.
    private static final double SAMPLE_STEP = 400;

    // How many samples a run must hold to be reported. Below this what is being measured is
    // where the grid happens to land against the edge of a channel rather than a piece of map
    // with nothing in it.
    private static final int MIN_PATCH_SAMPLES = 4;

    // The eight neighbours of a sample, as {across, down} steps. Diagonals included, so a
    // patch pinched to a corner between two cells is read as one patch rather than two.
    private static final int[][] NEIGHBOURING_STEPS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private UndrawnVoid() {
    }

    /**
     * One patch of settled space with nothing drawn in it.
     *
     * @param at      a point inside it, which is where to look on the map
     * @param across  how far apart its two most distant samples are
     * @param samples how many samples it holds, which is its area in units of the grid
     */
    public record UnfilledPatch(
        double[] at,
        double across,
        int samples) {

        @Override
        public String toString() {
            return String.format(
                Locale.ROOT,
                "%.0f across at %.0f,%.0f, over %d samples",
                across,
                at[0],
                at[1],
                samples);
        }
    }

    /**
     * Finds every patch of the map that lies inside the coast and is drawn by nothing.
     *
     * <p>The water should have been opened with every site taken as unowned, because a pocket
     * that one owner rings is pushed out into that owner's fills instead of being drawn as void
     * - so with a colouring in hand the shapes move, and a patch would be reported or not
     * depending on who happens to hold the cells around it rather than on whether anything drew
     * that patch of map.
     *
     * @param laid  the coast with its walls down - handed in rather than laid again, since a
     *              patch judged against one laying says nothing about a map drawn under another
     * @param water what the map paints over the void, which says what a sample can be inside
     *              of and which map - as drawn, or the void's true extent - is being asked about
     * @return one entry per patch, widest first
     */
    static List<UnfilledPatch> findUnfilledVoid(LaidCoast laid, FilledWater water) {

        var parameters = laid.parameters();
        var shaping = water.shaping();

        // The reach fills are drawn against, which is also the reach a patch has to be clear
        // of the cells at. At the drawn shaping that is a channel outside them, so the band
        // every fill gives up is not counted as map it failed to cover.
        var clearOfCells = shaping.isAtTrueExtent()
            ? parameters.cellRadius()
            : parameters.measureDrawnReach();

        // The same channel again, against the coast: a pocket stops short of the line that
        // closed it by exactly this much, so the band inside the coast is bare by construction.
        var clearOfCoast = shaping.isAtTrueExtent() ? 0 : parameters.borderInset();

        var patches = collectPatches(collectBareSamples(
            laid,
            Coastlines.collectCoastOutlines(laid.traced()),
            collectDrawnFills(water),
            clearOfCells,
            clearOfCoast));

        patches.sort(Comparator.comparingDouble(UnfilledPatch::across).reversed());

        return patches;
    }

    // Everything the map fills void with, every layer of it, each with the box it lies in. What
    // a sample asks is whether ANY fill covers it, and which one did is the layers' own
    // business; the box is what keeps that question cheap enough to ask of a whole sector.
    //
    // A lake's margin goes in as the band it is rather than as its outer ring. The open water
    // inside a drawn shore is left bare on purpose unless some other layer covers it, and that
    // other layer is in this list on its own account - so a margin that claimed the whole lake
    // would hide exactly the patch this exists to find.
    private static List<BoxedFill> collectDrawnFills(FilledWater water) {

        var fills = new ArrayList<BoxedFill>();

        for (var outline : water.collectShoreWater()) {
            fills.add(BoxedFill.boxFill(outline));
        }
        for (var outline : water.collectInletWater()) {
            fills.add(BoxedFill.boxFill(outline));
        }
        for (var outline : water.collectLakeWater()) {
            fills.add(BoxedFill.boxFill(outline));
        }
        for (var outline : water.collectPuddleWater()) {
            fills.add(BoxedFill.boxFill(outline));
        }
        for (var outline : water.collectLinkWater()) {
            fills.add(BoxedFill.boxFill(outline));
        }
        for (var outline : water.collectLinkedSectorWater()) {
            fills.add(BoxedFill.boxFill(outline));
        }
        for (var margin : water.collectLakeMargins()) {
            fills.add(BoxedFill.boxMargin(margin.waterEdge(), margin.drawnShore()));
        }
        return fills;
    }

    // Every sample of the grid that is inside the coast, clear of the cells and covered by
    // nothing - which is the definition of black on screen, asked one point at a time.
    //
    // In the order the tests are cheapest, since all but a few per cent of the sector fails
    // one of the first two: the cells are a distance each, the coast is a ring walk, and the
    // fills are only reached by a sample that has already turned out to be bare.
    private static List<double[]> collectBareSamples(
            LaidCoast laid,
            List<List<double[]>> coasts,
            List<BoxedFill> fills,
            double clearOfCells,
            double clearOfCoast) {

        // The sites' own box, opened out by a cell's reach. A coast runs round the OUTSIDE of
        // the outermost cells, so map inside it lies up to a full reach beyond the last site -
        // and a grid stopping at the sites would call that band swept without ever having
        // looked at it.
        var sites = laid.sites();
        var cells = new DiscUnion(sites, clearOfCells);
        var box = Bounds.computeEnclosingBounds(sites);
        var bare = new ArrayList<double[]>();

        var tall = box.maxY() - box.minY() + 2 * clearOfCells;
        var wide = box.maxX() - box.minX() + 2 * clearOfCells;

        for (var down = 0; down * SAMPLE_STEP <= tall; down++) {
            for (var across = 0; across * SAMPLE_STEP <= wide; across++) {

                var at = new double[] {
                    box.minX() - clearOfCells + across * SAMPLE_STEP,
                    box.minY() - clearOfCells + down * SAMPLE_STEP};

                if (cells.isPointInside(at)
                        || !isWellInsideCoast(coasts, at, clearOfCoast)
                        || isBesideAnyWall(laid, at, clearOfCoast)
                        || isCoveredByAny(fills, at)) {

                    continue;
                }
                bare.add(at);
            }
        }
        return bare;
    }

    // Bare samples gathered into the patches they form, so one hole in the map is one line of
    // report rather than forty. Grown outward from each unclaimed sample through its
    // neighbours, which is what makes a patch a patch: map a reader's eye runs across without
    // meeting a fill.
    private static List<UnfilledPatch> collectPatches(List<double[]> bare) {

        var taken = new boolean[bare.size()];
        var patches = new ArrayList<UnfilledPatch>();

        for (var seed = 0; seed < bare.size(); seed++) {

            if (taken[seed]) {
                continue;
            }
            var run = growPatch(bare, taken, seed);

            if (run.size() >= MIN_PATCH_SAMPLES) {
                patches.add(new UnfilledPatch(
                    Points.computeMean(run), measureWidest(run), run.size()));
            }
        }
        return patches;
    }

    // One patch, taken from a sample outward: every bare sample a step away, and every one a
    // step from those.
    private static List<double[]> growPatch(
            List<double[]> bare,
            boolean[] taken,
            int seed) {

        var run = new ArrayList<double[]>();
        var pending = new ArrayDeque<Integer>();

        pending.add(seed);
        taken[seed] = true;

        while (!pending.isEmpty()) {

            var at = pending.poll();

            run.add(bare.get(at));

            for (var other = 0; other < bare.size(); other++) {

                if (!taken[other] && isNeighbouring(bare.get(at), bare.get(other))) {

                    taken[other] = true;
                    pending.add(other);
                }
            }
        }
        return run;
    }

    // Whether two samples are a single step apart on the grid, in any of the eight directions.
    private static boolean isNeighbouring(double[] one, double[] other) {

        for (var step : NEIGHBOURING_STEPS) {

            if (Math.abs(one[0] + step[0] * SAMPLE_STEP - other[0]) < SAMPLE_STEP / 2
                    && Math.abs(one[1] + step[1] * SAMPLE_STEP - other[1]) < SAMPLE_STEP / 2) {

                return true;
            }
        }
        return false;
    }

    // How far apart a patch's two most distant samples are, which is what says whether it is a
    // pocket-sized hole or a sliver along an edge.
    private static double measureWidest(List<double[]> run) {

        var widest = 0.0;

        for (var one = 0; one < run.size(); one++) {
            for (var other = one + 1; other < run.size(); other++) {
                widest = Math.max(
                    widest, Points.computeDistance(run.get(one), run.get(other)));
            }
        }
        return widest;
    }

    // Whether a point lies in the band a wall holds its two sides apart by.
    //
    // A wall divides void into two pockets, and each of them gives up the channel against it -
    // so the strip along every laid wall is bare by construction, exactly as the strip along a
    // cell's border is. Left in, those strips are the whole population: they run the length of
    // every span on the map and swamp the patch anyone is looking for.
    //
    // Asked of every wall offered rather than only of those laid, because a wall the walk
    // turned down leaves no strip and so can only cost a sliver a channel wide - where telling
    // the two sets apart means naming the reach the trace was run at, which is a second answer
    // to go out of step with the map.
    private static boolean isBesideAnyWall(LaidCoast laid, double[] at, double clearBy) {

        for (var chord : laid.walls().chords()) {

            if (Segments.computeDistanceToPoint(
                    chord.findStart(), chord.findEnd(), at) <= clearBy) {
                return true;
            }
        }
        return false;
    }

    // Whether a point is inside the coast by more than the band a fill gives up against it.
    private static boolean isWellInsideCoast(
            List<List<double[]>> coasts,
            double[] at,
            double clearBy) {

        for (var coast : coasts) {

            if (PolygonRegions.isPointInsideRing(coast, at[0], at[1])
                    && PolygonRegions.computeDistanceToBoundary(coast, at) > clearBy) {

                return true;
            }
        }
        return false;
    }

    // Whether any fill on the map covers a point, which is the last and dearest of the
    // tests - and so the one asked only of a sample that has already turned out to be bare.
    private static boolean isCoveredByAny(List<BoxedFill> fills, double[] at) {

        for (var fill : fills) {

            if (fill.holds(at)) {
                return true;
            }
        }
        return false;
    }

    /**
     * One drawn fill with the box it lies in, and the hole it leaves where it is a margin.
     *
     * <p>The box is what makes sweeping a whole sector affordable: a ring walk per fill per
     * sample is hundreds of millions of steps, where a box rejects all but the handful of
     * fills a sample could possibly be in.
     *
     * @param outline the fill's own ring
     * @param hole    the ring inside it that the fill stops at, empty where the fill is whole
     * @param box     the box the outline lies within
     */
    private record BoxedFill(
        List<double[]> outline,
        List<double[]> hole,
        Bounds box) {

        // One whole fill with its box worked out, which is how every solid layer enters the
        // sweep.
        static BoxedFill boxFill(List<double[]> outline) {
            return new BoxedFill(outline, List.of(), Bounds.computeEnclosingBounds(outline));
        }

        // One band between two rings, which is how a lake's margin enters it: covering the
        // water between the shore and the cells' edge, and none of the water inside the shore.
        static BoxedFill boxMargin(List<double[]> outer, List<double[]> inner) {
            return new BoxedFill(outer, inner, Bounds.computeEnclosingBounds(outer));
        }

        // Whether this fill covers a point - the box first, since almost every fill on the
        // map is nowhere near any given sample and a box rejects those in four comparisons.
        boolean holds(double[] at) {

            return at[0] >= box.minX()
                && at[0] <= box.maxX()
                && at[1] >= box.minY()
                && at[1] <= box.maxY()
                && PolygonRegions.isPointInsideRing(outline, at[0], at[1])
                && (hole.isEmpty() || !PolygonRegions.isPointInsideRing(hole, at[0], at[1]));
        }
    }
}
