package kmu.maplayers.politicalmap.base.geometry;

import java.util.Map;

/**
 * The grouping rule that turns a cell-adjacency edge into an interior seam or a
 * cluster boundary.
 *
 * <p>Pure rule over opaque grouping keys: an edge is an interior seam only when the
 * same non-null key holds both the system and its neighbour across the edge; anything
 * else - a different key, an ungrouped neighbour, or a frontier into empty space -
 * is a boundary. A key is whatever a consumer clusters by (the faction layer keys by
 * dominant-faction id), so the geometry fuses cells without knowing what the key means.
 * The core {@link #classify} rule is kept free of the geometry source and of GL so it
 * can be exercised directly on hand-built key pairs, while {@link #classifyAcross} adapts
 * it to a raw {@link CellEdge} and the key map every consumer already holds - so
 * {@link CellShaper}, {@link SystemClusterBorders}, and {@link SystemClusters} decide
 * merging off one shared rule rather than each resolving the neighbour's key itself.
 */
public final class EdgeClassifier {

    private EdgeClassifier() {
    }

    /**
     * The core rule: an edge is an interior seam only when both sides carry the same
     * non-null grouping key; any other pairing - differing keys, an ungrouped side, or
     * a frontier into empty space - is a boundary.
     *
     * @param ownGroupKey       the grouping key of this side, or null if ungrouped
     * @param neighbourGroupKey the grouping key across the edge, or null if ungrouped or
     *                          a frontier into empty space
     * @return INTERIOR_SEAM when both sides share a non-null key, else BOUNDARY
     */
    public static EdgeClass classify(String ownGroupKey, String neighbourGroupKey) {
        if (ownGroupKey != null && ownGroupKey.equals(neighbourGroupKey)) {
            return EdgeClass.INTERIOR_SEAM;
        }
        return EdgeClass.BOUNDARY;
    }

    /**
     * Classifies one cell edge from a system's own grouping key against whatever lies across
     * it: the neighbour's key is read from the key map (null for a frontier into empty space,
     * whose {@code neighbourSystemId} is null), then handed to {@link #classify}. The one
     * place that turns an adjacency edge plus the keys into a seam-or-boundary verdict, so
     * every consumer classifies identically.
     *
     * @param edge             the cell edge, tagged with the neighbour across it
     * @param ownGroupKey      the grouping key of the cell this edge belongs to, or null if
     *                         that cell is ungrouped
     * @param groupKeyBySystemId the grouping key per system, to look up the neighbour's key
     * @return INTERIOR_SEAM when both sides share a non-null key, else BOUNDARY
     */
    public static EdgeClass classifyAcross(CellEdge edge, String ownGroupKey,
            Map<String, String> groupKeyBySystemId) {
        // The frontier edge (no neighbour) is guarded before the lookup: an immutable key map
        // (Map.of in tests) rejects a null-key get, and a frontier is ungrouped across anyway.
        var neighbourGroupKey = edge.neighbourSystemId() == null
                ? null
                : groupKeyBySystemId.get(edge.neighbourSystemId());
        return classify(ownGroupKey, neighbourGroupKey);
    }
}
