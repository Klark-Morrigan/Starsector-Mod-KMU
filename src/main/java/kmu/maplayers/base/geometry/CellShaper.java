package kmu.maplayers.base.geometry;

import kmlib.math.geometry.PolygonOffsets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shapes each raw cell into a merged cluster polygon, fused by owner.
 *
 * <p>An edge shared with a same-owner neighbour is left on the true cell border, so the two
 * cells' fills meet exactly along it and fuse into one cluster with no seam. Every other edge -
 * against a different owner, unowned space, or the map frontier - is pulled inward, so a
 * cluster keeps the uniform border channel against everything outside it. A kept seam edge is
 * truncated where it runs into a pulled-in border, so its ends stay within the padded border
 * rather than reaching the raw cell corner on the midline between cells.
 *
 * <p>Pure geometry over the adjacency graph and the per-system owners: it decides which
 * edges merge with {@link EdgeClassifier}'s rule and offsets the rest with
 * {@link PolygonOffsets#insetSelectedEdges}, handing the render layer a ready fill polygon and a
 * per-edge boundary flag so it never re-derives adjacency. The owner is opaque here, so the same
 * clustering serves any layer.
 */
public final class CellShaper {
    /**
     * The inward inset every border edge takes, so two neighbouring clusters leave a uniform
     * {@code 2 * inset} channel between them.
     *
     * <p>Fixed rather than player-tunable, because it is geometry and not look: it decides
     * where two clusters' fills stop, so anything that traces or clips against a shaped cell
     * has to inset by this exact value or its line lands somewhere the fills do not. It lives
     * here, on the shaping that applies it, so that agreement is a named dependency rather
     * than two call sites happening to pass the same number.
     *
     * <p>The border's rounding shape - corner radius, segments, chamfer angle, and vertex weld
     * tolerance - is player-tunable under the LunaLib "Dev" tab instead, read from
     * {@link kmu.settings.KmuMapLayerSettings} and the drawing layer's own settings.
     */
    public static final double BORDER_INSET_DISTANCE = 150.0;

    private CellShaper() {
    }

    /**
     * Shapes every cell into its merged-cluster polygon.
     *
     * @param edgesByCellId each cell's raw edges, in winding order, tagged with what lies
     *                      across them
     * @param grouping      which system each cell draws as and each system's owner -
     *                      a cell with no system, or whose system is unowned, shapes as
     *                      unowned
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
                    grouping.resolveOwnerOf(entry.getKey()),
                    grouping.ownerBySystemId(),
                    borderInset));
        }
        return shaped;
    }

    /**
     * Shapes one cell into its merged-cluster polygon, for the incremental
     * refresh path that re-shapes just the cells around an ownership change rather
     * than the whole map. Same rule as {@link #shapeCells}, applied to one cell.
     *
     * @param edges           the cell's raw edges, in winding order, each tagged
     *                        with what lies across it
     * @param cellOwner       the owner of this cell, or null if unowned
     * @param ownerBySystemId the owner per system, to classify each edge
     *                        as a same-owner seam or a border
     * @param borderInset     inward inset applied to every border edge
     * @return the shaped cell: its inset fill polygon and per-edge boundary flags
     */
    public static ShapedCell shapeCell(
            List<CellEdge> edges,
            String cellOwner,
            Map<String, String> ownerBySystemId,
            double borderInset) {

        var vertices = new ArrayList<double[]>(edges.size());
        var edgeInsets = new double[edges.size()];
        for (var i = 0; i < edges.size(); i++) {
            var edge = edges.get(i);
            vertices.add(new double[] {edge.x1(), edge.y1()});
            edgeInsets[i] = computeEdgeInset(edge, cellOwner, ownerBySystemId, borderInset);
        }
        var inset = PolygonOffsets.insetSelectedEdges(vertices, edgeInsets);
        return new ShapedCell(inset.vertices(), inset.edgeIsInset());
    }

    // The inward inset one edge receives: none for a same-owner seam (left on the raw cell
    // border to fuse), the border channel for every other edge - a different owner, unowned
    // space, or the map frontier alike - so the cluster keeps one uniform channel against
    // everything outside it.
    private static double computeEdgeInset(
            CellEdge edge,
            String cellOwner,
            Map<String, String> ownerBySystemId,
            double borderInset) {
                
        var edgeClass = EdgeClassifier.classifyAcross(edge, cellOwner, ownerBySystemId);
        return edgeClass == EdgeClass.INTERIOR_SEAM ? 0.0 : borderInset;
    }
}
