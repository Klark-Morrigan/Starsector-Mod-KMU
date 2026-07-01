package kmu.politicalmap.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Classifies cell-adjacency edges by faction ownership, turning the raw
 * adjacency graph into interior seams and national boundaries.
 *
 * <p>Pure rule over plain owner ids: an edge is an interior seam only when the
 * same faction holds both the system and its neighbour across the edge;
 * anything else - a different owner, an unowned neighbour, or a frontier into
 * empty space - is a boundary. Keeping it free of the geometry source and of GL
 * lets the classification be exercised directly on hand-built adjacency,
 * independent of how the cells are built or drawn.
 */
public final class EdgeClassifier {

    private EdgeClassifier() {
    }

    /**
     * Classifies every system's cell edges against the dominant owners.
     *
     * @param edgesBySystemId each system's raw cell edges, tagged with the
     *                        neighbour across them
     * @param ownerBySystemId the dominant owner per system; a system absent from
     *                        the map counts as unowned
     * @return one classified edge per input edge, in iteration order
     */
    public static List<ClassifiedEdge> classifyEdges(
            Map<String, List<CellEdge>> edgesBySystemId,
            Map<String, DominantOwner> ownerBySystemId) {
        var classified = new ArrayList<ClassifiedEdge>();
        for (var entry : edgesBySystemId.entrySet()) {
            var ownerFactionId = factionIdOf(ownerBySystemId.get(entry.getKey()));
            for (var edge : entry.getValue()) {
                // A null neighbour id is a frontier edge with no system across it,
                // so it has no owner to match and resolves to a boundary.
                var neighbourOwner = edge.neighbourSystemId() == null
                        ? null
                        : ownerBySystemId.get(edge.neighbourSystemId());
                var edgeClass = classify(ownerFactionId, factionIdOf(neighbourOwner));
                classified.add(new ClassifiedEdge(
                        edge.x1(), edge.y1(), edge.x2(), edge.y2(), edgeClass));
            }
        }
        return classified;
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

    private static String factionIdOf(DominantOwner owner) {
        return owner == null ? null : owner.factionId();
    }
}
