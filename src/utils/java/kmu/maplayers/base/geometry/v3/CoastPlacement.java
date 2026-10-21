package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Limits;

import kmu.maplayers.base.geometry.Chord;
import kmu.maplayers.base.geometry.CoastMark;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.walls.DiscUnionBoundary;
import kmu.maplayers.base.geometry.walls.Walls;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Where a coast's line actually goes: which stretches of border it visits, and where on each
 * cell it runs.
 *
 * <p>The half of tracing a coast that is not the walk. {@link DiscUnionBoundary} answers what
 * the boundary IS - a run of marks, each naming a cell and an arc - and this answers what a
 * line drawn along those marks looks like: which of them are worth visiting at all, which is
 * {@link StretchSelection}'s, and where on a visited arc the line touches down and what to do
 * where a wall is in the way, which is {@link OutlinePlacement}'s.
 *
 * <p>Apart from {@link Coastlines} because the two answer to different things. Which stretches
 * survive and where a run lands are decided by the frontage floor, the reach anchor and the
 * walls; what a trace then IS - continents, lakes, puddles, shores - is decided by the water
 * floor and the shape of the sector. Held together, a change to either read as a change to
 * both, and the file was long enough that neither could be read without the other in the way.
 *
 * <p>One placer per set of rules over a trace's union and bridged cells, because every run of
 * that trace is placed against the same three.
 *
 * <p>Package-private throughout. Nothing outside this package has any business placing a line
 * on a cell: what a caller wants is a traced coast, and the entries that hand one back are
 * {@link Coastlines}'.
 */
final class CoastPlacement {

    private final StretchSelection selection;
    private final OutlinePlacement placement;

    /**
     * @param union   the discs to draw against
     * @param bridged the cells a laid wall attaches to, which selection never drops
     * @param rules   what selection drops, and how finely placement samples
     */
    CoastPlacement(DiscUnion union, Set<Integer> bridged, BorderRules rules) {

        this.selection = new StretchSelection(union, bridged, rules);
        this.placement = new OutlinePlacement(union, rules);
    }

    /**
     * Builds the outer border of every run of connected cells: selection, then placement.
     *
     * <p>Judged on the points that came out rather than on the cells that went in. A cell
     * contributes a whole run of border rather than a single point, so two cells that touch
     * enclose the pair perfectly well. Only a run that came out too small to be a shape at all
     * is dropped, which after the winding is settled means one that could not be built. Runs
     * of one cell never arrive here at all - a lone island is not a coast.
     *
     * @param silhouettes the stretches of coast the cells make, in walk order
     * @return one closed run of points per run of connected cells
     */
    PlacedCoasts buildSilhouetteBorders(List<List<CoastMark>> silhouettes) {

        var placed = new ArrayList<List<CoastVertex>>();
        var dropped = new ArrayList<CoastMark>();

        for (var silhouette : silhouettes) {

            var one = buildOneBorder(silhouette);

            dropped.addAll(one.dropped());

            if (!one.outline().isEmpty()) {
                placed.add(one.outline());
            }
        }
        return new PlacedCoasts(placed, List.copyOf(dropped));
    }

    /**
     * Selection and placement over ONE run of coast marks, whichever kind of coast it came off.
     *
     * <p>The two stages meet here and nowhere else, which is what makes the order between them
     * a fact of the code rather than a convention: selection settles which stretches the border
     * visits, and only then is placement handed them.
     *
     * <p>Named on its own because two callers want it one run at a time: the silhouettes go
     * through in a batch, and a lake pairs its finished shore with its own water's edge, so
     * it has to keep hold of which run produced which. Called through the batch with a
     * one-element list, a lake would have to read the answer back out of a list that may have
     * dropped it.
     *
     * @param coast one run of coast marks, in walk order
     * @return the placed outline and the stretches selection left out of it. The outline is
     *         empty where what came out was too small to enclose anything, which is what a run
     *         that could not be built comes to
     */
    PlacedCoast buildOneBorder(List<CoastMark> coast) {

        var visited = selection.selectVisitedStretches(coast);
        var outline = placement.placeClearedOutline(coast, visited);
        var dropped = new ArrayList<CoastMark>();

        for (var index = 0; index < coast.size(); index++) {

            if (!visited.contains(index)) {
                dropped.add(coast.get(index));
            }
        }

        return new PlacedCoast(
            outline.size() >= Limits.MIN_VERTICES_TO_ENCLOSE_AREA ? outline : List.of(),
            List.copyOf(dropped));
    }

    // A cell alone in the void has no coast, so the runs that name one are not silhouettes.
    //
    // A coast is where settled space ends along a run of cells that hold something BETWEEN
    // them. A cell touching nothing, joined to nothing, holds only itself: the line traced
    // round it is the cell's own border a second time, and there is no void it encloses that
    // the cell does not already draw. Kept, it is an edge drawn twice on screen, a stretch of
    // frontage in every coast measure that no reach can ever be laid along, and a ring that
    // answers "inside the coast" for points the cell already claims.
    //
    // Read off the run itself: cells that touch, and cells a laid bridge joins, are walked
    // into ONE run - so a run naming a single circle is exactly the degenerate case, with no
    // separate test for touching or for bridges to fall out of step with the walk.
    static List<List<CoastMark>> keepJoinedRuns(
            List<List<CoastMark>> silhouettes) {

        var joined = new ArrayList<List<CoastMark>>(silhouettes.size());

        for (var silhouette : silhouettes) {

            if (!isLoneIsland(silhouette)) {
                joined.add(silhouette);
            }
        }
        return joined;
    }

