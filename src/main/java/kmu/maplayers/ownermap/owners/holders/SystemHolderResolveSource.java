package kmu.maplayers.ownermap.owners.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.ownermap.holding.HolderGrouping;

/**
 * What the incremental refresh opens a {@link SystemHolderResolve} from, for one batch.
 *
 * <p>A source rather than a resolve, because a resolve stands on a reading of the sector and the
 * batch is what decides when that reading is taken. The tier holds the source for the life of a
 * renderer and opens a resolve per batch, so nothing carries a walk of the sector between two
 * colony events.
 *
 * <p>The grouping is handed in rather than resolved here: it is the one the standing build
 * classified its holders under, so a batch re-deriving a system lands the same bloc the bulk pass
 * did. Resolved afresh, a batch could fold a system under a grouping the map was not painted
 * with.
 */
@FunctionalInterface
public interface SystemHolderResolveSource {

    /**
     * Opens one batch's reading of the sector and the holder resolve over it.
     *
     * @param sector   the sector the batch re-derives against
     * @param grouping the grouping the standing build resolved its holders under
     * @return the batch's resolve
     */
    SystemHolderResolve openResolveOver(SectorAPI sector, HolderGrouping grouping);
}
