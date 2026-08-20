package kmu.maplayers.base.geometry;

/**
 * One corridor of void between two cells.
 *
 * <p>The single currency for a straight run across void between two cells, whatever is being
 * done with it - offered as somewhere to divide a pocket, kept as void a pair holds, laid as a
 * wall, or drawn. Those are the same line measured the same way, so they are the same value;
 * naming them apart per construction only invites two of them to drift.
 *
 * <p>Its own type rather than one nested in whichever class finds them, because what wants a
 * corridor and what searches for corridors are not the same layer: the boundary trace is handed
 * gaps to lay walls across and never looks for one, and nesting the value inside the search made
 * the lower layer name the higher one.
 *
 * @param fromSite which cell it leaves
 * @param toSite   which cell it meets
 * @param start    where it meets the first cell's reach
 * @param end      where it meets the second's
 * @param width    how far apart the two cells are across it, at the reach that defines the void
 *                 rather than any reach something is drawn at, so two gaps stay comparable when
 *                 the channel width changes
 */
record CellGap(
    int fromSite,
    int toSite,
    double[] start,
    double[] end,
    double width) {
}
