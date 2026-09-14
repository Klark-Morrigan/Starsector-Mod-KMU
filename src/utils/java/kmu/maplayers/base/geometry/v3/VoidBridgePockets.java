package kmu.maplayers.base.geometry.v3;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.DiscUnionBoundary;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.VoidHole;

import java.util.ArrayList;
import java.util.List;

/**
 * The void the bridges close around, as shapes to draw.
 *
 * <p>A bridge is a line, and a line has nothing to fill. What is wanted is the space a run of
 * them shuts in, drawn to the same standard {@link VoidPockets} draws its own.
 *
 * <p><b>Only a bridge that closes a ring shuts anything in.</b> One whose two cells were
 * already reachable from each other completes a loop, and a loop has an inside; one that
 * joins two things not otherwise connected merely strings them together, and a chain of those
 * encloses no more than a single one does. Everything beyond such a chain is the unbounded
 * outside, so treating every bridge as enclosing fills the ocean in.
 *
 * <p>Those bridges are then handed to {@link DiscUnionBoundary} as chords and traced along
 * with the arcs, rather than cut into a finished outline. The cells' borders and the chords
 * are both boundary, so the shape is what walking them together closes around - and because
 * a chord meets a circle at the one angle facing the other circle, its ends are vertices of
 * that walk by construction. Cutting instead means finding where the chord went on a sampled
 * outline, and finding is what strays.
 *
 * <p>Which piece is void needs no asking either. A cycle that winds against the silhouettes
 * has void inside it, whether it was closed by a chord or by the cells alone, so the bays the
 * bridges wall off and the pockets the cells enclose come out of the same test.
 *
 * <p><b>Nothing here is offset.</b> The arcs come from a trace at the reach that leaves the
 * channel, exactly as the coast's own pockets get their outline, and the bridge keeps the same
 * channel the cells do - one that two neighbouring pockets share, half of it each. Both are
 * the same trace at a different distance rather than a shape pushed outward afterwards, which
 * is why the corners come out where the circles actually cross instead of on a mitre.
 *
 * <p>What governs this is where a fill's edge lands rather than how near the cells it sits. A
 * fill can run perfectly against every cell and still close on a line nowhere near the span it
 * belongs to, so the distance to the cells proves nothing about the part that can go wrong.
 */
public final class VoidBridgePockets {

    private VoidBridgePockets() {
    }

    /**
     * Finds what the bridges close around, shaped ready to draw.
     *
     * <p>EVERY bridge becomes a chord across the union of the cells' reach discs, and the
     * whole boundary - arcs and chords together - is walked in one pass. Every cycle that
     * comes out winding against the silhouettes has void inside it: the bays the chords wall
     * off, and the pockets the cells enclose without any help from a bridge.
     *
     * <p>Every one, rather than only the ring-closing ones, because the winding is what says
     * whether anything was shut in and it can only say so about boundary that is actually
     * there. A chain of bridges shuts in nothing, and traced it shuts in nothing - it draws a
     * dumbbell that winds like any other silhouette. But leave the chain out and lay only the
     * bridge that closes the ring, and its two ends sit on two silhouettes with nothing drawn
     * between them, so it strings THOSE together instead and the pocket never appears.
     *
     * <p>Traced at the reach that leaves the channel, exactly as the coast's own pockets get
     * their outline, so nothing here is offset. The bridges keep that same channel: each
     * pocket stops half of one short of the wall, so two pockets meeting across a bridge are
     * held apart by the same gap that holds a pocket off the cells around it, and every
     * section reads as its own shape rather than as part of one mass.
     *
     * @param sites      the sites
     * @param bridges    the bridges, as {@link VoidBridges} found them
     * @param parameters the knobs the cells are built under, which are also what the arcs are
     *                   flattened onto: the bound the cells' own vertices sit at
     * @param shaping    how much of the channel each pocket takes out of its own outline.
     *                   Taken by walking the discs at a moved reach rather than by offsetting
     *                   afterwards, so at its true extent it is the discs that move
     * @return one outline per captured pocket
     */
    public static List<List<double[]>> findCapturedPockets(
            List<double[]> sites,
            List<CellGap> bridges,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        var outlines = new ArrayList<List<double[]>>();

        for (var hole : WalledVoid.traceVoidAcrossWalls(
                sites, buildBridgeWalls(bridges, parameters), parameters, shaping)) {

            outlines.add(hole.boundary());
        }
        return outlines;
    }

