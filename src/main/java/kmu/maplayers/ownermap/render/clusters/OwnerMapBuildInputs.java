package kmu.maplayers.ownermap.render.clusters;

import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.ViewReading;
import kmu.maplayers.ownermap.render.style.MapPaints;
import kmu.maplayers.ownermap.render.style.MapStyling;

/**
 * What one build was baked under, retained whole so a cell rebuilt after it is resolved against
 * exactly the inputs the full build used: the paint scheme, the view and the owner reading it was
 * styled by, the sidebar picks it sampled, and whether the anchor overlay was on.
 *
 * <p>Fixed once the build ends, which is what parts it from the {@link SystemOccupancy} beside it:
 * the occupancy is folded per marked system as who owns it and what stands there come and go - the
 * fill state each system draws in among them - while nothing here moves until the next rebuild
 * samples afresh. A reader takes these inputs and names which snapshot it reads, so a pass cannot
 * compose a cell out of one build's theme and another's reading without the type saying so.
 *
 * <p>The scheme, the reading and the picks are held once, inside the build's one {@link MapPaints},
 * which every cell and cluster group of the build paints from. Held here rather than built by each
 * stage, because a stage building its own would resolve each owner's paint again, and the full
 * build and a later batch would each keep a memo of their own. What this adds beside the paints is
 * the view and its source, which a batch re-derives through.
 *
 * <p>The anchor overlay's toggle is held beside the picks because a batch re-fits the names, and the
 * fit is owed while either the names or the overlay reads the placements. Read live, a batch would
 * fit or empty the placements for an overlay the standing build was never baked under.
 */
public final class OwnerMapBuildInputs {

    private final boolean isAnchorOverlayShown;
    private final MapPaints paints;
    private final ViewReading viewReading;

    /**
     * @param styling              the resolved paint scheme every cell and cluster group is styled
     *                             from
     * @param viewReading          the view painted, the owner reading it was styled by, and the
     *                             source its owners came from, which a batch re-derives a marked
     *                             system through
     * @param contentInputs        the sidebar picks the build sampled, as its one reading of them
     * @param isAnchorOverlayShown whether the diagnostic anchor overlay was on, as the rebuild
     *                             sampled it
     */
    public OwnerMapBuildInputs(
            MapStyling styling,
            ViewReading viewReading,
            ContentInputs contentInputs,
            boolean isAnchorOverlayShown) {

        this.isAnchorOverlayShown = isAnchorOverlayShown;
        this.viewReading = viewReading;

        paints = new MapPaints(styling, viewReading.reading(), contentInputs);
    }

    /** @return the sidebar picks the build sampled, as its paints hold them */
    public ContentInputs getContentInputs() {
        return paints.getContentInputs();
    }

    /** @return the paints every cell and cluster group of this build draws in, over its own snapshots */
    public MapPaints getPaints() {
        return paints;
    }

    /** @return the resolved paint scheme every cell and cluster group is styled from, as its paints hold it */
    public MapStyling getStyling() {
        return paints.getStyling();
    }

    /** @return the view painted, with the owner reading and source the build was styled by */
    public ViewReading getViewReading() {
        return viewReading;
    }

    /** @return whether the diagnostic anchor overlay was on when the build sampled it */
    public boolean isAnchorOverlayShown() {
        return isAnchorOverlayShown;
    }
}
