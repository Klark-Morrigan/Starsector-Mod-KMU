package kmu.maplayers.politicalmap.base.geometry;

import kmlib.math.geometry.PolygonOffsets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapes each system's raw Voronoi cell into a merged cluster polygon, fused by grouping key.
 *
 * <p>An edge shared with a same-key neighbour is left on the true cell border, so the two
 * cells' fills meet exactly along it and fuse into one cluster with no seam. Every other edge -
 * against a different key, ungrouped space, or the map frontier - is pulled inward, so a
 * cluster keeps the uniform border channel against everything outside it. A kept seam edge is
 * truncated where it runs into a pulled-in border, so its ends stay within the padded border
 * rather than reaching the raw cell corner on the midline between cells.
 *
 * <p>Pure geometry over the adjacency graph and the per-system grouping keys: it decides which
 * edges merge with {@link EdgeClassifier}'s rule and offsets the rest with
 * {@link PolygonOffsets#insetSelectedEdges}, handing the render layer a ready fill polygon and a
 * per-edge boundary flag so it never re-derives adjacency. The key is opaque here (the faction
 * layer keys by dominant-faction id), so the same clustering serves any layer.
 */
public final class CellShaper {

    private CellShaper() {
    }

    /**
     * Shapes every system's cell into its merged-cluster polygon.
     *
     * @param edgesBySystemId    each system's raw cell edges, in winding order,
     *                           tagged with the neighbour across them
     * @param groupKeyBySystemId the grouping key per system; a system absent from
     *                           the map counts as ungrouped
     * @param borderInset        inward inset applied to every border edge
     * @return one shaped cell per input system, keyed by system id, in iteration
     *         order
     */
    public static Map<String, ShapedCell> shapeCells(
            Map<String, List<CellEdge>> edgesBySystemId,
            Map<String, String> groupKeyBySystemId, double borderInset) {
        var shaped = new LinkedHashMap<String, ShapedCell>();
        for (var entry : edgesBySystemId.entrySet()) {
            var ownGroupKey = groupKeyBySystemId.get(entry.getKey());
            shaped.put(entry.getKey(),
                    shapeCell(entry.getValue(), ownGroupKey, groupKeyBySystemId, borderInset));
        }
        return shaped;
    }

    /**
     * Shapes one system's cell into its merged-cluster polygon, for the incremental
     * refresh path that re-shapes just the cells around a grouping change rather
     * than the whole map. Same rule as {@link #shapeCells}, applied to one cell.
     *
     * @param edges              the cell's raw edges, in winding order, each tagged
     *                           with the neighbour across it
     * @param ownGroupKey        the grouping key of this cell, or null if ungrouped
     * @param groupKeyBySystemId the grouping key per system, to classify each edge
     *                           as a same-key seam or a border
     * @param borderInset        inward inset applied to every border edge
     * @return the shaped cell: its inset fill polygon and per-edge boundary flags
     */
    public static ShapedCell shapeCell(List<CellEdge> edges, String ownGroupKey,
            Map<String, String> groupKeyBySystemId, double borderInset) {
        var vertices = new ArrayList<double[]>(edges.size());
        var isBorderEdge = new boolean[edges.size()];
        for (var i = 0; i < edges.size(); i++) {
            var edge = edges.get(i);
            vertices.add(new double[] {edge.x1(), edge.y1()});
            isBorderEdge[i] = EdgeClassifier.classifyAcross(edge, ownGroupKey, groupKeyBySystemId)
                    == EdgeClass.BOUNDARY;
        }
        var inset = PolygonOffsets.insetSelectedEdges(vertices, isBorderEdge, borderInset);
        return new ShapedCell(inset.vertices(), inset.edgeIsInset());
    }
}
