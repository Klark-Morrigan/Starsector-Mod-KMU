package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;

import kmu.maplayers.politicalmap.base.refresh.FilterSelection;

import java.util.HashSet;

/**
 * Heals a loaded save's spotlight selection against the view that is active in it: the glue that binds
 * {@link FilterSelection#healStaleSelection}'s pure "clear if not selectable" rule to a concrete
 * source of which blocs are selectable now. {@link FilterSelection} stays ignorant of views (it takes
 * only a predicate); this supplies that predicate from the active view's {@link
 * PoliticalMapView#resolveSelectableBlocs}, so a faction removed or an alliance dissolved between
 * sessions clears the dangling filter instead of spotlighting a bloc no longer on the map.
 *
 * <p>Only heals while a view is selected. A filter may persist while the political map is toggled off,
 * and with no active view there is no grouping to judge which blocs are selectable - a faction id
 * would be meaningless under an alliance read and vice versa - so a persisted filter is left intact
 * until a view is up to validate it. It lives in {@code base} beside the view registry it reads,
 * since resolving "the active view and its selectable blocs" is a view concern the refresh-package
 * {@link FilterSelection} deliberately does not carry.
 */
public final class FilterSelectionHeal {

    private FilterSelectionHeal() {
    }

    /**
     * Clears the active view's stored spotlight selection when that view no longer offers it, a no-op
     * when no view is selected or the stored bloc is still selectable. Runs on game load and on a view
     * switch, before the overlay repaints, so it clears without requesting a refresh - the load or the
     * switch already repaints, so there is nothing extra to invalidate. Reads the live sector and the
     * player's current dominance and dev-reveal settings, the same gate the picker lists blocs under,
     * so a bloc is healed away exactly when it would no longer appear in the picker.
     */
    public static void healStaleSelectionAgainstActiveView() {
        var view = PoliticalMapViewRegistry.getSelectedView();
        if (view == null) {
            return;
        }
        var selectableBlocIds = new HashSet<String>();
        for (var bloc : view.resolveSelectableBlocs(Global.getSector())) {
            selectableBlocIds.add(bloc.blocId());
        }
        FilterSelection.healStaleSelection(view.getId(), selectableBlocIds::contains);
    }

    /**
     * Carries a pre-per-view save's single shared spotlight into the slot of the view it was picked
     * under, then retires the old key. The view is the one active in the save, or - when the save was
     * made with the map off, so no view is attributable - the default view, so the choice is not lost.
     * Supplies that view to {@link FilterSelection#migrateLegacySharedSelection}, which stays ignorant
     * of the registry. A no-op once migrated, on a save that never held a selection, or before the
     * views are registered. Runs on game load, after the active view is settled and before the heal.
     */
    public static void migrateLegacySharedSelectionToActiveView() {
        var view = PoliticalMapViewRegistry.getSelectedView();
        if (view == null) {
            view = PoliticalMapViewRegistry.getDefaultView();
        }
        if (view == null) {
            return;
        }
        FilterSelection.migrateLegacySharedSelection(view.getId());
    }
}
