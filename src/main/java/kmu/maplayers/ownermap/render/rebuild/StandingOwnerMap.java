package kmu.maplayers.ownermap.render.rebuild;

import kmlib.starsector.ui.font.FontAtlas;

import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterNameDisturbance;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.ownermap.owners.SectorWalk;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.labels.ClusterLabelStylingSnapshot;
import kmu.maplayers.ownermap.render.ribbon.CellRibbonsBaker;
import kmu.maplayers.ownermap.render.ribbon.RibbonBakeSettings;

import java.util.List;

/**
 * An owner map as it currently stands: the built clusters, the cluster-name placements
 * fitted over them, the labels minted from those placements, and the cells all three were built
 * against.
 *
 * <p>What an incremental refresh edits, gathered because they are edited together. The placements
 * and the labels are the drawables' own overlays rather than part of the clusters, yet a batch
 * that moves a border has to move them with it - so a redraw that reached only three of the four
 * would leave a name standing over an owner that no longer holds the cell beneath it. Carrying them
 * as one value is what stops a step of that redraw being handed a different three.
 *
 * <p>The stages that write the placements and the labels are this type's, rather than the two being
 * handed out: the full rebuild and a batch both fit the names and lay the bands through here, so the
 * two paths cannot fit or bake one map differently. Only the clusters and the cells are read out,
 * since a batch re-shapes the one within the other.
 *
 * <p>Assembled by the cache keeping these four once its drained batch has something in it, which is the
 * only caller in a position to know: it holds the board the batch came off. A frame that drained
 * nothing - nearly every frame - returns without building one.
 */
public final class StandingOwnerMap {

    private final OwnerMapClusters clusters;
    private final StandingClusterAnchors standingAnchors;
    private final List<Label> ownerLabels;
    private final RevisedCellGeometry cellGeometry;

    /**
     * @param clusters        the built map state - the draw lists, the occupancy, and the styling a
     *                        redrawn cell is resolved against
     * @param standingAnchors the cluster-name placements, paired with what they were fitted under so a
     *                        re-fit can carry over the clusters a batch did not move
     * @param ownerLabels     the drawn name labels, minted from those placements
     * @param cellGeometry    the cells, paired with the revision they stand at; a refresh re-shapes
     *                        within this partition and never recuts it
     */
    public StandingOwnerMap(
            OwnerMapClusters clusters,
            StandingClusterAnchors standingAnchors,
            List<Label> ownerLabels,
            RevisedCellGeometry cellGeometry) {

        this.clusters = clusters;
        this.standingAnchors = standingAnchors;
        this.ownerLabels = ownerLabels;
        this.cellGeometry = cellGeometry;
    }

    /** @return the built map state, which a batch folds into and re-shapes */
    public OwnerMapClusters getClusters() {
        return clusters;
    }

    /** @return the cells, paired with the revision they stand at */
    public RevisedCellGeometry getCellGeometry() {
        return cellGeometry;
    }

    /**
     * Fits the cluster names over the clusters as they now stand, carrying over the placements
     * of every cluster that has not moved.
     *
     * <p>The whole placement list is re-derived rather than one owner's anchors being patched,
     * because a flip can split or merge clusters. What that costs is settled by the matching inside
     * the fit: a re-partitioned cluster matches no standing placement and is searched again, while
     * every cluster left alone is carried over. Whether a fit is wanted comes off the build, so a
     * fit happens only where the build was baked with the names or the anchor overlay on.
     *
     * @param labelFace the face every name is measured in
     * @return what the fit moved, which the bands laid after it have to be re-laid around
     */
    ClusterNameDisturbance fitNamesIn(FontAtlas labelFace) {

        return ClusterAnchorsBuilder.rebuildClusterAnchors(
            standingAnchors,
            cellGeometry,
            ClusterLabelStylingSnapshot
                .resolveFrom(clusters, labelFace)
                .createFitInputs());
    }

    /**
     * Re-fits the names over a map a batch has just edited, and re-mints the labels from what the
     * fit left, so a renamed or relocated cluster's name follows.
     *
     * <p>Measured in the face the standing names were fitted in, so the names this re-fits read
     * exactly as the ones it carries over. The labels are minted under the name choice the build was
     * baked under rather than the live preference: a batch edits the build already on screen, and a
     * live read would mint or drop labels for a pick no cell of that build was shaped for.
     *
     * @return what the re-fit moved, which the bands laid after it have to be re-laid around
     */
    ClusterNameDisturbance refitNames() {

        var nameDisturbance = fitNamesIn(standingAnchors.getFitFingerprint().labelFace());

        LabelsBuilder.rebuildLabels(
            ownerLabels,
            standingAnchors,
            clusters.getBuildInputs().getContentInputs().nameFormat().areNamesDrawn());

        return nameDisturbance;
    }

    /**
     * The band bake one pass over this map lays its bands through, around the names as they now
     * stand.
     *
     * <p>The one construction of a pass, for the full rebuild baking every cell and a batch baking
     * the handful it disturbed. A second copy could bake from a different snapshot of the same map.
     *
     * @param walk the pass's walk of the sector, which the bands' counts are made off
     * @return the pass, ready to bake whichever cells the caller names
     */
    CellRibbonsBaker createRibbonsBakerOver(SectorWalk walk) {

        return CellRibbonsBaker.createForPass(
            clusters,
            cellGeometry.cells(),
            walk,
            standingAnchors,
            RibbonBakeSettings.readFromLunaSettings());
    }
}
