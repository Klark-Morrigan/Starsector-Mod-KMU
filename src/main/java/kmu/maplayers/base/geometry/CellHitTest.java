package kmu.maplayers.base.geometry;

import kmlib.math.geometry.PolygonRegions;
import kmlib.starsector.systems.SystemKey;

import java.util.List;
import java.util.Map;

/**
 * Resolves a world point to the system whose cell covers it - the map's
 * cursor-to-system read.
 *
 * <p>The anchor for hover feedback: which cell washes, which cluster the highlight
 * follows, and which system the standings break down. Tested against each cell's
 * shaped fill polygon rather than its raw Voronoi cell, so the answer matches the
 * painted cells: the border inset, the frontier setback, and any keep-out
 * clipping are already baked into that shape. The border channel between two cells
 * is therefore genuinely nobody's - a point there resolves to no system, exactly as
 * it draws.
 *
 * <p>Pure geometry over plain polygons: no GL, no Starsector types, no per-frame
 * state. The caller supplies the point (an unprojected cursor pixel) and the
 * polygons; nothing here knows where either came from.
 */
public final class CellHitTest {

    private CellHitTest() {
    }

    /**
     * The system whose cell contains the world point, or null when the point falls in
     * no cell - the gap between cells, or empty space beyond the map.
     *
     * <p>Cells do not overlap, so at most one can genuinely contain the point and the
     * search stops at the first hit. Where floating-point rounding would let two
     * abutting cells both claim a point on their shared edge, the map's iteration order
     * decides, which keeps the verdict stable frame to frame rather than flickering
     * between the two.
     *
     * @param worldX                 the point's x coordinate in world (hyperspace) space
     * @param worldY                 the point's y coordinate in world (hyperspace) space
     * @param fillPolygonBySystemKey each system's shaped fill polygon as {x, y} vertex
     *                               pairs in winding order; a system with no drawable cell
     *                               is absent or empty and can never be hit
     * @return the containing system's key, or null when no cell contains the point
     */
    public static SystemKey resolveSystemKeyAt(
            double worldX,
            double worldY,
            Map<SystemKey, List<double[]>> fillPolygonBySystemKey) {

        for (var entry : fillPolygonBySystemKey.entrySet()) {
            if (PolygonRegions.isPointInsideRing(entry.getValue(), worldX, worldY)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
