package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segment;
import kmlib.math.geometry.Segments;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Lines between two shores turned into walls the walk can divide by, each end moved onto its
 * shore.
 *
 * <p>Every tier that lays lines lays them this way, whatever the lines are - a coast's reaches,
 * the bridges across a lake. A line is anchored on the cells' true reach and the walk carries the
 * bound flattened, inscribed in it - so the line's ends float up to a sagitta off the piece, and
 * a wall with loose ends divides nothing. Each end is therefore moved to where the shore is:
 * along the line from the end towards its own cell's site, the frontier is crossed within a
 * sagitta or two, and the wall ends a little past that crossing, inside the cell. The walk cuts
 * the wall where it crosses the frontier; what is left beyond the cut is a slit in the cell's
 * face, which is discarded. The wall is one straight segment, so the piece it divides has one
 * straight edge along it, from shore to shore.
 *
 * <p><b>Not a stub past the end.</b> A wall laid as the line itself plus a short stub from each
 * end towards the site divides just as well, but leaves the stub's remainder - the few units
 * between the frontier crossing and the line's end - as an edge of the piece, at an angle to the
 * line. Inset by a channel many times its length, that edge folds, and the fold shows as a hook
 * at every junction; and where the end floats off the shore the stub, the line and the shore
 * close a sliver too small to draw and just big enough to keep. Moving the end instead moves the
 * line by at most the distance its end floated, which is under the map's resolution.
 *
 * <p>Held once rather than per tier, because what makes a line divide is the same fact about the
 * walk for every tier: a second copy is a second answer to where the shore is.
 */
public final class CarriedLines {

    // How far from an end the shore is looked for, in sagittas, along the line to the site. One
    // would reach the shore exactly where the end floats furthest from it; two finds it with
    // room for rounding.
    private static final double SHORE_SEARCH_SAGITTAS = 2;

    // How far past the shore the wall ends, in sagittas. Enough that the wall crosses the
    // frontier outright rather than touching it, which the cutting cannot see; and far below a
    // polygon edge's length, so the end is inside the cell and not out through another edge.
    private static final double PAST_THE_SHORE_SAGITTAS = 0.5;

    private CarriedLines() {
    }

    /**
     * Lays every line as a wall under one label, each end moved onto its shore.
     *
     * @param lines      the lines to lay, each a run across void from one cell's reach to
     *                   another's
     * @param frontier   every cell edge facing void, labelled with its cell, which is where an
     *                   end's shore is looked for
     * @param sites      the cells' own positions, which the ends are moved towards
     * @param parameters the knobs the map is drawn under, for the resolution that says how far
     *                   an end can float off the shore
     * @param label      what every wall is labelled with on the pieces it closes
     * @return the walls to lay, and the lines to draw
     */
    public static LaidLines layCarriedLines(
            List<CellGap> lines,
            List<LabelledWall> frontier,
            List<double[]> sites,
            SectorGeometryParameters parameters,
            int label) {

        var walls = new ArrayList<LabelledWall>();
        var drawn = new ArrayList<List<double[]>>();
        var sagitta = parameters.measureBoundSagitta();

        for (var line : lines) {

            drawn.add(List.of(line.start(), line.end()));
            walls.add(new LabelledWall(
                Segment.joinPoints(
                    landOnShore(line.start(), line.fromSite(), frontier, sites, sagitta),
                    landOnShore(line.end(), line.toSite(), frontier, sites, sagitta)),
                label));
        }
        return new LaidLines(List.copyOf(walls), List.copyOf(drawn));
    }

    // Where a line's end goes: just inside its cell's frontier, on the line from the end to the
    // site. Only the end's own cell is looked at, since the end sits on that cell's rim; the
    // nearest crossing wins where the search runs through a corner and meets two edges. With no
    // crossing found the end is already inside, or on a corner, and the same small step towards
    // the site puts it inside either way.
    private static double[] landOnShore(
            double[] end,
            int cell,
            List<LabelledWall> frontier,
            List<double[]> sites,
            double sagitta) {

        var towardsSite = Points.computeUnitVector(
            sites.get(cell)[0] - end[0], sites.get(cell)[1] - end[1], sagitta);
        var searchEnd = new double[] {
            end[0] + towardsSite[0] * SHORE_SEARCH_SAGITTAS * sagitta,
            end[1] + towardsSite[1] * SHORE_SEARCH_SAGITTAS * sagitta};
        var shore = end;
        var nearest = Double.MAX_VALUE;

        for (var edge : frontier) {

            if (edge.label() != cell) {
                continue;
            }

            var crossing = Segments.intersectSegments(
                end, searchEnd, edge.segment().readStart(), edge.segment().readEnd());

            if (crossing != null && Points.computeDistance(end, crossing) < nearest) {
                nearest = Points.computeDistance(end, crossing);
                shore = crossing;
            }
        }
        return new double[] {
            shore[0] + towardsSite[0] * PAST_THE_SHORE_SAGITTAS * sagitta,
            shore[1] + towardsSite[1] * PAST_THE_SHORE_SAGITTAS * sagitta};
    }

    /**
     * What a tier laid, and what of it is drawn.
     *
     * @param walls every wall handed to the walk, one straight segment per line
     * @param lines each line as the two points it runs between, for drawing: the line as its
     *              tier found it, not the wall's ends, which are moved onto the shore
     */
    public record LaidLines(
        List<LabelledWall> walls,
        List<List<double[]>> lines) {
    }
}
