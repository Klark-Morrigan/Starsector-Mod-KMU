package kmu.politicalmap.domain.geometry;

import kmu.politicalmap.domain.politics.DominantOwner;

import java.util.Map;

/**
 * The ownership rule that turns a cell-adjacency edge into an interior seam or a
 * national boundary.
 *
 * <p>Pure rule over plain owner ids: an edge is an interior seam only when the
 * same faction holds both the system and its neighbour across the edge; anything
 * else - a different owner, an unowned neighbour, or a frontier into empty space -
 * is a boundary. The core {@link #classify} rule is kept free of the geometry source
 * and of GL so it can be exercised directly on hand-built owner pairs, while
 * {@link #classifyAcross} adapts it to a raw {@link CellEdge} and the owner map every
 * consumer already holds - so {@link CellShaper}, {@link SystemClusterBorders}, and
 * {@link SystemClusters} decide merging off one shared rule rather than each resolving
 * the neighbour's faction itself.
 */
public final class EdgeClassifier {

    private EdgeClassifier() {
    }

    /**
     * The core rule: an edge is an interior seam only when both sides are held by
     * the same faction; any other pairing - differing owners, an unowned side, or
     * a frontier into empty space - is a boundary.
     *
     * @param ownerFactionId     the faction holding this side, or null if unowned
     * @param neighbourFactionId the faction across the edge, or null if unowned or
     *                           a frontier into empty space
     * @return INTERIOR_SEAM when both sides share a non-null owner, else BOUNDARY
     */
    public static EdgeClass classify(String ownerFactionId, String neighbourFactionId) {
        if (ownerFactionId != null && ownerFactionId.equals(neighbourFactionId)) {
            return EdgeClass.INTERIOR_SEAM;
        }
        return EdgeClass.BOUNDARY;
    }

    /**
     * Classifies one cell edge from a system's own faction against whoever lies across
     * it: the neighbour's faction is resolved from the owner map (null for a frontier
     * into empty space, whose {@code neighbourSystemId} is null), then handed to
     * {@link #classify}. The one place that turns an adjacency edge plus the owners
     * into a seam-or-boundary verdict, so every consumer classifies identically.
     *
     * @param edge            the cell edge, tagged with the neighbour across it
     * @param ownerFactionId  the faction holding the cell this edge belongs to, or
     *                        null if that cell is unowned
     * @param ownerBySystemId the dominant owner per system, to look up the neighbour's
     *                        faction
     * @return INTERIOR_SEAM when the same faction holds both sides, else BOUNDARY
     */
    public static EdgeClass classifyAcross(CellEdge edge, String ownerFactionId,
            Map<String, DominantOwner> ownerBySystemId) {
        // The frontier edge (no neighbour) is guarded before the lookup: an immutable
        // owner map (Map.of in tests) rejects a null-key get, and a frontier is unowned
        // across anyway.
        var neighbourFactionId = edge.neighbourSystemId() == null
                ? null
                : DominantOwner.factionIdOf(ownerBySystemId.get(edge.neighbourSystemId()));
        return classify(ownerFactionId, neighbourFactionId);
    }
}
