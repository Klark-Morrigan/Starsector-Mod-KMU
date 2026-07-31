package kmu.maplayers.base.sidebar;

import kmlib.starsector.memory.SectorMemoryString;

/**
 * The metric a sidebar picker list currently ranks its rows by and the direction that ranking runs in,
 * persisted per save. It holds only the raw stored values - a bare mode key and a bare direction key -
 * and the plumbing to read and write them; what a key means (which comparator or direction it maps to,
 * what it falls back to when unset) is the sort mode's and sort direction's concern, not this class's,
 * so this stays a leaf that names no sort type. The two are stored apart because they change apart:
 * switching mode rewrites both (a new mode resets to its default direction), while flipping rewrites
 * only the direction.
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
    
    // The two keys read as the political map's because this state shipped alongside it, before the
    // framework was carved out. A key is a save-serialised identity, not a description of where the
    // class lives: a renamed key reads as absent and silently resets every existing save's sort choice
    // back to the default, so both stay frozen in their original spelling.
    //
    // Save-serialised key of the chosen sort mode. Absent until the player first picks a mode, which
    // the read reports as null for the sort mode to default.
    private static final SectorMemoryString selectedSortMode =
        new SectorMemoryString("$kmu_political_sort_mode");

    // Save-serialised key of the chosen sort direction. Absent until the player first flips a
    // direction (or picks a mode), which the read reports as null for the caller to resolve to the
    // active mode's default direction.
    private static final SectorMemoryString selectedSortDirection =
        new SectorMemoryString("$kmu_political_sort_direction");

    private SortSelection() {
    }

    /**
     * @return the stored sort-mode key, or null when none is stored (a fresh save, or a read before
     *         the sector exists) - the caller resolves null to the default mode
     */
    public static String getSortModeKey() {
        return selectedSortMode.get();
    }

    /**
     * @return the stored sort-direction key, or null when none is stored (a save from before the
     *         direction existed, or one that never flipped) - the caller resolves null to the active
     *         mode's default direction
     */
    public static String getSortDirectionKey() {
        return selectedSortDirection.get();
    }

    /**
     * Persists the chosen sort mode's key in this save. A no-op before the sector exists, since there
     * is no save to write into yet. No repaint follows: the picker list re-sorts on the next per-frame
     * body build, and nothing on the map depends on the sort.
     *
     * @param modeKey the save-stable key of the mode to sort by
     */
    public static void selectSortMode(String modeKey) {
        selectedSortMode.set(modeKey);
    }

    /**
     * Persists the chosen sort direction's key in this save. A no-op before the sector exists. Like
     * the mode write it fires no repaint - only the picker list re-orders, on the next body build.
     *
     * @param directionKey the save-stable key of the direction to sort in
     */
    public static void selectSortDirection(String directionKey) {
        selectedSortDirection.set(directionKey);
    }
}
