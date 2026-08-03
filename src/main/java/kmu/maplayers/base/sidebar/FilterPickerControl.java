package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.controls.ReselectBehaviour;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.LabelledRow;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.lists.ColumnsSelectorControl;
import kmlib.starsector.ui.widgets.lists.ListColumns;
import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.ListSortMode;
import kmlib.starsector.ui.widgets.lists.ListSortModes;
import kmlib.starsector.ui.widgets.lists.SortSelectorControl;

import kmu.util.KmuStrings;

import java.util.ArrayList;
import java.util.List;

/**
 * A spotlight picker's body controls, top to bottom: a rule heading the block, the columns selector,
 * a row pairing the sort selector beside whatever the caller pairs with it, then the vertical
 * icon-radio list of selectable items. It turns a layer's {@link SelectableListItem} options into
 * one clickable list and wires each click straight to {@link FilterSelection}, so picking a row
 * spotlights that item and re-picking the lit row clears the filter. Which items the list holds is
 * decided before this is called, so it names no layer and no scope of its own.
 *
 * <p>The rule parts the controls above from the picker below, marking the section a caption string
 * used to. The columns selector rides directly under the rule, so how many columns the list wraps
 * across is chosen for the block as a whole. Below it a paired row sets the sort beside the caller's
 * own trailing controls: the sort selector on the left picks the metric the list ranks by (the
 * picker sorts the items by that metric and labels each row with its value), while the right half is
 * the caller's - the political map fills it with the recede toggles that fade the rest of the
 * sector, a layer with nothing to pair passes none and the row draws as the sort selector alone.
 *
 * <p>Both selectors are KMLib widgets that hold no store of their own, so this is where their picks
 * are bound to KMU's save slots ({@link SortSelectionBinder}, {@link ColumnSelectionBinder}) and
 * where the columns caption is resolved. Binding here rather than a step further out is what keeps
 * a calling layer's signature free of them.
 */
public final class FilterPickerControl {

    private FilterPickerControl() {
    }

    /**
     * Builds the picker block for one scope's selectable items, filter selection, and sort mode, top
     * to bottom: the section rule, the columns selector, a row pairing the sort selector beside the
     * caller's trailing controls, then the icon-radio list ranked by the sort mode (its lit row the
     * spotlighted item, or none when the stored id is not among these items). Returns an empty list
     * when there are no selectable items, so a scope with nothing to spotlight contributes no picker
     * rather than an empty list widget.
     *
     * @param <T>              the caller's own item type, ranked by its own comparators throughout
     * @param scopeId          the scope a pick or clear writes into, so the choice is remembered
     *                         against this scope alone
     * @param items            the selectable items in this scope; order here is immaterial since the
     *                         sort mode reorders them for display
     * @param selectedItemId   the currently spotlighted item's id, or null when no filter is active
     * @param sort             the metric and direction the list is ranked by, which also picks each
     *                         row's trailing value and the sort selector previews
     * @param sortModes        the caller's sort vocabulary the selector lays its rows out from and a
     *                         click resolves the stored mode against
     * @param columns          how many columns the item list wraps its rows across, which the columns
     *                         selector lights and the list lays out under
     * @param trailingControls the controls filling the right half of the sort row; empty leaves the
     *                         sort selector alone on the row
     * @return the picker body controls, top to bottom; empty when {@code items} is empty
     */
    public static <T extends SelectableListItem> List<ControlSpec> buildControls(
            String scopeId,
            List<T> items,
            String selectedItemId,
            ListSort<T> sort,
            ListSortModes<T> sortModes,
            ListColumns columns,
            List<ControlSpec> trailingControls) {

        if (items.isEmpty()) {
            return List.of();
        }

        // Rank a copy under the active mode and direction, leaving the caller's (cached) list
        // untouched, so the rows draw in the chosen order and the lit index below is resolved against
        // that same order.
        var rankedItems = new ArrayList<>(items);
        rankedItems.sort(sort.comparator());

        var selectedIndex = resolveSelectedIndex(rankedItems, selectedItemId);
        var controls = new ArrayList<ControlSpec>();

        // A rule heads the block, parting the controls above from the picker below - the section
        // break a caption used to mark, now carrying no text.
        controls.add(new ControlSpec.Divider());

        // The columns selector rides directly under the rule, so the column count is chosen for the
        // block as a whole; the list below then wraps its rows across that many columns. The
        // selector itself neither resolves the caption nor reaches a save - both are this mod's, so
        // the caption is looked up here and the pick handed to the store binder.
        controls.add(ColumnsSelectorControl.buildSelector(
            columns,
            KmuStrings.get(KmuStrings.MAP_LAYER_CTL_COLUMNS_CAPTION),
            ColumnSelectionBinder::storeColumns));

        // The sort selector and the caller's trailing controls share one row, the sort on the left
        // picking the metric the list ranks by. Pairing them keeps the picker compact; what sits
        // beside the sort is the caller's decision, so the framework composes the row and the layer
        // fills its right half. Like the columns selector it reports its pick rather than storing
        // one, so the binder is named here.
        controls.add(new ControlSpec.SideBySide(
            List.of(SortSelectorControl.buildSelector(
                sort,
                sortModes,
                SortSelectionBinder::storeSort)),
            trailingControls));

        // The item list is the body's one scrolling cluster: when the picker plus the controls above
        // and below it would run the box past the bottom margin, the list gives up the difference and
        // scrolls while everything around it stays pinned. asScrolling marks the list; the capped
        // layout, renderer, and input listener all read that one flag.
        controls.add(
            ControlSpec.VerticalTable
                .createColumnTable(
                    buildItemRows(rankedItems, sort.mode()),
                    selectedIndex,
                    cellIndex -> pickItem(scopeId, rankedItems, selectedIndex, cellIndex))
                .handlesReselect(ReselectBehaviour.DESELECT)
                .spreadsAcross(columns.columnCount())
                .asScrolling());

        return List.copyOf(controls);
    }

