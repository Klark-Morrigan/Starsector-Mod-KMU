package kmu.maplayers.base.hover;

import kmu.maplayers.base.geometry.SystemClusterIndex;

/**
 * What a frame put on screen for the cursor to land on: the painted cell shapes it inherits, plus
 * which contiguous cluster each of those cells belongs to.
 *
 * <p>The two travel together because one hover answer is built from both - the cursor resolves to
 * a cell by shape, and the answer then widens to the whole cluster around it, since that is the
 * extent a highlight traces and a tooltip speaks for. Splitting them would let a hover resolve
 * against this frame's shapes and cluster against a previous frame's grouping.
 */
public interface MapHoverTargets extends PaintedCellShapes {

    /**
     * @return which contiguous cluster each cell belongs to, so a resolved cell widens to the
     *         whole cluster around it
     */
    SystemClusterIndex getClusterIndex();
}
