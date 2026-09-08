package kmu.maplayers.base.sidebar;

import kmu.maplayers.base.layer.AddressedMemoryString;

/**
 * How many columns a sidebar picker lays its list across, persisted per save and per screen. Like
 * {@link SortSelection} it holds only the raw stored value - a bare column-count key - and the plumbing
 * to read and write it; what a key means (which column count it maps to, what it falls back to when
 * unset) is the column choice's concern, not this class's, so this stays a leaf that names no layout.
 *
 * <p>Sidebar-only state like the sort selection: the count is driven solely by a picker's columns
 * selector, never a settings-screen control, so it persists in sector memory (each save keeps its own
 * choice and it survives reload) rather than as a LunaLib field - a LunaLib field would render on a
 * settings tab and an unregistered key would not round-trip.
 *
 * <p>Like the sort selection this fires no refresh: the column count only re-wraps the picker list,
 * which the sidebar rebuilds from live state every frame, so the next frame re-reads the stored key and
 * re-lays the rows on its own. Nothing on the map changes, so there is no overlay repaint to request.
 */
public final class ColumnSelection {

    // One slot per mod per screen, shared by every scope on it, since a panel is a fixed width the
    // player laid the list out inside and the two panels are two widths. The scope is what this store
    // deliberately does not partition by; the mod is what it must, or a second mod's picker would
    // re-wrap the host's list. The key is layer-neutral and frozen for the reasons SortSelection's are.
    // Absent until the player first picks a count, which the read reports as null for the column choice
    // to default.
    private static final AddressedMemoryString SELECTED_COLUMN_COUNT =
        new AddressedMemoryString("list_columns");

    private ColumnSelection() {
    }

    /**
     * @param slot the mod and screen whose count is read
     * @return that slot's stored column-count key, or null when none is stored (a fresh save, or a
     *         read before the sector exists) - the caller resolves null to the default column count
     */
    public static String getColumnCountKey(ScreenSelectionSlot slot) {
        return SELECTED_COLUMN_COUNT.get(slot);
    }

    /**
     * Persists the chosen column count's key in this save, against the mod and screen it was picked
     * on. A no-op before the sector exists, since there is no save to write into yet. No repaint
     * follows: the picker list re-wraps on the next per-frame body build, and nothing on the map
     * depends on the column count.
     *
     * @param slot           the mod and screen whose panel made the pick
     * @param columnCountKey the save-stable key of the column count to lay the list out in
     */
    public static void selectColumnCount(ScreenSelectionSlot slot, String columnCountKey) {
        SELECTED_COLUMN_COUNT.set(slot, columnCountKey);
    }
}
