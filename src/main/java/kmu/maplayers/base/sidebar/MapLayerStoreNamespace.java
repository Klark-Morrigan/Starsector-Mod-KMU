package kmu.maplayers.base.sidebar;

/**
 * Which mod a shared sidebar store's keys belong to - the prefix {@link FilterSelection},
 * {@link SortSelection} and {@link ColumnSelection} compose their own suffix under, so the three
 * stores hold the plumbing while nothing about the mod holding the answer is written into them.
 *
 * <p>Without it the three stores are shared in the wrong sense: they already part one view from
 * another by scope id and one panel from another by screen, but both axes sit under a prefix spelled
 * inside the class, so two mods picking the scope string {@code "factions"} write the same key and a
 * second mod's column pick moves the first's column count. The mod is the axis that was missing.
 *
 * <p>One value rather than a prefix per store, because a caller passing three prefixes can pass two of
 * its own and one of somebody else's - a state nothing downstream can detect, surfacing as one mod's
 * picker silently moving another's sort direction. Passed once, the three cannot disagree.
 *
 * <p><b>It has no default and cannot be derived.</b> Standing one in from a layer's id would part one
 * mod's two layers into two namespaces, which is the partitioning the scope id already does correctly
 * and would then be doing twice; standing in a fixed fallback would put every consumer that forgot to
 * name itself in one shared namespace, which is the collision this exists to remove.
 *
 * <p>The prefix carries its own separator - nothing is appended between it and a store's suffix. A key
 * is a save-serialised identity, so what a namespace composes has to be exactly what the holding mod
 * already ships rather than what a rule here would compose it into.
 *
 * @param keyPrefix the holding mod's own sector-memory key prefix, ending in whatever separates it
 *                  from what follows; never blank
 */
public record MapLayerStoreNamespace(String keyPrefix) {

    public MapLayerStoreNamespace {
        // A blank prefix puts every mod that has one in the un-namespaced keys the frozen ones already
        // occupy, so a consumer would read and write the host's own picks rather than its own.
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalArgumentException("A map-layer store namespace must not be blank");
        }
    }

    /**
     * @param storeKeySuffix the store's own key, carrying neither this namespace nor any address
     *                       segment
     * @return that store's key inside this namespace, which the reading address then partitions
     */
    public String resolveNamespacedKey(String storeKeySuffix) {
        return keyPrefix + storeKeySuffix;
    }
}
