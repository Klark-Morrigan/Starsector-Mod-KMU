package kmu.maplayers.base.geometry;

/**
 * How a cell edge sits relative to the owners either side of it.
 *
 * <p>An interior seam runs between two systems sharing an owner; a boundary
 * runs against a different owner, between two unowned sides, or along the map
 * frontier; an open frontier runs between a owned system and an unowned
 * neighbour it can reach toward. The seam-vs-boundary split drives whether the edge
 * draws as a faint interior line or a bold cell-cluster border; the
 * boundary-vs-open-frontier split lets a consumer push a owned edge out into
 * unowned space while a plain boundary keeps its inward inset.
 *
 * <p>Drawing that only cares seam-or-border reads {@link #isBoundary()}, which folds
 * OPEN_FRONTIER back in with BOUNDARY, so the finer class is inert until a consumer
 * distinguishes it.
 */
public enum EdgeClass {
    INTERIOR_SEAM,
    BOUNDARY,
    OPEN_FRONTIER;

    /**
     * Whether this edge is drawn as a border rather than a fused interior seam - true
     * for both a plain BOUNDARY and an OPEN_FRONTIER, so a consumer that does not act
     * on the frontier distinction treats them alike.
     *
     * @return true for BOUNDARY and OPEN_FRONTIER, false for INTERIOR_SEAM
     */
    public boolean isBoundary() {
        return this == BOUNDARY || this == OPEN_FRONTIER;
    }
}
