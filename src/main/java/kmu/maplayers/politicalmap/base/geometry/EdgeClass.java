package kmu.maplayers.politicalmap.base.geometry;

/**
 * How a cell edge sits relative to faction ownership.
 *
 * <p>An interior seam runs between two systems the same faction holds; a boundary
 * runs against a different owner, empty-vs-empty space, or the map frontier; an
 * open frontier runs between an owned system and an unowned neighbour it can reach
 * toward (an uncontested dead or decivilised star). The seam-vs-boundary split
 * drives whether the edge draws as a faint interior line or a bold national border;
 * the boundary-vs-open-frontier split lets the frontier consumers push an owned edge
 * out toward a dead star while a plain boundary keeps its inward inset.
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
