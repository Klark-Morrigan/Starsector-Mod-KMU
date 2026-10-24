package kmu.maplayers.ownermap.owners.holders.base;

/**
 * What the holder source opens a {@link SystemHolderResolver} from, for one batch.
 *
 * <p>A source rather than a resolver, because a resolver stands on a reading of the sector and the
 * batch is what decides when that reading is taken. The layer supplies the source once, beside its
 * whole-sector rule, and the holder source opens a resolver per batch over the pass it has already
 * opened for that batch - so a marked system's holder and its habitation are answered off one walk,
 * and nothing carries a walk of the sector between two colony events.
 *
 * <p>The pass is handed in rather than opened here: it carries the grouping the standing build
 * classified its holders under, so a batch re-deriving a system lands the same bloc the bulk pass
 * did. Opened afresh, a batch could fold a system under a grouping the map was not painted with. The
 * spotlit bloc is handed in for the same reason: the batch re-derives a marked system as the standing
 * build painted it, which under a spotlight is the spotlit resolver rather than the plain one.
 */
@FunctionalInterface
public interface SystemHolderResolverSource {

    /**
     * Opens one batch's holder resolver over the batch's reading of the sector.
     *
     * @param pass          the batch's reading of the sector, under the grouping the standing build
     *                      was folded by
     * @param spotlitBlocId the bloc the standing build was spotlighting, or null off filter
     * @return the batch's resolver
     */
    SystemHolderResolver openResolverOver(HolderPass pass, String spotlitBlocId);
}
