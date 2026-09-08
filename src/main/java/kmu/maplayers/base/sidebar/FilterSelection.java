package kmu.maplayers.base.sidebar;

import kmlib.starsector.memory.SectorMemoryString;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import java.util.function.Predicate;

/**
 * The single id a sidebar filter currently selects, or none - held per {@link SelectionSlot}, so each
 * screen and scope keeps its own choice and moving between either neither clears nor cross-reads
 * another's. While an id is selected the layer reading it draws that one standing out and the rest
 * receded; with none selected it renders exactly as it does un-filtered. This holds only the choice - a
 * bare id - and the plumbing to persist, clear, and invalidate on it; what the id names, and how it
 * resolves into anything drawn, is the reading layer's concern, not this class's.
 *
 * <p>Partitioned because neither axis of a slot is optional: one scope's id read under another scope is
 * meaningless, and a spotlight is a pick the player made on one panel rather than a statement about the
 * sector. The class stays ignorant of what either axis is - it is handed a slot and appends its own
 * prefix to it, never a registry of scopes or a roster of screens.
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

    // The key prefix a slot composes its own two axes onto. Layer-neutral because every map layer's
    // picker stores through this one class - a prefix naming one layer would have every other layer
    // persisting its selection under that layer's key. A key is a save-serialised identity, not a
    // description of where the class lives: a renamed prefix reads as absent and silently drops every
    // existing save's filter choice back to none, so it stays frozen in this spelling.
    private static final String SELECTED_ID_KEY_PREFIX = "$kmu_map_filter_bloc_";

    private FilterSelection() {
    }

    /**
     * @param slot the screen and scope whose selection is read
     * @return that slot's selected id, or null when it has no selection (the un-filtered state) - also
     *         null before a save exists, since there is nothing to have picked yet
     */
    public static String getSelectedIdOf(SelectionSlot slot) {
        return resolveMemorySlot(slot).get();
    }

    /**
     * Selects an id in one slot, persisting the choice in this save and raising the filter signal so
     * the pick shows at once. A no-op before the sector exists, since there is no save to write into
     * and nothing painting to repaint.
     *
     * @param slot       the screen and scope the pick belongs to
     * @param selectedId the stable id to filter to
     * @param board      the refresh board of the sector whose picker made the pick, raised on so
     *                   that sector's overlay repaints
     */
    public static void selectId(
            SelectionSlot slot,
            String selectedId,
            MapLayerRefreshBoard board) {

        if (resolveMemorySlot(slot).set(selectedId)) {
            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);
        }
    }

    /**
     * Clears one slot's filter, dropping its stored choice and raising the filter signal so the reading
     * layer returns to its un-filtered look at once. A no-op before the sector exists, or when that
     * slot had no id selected - nothing to unset and nothing to repaint.
     *
     * @param slot  the screen and scope whose filter is cleared
     * @param board the refresh board of the sector whose picker made the clear, raised on so that
     *              sector's overlay repaints
     */
    public static void clearSelection(SelectionSlot slot, MapLayerRefreshBoard board) {
        if (resolveMemorySlot(slot).clear()) {
            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);
        }
    }

    /**
     * Clears one slot's filter when its stored id is no longer selectable - the self-heal for a save
     * whose selection stopped being on offer between sessions, so a dangling id never filters to
     * something that is no longer there. A no-op when the slot has no stored id or the stored id is
     * still selectable. Clears without a refresh request, so it is safe to run on load before the
     * reading layer paints and on a scope switch before the switched-in scope repaints.
     *
     * <p>Heals the one slot it is named for. A caller healing a scope every screen holds - which is
     * every scope, since the screens offer the same layers - runs it once per screen, because an id
     * that lapsed lapsed for both panels while only the one being looked at would otherwise clear.
     *
     * @param slot         the screen and scope whose selection is healed
     * @param isSelectable reports whether a stored id is still selectable in that scope
     */
    public static void healStaleSelection(SelectionSlot slot, Predicate<String> isSelectable) {

        var memorySlot = resolveMemorySlot(slot);
        String selectedId = memorySlot.get();
        if (selectedId != null && !isSelectable.test(selectedId)) {
            memorySlot.clear();
        }
    }

    // The sector-memory slot holding one selection, at the key its screen and scope compose. A fresh
    // wrapper per call - the wrapper only holds its key, the value lives in sector memory - so no
    // per-slot instance has to be cached here.
    private static SectorMemoryString resolveMemorySlot(SelectionSlot slot) {
        return new SectorMemoryString(slot.resolveKeyFor(SELECTED_ID_KEY_PREFIX));
    }
}
