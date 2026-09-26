package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segment;

import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import java.util.ArrayList;
import java.util.List;

/**
 * The lines the lake coast lays: each lake's coast where it cuts across a bay.
 *
 * <p>The coast is traced elsewhere and untouched here - the smoothing is the whole point of a
 * coastline, and reinventing it would mean re-tuning it. What this tier adds is only where that
 * line goes: into the one partition every tier divides, as walls, so the water inside it and the
 * bays outside it come back as pieces rather than as fills traced by a second construction.
 *
 * <p><b>Only the reaches are laid.</b> A coast alternates between fillets, which run along one
 * cell's own border, and reaches, which cross open void from one cell to another. Along a fillet
 * the piece's boundary already IS the coast, and laying a line there would lay it within the
 * walk's weld of the shore - where it is welded onto the shore's corners and becomes a partial
 * overlap the walk cannot cut. So a fillet lays nothing, and a reach lays one wall. Which is
 * which is the tracer's own answer, and the reaches arrive already told apart.
 *
 * <p><b>Each end is carried through the shore.</b> A reach is anchored on the cells' true reach
 * and the walk carries the bound flattened, inscribed in it - so the reach's ends float up to a
 * sagitta off the piece, and a wall with loose ends divides nothing. Rather than land the end on
 * the shore by projection, which leaves a junction to rounding, a short stub runs from each end
 * towards its own cell's site, far enough to cross the frontier outright. The walk cuts the
 * frontier where the stub crosses it; the stub's remainder is a slit in a cell's face, which is
 * discarded. The reach itself is the traced line to the unit.
 *
 * <p>What is drawn is what is laid - the reaches - and not the fillets, which are the shore and
 * are already on screen as the piece's own edge.
 */
public final class LakeCoast {

    /**
     * What a reach of a lake's coast is labelled with on the pieces it closes.
     *
     * <p>Its own negative below the frame's, for the reason the frame has one: a piece is named
     * by what closed it, and this is the first line a tier lays.
     */
    public static final int THE_LAKE_COAST = -3;

    // How far past the shore an end is carried, in sagittas. One would reach the shore exactly
    // where the reach's end floats furthest from it; two crosses it with room for rounding, and
    // costs nothing but a slightly longer slit in a face nobody keeps.
    private static final double CARRY_THROUGH_SAGITTAS = 2;

    private LakeCoast() {
    }

    /**
     * Lays every reach as a wall, each end carried through its shore.
     *
     * @param reaches    the lakes' coast reaches, already told apart from the fillets
     * @param union      the cells at their own reach, for the sites the stubs run towards
     * @param parameters the knobs the map is drawn under, for the resolution that says how far
     *                   an end can float off the shore
     * @return the walls to lay, and the lines to draw
     */
    public static LaidLakeCoast layCoastWalls(
            List<ReachLine> reaches,
            DiscUnion union,
            SectorGeometryParameters parameters) {

        var walls = new ArrayList<LabelledWall>();
        var reachLines = new ArrayList<List<double[]>>();
        var carry = CARRY_THROUGH_SAGITTAS * parameters.measureBoundSagitta();

        for (var reach : reaches) {

            reachLines.add(List.of(reach.from(), reach.to()));
            layCarriedWall(
                walls,
                reach.from(),
                reach.to(),
                union.sites().get(reach.fromCell()),
                union.sites().get(reach.toCell()),
                carry);
        }
        return new LaidLakeCoast(List.copyOf(walls), List.copyOf(reachLines));
    }

    // One line as three walls under one label: the line itself, and a stub off each end
    // towards the cell that end is anchored on. The three share their ends exactly, so the
    // walk welds them into one path with a turn at each end where the stub meets the shore.
    private static void layCarriedWall(
            List<LabelledWall> walls,
            double[] from,
            double[] to,
            double[] fromSite,
            double[] toSite,
            double carry) {

        walls.add(new LabelledWall(
            buildSegment(carryTowards(from, fromSite, carry), from), THE_LAKE_COAST));
        walls.add(new LabelledWall(buildSegment(from, to), THE_LAKE_COAST));
        walls.add(new LabelledWall(
            buildSegment(to, carryTowards(to, toSite, carry)), THE_LAKE_COAST));
    }

    // The point that far from an end along the line to its own site.
    private static double[] carryTowards(double[] end, double[] site, double carry) {

        var distance = Points.computeDistance(end, site);

        return new double[] {
            end[0] + (site[0] - end[0]) * carry / distance,
            end[1] + (site[1] - end[1]) * carry / distance};
    }

    private static Segment buildSegment(double[] from, double[] to) {
        return new Segment(from[0], from[1], to[0], to[1]);
    }

    /**
     * What the lake coast laid, and what of it is drawn.
     *
     * @param walls      every wall handed to the walk, stubs included
     * @param reachLines each reach as the two points it runs between, for drawing
     */
    public record LaidLakeCoast(
        List<LabelledWall> walls,
        List<List<double[]>> reachLines) {
    }
}
