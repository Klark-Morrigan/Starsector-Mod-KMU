package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.widgets.lists.ListColumns;
import kmlib.starsector.ui.widgets.lists.ListPickerControl;
import kmlib.starsector.ui.widgets.lists.ListPickerStore;
import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.SelectableListItem;

import kmu.util.KmuStrings;

import java.util.List;

/**
 * Ties KMLib's spotlight picker to the save slots KMU keeps its three answers in - the last of the
 * three binders, and the one that also builds the control. The picker holds no store: it takes the
 * live selection, sort, and column count as values and reports each pick back, so somewhere has to
 * read this mod's slots on the way in and write them on the way out. That is this class, and
 * putting it here keeps a calling layer's signature free of every slot the picker touches.
 *
 * <p>It builds rather than only binds because the picker's three ties resolve at one point: the
 * item pick is {@link FilterSelection}'s and is the only one scoped, while the other two route to
 * {@link SortSelectionBinder} and {@link ColumnSelectionBinder} unchanged. A layer that composed
 * the picker itself would have to name all three, which is exactly the knowledge the binders exist
 * to hold.
 */
public final class FilterSelectionBinder {

    private FilterSelectionBinder() {
    }

    /**
     * Builds the picker for one scope against this mod's stores: the spotlighted id read live off
     * {@link FilterSelection}, the columns caption resolved out of this mod's strings, and every
     * pick wired back to the slot that keeps it.
     *
     * @param <T>              the calling layer's own item type, ranked by its own comparators
     * @param scopeId          the scope a pick or clear is read from and written into, so the
     *                         choice is remembered against this scope alone
     * @param items            the selectable items in this scope; order here is immaterial since
     *                         the sort mode reorders them for display
     * @param sort             how the list is ranked - the metric, its direction, and the layer's
     *                         sort vocabulary the selector draws its rows from
     * @param columns          how many columns the item list wraps its rows across
     * @param trailingControls the controls filling the right half of the sort row; empty leaves the
     *                         sort selector alone on the row
     * @return the picker controls, top to bottom; empty when {@code items} is empty
     */
    public static <T extends SelectableListItem> List<ControlSpec> buildPicker(
            String scopeId,
            List<T> items,
            ListSort<T> sort,
            ListColumns columns,
            List<ControlSpec> trailingControls) {

        return ListPickerControl.buildPicker(
            items,
            FilterSelection.getSelectedIdOf(scopeId),
            sort,
            columns,
            KmuStrings.get(KmuStrings.MAP_LAYER_CTL_COLUMNS_CAPTION),
            trailingControls,
            new ScopedPickerStore(scopeId));
    }

    // The three slots one picker writes into, bound to the scope its item pick belongs to. A value
    // rather than three loose callbacks so the scope is captured once, where it is read, rather
    // than threaded into each write separately.
    private record ScopedPickerStore(String scopeId) implements ListPickerStore {

        @Override
        public void clearItemPick() {
            FilterSelection.clearSelection(scopeId);
        }

        @Override
        public void storeColumnsPick(ListColumns columns) {
            ColumnSelectionBinder.storeColumns(columns);
        }

        @Override
        public void storeItemPick(String itemId) {
            FilterSelection.selectId(scopeId, itemId);
        }

        @Override
        public void storeSortPick(ListSort<?> sort) {
            SortSelectionBinder.storeSort(sort);
        }
    }
}
