package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.BlocSortMode;
import kmu.maplayers.politicalmap.base.refresh.SortSelection;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * The picker's sort selector: a vertical radio with one row per {@link BlocSortMode}, the lit row the
 * mode the list is currently ranked by. Picking a row persists that mode through {@link SortSelection}
 * and the next per-frame body build reorders the bloc list under it, so the selector and the list
 * always agree on the active metric. A sort is always active (the list is always ordered somehow), so
 * unlike the bloc picker this radio is never deselectable - a mode stays lit until another is picked.
 *
 * <p>The rows are drawn in {@link BlocSortMode}'s own order, so the row index the click reports maps
 * straight back to a mode by position. The active mode is passed in rather than read here, so this
 * builds a pure function of the current mode and the write is the only static reach.
 */
public final class SortSelectorControl {

    private SortSelectorControl() {
    }

    /**
     * Builds the sort selector for the active mode: a vertical radio lit on that mode's row, each row
     * labelled with its mode's string, a click persisting the picked mode.
     *
     * @param activeMode the mode the list is currently ranked by, which this lights
     * @return the vertical, always-selected sort-selector radio
     */
    public static ControlSpec buildSelector(BlocSortMode activeMode) {
        var modes = List.of(BlocSortMode.values());
        var labels = new ArrayList<String>(modes.size());
        for (var mode : modes) {
            labels.add(KmuStrings.get(mode.labelKey()));
        }
        return ControlSpec.createVerticalRadio(labels, modes.indexOf(activeMode),
                cellIndex -> selectMode(modes, cellIndex), false);
    }

    // Persists the mode its clicked row names. Any index outside the mode rows is ignored, so a stray
    // hit changes nothing; a click on the lit row re-selects the same mode, which the store folds into
    // a harmless rewrite (direction flipping on a re-click is a later concern).
    private static void selectMode(List<BlocSortMode> modes, int cellIndex) {
        if (cellIndex < 0 || cellIndex >= modes.size()) {
            return;
        }
        SortSelection.selectSortMode(modes.get(cellIndex).persistenceKey());
    }
}
