package kmu.maplayers.base.render.clusters;

import java.util.List;

/**
 * One cell ready to draw, carrying the ring its ink was laid on.
 *
 * <p>The two travel as one value because they have to describe the same shape. A draw record is
 * flattened geometry - a triangle soup and GL_LINES segments - that no reader can recover a
 * boundary from, yet resolving a cursor to a cell and lighting that cell up both need exactly
 * that boundary, and need the one the cell painted rather than whatever it was derived from.
 * Handed over apart, the ring is whatever a caller chooses to pass, and a caller that passes the
 * shape from before the cell's own smoothing leaves those two reading corners the cell no longer
 * draws.
 *
 * <p>Which ring that is differs by the form the cell takes, which is why only the builder can
 * answer it: a cell fused into a cluster paints within the cluster's shape, so its ring is its
 * raw extent and the cluster's own border is what bounds it, while a lone cell strokes and fills
 * a ring of its own - the one left after the sector-wide corner rounding.
 *
 * @param styledCell    the cell's draw record
 * @param paintedExtent the ring that record was built from, as {x, y} vertex pairs in world
 *                      coordinates
 */
public record PaintedCell(
    StyledCell styledCell,
    List<double[]> paintedExtent) {
}
