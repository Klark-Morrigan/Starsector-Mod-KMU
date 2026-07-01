package kmu.politicalmap.domain;

import kmlib.math.geometry.Polygons;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapes each system's raw Voronoi cell into a merged faction-bloc polygon.
 *
 * <p>An edge shared with a same-faction neighbour is left on the true cell border,
 * so the two cells' fills meet exactly along it and fuse into one bloc with no
 * seam. Every other edge - against a different faction, unowned space, or the map
 * frontier - is pulled inward, so a bloc keeps the uniform national-border channel
 * against everything outside it. A kept seam edge is truncated where it runs into
 * a pulled-in border, so its ends stay within the padded border rather than
 * reaching the raw cell corner on the midline between cells.
 *
 * <p>Pure geometry over the adjacency graph and the resolved owners: it decides
 * which edges merge with {@link EdgeClassifier}'s rule and offsets the rest with
 * {@link Polygons#insetSelectedEdges}, handing the render layer a ready fill
 * polygon and a per-edge boundary flag so it never re-derives adjacency.
 */
public final class CellShaper {

    private CellShaper() {
    }

    /**
     * Shapes every system's cell into its merged-bloc polygon.
     *
     * @param edgesBySystemId each system's raw cell edges, in winding order,
     *                        tagged with the neighbour across them
     * @param ownerBySystemId the dominant owner per system; a system absent from
     *                        the map counts as unowned
     * @param borderInset     inward inset applied to every national-border edge
     * @return one shaped cell per input system, keyed by system id, in iteration
     *         order
     */
    public static Map<String, ShapedCell> shapeCells(
            Map<String, List<CellEdge>> edgesBySystemId,
            Map<String, DominantOwner> ownerBySystemId, double borderInset) {
        var shaped = new LinkedHashMap<String, ShapedCell>();
        for (var entry : edgesBySystemId.entrySet()) {
            var ownerFactionId = factionIdOf(ownerBySystemId.get(entry.getKey()));
            shaped.put(entry.getKey(),
                    shapeCell(entry.getValue(), ownerFactionId, ownerBySystemId, borderInset));
        }
        return shaped;
    }

    // Shapes one cell: rebuild its raw ring from the ordered edges (each edge's
    // start vertex, in order, is the ring), flag every edge that is a national
    // border rather than a same-faction seam, and inset only those.
    private static ShapedCell shapeCell(List<CellEdge> edges, String ownerFactionId,
            Map<String, DominantOwner> ownerBySystemId, double borderInset) {
        var vertices = new ArrayList<double[]>(edges.size());
        var isBorderEdge = new boolean[edges.size()];
        for (var i = 0; i < edges.size(); i++) {
            var edge = edges.get(i);
            vertices.add(new double[] {edge.x1(), edge.y1()});
            var neighbourFactionId = edge.neighbourSystemId() == null
                    ? null
                    : factionIdOf(ownerBySystemId.get(edge.neighbourSystemId()));
            isBorderEdge[i] =
                    EdgeClassifier.classify(ownerFactionId, neighbourFactionId) == EdgeClass.BOUNDARY;
        }
        var inset = Polygons.insetSelectedEdges(vertices, isBorderEdge, borderInset);
        return new ShapedCell(inset.vertices(), inset.edgeIsInset());
    }

    private static String factionIdOf(DominantOwner owner) {
        return owner == null ? null : owner.factionId();
    }
}
