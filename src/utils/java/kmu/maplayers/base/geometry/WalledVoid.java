package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * Void read behind a set of walls: every hole one walk finds, and which of a caller's own walls
 * closed each.
 *
 * <p>The step every construction that fills void takes. Each of them lays a different wall set
 * and keeps a different answer - the coast keeps what its reaches closed, the bridges keep what
 * their spans closed - but between choosing the walls and choosing what to keep they all do the
 * same two things, and those two are here.
 *
 * <p><b>The discs are built to match the shaping, and the walls are laid across them.</b> Which
 * discs a hole is walked against is not a detail a caller may vary: a pocket gives up the
 * channel against the cells by being WALKED a channel outside them, so the reach the union is
 * built at and the shaping the caller asked for are one decision. Made per construction, two
 * fills drawn side by side could be inset differently while looking like one map.
 *
 * <p><b>Which walls closed a hole is read off the walk, not matched afterwards.</b>
 * {@link VoidHole#walledBy} is the walk's own record of what each cycle came to rest against,
 * so asking it costs nothing and cannot disagree with the shape it hands back. Worked out again
 * by testing outlines against lines, the answer would be a second opinion about a walk that has
 * already finished.
 *
 * <p>What is NOT here is the keep-rule. A hole no wall of the caller's closed may be that
 * caller's to draw anyway - the bridge fill takes the void the cells enclose unaided as readily
 * as the bays its own spans wall off - or it may belong to another layer entirely, which is what
 * the coast's own pockets answer for the same holes. Neither is more correct, so neither is
 * decided here. {@link #traceVoidWalledBy} only APPLIES the rule a caller states, by taking the
 * walls it means to keep on as an argument.
 */
final class WalledVoid {

    private WalledVoid() {
    }

    /**
     * Every hole in the void, with the walls laid across it.
     *
     * @param sites      the sites the void lies between
     * @param walls      the walls to lay, at the channel each keeps
     * @param parameters the knobs the cells are built under, which are also what the arcs are
     *                   flattened onto
     * @param shaping    how much of the channel each hole gives up against the cells, which is
     *                   taken by walking the discs at a moved reach rather than by offsetting
     *                   afterwards
     * @return the holes, in the order the boundary walk found them
     */
    static List<VoidHole> traceVoidAcrossWalls(
            List<double[]> sites,
            DiscUnionBoundary.Walls walls,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        return DiscUnionBoundary.traceHolesAcrossWalls(
            VoidPockets.buildUnionFor(sites, parameters, shaping),
            walls,
            parameters.boundSegments());
    }

    /**
     * Every hole that came to rest against a wall the caller means to keep on.
     *
     * <p>The walls LAID are not the walls kept on. A construction lays everything that divides
     * the void - its own lines, and whatever another layer already put down - so that its shapes
     * stop where the map says they stop, and then keeps only the holes its own lines closed.
     * Handed one list for both, a caller either loses the divisions or claims the other layer's
     * water.
     *
     * @param sites      the sites the void lies between
     * @param walls      every wall to lay, at the channel each keeps
     * @param keepOn     those of them a hole has to close on to be the caller's, which are the
     *                   caller's own lines
     * @param parameters the knobs the cells are built under
     * @param shaping    how much of the channel each hole gives up against the cells
     * @return the holes at least one of {@code keepOn} closed, in walk order
     */
    static List<VoidHole> traceVoidWalledBy(
            List<double[]> sites,
            DiscUnionBoundary.Walls walls,
            List<DiscUnionBoundary.Chord> keepOn,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        var walled = new ArrayList<VoidHole>();

        for (var hole : traceVoidAcrossWalls(sites, walls, parameters, shaping)) {

            if (!findClosingWalls(hole, keepOn).isEmpty()) {
                walled.add(hole);
            }
        }
        return walled;
    }

    /**
     * Which of a set of walls closed one hole.
     *
     * <p>Empty for a hole the cells closed unaided, and for one closed only by walls the caller
     * did not ask about - a coast reach, where the caller is asking about spans. Those two are
     * not the same thing to a caller, and both are for it to decide about rather than for this
     * to rule on.
     *
     * @param hole  the hole
     * @param among the walls to ask about, which are the caller's own
     * @return those of them it came to rest against, in the order the walk recorded them
     */
    static List<DiscUnionBoundary.Chord> findClosingWalls(
            VoidHole hole,
            List<DiscUnionBoundary.Chord> among) {

        var closing = new ArrayList<>(hole.walledBy());

        closing.retainAll(among);

        return List.copyOf(closing);
    }
}
