package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segment;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Lines between two shores turned into walls the walk can divide by, each end carried through
 * its shore.
 *
 * <p>Every tier that lays lines lays them this way, whatever the lines are - a coast's reaches,
 * the bridges across a lake. A line is anchored on the cells' true reach and the walk carries the
 * bound flattened, inscribed in it - so the line's ends float up to a sagitta off the piece, and
 * a wall with loose ends divides nothing. Rather than land the end on the shore by projection,
 * which leaves a junction to rounding, a short stub runs from each end towards its own cell's
 * site, far enough to cross the frontier outright. The walk cuts the frontier where the stub
 * crosses it; the stub's remainder is a slit in a cell's face, which is discarded. The line
 * itself is laid to the unit.
 *
 * <p>Held once rather than per tier, because what makes a line divide is the same fact about the
 * walk for every tier: a second copy is a second answer to how far past the shore is far enough.
 */
public final class CarriedLines {

    // How far past the shore an end is carried, in sagittas. One would reach the shore exactly
    // where the line's end floats furthest from it; two crosses it with room for rounding, and
    // costs nothing but a slightly longer slit in a face nobody keeps.
    private static final double CARRY_THROUGH_SAGITTAS = 2;

    private CarriedLines() {
    }

    /**
     * Lays every line as a wall under one label, each end carried through its shore.
     *
     * @param lines      the lines to lay, each a run across void from one cell's reach to
     *                   another's
     * @param sites      the cells' own positions, which the stubs run towards
     * @param parameters the knobs the map is drawn under, for the resolution that says how far
     *                   an end can float off the shore
     * @param label      what every wall is labelled with on the pieces it closes
     * @return the walls to lay, and the lines to draw
     */
    public static LaidLines layCarriedLines(
            List<CellGap> lines,
            List<double[]> sites,
            SectorGeometryParameters parameters,
            int label) {

        var walls = new ArrayList<LabelledWall>();
        var drawn = new ArrayList<List<double[]>>();
        var carry = CARRY_THROUGH_SAGITTAS * parameters.measureBoundSagitta();

        for (var line : lines) {

            drawn.add(List.of(line.start(), line.end()));
            layCarriedLine(
                walls,
                line.start(),
                line.end(),
                sites.get(line.fromSite()),
                sites.get(line.toSite()),
                carry,
                label);
        }
        return new LaidLines(List.copyOf(walls), List.copyOf(drawn));
    }

    // One line as three walls under one label: the line itself, and a stub off each end
    // towards the cell that end is anchored on. The three share their ends exactly, so the
    // walk welds them into one path with a turn at each end where the stub meets the shore.
    private static void layCarriedLine(
            List<LabelledWall> walls,
            double[] from,
            double[] to,
            double[] fromSite,
            double[] toSite,
            double carry,
            int label) {

        walls.add(new LabelledWall(
            Segment.joinPoints(carryTowards(from, fromSite, carry), from), label));
        walls.add(new LabelledWall(Segment.joinPoints(from, to), label));
        walls.add(new LabelledWall(
            Segment.joinPoints(to, carryTowards(to, toSite, carry)), label));
    }

    // The point that far from an end along the line to its own site.
    private static double[] carryTowards(double[] end, double[] site, double carry) {

        var distance = Points.computeDistance(end, site);

        return new double[] {
            end[0] + (site[0] - end[0]) * carry / distance,
            end[1] + (site[1] - end[1]) * carry / distance};
    }

    /**
     * What a tier laid, and what of it is drawn.
     *
     * @param walls every wall handed to the walk, stubs included
     * @param lines each line as the two points it runs between, for drawing - the stubs are
     *              not, being slits in a cell's face rather than anything on the map
     */
    public record LaidLines(
        List<LabelledWall> walls,
        List<List<double[]>> lines) {
    }
}
