package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.VoronoiCellBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * The Voronoi partition as it is before the radius clip - the cells that tile the plane with
 * no void at all.
 *
 * <p>Worth being able to see, because it is where the void comes from. Every point of the
 * sector belongs to exactly one site under this partition, and it is only clipping each cell
 * back to {@code cellRadius} that opens gaps. So the void is not unowned space that has to be
 * divided by some new rule: it is space that already has an owner, hidden by the clip. Drawing
 * the unclipped cells under the clipped ones shows the bisectors the clip removed, which are
 * where the cells would reach if nothing bounded them.
 *
 * <p>"Unbounded" is a convenience rather than the truth - an outer cell really is unbounded,
 * and a polygon cannot be. They are built at a radius far past the sector instead, so every
 * bisector inside the sector survives and only the outermost cells are cut, well away from
 * anything being looked at.
 */
public final class UnboundedCells {

    // How far past the sector's own extent to put the bound, as a multiple of that extent.
    // Large enough that the clip cannot reach any bisector between real sites, small enough
    // that the coordinates stay comfortably inside double precision.
    private static final double BOUND_EXTENT_MULTIPLIER = 4.0;

    private UnboundedCells() {
    }

    /**
     * Builds each site's Voronoi cell without the reach bound biting.
     *
     * @param sites         the sites, in the order the cells should come back
     * @param boundSegments how many sides approximate the far-off bound
     * @return one cell per site, index-aligned
     */
    public static List<List<double[]>> buildUnboundedCells(List<double[]> sites, int boundSegments) {

        var bounds = Bounds.computeEnclosingBounds(sites);
        var radius = Math.max(
                bounds.maxX() - bounds.minX(),
                bounds.maxY() - bounds.minY())
            * BOUND_EXTENT_MULTIPLIER;

        var cells = new ArrayList<List<double[]>>(sites.size());

        for (var index = 0; index < sites.size(); index++) {

            cells.add(VoronoiCellBuilder.buildCell(
                sites.get(index),
                sites,
                radius,
                boundSegments));
        }
        return cells;
    }
}
