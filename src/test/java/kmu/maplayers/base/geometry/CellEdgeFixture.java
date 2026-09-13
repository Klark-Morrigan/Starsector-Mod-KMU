package kmu.maplayers.base.geometry;

import kmlib.starsector.systems.SystemKey;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

/**
 * Posing the edges of a cell: a segment tagged with the neighbour across it, or with the cell's
 * own reach bound where nothing is.
 *
 * <p>Stated once here because every suite over the partition and what is cut from it had
 * otherwise written the same builder for itself, and a neighbour keyed one way by one suite and
 * another way by the next is a case posed against a graph its neighbours are not.
 *
 * <p>The neighbour is named as a case names its systems, and keyed through {@link CellKeyFixture}
 * on the way in. A case about two systems sharing an id keys the neighbour itself, that being what
 * the case is about.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class CellEdgeFixture {

    private CellEdgeFixture() {
        // fixture of static wiring, no instances.
    }

    /**
     * One edge from {@code (x1, y1)} to {@code (x2, y2)} facing the named neighbour, or the reach
     * bound where the name is null - a frontier into empty space.
     */
    public static CellEdge buildEdgeFacing(
            double x1,
            double y1,
            double x2,
            double y2,
            String neighbourSystemId) {

        return buildEdgeFacingCell(
            x1, y1, x2, y2,
            neighbourSystemId == null ? null : buildCellKey(neighbourSystemId));
    }

    /**
     * The same edge against a neighbour a case keyed itself, or the reach bound where the key is
     * null.
     */
    public static CellEdge buildEdgeFacingCell(
            double x1,
            double y1,
            double x2,
            double y2,
            SystemKey neighbourSystemKey) {

        return new CellEdge(
            x1, y1, x2, y2,
            neighbourSystemKey == null
                ? EdgeTarget.REACH_BOUND
                : new EdgeTarget.AcrossSystem(neighbourSystemKey));
    }

    /**
     * An edge at the origin facing the named neighbour, for a case that reads only the adjacency
     * tag and never the geometry.
     */
    public static CellEdge buildEdgeTo(String neighbourSystemId) {
        return buildEdgeFacing(0, 0, 0, 0, neighbourSystemId);
    }

    /**
     * An edge at the origin facing a neighbour a case keyed itself.
     */
    public static CellEdge buildEdgeToCell(SystemKey neighbourSystemKey) {
        return buildEdgeFacingCell(0, 0, 0, 0, neighbourSystemKey);
    }

    /**
     * An edge at the origin facing the reach bound.
     */
    public static CellEdge buildBoundEdge() {
        return buildEdgeFacingCell(0, 0, 0, 0, null);
    }
}
