package kmu.maplayers.base.geometry;

import kmlib.starsector.systems.SystemKey;

import java.util.Map;

/**
 * The ownership rule that turns a cell-adjacency edge into an interior seam or a
 * cluster boundary.
 *
 * <p>Pure rule over opaque owner ids: an edge is an interior seam only when the
 * same non-null owner holds both the system and its neighbour across the edge; an edge
 * with an owner on exactly one side (a owned system facing unowned space)
 * is an open frontier; any other pairing - two different owners, or two unowned sides -
 * is a plain boundary. An owner is whatever a consumer clusters by, so the geometry fuses
 * cells without knowing what the owner means.
 * The core {@link #classify} rule is kept free of the geometry source and of GL so it
 * can be exercised directly on hand-built owner pairs, while {@link #classifyAcross} adapts
 * it to a raw {@link CellEdge} - resolving what the edge's {@link EdgeTarget} names before
 * the owners are compared - and the owner map every consumer already holds, so
 * {@link CellShaper}, {@link SystemClusterBorders}, and {@link SystemClusters} decide
 * merging off one shared rule rather than each resolving the neighbour's owner itself.
 */
public final class EdgeClassifier {

    private EdgeClassifier() {
    }

    /**
     * The core rule over two owners:
     * <ul>
     *   <li>same non-null owner on both sides - INTERIOR_SEAM (the cells fuse);</li>
     *   <li>a non-null owner on exactly one side - OPEN_FRONTIER (an owned system facing
     *       unowned space it can reach toward);</li>
     *   <li>anything else - two differing non-null owners, or two unowned sides -
     *       BOUNDARY.</li>
     * </ul>
     *
     * @param cellOwner      the owner of this side, or null if unowned
     * @param neighbourOwner the owner across the edge, or null if unowned or
     *                       a frontier into empty space
     * @return INTERIOR_SEAM for a shared non-null owner, OPEN_FRONTIER for an owner on exactly
     *         one side, else BOUNDARY
     */
    public static EdgeClass classify(String cellOwner, String neighbourOwner) {
        if (cellOwner != null && cellOwner.equals(neighbourOwner)) {
            return EdgeClass.INTERIOR_SEAM;
        }
        // Exactly one side grouped: a owned cell facing an unowned neighbour (or the
        // reverse). This is the frontier a consumer can push outward, kept distinct from a
        // boundary between two differing owners or between two unowned cells.
        if ((cellOwner == null) != (neighbourOwner == null)) {
            return EdgeClass.OPEN_FRONTIER;
        }
        return EdgeClass.BOUNDARY;
    }

    /**
     * Classifies one cell edge from its cell's own owner against whatever the edge's
     * target names across it. The one place that turns an adjacency edge plus the owners into a
     * seam-or-boundary verdict, so every consumer classifies identically.
     *
     * <p>Each target decides on its own terms: a system across the edge has its owner looked up
     * and handed to {@link #classify}; the cell's reach bound is a plain boundary, since with
     * no star across it there is nothing to reach toward; and more of the same cell is an
     * interior seam outright - the far side is this same cell, so no owner can differ.
     *
     * <p>The neighbour and the owner map share one address, so the target's own
     * {@link SystemKey} is the lookup: two systems sharing a vanilla id are two neighbours with
     * two owners rather than one.
     *
     * @param edge             the cell edge, tagged with what lies across it
     * @param cellOwner        the owner of the cell this edge belongs to, or null if
     *                         that cell is unowned
     * @param ownerBySystemKey the owner per system, to look up the owner of a system
     *                         across the edge
     * @return INTERIOR_SEAM for a shared non-null owner or for the same cell, OPEN_FRONTIER for
     *         a owned cell facing an unowned star, else BOUNDARY; the reach bound is always
     *         BOUNDARY
     */
    public static EdgeClass classifyAcross(
            CellEdge edge,
            String cellOwner,
            Map<SystemKey, String> ownerBySystemKey) {

        var target = edge.target();
        if (target instanceof EdgeTarget.AcrossSystem acrossSystem) {
            return classify(cellOwner, ownerBySystemKey.get(acrossSystem.systemKey()));
        }
        // The same cell on both sides, so the edge fuses whatever that cell's owner is - an
        // unowned cell's own cut included, which "the same owner both sides" could not express
        // for a null owner.
        if (EdgeTarget.SAME_OWNER.equals(target)) {
            return EdgeClass.INTERIOR_SEAM;
        }
        return EdgeClass.BOUNDARY;
    }
}