    /**
     * Finds what the bridges close around, leaving out the void they had no part in closing.
     *
     * <p>The same walk as {@link #findCapturedPockets}, reported to a caller that draws the
     * cell-enclosed void elsewhere. That walk hands back every hole it finds, because the
     * winding is what says whether anything was shut in and a bridge left out of the walk
     * strings two silhouettes together instead of closing a ring. But a hole the cells closed
     * unaided is not a bridge's - it is a lake or a puddle - and a construction that draws
     * those under their own switches would paint the same water twice and go on painting it
     * with those switches off.
     *
     * <p>Read off {@link VoidHole#walledBy}, which is the walk's own answer about what closed
     * each hole, rather than by matching shapes afterwards.
     *
     * @param sites      the sites
     * @param bridges    the bridges, as {@link VoidBridges} found them
     * @param parameters the knobs the cells are built under
     * @param shaping    how much of the channel each pocket takes out of its own outline
     * @return one outline per pocket a bridge helped close
     */
    public static List<List<double[]>> findBridgeWalledPockets(
            List<double[]> sites,
            List<CellGap> bridges,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        var outlines = new ArrayList<List<double[]>>();

        for (var hole : findBridgeWalledHoles(sites, bridges, parameters, shaping)) {
            outlines.add(hole.boundary());
        }
        return outlines;
    }

    /**
     * The same water as {@link #findBridgeWalledPockets}, as the walk found it rather than as
     * an outline to fill.
     *
     * <p>What a caller wants when the question is not what to draw: which cells the water runs
     * against and which bridges closed it are the walk's own record, and both are gone by the
     * time a pocket is an outline. Recovered from the outline afterwards they would be a
     * second opinion arrived at by testing points against lines - which is the finding that
     * strays, and the reason the walk records them in the first place.
     *
     * @param sites      the sites
     * @param bridges    the bridges, as {@link VoidBridges} found them
     * @param parameters the knobs the cells are built under
     * @param shaping    how much of the channel each pocket gives up against the cells
     * @return one hole per pocket a bridge helped close
     */
    public static List<VoidHole> findBridgeWalledHoles(
            List<double[]> sites,
            List<CellGap> bridges,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        // The same chord instances the walk was handed, so a hole's walls are asked about by
        // the walls themselves rather than by a set built to look like them.
        var walls = buildBridgeWalls(bridges, parameters);

        // Everything laid is also what is kept on: the bridges are the only walls here, so a
        // hole no bridge closed is one the cells closed unaided.
        return WalledVoid.traceVoidWalledBy(
            sites, walls, walls.chords(), parameters, shaping);
    }

    // How far the further of one side's two ends sits from the outline that should carry it.
    // The further of the two rather than the nearer, because a line that landed one end right

    /**
     * The walls the bridges become.
     *
     * <p>The channel is the same border inset the cells keep, so two pockets meeting across a
     * bridge are held apart by the same gap that holds a pocket off the cells around it.
     *
     * <p>Shared rather than private because what the walk did with these walls is a question
     * asked from outside, and the walk answers about the walls it was handed - so a second
     * build of the same lines is a set of walls no verdict can be about.
     *
     * @param bridges    the bridges, as {@link VoidBridges} found them
     * @param parameters the knobs the cells are built under
     * @return the walls, at the channel a pocket keeps against them
     */
    public static DiscUnionBoundary.Walls buildBridgeWalls(
            List<CellGap> bridges,
            SectorGeometryParameters parameters) {

        return new DiscUnionBoundary.Walls(
            DiscUnionBoundary.buildChordsFrom(bridges), parameters.borderInset());
    }

}
