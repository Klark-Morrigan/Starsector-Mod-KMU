package kmu.maplayers.ownermap;

import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.OwnerReading;

/**
 * The view one build painted, the owner reading it styled and named every owner by, and the
 * grouping its holding was folded under - held together so an incremental re-shape classifies a
 * cell against the same view and the same once-sampled reading the full build did. A view's
 * reading may rest on a live source, so it is resolved once and every stage keys off one snapshot.
 *
 * <p>Every per-owner answer a stage paints from is the reading's. The grouping rides beside it for
 * one reader alone - the per-system resolve an incremental refresh reopens, which has to fold a
 * marked system's colonies exactly as the holding it patches was folded - and no stage styles or
 * names anything by it.
 *
 * @param view     the view painted
 * @param reading  the answers about its owners, resolved once for the build
 * @param grouping the grouping the build's holding was folded under
 */
public record ViewReading(
    OwnerPaintedView view,
    OwnerReading reading,
    HolderGrouping grouping) {
}
