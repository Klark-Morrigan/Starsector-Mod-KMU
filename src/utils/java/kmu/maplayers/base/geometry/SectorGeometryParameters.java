package kmu.maplayers.base.geometry;

import kmlib.math.geometry.VoronoiCellBuilder;

/**
 * The knobs a sector's geometry is built under, gathered so a caller can vary them.
 *
 * <p>In the mod these arrive from Luna settings and from
 * {@link CellShaper#BORDER_INSET_DISTANCE}; here they are a plain value so the same pipeline
 * can be run at a default for a test's assertions and swept live by
 * {@link SectorGeometryViewer}'s sliders. The defaults mirror the shipped ones, so a run that
 * passes none describes the map as a player sees it.
 *
 * @param cellRadius      how far a cell may reach from its site
 * @param boundSegments   sides of the polygon approximating that radius bound
 * @param borderInset     the channel every border edge is cut inward by
 * @param weldTolerance   largest gap between two reports of a shared corner still welded
 *                        into one when chaining a cluster's boundary
 * @param miterSpikeLimit multiple of the inset past which a sharp corner bevels
 */
record SectorGeometryParameters(
        double cellRadius,
        int boundSegments,
        double borderInset,
        double weldTolerance,
        double miterSpikeLimit) {
    // KmuPoliticalMapSettings' DEFAULT_CELL_RADIUS. Duplicated rather than read, because reading
    // it would drag LunaLib into a pipeline that is otherwise pure geometry.
    static final double DEFAULT_CELL_RADIUS = 4000.0;
    // KmuMapLayerSettings' DEFAULT_BORDER_WELD_TOLERANCE. Real cells need this: two neighbours
    // each carry their own polygonal radius bound, so a shared bisector meets those two arcs
    // about a chord's sagitta apart, and welding tighter than that leaves every multi-system
    // cluster's chain open.
    static final double DEFAULT_WELD_TOLERANCE = 100.0;
    // KmuMapLayerSettings' DEFAULT_BORDER_MITER_LIMIT.
    static final double DEFAULT_MITER_SPIKE_LIMIT = 4.0;

    /**
     * The shipped defaults - the geometry a player actually sees.
     *
     * @return the default parameters
     */
    static SectorGeometryParameters createDefaults() {
        return new SectorGeometryParameters(
                DEFAULT_CELL_RADIUS,
                VoronoiCellBuilder.DEFAULT_CELL_BOUND_SEGMENTS,
                CellShaper.BORDER_INSET_DISTANCE,
                DEFAULT_WELD_TOLERANCE,
                DEFAULT_MITER_SPIKE_LIMIT);
    }
}
