package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * A picker's columns selector: a two-segment horizontal radio ("1" / "2") with a trailing
 * "Columns" caption, the lit segment the count the picker list is currently laid across. Picking a
 * segment persists that count through {@link ColumnSelection} and the caller's next per-frame body
 * build re-wraps its list under it, so the selector and the list always agree on how many columns
 * the rows spread across.
 *
 * <p>The segments are drawn in {@link ListColumns}'s own order, so the segment index a click
 * reports maps straight back to a choice by position. It is an ordinary option radio - re-picking
 * the lit segment is inert, and the count only changes by picking the other segment - rather than
 * a deselectable list or the re-firing {@link SortSelectorControl}.
 */
public final class ColumnsSelectorControl {

    private ColumnsSelectorControl() {
    }

    /**
     * Builds the columns selector for the active layout: a two-segment radio lit on that choice's
     * segment, each segment labelled with its count and the control trailed by the "Columns"
     * caption.
     *
     * @param activeColumns the column count the list is currently laid across, which this lights
     * @return the horizontal, two-segment columns-selector radio
     */
    public static ControlSpec.HorizontalRadio buildSelector(ListColumns activeColumns) {
        var choices = List.of(ListColumns.values());
        var labels = new ArrayList<String>(choices.size());
        for (var choice : choices) {
            labels.add(KmuStrings.get(choice.labelKey()));
        }
        return ControlSpec.HorizontalRadio
            .of(
                labels,
                choices.indexOf(activeColumns),
                cellIndex -> applySelection(choices, cellIndex))
            .showsCaption(KmuStrings.get(KmuStrings.MAP_LAYER_CTL_COLUMNS_CAPTION));
    }

    // Persists the column count for the clicked segment. Any index outside the two segments is
    // ignored, so a stray hit changes nothing. Re-picking the lit segment never reaches here (the
    // horizontal radio swallows a re-pick as inert), so this only ever runs for a real change.
    private static void applySelection(List<ListColumns> choices, int cellIndex) {
        if (cellIndex < 0 || cellIndex >= choices.size()) {
            return;
        }
        ColumnSelection.selectColumnCount(choices.get(cellIndex).persistenceKey());
    }
}
