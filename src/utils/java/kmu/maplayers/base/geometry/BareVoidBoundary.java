package kmu.maplayers.base.geometry;

import kmu.maplayers.base.geometry.walls.DiscUnionBoundary;
import kmu.maplayers.base.geometry.walls.Walls;

import java.util.List;

/**
 * The void the cells bind with nothing laid across it.
 *
 * <p>The holes of the cells' own union: what is void because no cell reaches it, before any
 * question of coasts, spans or links. One call, because there is only one thing to ask.
 *
 * <p><b>This is the one way into the trace that does not go through the walls.</b> The sweep it
 * calls is the same sweep {@link DiscUnionBoundary} runs, and walls are threaded all the way
 * through it - there is no separate unwalled tracer to extract, which is itself the shape of
 * the problem. So the separation is made where it can be made: the walled trace and everything
 * that judges what may be laid live in their own package, and this door stands outside it.
 *
 * <p>Why that matters is a measurement rather than a preference. The sweep's model is that a
 * thing COVERS a stretch of a circle and the boundary is the gaps between the covers, and a
 * wall laid at no width covers nothing - a contradiction the walled trace carries five separate
 * accommodations for. Traced with no walls, not one of them fires, so this reading is sound in
 * a way the walled one has to be argued for case by case.
 *
 * <p>That is what a reading built on the true partition is checked against, and the reason the
 * door is worth naming rather than left as one more method on a class of sixty.
 */
public final class BareVoidBoundary {

    private BareVoidBoundary() {
    }

    /**
     * Every hole in the union of the cells' reach discs.
     *
     * <p>Run at the true reach it finds the pockets; run at the reach plus the channel it finds
     * what is left of them once the channel is taken out; run at the reach minus it, what they
     * become when the cells fill right up to them.
     *
     * <p>Run afresh at each reach rather than redrawing one ring's arcs at another radius. A
     * ring is not the same ring at a different reach: a cell whose arc its neighbours have
     * swallowed drops out of it, and a pocket can pinch in two. Redrawing in place cannot
     * express either, and reads both as the pocket having closed.
     *
     * @param union         the discs to trace
     * @param boundSegments sides of the cells' own radius bound, whose vertex angles every arc
     *                      is flattened onto
     * @return the holes, wound the way any other filled shape is, each walled by nothing
     */
    public static List<VoidHole> traceBareHoles(DiscUnion union, int boundSegments) {
        return DiscUnionBoundary.traceHolesAcrossWalls(union, Walls.NONE, boundSegments);
    }
}
