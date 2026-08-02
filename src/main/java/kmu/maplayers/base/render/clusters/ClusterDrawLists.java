package kmu.maplayers.base.render.clusters;

import kmu.maplayers.base.theme.GlobalStyle;

import java.util.Map;

/**
 * Everything a cluster paint pass reads of a layer's built state: whether there is anything to
 * paint at all, the sector-wide style tier bound once for the frame, and the two draw lists
 * themselves - one record per cell and one per fused footprint.
 *
 * <p>Declared as its own type rather than the pass taking a layer's built state directly, because
 * the emission is the framework's and the built state is not. Typed on one layer's model, the
 * only cluster painter there is would either have to live in that layer's package - where a
 * second layer reaches it by importing the first - or point the framework permanently at one
 * feature. These four reads are what the emission actually uses, so they are all a layer has to
 * answer to be painted.
 *
 * <p>A layer's built state satisfies this directly wherever its draw lists already are these two
 * maps. How they were shaped, cached, styled, or keyed is not asked and cannot be read here: the
 * ids are opaque, and what an owner means stays with the layer that resolved it.
 */
public interface ClusterDrawLists {

    /**
     * @return true when nothing would be painted, so the pass can skip its GL state push entirely
     *         rather than emitting runs that cover no pixels
     */
    boolean isEmpty();

    /**
     * @return the sector-wide style tier - the knobs that resolve once for the whole frame rather
     *         than per cell or per footprint
     */
    GlobalStyle getGlobalStyle();

    /**
     * @return each drawn cell's own draw record, keyed by cell id: the seam a fused cell
     *         contributes, or a lone cell's fill and outline
     */
    Map<String, StyledCell> getStyledCellByCellId();

    /**
     * @return each fused footprint's draw record, keyed by the opaque id its cells fused under -
     *         the fill, hatch, and border rings drawn once for the whole body
     */
    Map<String, StyledCluster> getStyledClusterById();
}
