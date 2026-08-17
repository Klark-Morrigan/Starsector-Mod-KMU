package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;

import java.util.List;

/**
 * The political map as it currently stands: the built territories, the cluster-name placements
 * fitted over them, the labels minted from those placements, and the cells all three were built
 * against.
 *
 * <p>What an incremental refresh edits, gathered because they are edited together. The placements
 * and the labels are the plugin's own overlays rather than part of the territories, yet a batch
 * that moves a border has to move them with it - so a redraw that reached only three of the four
 * would leave a name standing over a bloc that no longer holds the cell beneath it. Carrying them
 * as one value is what stops a step of that redraw being handed a different three.
 *
 * <p>Assembled inside the refresh once a batch has something to do, rather than by the caller
 * holding these four: the per-frame path drains an empty stale set and returns, and it does that
 * on nearly every frame, so nothing is built for it.
 *
 * @param territories     the built map state - the draw lists, the occupancy, and the styling a
 *                        redrawn cell is resolved against
 * @param standingAnchors the cluster-name placements, paired with what they were fitted under so a
 *                        re-fit can carry over the clusters a batch did not move
 * @param factionLabels   the drawn name labels, minted from those placements
 * @param cellGeometry    the cells, paired with the revision they stand at; a refresh re-shapes
 *                        within this partition and never recuts it
 */
record StandingPoliticalMap(
    PoliticalMapTerritories territories,
    StandingClusterAnchors standingAnchors,
    List<Label> factionLabels,
    RevisedCellGeometry cellGeometry) {
}
