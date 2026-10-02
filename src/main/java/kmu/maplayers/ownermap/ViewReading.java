package kmu.maplayers.ownermap;

import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.OwnerSource;

/**
 * The view one build painted and its two answers about its owners - how each one looks, and who
 * owns what - resolved together under one sampling of whatever the view reads live, and held
 * together so an incremental re-shape re-derives a cell against the same snapshot the full build
 * painted it by.
 *
 * <p>Every per-owner answer a stage paints from is the reading's. The source rides beside it for
 * the batches that follow the build: a marked system is re-derived through the source the standing
 * build was resolved under, so it lands the same owner its neighbours were painted by.
 *
 * @param view    the view painted
 * @param reading the answers about its owners' looks, resolved once for the build
 * @param source  where its owners come from, bound to the same sampling as the reading
 */
public record ViewReading(
    OwnerPaintedView view,
    OwnerReading reading,
    OwnerSource source) {
}
