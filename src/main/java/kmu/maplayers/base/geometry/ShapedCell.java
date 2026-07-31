package kmu.maplayers.base.geometry;

import java.util.List;

/**
 * One system's Voronoi cell shaped into its merged cell cluster: a closed fill
 * polygon plus, per edge, whether that edge is a cluster border rather than an
 * interior seam fused with a same-owner neighbour.
 *
 * <p>{@code fillPolygon} is the convex fill in winding order, or empty when the
 * border inset consumed or collapsed the cell (nothing to draw). {@code
 * edgeIsBoundary} runs parallel to it: entry {@code i} covers the edge from vertex
 * {@code i} to vertex {@code (i + 1)} modulo the vertex count, true when that edge
 * was pulled inward to the padded channel (a border against a different owner,
 * unowned space, or the map frontier) and false when it was left on the true cell
 * edge to fuse with a same-owner neighbour. The render layer fills the polygon and
 * strokes its boundary edges as the bold cluster border and its remaining edges as
 * the faint interior seam lines.
 */
public record ShapedCell(List<double[]> fillPolygon, boolean[] edgeIsBoundary) {
}
