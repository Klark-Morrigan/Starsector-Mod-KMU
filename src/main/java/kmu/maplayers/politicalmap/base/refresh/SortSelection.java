package kmu.maplayers.politicalmap.base.refresh;

import kmlib.starsector.memory.SectorMemoryString;

/**
 * The metric the political-map filter picker currently ranks its blocs by and the direction it runs
 * in, persisted per save. Like {@link FilterSelection} it holds only the raw stored values - a bare
 * mode key and a bare direction key - and the plumbing to read and write them; what a key means (which
 * comparator or direction it maps to, what it falls back to when unset) is the sort mode's and sort
 * direction's concern, not this class's, so this stays a leaf that names no sort type. The two are
 * stored apart because they change apart: switching mode rewrites both (a new mode resets to its
 * default direction), while flipping rewrites only the direction.
 *
 * <p>Sidebar-only like the filter selection and the recede toggles: the mode is driven solely by the
 * picker's sort selector, never a settings-screen control, so it persists in sector memory (each save
 * keeps its own choice and it survives reload) rather than as a LunaLib field - a LunaLib field would
 * render on a settings tab and an unregistered key would not round-trip.
 *
 * <p>Unlike the filter selection this fires no refresh: the sort only reorders the sidebar picker
 * list, which the tab rebuilds from live state every frame, so the next frame re-reads the stored key
 * and re-sorts on its own. Nothing on the map changes, so there is no overlay repaint to request.
 */
public final class SortSelection {
    // Save-serialised key of the chosen sort mode; frozen once shipped, since renaming it silently
    // resets every existing save's sort choice back to the default. Absent until the player first
    // picks a mode, which the read reports as null for the sort mode to default.
    private static final SectorMemoryString selectedSortMode =
            new SectorMemoryString("$kmu_political_sort_mode");

    // Save-serialised key of the chosen sort direction; frozen once shipped for the same reason.
    // Absent until the player first flips a direction (or picks a mode), which the read reports as
    // null for the caller to resolve to the active mode's default direction.
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
