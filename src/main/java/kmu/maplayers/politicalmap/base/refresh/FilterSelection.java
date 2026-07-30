package kmu.maplayers.politicalmap.base.refresh;

import kmlib.starsector.memory.SectorMemoryString;

import kmu.maplayers.base.refresh.PoliticalMapRefresh;

import java.util.function.Predicate;

/**
 * The single bloc the political-map filter spotlights in one view, or none - held per view, so each
 * view keeps its own choice and switching views neither clears nor cross-reads the other's. The
 * filter is a mode orthogonal to the active view: while a bloc is selected the overlay draws that
 * bloc standing out and the rest of the sector receded; with none selected the map renders exactly as
 * it does un-filtered. This holds only the choice - a bare bloc id (a faction id under the factions
 * view, an alliance id under the alliances view) - and the plumbing to persist, clear, and invalidate
 * on it; how that id resolves into presence-aware territory is the resolver's concern, not this
 * class's.
 *
 * <p>Per view because the id is view-specific: a faction id read under the alliance grouping is
 * meaningless, so each view stores its choice under its own key rather than one shared slot every
 * view would have to clear on a switch. The class stays ignorant of what a view is - it partitions
 * storage by an opaque id the caller supplies (the active view's id), never the view registry.
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
 * make. The bump is gated on the store actually landing, so a call before the sector exists (or a
 * clear with nothing selected) neither writes nor repaints.
 */
public final class FilterSelection {
    // Per-view key prefix; the view's own id is appended to give one save-serialised slot per view.
    // Frozen once shipped (view ids are themselves frozen), since renaming it silently drops every
    // existing save's filter choice back to none.
    private static final String SELECTED_BLOC_KEY_PREFIX = "$kmu_political_filter_bloc_";

    // The pre-per-view single slot every view once shared. Read only by the one-time load migration
    // that moves its value into a view's slot, then retires it; frozen so that migration keeps
    // finding it on an un-migrated save.
    private static final SectorMemoryString legacySharedSelection =
            new SectorMemoryString("$kmu_political_filter_bloc");

    private FilterSelection() {
    }

    /**
     * @param viewId the view whose slot is read (the active view's id)
     * @return the view's spotlighted bloc id, or null when it has no selection (the un-filtered
     *         state) - also null before a save exists, since there is nothing to have picked yet
     */
    public static String getSelectedBlocId(String viewId) {
        return resolveSlot(viewId).get();
    }

    /**
     * Spotlights a bloc in one view, persisting the choice in this save and repainting the overlay so
     * the pick shows at once. A no-op before the sector exists, since there is no save to write into
     * and nothing painting to repaint.
     *
     * @param viewId the view the pick belongs to (the active view's id)
     * @param blocId the stable id of the bloc to filter to (a faction id or an alliance id)
     */
    public static void selectBloc(String viewId, String blocId) {
        if (resolveSlot(viewId).set(blocId)) {
            PoliticalMapRefresh.requestFilterRefresh();
        }
    }

    /**
     * Clears one view's filter, dropping its stored choice and repainting the overlay so the sector
     * returns to its un-filtered look at once. A no-op before the sector exists, or when the view had
     * no bloc selected - nothing to unset and nothing to repaint.
     *
     * @param viewId the view whose filter is cleared (the active view's id)
     */
    public static void clearSelection(String viewId) {
        if (resolveSlot(viewId).clear()) {
            PoliticalMapRefresh.requestFilterRefresh();
        }
    }

    /**
     * Clears one view's filter when its stored bloc is no longer selectable - the self-heal for a save
     * whose spotlighted faction was removed or whose alliance dissolved, so a dangling id never
     * spotlights a bloc that is not on the map. A no-op when the view has no stored bloc or the stored
     * id is still selectable. Clears without a refresh request, so it is safe to run on load before
     * the overlay paints and on a view switch before the switched-in view repaints.
     *
     * @param viewId            the view whose slot is healed (the active view's id)
     * @param isBlocSelectable  reports whether a stored bloc id is still a selectable bloc under that
     *                          view's visibility gate
     */
    public static void healStaleSelection(String viewId, Predicate<String> isBlocSelectable) {
        var slot = resolveSlot(viewId);
        String selectedBlocId = slot.get();
        if (selectedBlocId != null && !isBlocSelectable.test(selectedBlocId)) {
            slot.clear();
        }
    }

    /**
     * Carries a pre-per-view save's single shared selection into one view's slot, then retires the old
     * key, so a save made before the split keeps its spotlight under the view it was picked in. A
     * no-op once migrated, or on a save that never held a selection. Runs on load before the overlay
     * paints, so it moves the value without requesting a refresh.
     *
     * @param viewId the view to receive the legacy selection - the view active in the save, or the
     *               default view when the save was made with the map off (no active view to attribute
     *               it to)
     */
    public static void migrateLegacySharedSelection(String viewId) {
        if (!legacySharedSelection.isSet()) {
            return;
        }
        resolveSlot(viewId).set(legacySharedSelection.get());
        legacySharedSelection.clear();
    }

    // The sector-memory slot holding one view's selection, keyed by the view's id. A fresh wrapper
    // per call - the wrapper only holds its key, the value lives in sector memory - so no per-view
    // instance has to be cached here.
    private static SectorMemoryString resolveSlot(String viewId) {
        return new SectorMemoryString(SELECTED_BLOC_KEY_PREFIX + viewId);
    }
}
