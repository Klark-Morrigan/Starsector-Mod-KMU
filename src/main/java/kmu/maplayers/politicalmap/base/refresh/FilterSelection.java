package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import java.util.function.Predicate;

/**
 * The single bloc the political-map filter spotlights, or none. The filter is a mode orthogonal to
 * the active view: while a bloc is selected the overlay draws that bloc standing out and the rest of
 * the sector receded; with none selected the map renders exactly as it does un-filtered. This holds
 * only the choice - a bare bloc id (a faction id under the factions view, an alliance id under the
 * alliances view) - and the plumbing to persist, clear, and invalidate on it; how that id resolves
 * into presence-aware territory is the resolver's concern, not this class's.
 *
 * <p>Sidebar-only like the recede toggles: the selection is driven solely by the overlay's picker,
 * never a settings-screen control, so it persists in sector memory (each save keeps its own choice
 * and it survives reload) rather than as a LunaLib field - a LunaLib field would render on a settings
 * tab and an unregistered key would not round-trip. Key absence is the no-filter state, which is also
 * the default a fresh save holds, so no off sentinel is needed: a stored id means "filtering that
 * bloc", no key means "not filtering".
 *
 * <p>A pick or a clear bumps {@link PoliticalMapRefresh#requestFilterRefresh()} so the overlay
 * repaints live, standing in for the {@code settingsRevision} bump these sidebar-only changes never
 * make.
 */
public final class FilterSelection {
    // Save-serialised id of the spotlighted bloc; frozen once shipped, since renaming it silently
    // drops every existing save's filter choice back to none. Absent until the player first picks a
    // bloc, which the read reports as no filter.
    private static final String SELECTED_BLOC_KEY = "$kmu_political_filter_bloc";

    private FilterSelection() {
    }

    /**
     * @return the spotlighted bloc's stable id, or null when no bloc is selected (the un-filtered
     *         state) - also null before a save exists, since there is nothing to have picked yet
     */
    public static String getSelectedBlocId() {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null || !memory.contains(SELECTED_BLOC_KEY)) {
            return null;
        }
        return memory.getString(SELECTED_BLOC_KEY);
    }

    /**
     * @return whether a bloc is currently spotlighted - the gate the render pipeline reads to take
     *         the filter branch rather than the normal un-filtered pass
     */
    public static boolean hasSelection() {
        return getSelectedBlocId() != null;
    }

    /**
     * Spotlights a bloc, persisting the choice in this save and repainting the overlay so the pick
     * shows at once. A no-op before the sector exists, since there is no save to write into and
     * nothing painting to repaint.
     *
     * @param blocId the stable id of the bloc to filter to (a faction id or an alliance id)
     */
    public static void selectBloc(String blocId) {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null) {
            return;
        }
        memory.set(SELECTED_BLOC_KEY, blocId);
        PoliticalMapRefresh.requestFilterRefresh();
    }

    /**
     * Clears the filter, dropping the stored choice and repainting the overlay so the sector returns
     * to its un-filtered look at once. A no-op before the sector exists, or when no bloc was
     * selected - nothing to unset and nothing to repaint.
     */
    public static void clearSelection() {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null || !memory.contains(SELECTED_BLOC_KEY)) {
            return;
        }
        memory.unset(SELECTED_BLOC_KEY);
        PoliticalMapRefresh.requestFilterRefresh();
    }

    /**
     * Clears the filter when the stored bloc is no longer selectable - the self-heal for a save whose
     * spotlighted faction was removed or whose alliance dissolved between sessions, so a dangling id
     * never spotlights a bloc that is not on the map. A no-op when no bloc is stored or the stored id
     * is still selectable. The clear here mutates memory directly and skips the refresh request:
     * this runs on load before the overlay paints, so there is nothing yet to invalidate. Call once
     * on game load, once a source of the currently-selectable bloc ids exists.
     *
     * @param isBlocSelectable reports whether a stored bloc id is still a selectable bloc under the
     *                         active view's visibility gate
     */
    public static void healStaleSelection(Predicate<String> isBlocSelectable) {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null || !memory.contains(SELECTED_BLOC_KEY)) {
            return;
        }
        if (!isBlocSelectable.test(memory.getString(SELECTED_BLOC_KEY))) {
            memory.unset(SELECTED_BLOC_KEY);
        }
    }
}
