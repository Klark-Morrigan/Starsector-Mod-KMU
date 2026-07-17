package kmu.maplayers.politicalmap.base.geometry;

/**
 * One edge of a cell, tagged with what lies across it - a single entry in the
 * political map's cell-adjacency graph.
 *
 * <p>{@code (x1, y1)}..{@code (x2, y2)} is the edge segment in world (hyperspace)
 * coordinates. {@link #target()} names the far side: the neighbouring system, the
 * cell's outer reach bound, or more of the same territory. The edge classifier reads
 * this cell's owner against whatever the target names to decide whether the edge is an
 * interior seam (same faction both sides, or no far side to differ from) or a national
 * boundary.
 */
public record CellEdge(double x1, double y1, double x2, double y2, EdgeTarget target) {
}
