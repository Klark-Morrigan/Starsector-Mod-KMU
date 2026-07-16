package kmu.maplayers.politicalmap.base.geometry;

import java.util.Map;

/**
 * The grouping rule that turns a cell-adjacency edge into an interior seam or a
 * cluster boundary.
 *
 * <p>Pure rule over opaque grouping keys: an edge is an interior seam only when the
 * same non-null key holds both the system and its neighbour across the edge; an edge
 * with a grouping key on exactly one side (an owned system facing an unowned dead or
 * decivilised star) is an open frontier; any other pairing - two different keys, or two
 * ungrouped sides - is a plain boundary. A key is whatever a consumer clusters by (the
 * faction layer keys by dominant-faction id), so the geometry fuses cells without
 * knowing what the key means.
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
     * The core rule over two grouping keys:
     * <ul>
     *   <li>same non-null key on both sides - INTERIOR_SEAM (the cells fuse);</li>
     *   <li>a non-null key on exactly one side - OPEN_FRONTIER (an owned system facing
     *       an unowned dead or decivilised star it can reach toward);</li>
     *   <li>anything else - two differing non-null keys, or two ungrouped sides -
     *       BOUNDARY.</li>
     * </ul>
     *
     * @param ownGroupKey       the grouping key of this side, or null if ungrouped
     * @param neighbourGroupKey the grouping key across the edge, or null if ungrouped or
     *                          a frontier into empty space
     * @return INTERIOR_SEAM for a shared non-null key, OPEN_FRONTIER for a key on exactly
     *         one side, else BOUNDARY
     */
    public static EdgeClass classify(String ownGroupKey, String neighbourGroupKey) {
        if (ownGroupKey != null && ownGroupKey.equals(neighbourGroupKey)) {
            return EdgeClass.INTERIOR_SEAM;
        }
        // Exactly one side grouped: an owned cell facing an unowned neighbour (or the
        // reverse). This is the frontier a consumer can push out toward the dead star,
        // kept distinct from a boundary between two owners or between two empty cells.
        if ((ownGroupKey == null) != (neighbourGroupKey == null)) {
            return EdgeClass.OPEN_FRONTIER;
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
     * @return INTERIOR_SEAM for a shared non-null key, OPEN_FRONTIER for an owned cell facing
     *         an unowned star, else BOUNDARY; the map-reach bound is always BOUNDARY
     */
    public static EdgeClass classifyAcross(CellEdge edge, String ownGroupKey,
            Map<String, String> groupKeyBySystemId) {
        // The map-reach bound (no neighbour system) has no star across it, so it can never be
        // an open frontier to reach toward - it stays a plain boundary and skips the key lookup
        // (an immutable key map, Map.of in tests, would reject a null-key get anyway).
        if (edge.neighbourSystemId() == null) {
            return EdgeClass.BOUNDARY;
        }
        return classify(ownGroupKey, groupKeyBySystemId.get(edge.neighbourSystemId()));
    }
}
