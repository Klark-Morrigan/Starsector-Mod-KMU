package kmu.maplayers.base.geometry;

/**
 * What lies across one edge of a cell - the three things a cell can face, named.
 *
 * <p>A cell edge faces one of exactly three things: another system's cell
 * ({@link AcrossSystem}), open space at the cell's own outer reach ({@link #REACH_BOUND}),
 * or more of the same ground ({@link #SAME_OWNER}), which is a cut interior to one
 * owner's ground with no star on the far side at all. Naming the three keeps the far side
 * a stated fact rather than something inferred from a null or from a system tagged against
 * itself: a consumer that reads the far side's owner - to fuse the edge away, or to weigh
 * one side's strength against the other's - is then told outright when there is no far side
 * to read.
 */
public sealed interface EdgeTarget {

    /**
     * The cell's outer reach bound: the arc it stops at rather than a shared border, so
     * nothing lies across it.
     */
    EdgeTarget REACH_BOUND = new NoSystem("REACH_BOUND");

    /**
     * More of the same owner's ground: a cut through it, made where that
     * ground was assembled from more than one piece. The two sides are the same owner,
     * so the edge fuses away.
     */
    EdgeTarget SAME_OWNER = new NoSystem("SAME_OWNER");

    /**
     * Another system's cell across the edge - the ordinary adjacency, and the only
     * target that names anything a consumer can look an owner up by.
     *
     * @param systemId the system whose cell meets this one along the edge
     */
    record AcrossSystem(
        String systemId) implements EdgeTarget {
    }

    /**
     * A target with no system on the far side, standing behind {@link #REACH_BOUND} and
     * {@link #SAME_OWNER}. The name carries no meaning of its own beyond telling the
     * two apart - in a log line, and in the equality that distinguishes them.
     *
     * @param name which of the two systemless targets this is
     */
    record NoSystem(
        String name) implements EdgeTarget {
    }
}
