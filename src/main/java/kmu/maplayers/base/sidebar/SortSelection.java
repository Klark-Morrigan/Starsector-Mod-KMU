package kmu.maplayers.base.sidebar;

import kmu.maplayers.base.layer.AddressedMemoryString;

/**
 * The metric a sidebar picker list currently ranks its rows by and the direction that ranking runs in
 * - held per {@link SelectionSlot}, so each screen and scope keeps its own answer and moving between
 * either neither resets nor cross-reads another's. It holds only the raw stored values - a bare mode
 * key and a bare direction key - and the plumbing to read and write them; what a key means (which
 * comparator or direction it maps to, what it falls back to when unset) is the sort mode's and sort
 * direction's concern, not this class's, so this stays a leaf that names no sort type. The two are
 * stored apart because they change apart: switching mode rewrites both (a new mode resets to its
 * default direction), while flipping rewrites only the direction.
 *
 * <p>Partitioned exactly as {@link FilterSelection} is, and for the matching reasons: scopes rank their
 * rows by different vocabularies, so one scope's stored key read under another resolves against nothing
 * and makes every switch look like a reset, and a ranking is a pick made on one panel rather than a
 * fact about the list.
 *
 * <p>Sidebar-only state: the mode is driven solely by a picker's sort selector, never a settings-screen
 * control, so it persists in sector memory (each save keeps its own choice and it survives reload)
 * rather than as a LunaLib field - a LunaLib field would render on a settings tab and an unregistered
 * key would not round-trip.
 *
 * <p>No refresh follows a write: the sort only reorders the picker list, which the sidebar rebuilds
 * from live state every frame, so the next frame re-reads the stored key and re-sorts on its own.
 * Nothing on the map changes, so there is no overlay repaint to request.
 */
public final class SortSelection {

    // The two halves' keys, which a slot composes its namespace, scope and screen onto. Both are
    // layer-neutral because every map layer's picker stores through this one class - a key naming one
    // layer would have every other layer persisting its sort under that layer's - and mod-neutral for
    // the reason MapLayerStoreNamespace exists: which mod's picker this is arrives with the slot. A key
    // is a save-serialised identity, not a description of where the class lives: a renamed key reads as
    // absent and silently resets every existing save's sort choice back to the default, so both stay
    // frozen in this spelling.
    //
    // The chosen sort mode. Absent until the player first picks a mode in that slot, which the read
    // reports as null for the sort mode to default.
    private static final AddressedMemoryString SELECTED_SORT_MODE =
        new AddressedMemoryString("sort_mode_");

    // The chosen sort direction. Absent until the player first flips a direction (or picks a mode) in
    // that slot, which the read reports as null for the caller to resolve to the active mode's default
    // direction.
    private static final AddressedMemoryString SELECTED_SORT_DIRECTION =
        new AddressedMemoryString("sort_direction_");

    private SortSelection() {
    }

    /**
     * @param slot the mod, screen and scope whose sort is read
     * @return that slot's stored sort-mode key, or null when none is stored (a fresh save, a slot
     *         never sorted in, or a read before the sector exists) - the caller resolves null to the
     *         default mode
     */
    public static String getSortModeKeyOf(SelectionSlot slot) {
        return SELECTED_SORT_MODE.get(slot);
    }

    /**
     * @param slot the mod, screen and scope whose sort is read
     * @return that slot's stored sort-direction key, or null when none is stored (a slot whose sort
     *         was never flipped) - the caller resolves null to the active mode's default direction
     */
    public static String getSortDirectionKeyOf(SelectionSlot slot) {
        return SELECTED_SORT_DIRECTION.get(slot);
    }

    /**
     * Persists the chosen sort mode's key in one slot, so the choice is remembered against that screen
     * and scope alone. A no-op before the sector exists, since there is no save to write into yet. No
     * repaint follows: the picker list re-sorts on the next per-frame body build, and nothing on the
     * map depends on the sort.
     *
     * @param slot    the mod, screen and scope the pick belongs to
     * @param modeKey the save-stable key of the mode to sort by
     */
    public static void selectSortMode(SelectionSlot slot, String modeKey) {
        SELECTED_SORT_MODE.set(slot, modeKey);
    }

    /**
     * Persists the chosen sort direction's key in one slot. A no-op before the sector exists. Like the
     * mode write it fires no repaint - only the picker list re-orders, on the next body build.
     *
     * @param slot         the mod, screen and scope the flip belongs to
     * @param directionKey the save-stable key of the direction to sort in
     */
    public static void selectSortDirection(SelectionSlot slot, String directionKey) {
        SELECTED_SORT_DIRECTION.set(slot, directionKey);
    }
}
