package kmu.politicalmap.domain;

/**
 * A cell edge paired with its ownership classification, ready to draw.
 *
 * <p>{@code (x1, y1)}..{@code (x2, y2)} is the segment in world (hyperspace)
 * coordinates; {@link EdgeClass} selects how the political map tints it. The
 * render-facing output of {@link EdgeClassifier}: the adjacency and owner reads
 * are already resolved, so the renderer only chooses a colour per class.
 */
public record ClassifiedEdge(double x1, double y1, double x2, double y2, EdgeClass edgeClass) {
}
