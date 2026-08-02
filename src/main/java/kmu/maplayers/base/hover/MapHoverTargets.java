package kmu.maplayers.base.hover;

import kmu.maplayers.base.geometry.SystemClusterIndex;

import java.util.List;
import java.util.Map;

/**
 * What a frame put on screen for the cursor to land on: each drawn cell's painted shape, and
 * which contiguous cluster that cell belongs to.
 *
 * <p>The two travel together because one hover answer is built from both - the cursor resolves to
 * a cell by shape, and the answer then widens to the whole cluster around it, since that is the
 * extent a highlight traces and a tooltip speaks for. Splitting them would let a hover resolve
 * against this frame's shapes and cluster against a previous frame's grouping.
 *
 * <p>The shapes are the ones actually painted rather than a re-derivation, so the border channel
 * between two cells is genuinely nobody's - a cursor there resolves to no cell, exactly as it
 * draws.
 */
public interface MapHoverTargets {

    /**
     * @return which contiguous cluster each cell belongs to, so a resolved cell widens to the
     *         whole cluster around it
     */
    SystemClusterIndex getClusterIndex();

    /**
     * @return each drawn cell's painted extent as {x, y} vertex pairs in world coordinates,
     *         keyed by cell id; a cell that draws nothing is absent and can never be hit
     */
    Map<String, List<double[]>> getFillPolygonByCellId();
}
