package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.campaign.SectorAPI;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.function.Supplier;

/**
 * Memoises one picker's selectable-item list so the per-frame sidebar draw reads a cache instead of
 * re-resolving it. A body build runs twice a frame - the render and the hit-test passes - and a
 * picker list is typically a full pass over whatever the layer scores its items from, so without a
 * memo the sidebar would re-run that pass several times a frame. The caller says which revision the
 * list depends on; the list is rebuilt only when that revision moves and the per-frame path is
 * otherwise an int compare.
 *
 * <p>Keyed on the sector identity as well, so a save reloaded in the same session - a fresh sector
 * whose revision happens to match the last - recomputes against the loaded state rather than serving
 * the previous save's items. The held sector reference is weak, so a cached sector never outlives its
 * unload.
 *
 * <p>What belongs in the revision is the caller's judgement, and it is the whole of the invalidation
 * contract: whatever the resolved list reads and can change without moving the revision will lag
 * until something else forces a recompute. A filter selection deliberately does not belong in it -
 * a picker list is which items are selectable, not which one is lit - so a pick or a clear moves the
 * lit row without invalidating the list.
 *
 * @param <T> the item type the memoised list holds
 */
public final class SelectableItemCache<T> {

    // The sector the memo was built against, held weakly so a cached sector never outlives its
    // unload. Starts empty so the first resolve always recomputes.
    private WeakReference<SectorAPI> cachedSector = new WeakReference<>(null);
    private String cachedScopeId;
    private int cachedRevision;
    private List<T> cachedItems = List.of();

    /**
     * The scope's selectable items, rebuilt through {@code resolveFreshItems} only when the sector,
     * the scope, or the revision has changed since the last call, and served from the memo otherwise.
     *
     * @param sector            the sector the list is read against; part of the key, never
     *                          dereferenced here, so a null sector is a key like any other
     * @param scopeId           the scope the list belongs to, so a switch between scopes recomputes
     *                          rather than serving the previous scope's items
     * @param revision          the caller's revision of everything the list depends on; a moved value
     *                          is what forces the rebuild
     * @param resolveFreshItems walks the list from scratch, called only on a miss
     * @return the memoised items; the same list instance while nothing the key holds moves
     */
    public List<T> resolveItems(
            SectorAPI sector,
            String scopeId,
            int revision,
            Supplier<List<T>> resolveFreshItems) {

        if (sector != cachedSector.get()
                || !scopeId.equals(cachedScopeId)
                || revision != cachedRevision) {

            cachedSector = new WeakReference<>(sector);
            cachedScopeId = scopeId;
            cachedRevision = revision;
            cachedItems = resolveFreshItems.get();
        }
        return cachedItems;
    }
}
