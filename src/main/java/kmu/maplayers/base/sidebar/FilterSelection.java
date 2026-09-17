package kmu.maplayers.base.sidebar;

import kmlib.starsector.memory.AddressedMemoryString;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import java.util.function.Predicate;

/**
 * The single ID a sidebar filter currently selects, or none - held per {@link SelectionSlot}, so each
 * mod, screen and scope keeps its own choice and moving between any of them neither clears nor
 * cross-reads another's. While an ID is selected the layer reading it draws that one standing out and the rest
 * receded; with none selected it renders exactly as it does un-filtered. This holds only the choice - a
 * bare ID - and the plumbing to persist, clear, and invalidate on it; what the ID names, and how it
 * resolves into anything drawn, is the reading layer's concern, not this class's.
 *
 * <p>Partitioned because no axis of a slot is optional: one scope's ID read under another scope is
 * meaningless, a spotlight is a pick the player made on one panel rather than a statement about the
 * sector, and one mod's picker has no business reading a pick made on another's. The class stays
 * ignorant of what any of the three is - it hands a slot its own key and takes back the key that slot
 * composes, never a registry of scopes, a roster of screens or the name of the mod asking.
 *
 * <p>Sidebar-only like the sort and column stores: the selection is driven solely by a sidebar
 * picker, never a settings-screen control, so it persists in sector memory (each save keeps its own
 * choice and it survives reload) rather than as a LunaLib field - a LunaLib field would render on a
 * settings tab and an unregistered key would not round-trip. Key absence is the no-filter state,
 * which is also the default a fresh save holds, so no off sentinel is needed: a stored ID means
 * "filtering to that id", no key means "not filtering".
 *
 * <p>Unlike those stores, a pick or a clear raises {@link MapLayerCommonRefreshSignal#FILTER} so
 * the reading layer repaints live, standing in for the {@code settingsRevision} bump these
 * sidebar-only changes never make. The bump is gated on the store actually landing, so a call
 * before the sector exists (or a clear with nothing selected) neither writes nor repaints. The board
 * it raises on arrives with the write, since a picker belongs to the sector it was listed for.
 */
public final class FilterSelection {

    // The selected ID, under the key a slot composes its namespace, scope and screen onto. Layer-neutral
    // because every map layer's picker stores through this one class - a key naming one layer would have
    // every other layer persisting its selection under that layer's. Mod-neutral for the reason
    // MapLayerStoreNamespace exists: which mod's picker this is arrives with the slot, so this class
    // spells only what the store itself is. A key is a save-serialised identity, not a description of
    // where the class lives: a renamed key reads as absent and silently drops every existing save's
    // filter choice back to none, so it stays frozen in this spelling.
    private static final AddressedMemoryString SELECTED_ID =
        new AddressedMemoryString("filter_bloc_");

    private FilterSelection() {
    }

    /**
     * @param slot the mod, screen and scope whose selection is read
     * @return that slot's selected ID, or null when it has no selection (the un-filtered state) - also
     *         null before a save exists, since there is nothing to have picked yet
     */
    public static String getSelectedIdOf(SelectionSlot slot) {
        return SELECTED_ID.get(slot);
    }

    /**
     * Selects an ID in one slot, persisting the choice in this save and raising the filter signal so
     * the pick shows at once. A no-op before the sector exists, since there is no save to write into
     * and nothing painting to repaint.
     *
     * @param slot       the mod, screen and scope the pick belongs to
     * @param selectedId the stable ID to filter to
     * @param board      the refresh board of the sector whose picker made the pick, raised on so
     *                   that sector's overlay repaints
     */
    public static void selectId(
            SelectionSlot slot,
            String selectedId,
            MapLayerRefreshBoard board) {

        if (SELECTED_ID.set(slot, selectedId)) {
            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);
        }
    }

    /**
     * Clears one slot's filter, dropping its stored choice and raising the filter signal so the reading
     * layer returns to its un-filtered look at once. A no-op before the sector exists, or when that
     * slot had no ID selected - nothing to unset and nothing to repaint.
     *
     * @param slot  the mod, screen and scope whose filter is cleared
     * @param board the refresh board of the sector whose picker made the clear, raised on so that
     *              sector's overlay repaints
     */
    public static void clearSelection(SelectionSlot slot, MapLayerRefreshBoard board) {
        if (SELECTED_ID.clear(slot)) {
            board.requestRefresh(MapLayerCommonRefreshSignal.FILTER);
        }
    }

    /**
     * Clears one slot's filter when its stored ID is no longer selectable - the self-heal for a save
     * whose selection stopped being on offer between sessions, so a dangling ID never filters to
     * something that is no longer there. A no-op when the slot has no stored ID or the stored ID is
     * still selectable. Clears without a refresh request, so it is safe to run on load before the
     * reading layer paints and on a scope switch before the switched-in scope repaints.
     *
     * <p>Heals the one slot it is named for. A caller healing a scope every screen holds - which is
     * every scope, since the screens offer the same layers - runs it once per screen, because an ID
     * that lapsed lapsed for both panels while only the one being looked at would otherwise clear.
     *
     * @param slot         the mod, screen and scope whose selection is healed
     * @param isSelectable reports whether a stored ID is still selectable in that scope
     */
    public static void healStaleSelection(SelectionSlot slot, Predicate<String> isSelectable) {

        String selectedId = SELECTED_ID.get(slot);
        if (selectedId != null && !isSelectable.test(selectedId)) {
            SELECTED_ID.clear(slot);
        }
    }
}
