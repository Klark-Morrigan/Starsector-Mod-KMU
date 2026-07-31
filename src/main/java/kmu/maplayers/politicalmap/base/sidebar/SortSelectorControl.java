package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.controls.ReselectBehaviour;
import kmlib.starsector.ui.controls.TriangleDirection;

import kmu.maplayers.base.sidebar.SortDirection;
import kmu.maplayers.base.sidebar.SortSelection;
import kmu.maplayers.politicalmap.base.BlocSort;
import kmu.maplayers.politicalmap.base.BlocSortMode;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * The picker's sort selector: a vertical radio table with one row per {@link BlocSortMode}, the lit
 * row the mode the list is currently ranked by, and each row's trailing slot showing the direction
 * that mode would sort in. Picking a row persists that mode through {@link SortSelection} and the
 * next per-frame body build reorders the bloc list under it, so the selector and the list always
 * agree on the active metric and direction.
 *
 * <p>A sort is always active (the list is always ordered somehow), so unlike the bloc picker this
 * radio never clears to nothing. Instead it re-fires on a re-pick ({@link ReselectBehaviour#REFIRE}):
 * clicking the already-lit mode flips its direction, while clicking a different mode switches to it at
 * that mode's default direction. Each row's direction is drawn as a small filled triangle (up for
 * ascending, down for descending) rather than a letter, since the sidebar body font renders no up/down
 * glyph.
 *
 * <p>The rows carry no icon - the direction table's icon column is all null - so the selector reuses
 * the bloc list's three-column table geometry (a would-be crest column, the mode name, the trailing
 * direction triangle) and reads as a left-aligned list of modes with their directions flush right. The
 * rows are drawn in {@link BlocSortMode}'s own order, so the row index a click reports maps straight
 * back to a mode by position.
 */
public final class SortSelectorControl {

    private SortSelectorControl() {
    }

    /**
     * Builds the sort selector for the active mode and direction: a vertical radio lit on that mode's
     * row, each row labelled with its mode's string and trailed by a direction triangle - the active
     * mode's current direction on the lit row, each other mode's default direction on its own row, so
     * every row previews the order picking it would give.
     *
     * @param activeSort the sort the list is currently ranked by - the mode this lights and the
     *                   direction that mode is ranking in
     * @return the vertical, re-firing sort-selector radio table
     */
    public static ControlSpec.VerticalTable buildSelector(BlocSort activeSort) {
        var modes = List.of(BlocSortMode.values());
        var labels = new ArrayList<String>(modes.size());
        var directions = new ArrayList<TriangleDirection>(modes.size());
        for (var mode : modes) {
            labels.add(KmuStrings.get(mode.labelKey()));
            // The lit mode shows its live direction; every other row previews its own default, so a
            // row reads as "pick me and the list sorts this way".
            var rowDirection = mode == activeSort.mode()
                    ? activeSort.direction()
                    : mode.defaultDirection();
            directions.add(resolveTriangleDirection(rowDirection));
        }
        return ControlSpec.VerticalTable.directionTable(
                labels,
                directions,
                modes.indexOf(activeSort.mode()),
                cellIndex -> applySelection(modes, cellIndex),
                ReselectBehaviour.REFIRE);
    }

    // The triangle that previews a sort direction: ascending points up, descending down. The selector
    // draws this shape in each row's trailing slot in place of a direction word, since the body font
    // renders no up/down glyph.
    private static TriangleDirection resolveTriangleDirection(SortDirection direction) {
        return direction == SortDirection.ASCENDING ? TriangleDirection.UP : TriangleDirection.DOWN;
    }

    // Applies a click on a sort row. Re-picking the lit mode flips its direction; picking a different
    // mode switches to it at that mode's default direction. The current selection is read fresh from
    // the store rather than captured, so a click always acts on the state the store holds now. Any
    // index outside the mode rows is ignored, so a stray hit changes nothing.
    private static void applySelection(List<BlocSortMode> modes, int cellIndex) {
        if (cellIndex < 0 || cellIndex >= modes.size()) {
            return;
        }
        var clickedMode = modes.get(cellIndex);
        var current = BlocSort.resolveStored();
        if (clickedMode == current.mode()) {
            SortSelection.selectSortDirection(current.direction().opposite().persistenceKey());
        } else {
            SortSelection.selectSortMode(clickedMode.persistenceKey());
            SortSelection.selectSortDirection(clickedMode.defaultDirection().persistenceKey());
        }
    }
}
