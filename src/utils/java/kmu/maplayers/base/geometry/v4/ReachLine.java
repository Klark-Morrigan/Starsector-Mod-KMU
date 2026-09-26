package kmu.maplayers.base.geometry.v4;

/**
 * One straight stretch of a coast crossing open void from one cell's border to another's, as
 * the two points and the two cells and nothing else.
 *
 * <p>The shape a coast arrives in. Which construction traced the coast, how it was smoothed
 * and what else it knows about itself stay with that construction; what a tier here lays is a
 * line between two shores, and a line is two points. The cells travel with the points because
 * an end is carried through the shore towards its own cell, and which cell that is cannot be
 * read off the point alone.
 *
 * @param from     where the reach leaves one cell's border
 * @param to       where it arrives on the other's
 * @param fromCell the cell whose border it leaves, as an index into the sites
 * @param toCell   the cell whose border it arrives on
 */
public record ReachLine(
    double[] from,
    double[] to,
    int fromCell,
    int toCell) {
}
