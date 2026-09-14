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
 * was pulled inward to the padded channel and false when it was left on the true
 * cell edge. The render layer fills the polygon and strokes its boundary edges as
 * the bold cluster border and its remaining edges as the faint interior seam lines.
 *
 * <p><b>Where an edge sits, not what it faces.</b> Under the shipped
 * {@link EdgeInsetRule#AT_EVERY_BORDER} the two coincide - an edge is pulled in
 * exactly where it borders something the cluster is not part of - so a consumer can
 * read a false entry as a same-owner seam. Under any other rule, or at a zero
 * channel depth, it cannot: an edge can be left on its line for want of an inset
 * rather than because it fuses. A consumer that needs the ownership itself asks
 * {@link EdgeClassifier}, which is where that question is answered.
 */
public record ShapedCell(
    List<double[]> fillPolygon,
    boolean[] edgeIsBoundary) {
}