    // Spotlights the clicked item, or clears the filter when the click landed on the already-lit row.
    // The list is deselectable, so a press on the lit option reaches here with its own index; re-
    // picking it means "stop spotlighting". Any index outside the item list is ignored, so a stray
    // hit changes nothing.
    private static void pickItem(
            String scopeId,
            List<? extends SelectableListItem> items,
            int selectedIndex,
            int cellIndex) {

        if (cellIndex < 0 || cellIndex >= items.size()) {
            return;
        }
        if (cellIndex == selectedIndex) {
            FilterSelection.clearSelection(scopeId);
        } else {
            FilterSelection.selectId(scopeId, items.get(cellIndex).itemId());
        }
    }

    // The lit row: the index of the item whose id is stored, or no selection when the stored id is
    // absent (no filter) or names an item no longer in the list (a stale id the load heal has not yet
    // cleared). An unlit list still shows every option, so the player can pick one.
    private static int resolveSelectedIndex(
            List<? extends SelectableListItem> items,
            String selectedItemId) {

        if (selectedItemId == null) {
            return ControlSpec.NO_SELECTION;
        }
        for (var index = 0; index < items.size(); index++) {
            if (selectedItemId.equals(items.get(index).itemId())) {
                return index;
            }
        }
        return ControlSpec.NO_SELECTION;
    }

    // One row per item, in list order: the item's crest leading it, its name, and the active sort
    // metric's value for that item trailing it. Built as whole rows rather than as a column each, so an
    // item's three parts are written together and cannot fall out of step with one another.
    //
    // An item with no resolved name draws as an unlabelled row rather than a null the width measurer
    // would choke on; an item with no crest leads with nothing; and every row carries the metric's value
    // (a zero metric shows "0" rather than dropping the column), which for a mode with no numeric metric
    // is blank throughout and the rows read as a plain list.
    private static <T extends SelectableListItem> List<LabelledRow> buildItemRows(
            List<T> items,
            ListSortMode<T> sortMode) {

        var textColour = StarsectorUiColour.VANILLA_TEXT.resolve();
        var itemRows = new ArrayList<LabelledRow>(items.size());

        for (var item : items) {
            var displayName = item.displayName() == null ? "" : item.displayName();
            var crestSpritePath = item.crestSpritePath();

            itemRows.add(LabelledRow
                .createRow(new TextSpan(displayName, textColour))
                .leadsWith(crestSpritePath == null
                    ? RowSlot.EMPTY
                    : new RowSlot.Image(crestSpritePath))
                .trailsWith(new RowSlot.Text(
                    new TextSpan(sortMode.resolveTrailingValue(item), textColour))));
        }
        return itemRows;
    }
}
