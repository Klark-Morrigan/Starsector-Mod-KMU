package kmu.maplayers.ownermap.owners.holders;

import kmu.maplayers.ownermap.holding.HolderPass;

/**
 * What the holder source opens a {@link SystemHolderResolve} from, for one batch.
 *
 * <p>A source rather than a resolve, because a resolve stands on a reading of the sector and the
 * batch is what decides when that reading is taken. The layer supplies the source once, beside its
 * whole-sector rule, and the holder source opens a resolve per batch over the pass it has already
 * opened for that batch - so a marked system's holder and its habitation are answered off one walk,
 * and nothing carries a walk of the sector between two colony events.
 *
 * <p>The pass is handed in rather than opened here: it carries the grouping the standing build
 * classified its holders under, so a batch re-deriving a system lands the same bloc the bulk pass
 * did. Opened afresh, a batch could fold a system under a grouping the map was not painted with.
 */
@FunctionalInterface
public interface SystemHolderResolveSource {

    /**
     * Opens one batch's holder resolve over the batch's reading of the sector.
     *
     * @param pass the batch's reading of the sector, under the grouping the standing build was
     *             folded by
     * @return the batch's resolve
     */
    SystemHolderResolve openResolveOver(HolderPass pass);
}
