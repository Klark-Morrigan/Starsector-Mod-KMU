package kmu.maplayers.base.geometry;

import java.util.Map;

/**
 * The grouping rule that turns a cell-adjacency edge into an interior seam or a
 * cluster boundary.
 *
 * <p>Pure rule over opaque grouping keys: an edge is an interior seam only when the
 * same non-null key holds both the system and its neighbour across the edge; an edge
 * with a grouping key on exactly one side (a grouped system facing ungrouped space)
 * is an open frontier; any other pairing - two different keys, or two ungrouped sides -
 * is a plain boundary. A key is whatever a consumer clusters by, so the geometry fuses
 * cells without knowing what the key means.
 * The core {@link #classify} rule is kept free of the geometry source and of GL so it
 * can be exercised directly on hand-built key pairs, while {@link #classifyAcross} adapts
 * it to a raw {@link CellEdge} - resolving what the edge's {@link EdgeTarget} names before
 * the keys are compared - and the key map every consumer already holds, so
 * {@link CellShaper}, {@link SystemClusterBorders}, and {@link SystemClusters} decide
 * merging off one shared rule rather than each resolving the neighbour's key itself.
 */
public final class EdgeClassifier {

    private EdgeClassifier() {
    }

    /**
     * The core rule over two grouping keys:
     * <ul>
     *   <li>same non-null key on both sides - INTERIOR_SEAM (the cells fuse);</li>
     *   <li>a non-null key on exactly one side - OPEN_FRONTIER (a grouped system facing
     *       ungrouped space it can reach toward);</li>
     *   <li>anything else - two differing non-null keys, or two ungrouped sides -
     *       BOUNDARY.</li>
     * </ul>
     *
     * @param cellOwner       the grouping key of this side, or null if ungrouped
     * @param neighbourOwner the grouping key across the edge, or null if ungrouped or
     *                          a frontier into empty space
     * @return INTERIOR_SEAM for a shared non-null key, OPEN_FRONTIER for a key on exactly
     *         one side, else BOUNDARY
     */
    public static EdgeClass classify(String cellOwner, String neighbourOwner) {
        if (cellOwner != null && cellOwner.equals(neighbourOwner)) {
            return EdgeClass.INTERIOR_SEAM;
        }
        // Exactly one side grouped: a keyed cell facing an unkeyed neighbour (or the
        // reverse). This is the frontier a consumer can push outward, kept distinct from a
        // boundary between two differing keys or between two ungrouped cells.
        if ((cellOwner == null) != (neighbourOwner == null)) {
            return EdgeClass.OPEN_FRONTIER;
        }
        return EdgeClass.BOUNDARY;
    }

    /**
     * Classifies one cell edge from its cell's own grouping key against whatever the edge's
     * target names across it. The one place that turns an adjacency edge plus the keys into a
     * seam-or-boundary verdict, so every consumer classifies identically.
     *
     * <p>Each target decides on its own terms: a system across the edge has its key looked up
     * and handed to {@link #classify}; the cell's reach bound is a plain boundary, since with
     * no star across it there is nothing to reach toward; and more of the same ground is an
     * interior seam outright - the far side is this cell's own ground, so no key can differ.
     *
     * @param edge             the cell edge, tagged with what lies across it
     * @param cellOwner      the grouping key of the cell this edge belongs to, or null if
     *                         that cell is ungrouped
     * @param ownerBySystemId the grouping key per system, to look up the key of a system
     *                         across the edge
     * @return INTERIOR_SEAM for a shared non-null key or for same-ground, OPEN_FRONTIER for
     *         a grouped cell facing an ungrouped star, else BOUNDARY; the reach bound is always
     *         BOUNDARY
     */
    public static EdgeClass classifyAcross(
            CellEdge edge,
            String cellOwner,
            Map<String, String> ownerBySystemId) {
        var target = edge.target();
        if (target instanceof EdgeTarget.AcrossSystem acrossSystem) {
            return classify(cellOwner, ownerBySystemId.get(acrossSystem.systemId()));
        }
        // Same ground on both sides, so the edge fuses whatever this cell's key is - an
        // ungrouped cell's own cut included, which "same key both sides" could not express
        // for a null key.
        if (EdgeTarget.SAME_OWNER.equals(target)) {
            return EdgeClass.INTERIOR_SEAM;
        }
        return EdgeClass.BOUNDARY;
    }
}
