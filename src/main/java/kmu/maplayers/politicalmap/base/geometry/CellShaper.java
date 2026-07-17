package kmu.maplayers.politicalmap.base.geometry;

import kmlib.math.geometry.PolygonOffsets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapes each raw cell into a merged cluster polygon, fused by grouping key.
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
     * Shapes every cell into its merged-cluster polygon.
     *
     * @param edgesByCellId each cell's raw edges, in winding order, tagged with what lies
     *                      across them
     * @param grouping      which system each cell draws as and each system's grouping key -
     *                      a cell with no system, or whose system is ungrouped, shapes as
     *                      ungrouped
     * @param borderInset   inward inset applied to every border edge
     * @return one shaped cell per input cell, keyed by cell id, in iteration order
     */
    public static Map<String, ShapedCell> shapeCells(
            Map<String, List<CellEdge>> edgesByCellId,
            CellGrouping grouping,
            double borderInset) {
        var shaped = new LinkedHashMap<String, ShapedCell>();
        for (var entry : edgesByCellId.entrySet()) {
            shaped.put(
                    entry.getKey(),
                    shapeCell(
                        entry.getValue(),
                        grouping.resolveGroupKeyOf(entry.getKey()),
                        grouping.groupKeyBySystemId(),
                        borderInset));
        }
        return shaped;
    }

    /**
     * Shapes one cell into its merged-cluster polygon, for the incremental
     * refresh path that re-shapes just the cells around a grouping change rather
     * than the whole map. Same rule as {@link #shapeCells}, applied to one cell.
     *
     * @param edges              the cell's raw edges, in winding order, each tagged
     *                           with what lies across it
     * @param ownGroupKey        the grouping key of this cell, or null if ungrouped
     * @param groupKeyBySystemId the grouping key per system, to classify each edge
     *                           as a same-key seam or a border
     * @param borderInset        inward inset applied to every border edge
     * @return the shaped cell: its inset fill polygon and per-edge boundary flags
     */
    public static ShapedCell shapeCell(
            List<CellEdge> edges,
            String ownGroupKey,
            Map<String, String> groupKeyBySystemId,
            double borderInset) {
        var vertices = new ArrayList<double[]>(edges.size());
        var edgeInsets = new double[edges.size()];
        for (var i = 0; i < edges.size(); i++) {
            var edge = edges.get(i);
            vertices.add(new double[] {edge.x1(), edge.y1()});
            edgeInsets[i] = computeEdgeInset(edge, ownGroupKey, groupKeyBySystemId, borderInset);
        }
        var inset = PolygonOffsets.insetSelectedEdges(vertices, edgeInsets);
        return new ShapedCell(inset.vertices(), inset.edgeIsInset());
    }

    // The inward inset one edge receives: none for a same-key seam (left on the raw cell
    // border to fuse), the border channel for every other edge - a different key, ungrouped
    // space, or the map frontier alike - so the cluster keeps one uniform channel against
    // everything outside it.
    private static double computeEdgeInset(
            CellEdge edge,
            String ownGroupKey,
            Map<String, String> groupKeyBySystemId,
            double borderInset) {
        var edgeClass = EdgeClassifier.classifyAcross(edge, ownGroupKey, groupKeyBySystemId);
        return edgeClass == EdgeClass.INTERIOR_SEAM ? 0.0 : borderInset;
    }
}
