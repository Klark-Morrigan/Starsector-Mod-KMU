package kmu.maplayers.base.sidebar;

import kmlib.starsector.memory.SectorMemoryString;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import java.util.function.Predicate;

/**
 * The single id a sidebar filter currently selects, or none - held per screen and per scope, so each
 * pair keeps its own choice and moving between either neither clears nor cross-reads another's.
 * While an id is selected the layer reading it draws that one standing out and the rest receded;
 * with none selected it renders exactly as it does un-filtered. This holds only the choice - a bare
 * id - and the plumbing to persist, clear, and invalidate on it; what the id names, and how it
 * resolves into anything drawn, is the reading layer's concern, not this class's.
 *
 * <p>Per scope because an id is scope-specific: one scope's id read under another scope is
 * meaningless, so each scope stores its choice under its own key rather than one shared slot every
 * scope would have to clear on a switch. The class stays ignorant of what a scope is - it
 * partitions storage by an opaque id the caller supplies, never a registry of scopes.
 *
 * <p>Per screen for a different reason: a spotlight is a pick the player made on one panel, and the
 * two panels are two places to be looking at the same sector. The screen arrives as the
 * {@link ScreenMemoryScope} the calling panel was built under and composes last into the key, so the
 * two axes are one slot each rather than one shared between them - the same partitioning, over the
 * axis the caller does not name.
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
 * before the sector exists (or a clear with nothing selected) neither writes nor repaints. The board
 * it raises on arrives with the write, since a picker belongs to the sector it was listed for.
 */
public final class FilterSelection {

    // Per-scope key prefix; the scope's own id is appended, and the screen's segment after that, to
    // give one save-serialised slot per screen per scope. The prefix is layer-neutral because every
    // map layer's picker stores through this one class - a prefix naming one layer would have every
    // other layer persisting its selection under that layer's key. A key is a save-serialised
    // identity, not a description of where the class lives: a renamed prefix reads as absent and
    // silently drops every existing save's filter choice back to none, so it stays frozen in this
    // spelling (as are the scope ids appended to it).
    private static final String SELECTED_ID_KEY_PREFIX = "$kmu_map_filter_bloc_";

    private FilterSelection() {
    }

    /**
     * @param memoryScope the screen whose slot is read
     * @param scopeId     the scope whose slot is read
     * @return that screen's selected id in that scope, or null when it has no selection (the
     *         un-filtered state) - also null before a save exists, since there is nothing to have
     *         picked yet
     */
    public static String getSelectedIdOf(ScreenMemoryScope memoryScope, String scopeId) {
        return resolveSlot(memoryScope, scopeId).get();
    }

    /**
     * Selects an id in one screen's scope, persisting the choice in this save and raising the filter
     * signal so the pick shows at once. A no-op before the sector exists, since there is no save to
     * write into and nothing painting to repaint.
     *
     * @param memoryScope the screen whose panel made the pick, which is what it is filed under
     * @param scopeId     the scope the pick belongs to
     * @param selectedId  the stable id to filter to
     * @param board       the refresh board of the sector whose picker made the pick, raised on so
     *                    that sector's overlay repaints
     */
    public static void selectId(
            ScreenMemoryScope memoryScope,
            String scopeId,
            String selectedId,
            MapLayerRefreshBoard board) {

        if (resolveSlot(memoryScope, scopeId).set(selectedId)) {
            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);
        }
    }

    /**
     * Clears one screen's filter in one scope, dropping its stored choice and raising the filter
     * signal so the reading layer returns to its un-filtered look at once. A no-op before the sector
     * exists, or when that pair had no id selected - nothing to unset and nothing to repaint.
     *
     * @param memoryScope the screen whose panel made the clear
     * @param scopeId     the scope whose filter is cleared
     * @param board       the refresh board of the sector whose picker made the clear, raised on so
     *                    that sector's overlay repaints
     */
    public static void clearSelection(
            ScreenMemoryScope memoryScope,
            String scopeId,
            MapLayerRefreshBoard board) {

        if (resolveSlot(memoryScope, scopeId).clear()) {
            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);
        }
    }

    /**
     * Clears one screen's filter in one scope when its stored id is no longer selectable - the
     * self-heal for a save whose selection stopped being on offer between sessions, so a dangling id
     * never filters to something that is no longer there. A no-op when that pair has no stored id or
     * the stored id is still selectable. Clears without a refresh request, so it is safe to run on
     * load before the reading layer paints and on a scope switch before the switched-in scope
     * repaints.
     *
     * <p>Heals the one screen it is named for. A caller healing a scope every screen holds - which
     * is every scope, since the screens offer the same layers - runs it once per screen, because a
     * bloc that lapsed lapsed for both panels while only the one being looked at would otherwise
     * clear.
     *
     * @param memoryScope  the screen whose slot is healed
     * @param scopeId      the scope whose slot is healed
     * @param isSelectable reports whether a stored id is still selectable in that scope
     */
    public static void healStaleSelection(
            ScreenMemoryScope memoryScope,
            String scopeId,
            Predicate<String> isSelectable) {

        var slot = resolveSlot(memoryScope, scopeId);
        String selectedId = slot.get();
        if (selectedId != null && !isSelectable.test(selectedId)) {
            slot.clear();
        }
    }

    // The sector-memory slot holding one screen's selection in one scope, keyed by the scope's id
    // under the screen's segment. A fresh wrapper per call - the wrapper only holds its key, the
    // value lives in sector memory - so no per-pair instance has to be cached here.
    private static SectorMemoryString resolveSlot(ScreenMemoryScope memoryScope, String scopeId) {
        return new SectorMemoryString(memoryScope.resolveKeyFor(SELECTED_ID_KEY_PREFIX + scopeId));
    }
}
