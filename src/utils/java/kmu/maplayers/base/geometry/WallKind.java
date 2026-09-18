package kmu.maplayers.base.geometry;

/**
 * The sorts of wall, which sit on the boundary in two different ways.
 *
 * <p>A named kind rather than a flag, and carried by the wall rather than passed to the
 * test, because it is a fact about how the line was arrived at: a span is the line
 * joining two sites and a reach of coast is a tangent the smoothing drew. Nothing else in
 * the walk asks - every kind opens a mouth, takes a stretch of circle out of the boundary,
 * and closes a cycle.
 *
 * <p>The spans are told apart by which water they were laid over, which the walk never
 * asks and everything reading a hole it closed does: a piece of void is the kind of piece
 * its walls say it is, and a wall that had forgotten where it came from would leave that
 * unanswerable.
 */
public enum WallKind {

    /**
     * The line joining two sites, spanning the gap between their cells. It crosses its
     * circles steeply and squarely between them, so the two edges of its mouth say
     * whether the gap it spans is still there: buried, and the cells have closed over it.
     *
     * <p>The span the cell-pair search lays over the cells alone, with no shore consulted.
     * Every span below sits on the boundary exactly as this one does.
     */
    BRIDGE,

    /** A span across an inlet of an outer shore: water between two cells of one continent
     * on the side that faces the open void. */
    INLET_SPAN,

    /** A span across a lake: water a continent's own cells closed around unaided. */
    LAKE_SPAN,

    /** A span across a puddle: a hole too small to have been drawn a shore. */
    PUDDLE_SPAN,

    /** A span between two continents, joining what tracing without bridges took apart. */
    LINK,

    /**
     * A straight run the coast smoothing drew from one cell's frontage to another's. It
     * LEAVES along a tangent and can end exactly where two circles cross, so half its
     * mouth lies inside the neighbouring disc whatever the reach did - its own end is the
     * only thing that says whether it is on the boundary.
     */
    COAST_REACH,

    /**
     * The same run, drawn on a lake's shore instead of an outer coast's.
     *
     * <p>Told apart from {@link #COAST_REACH} by what lies either side, not by how it is
     * built. An outer coast has water on one side and cells on the other; a lake shore has
     * water on BOTH - the pockets within the line, and the margin the line conceded to the
     * cells - so a hole closing on one is lake water either way, and which side it fell is
     * a question of where it sits rather than of what walled it.
     */
    LAKE_SHORE;

    /**
     * Whether this is a run of a drawn shoreline rather than a span across void.
     *
     * <p>The two are placed differently and so are judged differently: a shore's run leaves
     * its cell along a tangent and may end exactly on a crossing, where a span crosses its
     * circles squarely between the two sites. Asked as one question rather than compared
     * against each kind in turn, so a shore added later cannot be judged as a span by a
     * test that was never told about it.
     *
     * @return whether a run of drawn shoreline
     */
    public boolean isShoreReach() {
        return this == COAST_REACH || this == LAKE_SHORE;
    }
}