    // The other half of the same reading: which cells those dropped runs were.
    //
    // Kept because having no coast is not the same as not being there. A cell alone in the void
    // is a shape of the sector like any other - it can be reached, and a span laid to it joins
    // it to whatever it reaches - and the only thing it lacks is a line of its own to draw.
    //
    // An empty run names no cell and is not an island; it is a walk that found nothing, and
    // there is no cell to hand on.
    static List<Integer> collectLoneIslands(
            List<List<CoastMark>> silhouettes) {

        var islands = new ArrayList<Integer>();

        for (var silhouette : silhouettes) {

            if (isLoneIsland(silhouette) && !silhouette.isEmpty()) {
                islands.add(silhouette.get(0).circle());
            }
        }
        return List.copyOf(islands);
    }

    // The cells a laid wall attaches to. Asked of the laid chords rather than of every bridge
    // offered, because a bridge that was never drawn has no wall for the coast to cut across
    // and protecting its cells would only cost selection detail for nothing.
    static Set<Integer> collectBridgedCircles(List<Chord> laid) {

        var bridged = new LinkedHashSet<Integer>();

        for (var chord : laid) {

            bridged.add(chord.fromCircle());
            bridged.add(chord.toCircle());
        }

        return bridged;
    }

    // Whether a run of coast is one cell's own border and nothing else.
    private static boolean isLoneIsland(List<CoastMark> silhouette) {

        if (silhouette.isEmpty()) {
            return true;
        }

        for (var mark : silhouette) {

            if (mark.circle() != silhouette.get(0).circle()) {
                return false;
            }
        }
        return true;
    }

    /**
     * What selection and placement are run under, in the units those two stages work in.
     *
     * <p>One knob each, and they do not interact: the first is the whole of what selection
     * decides on, the second is how finely placement samples what selection left it. Rounding
     * takes nothing from here, since it happens to a finished border rather than while one is
     * being built.
     *
     * <p>Apart from {@link CoastRules}, which is the same settings as a caller sets them -
     * multiples of a cell radius, and a rounding no stage below reads. This is what those come
     * to once converted, so no stage has to convert them itself.
     *
     * @param minFrontageShare how much of its own border a cell has to face the void with to
     *                         be worth visiting, as a share of the whole turn. Selection's
     *                         only knob
     * @param arcSegments      how finely a half-turn of arc is sampled, which is how smooth
     *                         the fillets placement lays come out
     * @param walls            the walls the coast was walked against. Their channel is what
     *                         tells placement a join between two adjacent stretches is a
     *                         wall's side rather than the crossing of two cells - a crossing
     *                         has no length, a wall's side has at least the channel's - and
     *                         which cells they are pinched on is where a side ends in a point
     * @param wallPlacement    what placement does with a join that is a wall's side
     * @param reachAnchor      which turn the reachable window is read on, carried through from
     *                         {@link CoastRules} because selection's repair pass and placement
     *                         both ask the clamp and have to ask it the same way
     */
    record BorderRules(
        double minFrontageShare,
        int arcSegments,
        Walls walls,
        WallPlacement wallPlacement,
        StraightRuns.ReachAnchor reachAnchor) {
    }

    /**
     * What placement does with a join the walk made along a wall's side.
     *
     * <p>Two answers because the two shores stand on opposite sides of every wall. A shore
     * inside a wall's water is the wall's own edge, so it follows the side. The outer coast has
     * the wall on its land side and rounds up over it, so a side there is a stretch to cut
     * across like any other - except where the mouth at its end has closed to a single point,
     * where there is no width to cut and the coast is to meet the anchor.
     */
    enum WallPlacement {
        HUG_SIDES,
        TOUCH_POINT_MOUTHS
    }

    /**
     * What the two stages made of one run of coast, and what selection left out on the way.
     *
     * @param outline the placed line, or empty where it came out too small to be a shape
     * @param dropped every stretch of this run the line was not drawn through
     */
    record PlacedCoast(
        List<CoastVertex> outline,
        List<CoastMark> dropped) {
    }

    /**
     * What they made of a batch of them, and what was left out over all of them.
     *
     * <p>The stretches dropped are kept rather than discarded because they are the only record
     * of a decision selection otherwise makes silently. A coast that came out wrong looks the
     * same on screen whether a rule dropped too much or the walk never offered the stretch at
     * all, and those are opposite faults with opposite fixes.
     *
     * @param coasts  one closed run of points per run of connected cells, degenerate ones
     *                left out - so this does NOT line up with what went in
     * @param dropped every stretch the coast was not drawn through, over all of them
     */
    record PlacedCoasts(
        List<List<CoastVertex>> coasts,
        List<CoastMark> dropped) {
    }
}
