package kmu.maplayers.ownermap;

import kmu.maplayers.ownermap.holding.HolderGrouping;

/**
 * The view one build painted and the grouping snapshot it resolved holding under, held
 * together so an incremental re-shape classifies a cell against the same view and the same
 * once-sampled grouping the full build did - a view's grouping may read a live source, so it is
 * sampled once and every stage keys off one snapshot.
 */
public record ViewGrouping(
    OwnerPaintedView view,
    HolderGrouping grouping) {
}
