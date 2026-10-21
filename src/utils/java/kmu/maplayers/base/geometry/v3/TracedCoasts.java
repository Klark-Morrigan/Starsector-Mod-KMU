package kmu.maplayers.base.geometry.v3;

import kmu.maplayers.base.geometry.CoastMark;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.walls.Walls;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A traced coast and the two things it was traced against.
 *
 * <p>Handed back together because everything asked of a coast afterwards needs one of
 * them: which cells a reach crosses needs the discs, how much the smoothing took out
 * needs the stretches it started from, and laying more walls beside the coast's own needs
 * those walls. Rebuilt separately by each asker, they can be built from knobs that have
 * since moved.
 *
 * @param coasts       one coast per run of connected cells
 * @param silhouettes  the stretches of coast the cells make, in walk order, before any
 *                     smoothing. Carried rather than walked again by whatever wants them:
 *                     the walk is not cheap, and a second one is a second answer that can
 *                     disagree with the coast it is supposed to describe
 * @param islands      the cells the walk found alone in the void, which are on no silhouette
 *                     because they have no coast: a cell touching nothing draws its own
 *                     border and encloses nothing further. Handed on rather than forgotten,
 *                     since a shape with no coast is still a shape a span can reach - and
 *                     read off the walk that dropped them rather than worked out again, so
 *                     nothing can come to disagree with it about what is alone
 * @param dropped      the stretches selection chose not to pass through, over both
 *                     kinds of coast, which is the one thing about a finished line that
 *                     cannot be read back off it: a stretch the walk never offered and a
 *                     stretch a rule threw away are both simply absent from it
 * @param union        the discs it was walked and placed against, which are the same discs:
 *                     a coast is a border, and everything measured from a border has to be
 *                     measured from the one that border was built at
 * @param walls        the bridges it was walled by, and the channel they were laid at.
 *                     Carried for whatever lays more walls alongside them: found again
 *                     from the knobs, they are a second answer that can differ from the
 *                     one the coast was actually walked against
 * @param lakes        the void the cells closed around unaided - inland water, ringed by
 *                     land the whole way round. Out of the same walk and the same
 *                     smoothing as the coasts above, because a lake shore and an outer
 *                     shore are the same kind of line looked at from opposite sides
 * @param puddles      the holes the floor judged too small to be lakes. Kept rather than
 *                     discarded, because being too small for a shore does not stop them
 *                     being water: what a puddle earns instead is being filled up, from
 *                     the cell-pair spans laid across it
 * @param walledShores one shore per hole a laid wall closed, smoothed exactly as a lake
 *                     shore is. Apart from the lakes rather than among them, because what
 *                     shut the water in is the whole difference between the two: a lake is
 *                     the cells' own doing and this is a wall's, and the constructions that
 *                     fill such water already answer for it. Empty wherever no wall was
 *                     laid, which is every coast traced without them
 */
