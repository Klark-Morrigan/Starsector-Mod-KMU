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
 * @param boundSegments   sides of the polygon approximating that radius bound, whose vertex
 *                        angles are also what every outline traced against the cells is
 *                        flattened onto
 * @param borderInset     the channel every border edge is cut inward by
 * @param weldTolerance   largest gap between two reports of a shared corner still welded
 *                        into one when chaining a cluster's boundary
 * @param miterSpikeLimit multiple of the inset past which a sharp corner bevels
 */
public record SectorGeometryParameters(
        double cellRadius,
        int boundSegments,
        double borderInset,
        double weldTolerance,
        double miterSpikeLimit) {
    // What converts a count of sides round a whole circle into a count of samples per half
    // turn of arc.
    private static final int HALF_TURNS_PER_CIRCLE = 2;

    // KmuPoliticalMapGeometrySettings' DEFAULT_CELL_RADIUS. Duplicated rather than read, because reading
    // it would drag LunaLib into a pipeline that is otherwise pure geometry.
    public static final double DEFAULT_CELL_RADIUS = 4000.0;
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
    public static SectorGeometryParameters createDefaults() {
        return new SectorGeometryParameters(
                DEFAULT_CELL_RADIUS,
                VoronoiCellBuilder.DEFAULT_CELL_BOUND_SEGMENTS,
                CellShaper.BORDER_INSET_DISTANCE,
                DEFAULT_WELD_TOLERANCE,
                DEFAULT_MITER_SPIKE_LIMIT);
    }

    /**
     * How far a cell reaches once the channel is taken off the void's side.
     *
     * <p>The reach a void shape is DRAWN at. A pocket traced here stops one channel short of
     * the cells around it, which is what leaves the gap between a fill and a border.
     *
     * @return the reach to trace a drawn void shape at
     */
    public double measureDrawnReach() {
        return cellRadius + borderInset;
    }

    /**
     * How finely a half-turn of a SMOOTHED line's fillets is sampled.
     *
     * <p>Taken from the cells' own bound rather than set apart from it. The two count the
     * same thing in different units - a bound is so many sides round a whole circle, a fillet
     * is sampled so many times per half turn - so a line sampled at its own number comes out
     * at a different smoothness from the cell it runs against, and the knob that refines the
     * cells leaves everything drawn beside them where it was.
     *
     * <p>Only a smoothed line takes its sampling this way, and only because it is a line
     * rather than an outline: it runs near the cells without having to meet them, so it is
     * free to be sampled evenly along its own sweep. Anything that has to ABUT a cell is
     * flattened onto {@link #boundSegments} instead - onto the cell's own vertex angles - and
     * an even sweep would leave the two a half step out of phase, sharing no vertex.
     *
     * @return the sample count a fillet should be drawn at
     */
    public int measureArcSegments() {
        return boundSegments() / HALF_TURNS_PER_CIRCLE;
    }

    /**
     * How far a cell reaches once the channel is taken off the cell's own side.
     *
     * <p>The reach a CELL is filled to. A pocket traced here is what the void becomes when
     * the cells around it fill right up to it, which is how a pocket is told from a channel
     * that merely widened.
     *
     * @return the reach to trace a filled cell at
     */
    public double measureFilledReach() {
        return cellRadius - borderInset;
    }
}
