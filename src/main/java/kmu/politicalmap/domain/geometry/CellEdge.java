package kmu.politicalmap.domain.geometry;

/**
 * One edge of a system's raw Voronoi cell, tagged with the neighbouring system
 * across it - a single entry in the political map's cell-adjacency graph.
 *
 * <p>{@code (x1, y1)}..{@code (x2, y2)} is the edge segment in world (hyperspace)
 * coordinates. {@code neighbourSystemId} is the system whose cell meets this one
 * along the edge, or {@code null} when the edge is a frontier into empty space
 * (the cell's max-radius bound rather than a shared border). The edge classifier
 * compares this system's owner against the neighbour's to decide whether the
 * edge is an interior seam (same faction both sides) or a national boundary.
 */
public record CellEdge(double x1, double y1, double x2, double y2, String neighbourSystemId) {
}
