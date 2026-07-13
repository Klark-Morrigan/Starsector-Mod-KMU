package kmu.maplayers.politicalmap.base.refresh;

import kmlib.starsector.memory.SectorMemoryString;

/**
 * The metric the political-map filter picker currently ranks its blocs by, persisted per save. Like
 * {@link FilterSelection} it holds only the raw stored value - a bare mode key - and the plumbing to
 * read and write it; what the key means (which comparator it maps to, what it falls back to when
 * unset) is the sort mode's concern, not this class's, so this stays a leaf that names no sort type.
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
     * Persists the chosen sort mode's key in this save. A no-op before the sector exists, since there
     * is no save to write into yet. No repaint follows: the picker list re-sorts on the next per-frame
     * body build, and nothing on the map depends on the sort.
     *
     * @param modeKey the save-stable key of the mode to sort by
     */
    public static void selectSortMode(String modeKey) {
        selectedSortMode.set(modeKey);
    }
}
