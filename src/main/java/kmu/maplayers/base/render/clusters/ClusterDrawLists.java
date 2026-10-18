package kmu.maplayers.base.render.clusters;

import kmu.maplayers.base.render.clusters.StyledCell.FusedCell;
import kmu.maplayers.base.render.clusters.StyledCell.LoneCell;
import kmu.maplayers.base.theme.GlobalStyle;

import java.util.List;
import java.util.Map;

/**
 * Everything a cluster paint pass reads of a layer's built state: whether there is anything to
 * paint at all, the sector-wide style tier bound once for the frame, and the draw lists
 * themselves - the drawn cells in each of their two forms, and one record per owner covering every
 * cluster that owner holds.
 *
 * <p>Declared as its own type rather than the pass taking a layer's built state directly, because
 * the emission is the framework's and the built state is not. Typed on one layer's model, the
 * only cluster painter there is would either have to live in that layer's package - where a
 * second layer reaches it by importing the first - or point the framework permanently at one
 * feature. These five reads are what the emission actually uses, so they are all a layer has to
 * answer to be painted.
 *
 * <p>The cells arrive already parted by form because every pass over them draws one form only, and
 * the pass runs every frame: parting them there would test every drawn cell's type once per pass
 * per frame for an answer that only a rebuild can change. How the cells and clusters were shaped,
 * cached, styled, or keyed is not asked and cannot be read here: the IDs are opaque, and what an
 * owner means stays with the layer that resolved it.
 */
public interface ClusterDrawLists {

    /**
     * @return true when nothing would be painted, so the pass can skip its GL state push entirely
     *         rather than emitting runs that cover no pixels
     */
    boolean isEmpty();

    /**
     * @return the sector-wide style tier - the knobs that resolve once for the whole frame rather
     *         than per cell or per cluster
     */
    GlobalStyle getGlobalStyle();

    /**
     * @return every drawn cell that fused into a cluster, each drawing only its seams; the same list
     *         from one rebuild to the next frame that changes the cells
     */
    List<FusedCell> getFusedCells();

    /**
     * @return every drawn cell that fused with nothing, each drawing its own fill and outline; the
     *         same list from one rebuild to the next frame that changes the cells
     */
    List<LoneCell> getLoneCells();

    /**
     * @return each owner's clusters and the paint they share, keyed by the opaque owner ID its
     *         cells fused under - one entry however many disjoint bodies that owner holds
     */
    Map<String, StyledClusterGroup> getStyledClusterGroupByOwnerId();
}
