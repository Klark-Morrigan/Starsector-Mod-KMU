package kmu.maplayers.base.geometry.v3;

/**
 * One point of a smoothed coast, and the cell whose border it sits on.
 *
 * <p>The cell travels with the point because the two things drawn here are not the same
 * kind of run: a step between two points on ONE cell is a fillet along that cell's own
 * border, where a step between two cells is a straight reach across open void. Only the
 * second has to clear every cell, and a check that could not tell them apart would read
 * every fillet as a coast cutting into the cell it is drawn on.
 *
 * @param point  where the coast passes
 * @param circle whose cell's border it is on
 */
public record CoastVertex(
    double[] point,
    int circle) {
}