public record TracedCoasts(
    List<Coast> coasts,
    List<List<CoastMark>> silhouettes,
    List<Integer> islands,
    List<CoastMark> dropped,
    DiscUnion union,
    Walls walls,
    List<Lake> lakes,
    List<Puddle> puddles,
    List<Coast> walledShores) {

    /**
     * The stretches selection left out, as lines along the borders they sit on.
     *
     * <p>Drawn where the coast WOULD have run had it passed through them, which is what makes
     * them legible beside the line that replaced them: the gap between the two is exactly what
     * the rule bought. Sampled rather than chorded, so a wide frontage reads as the arc of
     * border it is rather than as a straight line cutting through its own cell.
     *
     * @param arcSegments how finely a half-turn of arc is sampled
     * @return one open run of points per dropped stretch
     */
    public List<List<double[]>> collectDroppedRuns(int arcSegments) {

        var runs = new ArrayList<List<double[]>>(dropped.size());

        for (var mark : dropped) {
            runs.add(Coastlines.sampleMarkArc(union, mark, arcSegments));
        }

        return List.copyOf(runs);
    }

    /**
     * Which continent each cell belongs to.
     *
     * <p>Read off the silhouettes rather than worked out again: the coast walk already
     * separated the cells into runs of touching neighbours - one run per continent - and that
     * separation is precisely what a continent IS here. Grouping them a second way would be a
     * second answer, and everything drawn from the two would stop describing one map.
     *
     * <p>Keyed off the SILHOUETTES rather than off the finished coasts, which is what makes a
     * continent's number mean the same thing to every asker. A silhouette too small to smooth
     * into a line leaves no coast behind it, so the two lists part company from there on, and a
     * number read off the coasts would name a different continent either side of the drop.
     *
     * @return the continent each cell sits on, by cell. A cell the outer walk never touched is
     *         absent rather than present under some sentinel - which includes every cell that
     *         faces only a lake, and is why nothing asks this about an interior shore
     */
    public Map<Integer, Integer> mapCellsToContinents() {

        var continentOf = new LinkedHashMap<Integer, Integer>();

        for (var continent = 0; continent < silhouettes.size(); continent++) {
            for (var mark : silhouettes.get(continent)) {

                continentOf.put(mark.circle(), continent);
            }
        }

        return continentOf;
    }

    /**
     * Which shape of the sector each cell belongs to: its continent, or its own island.
     *
     * <p>The continents come off the silhouettes, as everything else reads them. The islands
     * are the cells the walk found alone, which are on no silhouette at all - a lone cell has
     * no coast, since the line round it would be its own border drawn twice.
     *
     * <p><b>Numbered after the continents rather than among them</b>, so that a number means the
     * same thing here as it does to every other reader of the trace. What this adds is names for
     * shapes those readers have none for, and it adds them where they cannot be mistaken for a
     * continent's.
     *
     * <p>Its own reading rather than {@link #mapCellsToContinents}, and deliberately
     * not an extension of it: that map is indexed against the silhouettes by everything that
     * gathers per continent, so a cell numbered past the end of them would run off those lists.
     * What is wanted here is a coarser question - is this the same SHAPE - which the sector's
     * islands are part of the answer to and the continents' own bookkeeping is not.
     *
     * @return the shape each cell sits on, by cell; a cell on neither is absent
     */
    public Map<Integer, Integer> mapCellsToShapes() {

        var shapeOf = new LinkedHashMap<>(mapCellsToContinents());
        var island = silhouettes.size();

        for (var cell : islands) {

            shapeOf.put(cell, island);
            island++;
        }

        return shapeOf;
    }

    /**
     * Every straight reach of the outer coasts, in the order the coast is walked, told from a
     * handover by the channel the walls were laid at.
     *
     * @return one entry per reach, in walk order
     * @see Coastlines#collectStraightReaches(List, double)
     */
    List<CoastReach> collectStraightReaches() {
        return Coastlines.collectStraightReaches(coasts, walls.channel());
    }

    /**
     * Every outer coast as a bare closed ring: the border, with the cells its points sit on
     * dropped.
     *
     * <p>What a shape is judged against. Void outside this line is void nothing shut in, and a
     * span crossing it crosses the edge of settled space - so everything DECIDING against a
     * coast asks here, and what it gets is the line selection and placement settled rather than
     * anything a later pass made of it for the eye.
     *
     * <p>Rings rather than vertices for the readers that have no use for the cells: a point in
     * or out of a shape is answered by the shape.
     *
     * @return one ring per stretch of coast, in the order they were traced
     */
    public List<List<double[]>> collectCoastOutlines() {

        return coasts
            .stream()
            .map(Coast::collectPoints)
            .toList();
    }

    /**
     * Every lake shore as a bare closed ring, for the same readers as above.
     *
     * @return one ring per lake, in the order they were traced
     */
    public List<List<double[]>> collectLakeOutlines() {

        return lakes
            .stream()
            .map(lake -> lake.shore()
            .collectPoints())
            .toList();
    }

    /**
     * Every shore of water a laid wall closed, as a bare closed ring.
     *
     * @return one ring per walled hole, in the order they were traced; empty for a coast traced
     *         without walls
     */
    public List<List<double[]>> collectWalledShoreOutlines() {

        return walledShores
            .stream()
            .map(Coast::collectPoints)
            .toList();
    }

    /**
     * Every line the coast draws between land and water: the outer coasts, then the lake
     * shores.
     *
     * <p>What a span is judged against as a wall, and what a span's water is bounded by. Both
     * kinds together, because a span is laid across either and a check that read only one
     * would let a span double the other.
     *
     * @return the outer coasts' rings followed by the lakes', each in the order traced
     */
    public List<List<double[]>> collectShoreOutlines() {

        var shores = new ArrayList<>(collectCoastOutlines());

        shores.addAll(collectLakeOutlines());

        return shores;
    }
}
