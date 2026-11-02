package kmu.maplayers.ownermap.render.rebuild;

import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.owners.SectorWalk;

/**
 * What one rebuild builds from, taken once and handed whole to whichever view it builds.
 *
 * <p>One value because every stage has to build off the same moment. A stage handed the parts one by
 * one could be handed a walk of one frame beside a reading of another, and the map would draw owners
 * from one sampling of the view under names fitted for a different one. Built once per rebuild and
 * discarded with it, for the reason the walk inside it is.
 *
 * @param cellGeometry         the cells to build over, carrying the cut they stand at
 * @param walk                 the rebuild's one walk of the sector, which every stage reads over
 * @param viewReading          the view being built, with its reading and source, resolved once under
 *                             one sampling of whatever the view reads live
 * @param contentInputs        the sidebar preferences the rebuild sampled when it decided it was owed
 * @param isAnchorOverlayShown whether the anchor overlay is shown, sampled beside the preferences
 *                             because the overlay decides whether the names are fitted
 */
record RebuildReading(
    RevisedCellGeometry cellGeometry,
    SectorWalk walk,
    ViewReading viewReading,
    ContentInputs contentInputs,
    boolean isAnchorOverlayShown) {
}
