package kmu.maplayers.ownermap.owners.holders.base;

/**
 * What the holder source opens a {@link SystemHolderResolver} from, for one rebuild or batch.
 *
 * <p>A source rather than a resolver, because a resolver stands on a reading of the sector and the
 * rebuild or batch is what decides when that reading is taken. The layer supplies the source once,
 * and the holder source opens a resolver over the pass it has already opened for that walk - so a
 * system's holder and its habitation are answered off one walk, and nothing carries a walk of the
 * sector between two colony events.
 *
 * <p>The pass is handed in rather than opened here: it carries the grouping the standing build
 * classified its holders under, so a batch re-deriving a system lands the same bloc the rebuild did.
 * Opened afresh, a batch could fold a system under a grouping the map was not painted with. The
 * spotlit bloc is handed in for the same reason: a batch re-derives a marked system as the standing
 * build painted it, which under a spotlight is the spotlit rule rather than the plain one.
 */
@FunctionalInterface
public interface SystemHolderResolverSource {

    /**
     * Opens one rebuild's or batch's holder resolver over its reading of the sector.
     *
     * @param pass          the walk's reading of the sector, under the grouping the build is folded
     *                      by
     * @param spotlitBlocId the bloc the build is spotlighting, or null off filter
     * @return the walk's resolver
     */
    SystemHolderResolver openResolverOver(HolderPass pass, String spotlitBlocId);
}
