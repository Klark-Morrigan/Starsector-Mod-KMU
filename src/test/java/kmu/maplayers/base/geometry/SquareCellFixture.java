package kmu.maplayers.base.geometry;

import java.util.List;

import static kmu.maplayers.base.geometry.CellEdgeFixture.buildEdgeFacing;

/**
 * Posing a square cell: its four edges counter-clockwise from the bottom-left corner, each facing
 * the cell's own reach bound unless a case names the neighbour across one side.
 *
 * <p>Stated once here because the suites over the owner map each hand-built the same square, one
 * side at a time, and a square wound one way by one suite and another way by the next traces two
 * different rings from what reads as one shape.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class SquareCellFixture {

    private SquareCellFixture() {
        // fixture of static wiring, no instances.
    }

    /**
     * A square standing alone, every edge facing the reach bound - a cell nothing borders.
     *
     * @param minX the x of its bottom-left corner
     * @param minY the y of its bottom-left corner
     * @param side the length of each side
     * @return its four edges, counter-clockwise from the bottom-left corner
     */
    public static List<CellEdge> buildSquareCell(double minX, double minY, double side) {
        return buildSquareCellFacing(minX, minY, side, SquareSide.BOTTOM, null);
    }

    /**
     * A square with one side facing the named neighbour and the other three the reach bound.
     *
     * @param minX              the x of its bottom-left corner
     * @param minY              the y of its bottom-left corner
     * @param side              the length of each side
     * @param facingSide        the side the neighbour is across
     * @param neighbourSystemId the neighbour across that side, or null for the reach bound
     * @return its four edges, counter-clockwise from the bottom-left corner
     */
    public static List<CellEdge> buildSquareCellFacing(
            double minX,
            double minY,
            double side,
            SquareSide facingSide,
            String neighbourSystemId) {

        var maxX = minX + side;
        var maxY = minY + side;

        return List.of(
            buildEdgeFacing(minX, minY, maxX, minY, readNeighbourOn(SquareSide.BOTTOM, facingSide, neighbourSystemId)),
            buildEdgeFacing(maxX, minY, maxX, maxY, readNeighbourOn(SquareSide.RIGHT, facingSide, neighbourSystemId)),
            buildEdgeFacing(maxX, maxY, minX, maxY, readNeighbourOn(SquareSide.TOP, facingSide, neighbourSystemId)),
            buildEdgeFacing(minX, maxY, minX, minY, readNeighbourOn(SquareSide.LEFT, facingSide, neighbourSystemId)));
    }

    // The neighbour across one side: the named one on the side it faces, the reach bound elsewhere.
    private static String readNeighbourOn(
            SquareSide side,
            SquareSide facingSide,
            String neighbourSystemId) {

        return side == facingSide
            ? neighbourSystemId
            : null;
    }

    /** One side of a square cell. */
    public enum SquareSide {
        BOTTOM,
        RIGHT,
        TOP,
        LEFT
    }
}
