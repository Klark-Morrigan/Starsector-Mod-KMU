package kmu.maplayers.base.sidebar;

import kmlib.starsector.memory.SectorMemoryString;

/**
 * How many columns a sidebar picker lays its list across, persisted per save. Like
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
    
    // One shared slot rather than one per scope: the count is a layout preference over a list, not a
    // statement about what the list holds, so it means the same thing under every picker. The key is
    // layer-neutral because every map layer's picker stores through this one class - a key naming one
    // layer would have every other layer persisting its column count under that layer's key. A key is
    // a save-serialised identity, not a description of where the class lives: a renamed key reads as
    // absent and silently resets every existing save's column choice back to the default, so it stays
    // frozen in this spelling. Absent until the player first picks a count, which the read reports as
    // null for the column choice to default.
    private static final SectorMemoryString selectedColumnCount =
        new SectorMemoryString("$kmu_map_list_columns");

    private ColumnSelection() {
    }

    /**
     * @return the stored column-count key, or null when none is stored (a fresh save, or a read before
     *         the sector exists) - the caller resolves null to the default column count
     */
    public static String getColumnCountKey() {
        return selectedColumnCount.get();
    }

    /**
     * Persists the chosen column count's key in this save. A no-op before the sector exists, since
     * there is no save to write into yet. No repaint follows: the picker list re-wraps on the next
     * per-frame body build, and nothing on the map depends on the column count.
     *
     * @param columnCountKey the save-stable key of the column count to lay the list out in
     */
    public static void selectColumnCount(String columnCountKey) {
        selectedColumnCount.set(columnCountKey);
    }
}
