package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;

import kmu.maplayers.base.sidebar.FilterSelection;

import java.util.HashSet;

/**
 * Heals a loaded save's spotlight selection against the view that is active in it: the glue that binds
 * {@link FilterSelection#healStaleSelection}'s pure "clear if not selectable" rule to a concrete
 * source of which blocs are selectable now. {@link FilterSelection} stays ignorant of views (it takes
 * only a predicate); this supplies that predicate from the active view's {@link
 * PoliticalMapView#resolveBlocPicker}, so a faction removed or an alliance dissolved between
 * sessions clears the dangling filter instead of spotlighting a bloc no longer on the map.
 *
 * <p>Only heals while a view is selected. A filter may persist while the political map is toggled off,
 * and with no active view there is no grouping to judge which blocs are selectable - a faction id
 * would be meaningless under an alliance read and vice versa - so a persisted filter is left intact
 * until a view is up to validate it. It lives in {@code base} beside the view registry it reads,
 * since resolving "the active view and its selectable blocs" is a view concern the framework's
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
     * player's current dominance and visibility settings, the same gate the picker lists blocs under,
     * so a bloc is healed away exactly when it would no longer appear in the picker.
     */
    public static void healStaleSelectionAgainstActiveView() {
        var view = PoliticalMapViewRegistry.getSelectedView();
        if (view == null) {
            return;
        }
        var selectableBlocIds = new HashSet<String>();
        for (var bloc : view.resolveBlocPicker(Global.getSector()).items()) {
            // Read through the picker seam rather than the bloc identity: the heal matches on the id
            // alone, so it needs nothing a view's own metrics carry and stays valid for any of them.
            // That is also why the view's sort vocabulary is passed over - a heal ranks nothing.
            selectableBlocIds.add(bloc.itemId());
        }
        FilterSelection.healStaleSelection(view.getId(), selectableBlocIds::contains);
    }
}
