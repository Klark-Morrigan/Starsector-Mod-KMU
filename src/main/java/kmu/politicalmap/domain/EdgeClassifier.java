package kmu.politicalmap.domain;

/**
 * The ownership rule that turns a cell-adjacency edge into an interior seam or a
 * national boundary.
 *
 * <p>Pure rule over plain owner ids: an edge is an interior seam only when the
 * same faction holds both the system and its neighbour across the edge; anything
 * else - a different owner, an unowned neighbour, or a frontier into empty space -
 * is a boundary. Kept free of the geometry source and of GL so the classification
 * can be exercised directly on hand-built owner pairs, and so {@link CellShaper}
 * and any later consumer decide merging off one shared rule.
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
}
