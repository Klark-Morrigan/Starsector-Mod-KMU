package kmu.maplayers.base.sidebar;

import kmlib.starsector.memory.SectorMemoryString;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefresh;

import java.util.function.Predicate;

/**
 * The single id a sidebar filter currently selects, or none - held per scope, so each scope keeps
 * its own choice and switching scopes neither clears nor cross-reads another's. While an id is
 * selected the layer reading it draws that one standing out and the rest receded; with none
 * selected it renders exactly as it does un-filtered. This holds only the choice - a bare id - and
 * the plumbing to persist, clear, and invalidate on it; what the id names, and how it resolves into
 * anything drawn, is the reading layer's concern, not this class's.
 *
 * <p>Per scope because an id is scope-specific: one scope's id read under another scope is
 * meaningless, so each scope stores its choice under its own key rather than one shared slot every
 * scope would have to clear on a switch. The class stays ignorant of what a scope is - it
 * partitions storage by an opaque id the caller supplies, never a registry of scopes.
 *
 * <p>Sidebar-only like the sort and column stores: the selection is driven solely by a sidebar
 * picker, never a settings-screen control, so it persists in sector memory (each save keeps its own
 * choice and it survives reload) rather than as a LunaLib field - a LunaLib field would render on a
 * settings tab and an unregistered key would not round-trip. Key absence is the no-filter state,
 * which is also the default a fresh save holds, so no off sentinel is needed: a stored id means
 * "filtering to that id", no key means "not filtering".
 *
 * <p>Unlike those stores, a pick or a clear raises {@link MapLayerCommonRefreshSignal#FILTER} so
 * the reading layer repaints live, standing in for the {@code settingsRevision} bump these
 * sidebar-only changes never make. The bump is gated on the store actually landing, so a call
 * before the sector exists (or a clear with nothing selected) neither writes nor repaints.
 */
public final class FilterSelection {
    
    // Per-scope key prefix; the scope's own id is appended to give one save-serialised slot per
    // scope. The prefix reads as the political map's because this state shipped alongside it,
    // before the framework was carved out. A key is a save-serialised identity, not a description
    // of where the class lives: a renamed prefix reads as absent and silently drops every existing
    // save's filter choice back to none, so it stays frozen in its original spelling (as are the
    // scope ids appended to it).
    private static final String SELECTED_ID_KEY_PREFIX = "$kmu_political_filter_bloc_";

    private FilterSelection() {
    }

    /**
     * @param scopeId the scope whose slot is read
     * @return the scope's selected id, or null when it has no selection (the un-filtered state) -
     *         also null before a save exists, since there is nothing to have picked yet
     */
    public static String getSelectedIdOf(String scopeId) {
        return resolveSlot(scopeId).get();
    }

    /**
     * Selects an id in one scope, persisting the choice in this save and raising the filter signal
     * so the pick shows at once. A no-op before the sector exists, since there is no save to write
     * into and nothing painting to repaint.
     *
     * @param scopeId    the scope the pick belongs to
     * @param selectedId the stable id to filter to
     */
    public static void selectId(String scopeId, String selectedId) {
        if (resolveSlot(scopeId).set(selectedId)) {
            MapLayerRefresh.requestRefresh(MapLayerCommonRefreshSignal.FILTER);
        }
    }

    /**
     * Clears one scope's filter, dropping its stored choice and raising the filter signal so the
     * reading layer returns to its un-filtered look at once. A no-op before the sector exists, or
     * when the scope had no id selected - nothing to unset and nothing to repaint.
     *
     * @param scopeId the scope whose filter is cleared
     */
    public static void clearSelection(String scopeId) {
        if (resolveSlot(scopeId).clear()) {
            MapLayerRefresh.requestRefresh(MapLayerCommonRefreshSignal.FILTER);
        }
    }

    /**
     * Clears one scope's filter when its stored id is no longer selectable - the self-heal for a
     * save whose selection stopped being on offer between sessions, so a dangling id never filters
     * to something that is no longer there. A no-op when the scope has no stored id or the stored
     * id is still selectable. Clears without a refresh request, so it is safe to run on load before
     * the reading layer paints and on a scope switch before the switched-in scope repaints.
     *
     * @param scopeId      the scope whose slot is healed
     * @param isSelectable reports whether a stored id is still selectable in that scope
     */
    public static void healStaleSelection(String scopeId, Predicate<String> isSelectable) {
        var slot = resolveSlot(scopeId);
        String selectedId = slot.get();
        if (selectedId != null && !isSelectable.test(selectedId)) {
            slot.clear();
        }
    }

    // The sector-memory slot holding one scope's selection, keyed by the scope's id. A fresh
    // wrapper per call - the wrapper only holds its key, the value lives in sector memory - so no
    // per-scope instance has to be cached here.
    private static SectorMemoryString resolveSlot(String scopeId) {
        return new SectorMemoryString(SELECTED_ID_KEY_PREFIX + scopeId);
    }
}
