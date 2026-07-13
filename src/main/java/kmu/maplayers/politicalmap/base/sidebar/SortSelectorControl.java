package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.controls.ReselectBehaviour;

import kmu.maplayers.politicalmap.base.BlocSortMode;
import kmu.maplayers.politicalmap.base.SortDirection;
import kmu.maplayers.politicalmap.base.refresh.SortSelection;
import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.Collections;
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
 * that mode's default direction. The trailing letters read "UP" / "DWN" (a compact, reduced-size
 * column) rather than an arrow or triangle glyph, since the sidebar body font renders no such glyph.
 *
 * <p>The rows carry no icon - every {@code iconPaths} entry is null - so the selector reuses the bloc
 * list's three-column table geometry (a would-be crest column, the mode name, the trailing direction)
 * and reads as a left-aligned list of modes with their directions flush right. The rows are drawn in
 * {@link BlocSortMode}'s own order, so the row index a click reports maps straight back to a mode by
 * position.
 */
public final class SortSelectorControl {
    // The direction letters draw smaller than the mode names so the trailing column reads as a quiet
    // annotation, not a second label competing with the mode name. Relative to the strip body size.
    private static final double DIRECTION_LABEL_SCALE = 0.8d;

    private SortSelectorControl() {
    }

    /**
     * Builds the sort selector for the active mode and direction: a vertical radio lit on that mode's
     * row, each row labelled with its mode's string and trailed by a direction letter - the active
     * mode's current direction on the lit row, each other mode's default direction on its own row, so
     * every row previews the order picking it would give.
     *
     * @param activeMode      the mode the list is currently ranked by, which this lights
     * @param activeDirection the direction the active mode is currently ranking in
     * @return the vertical, re-firing sort-selector radio table
     */
    public static ControlSpec buildSelector(BlocSortMode activeMode, SortDirection activeDirection) {
        var modes = List.of(BlocSortMode.values());
        var labels = new ArrayList<String>(modes.size());
        var directionLabels = new ArrayList<String>(modes.size());
        for (var mode : modes) {
            labels.add(KmuStrings.get(mode.labelKey()));
            // The lit mode shows its live direction; every other row previews its own default, so a
            // row reads as "pick me and the list sorts this way".
            var rowDirection = mode == activeMode ? activeDirection : mode.defaultDirection();
            directionLabels.add(KmuStrings.get(rowDirection.labelKey()));
        }
        return ControlSpec.createVerticalRadioTable(labels,
                Collections.<String>nCopies(modes.size(), null), directionLabels,
                modes.indexOf(activeMode), cellIndex -> applySelection(modes, cellIndex),
                ReselectBehaviour.REFIRE, DIRECTION_LABEL_SCALE);
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
        var currentMode = BlocSortMode.fromKeyOrDefault(SortSelection.getSortModeKey());
        if (clickedMode == currentMode) {
            var currentDirection = SortDirection.fromKeyOrDefault(
                    SortSelection.getSortDirectionKey(), currentMode.defaultDirection());
            SortSelection.selectSortDirection(currentDirection.opposite().persistenceKey());
        } else {
            SortSelection.selectSortMode(clickedMode.persistenceKey());
            SortSelection.selectSortDirection(clickedMode.defaultDirection().persistenceKey());
        }
    }
}
