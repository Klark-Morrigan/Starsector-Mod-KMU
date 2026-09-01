package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.widgets.lists.ActivePicks;
import kmlib.starsector.ui.widgets.lists.ListColumns;
import kmlib.starsector.ui.widgets.lists.ListPicker;
import kmlib.starsector.ui.widgets.lists.ListPickerControl;
import kmlib.starsector.ui.widgets.lists.ListPickerStore;
import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.SelectableListItem;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
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
 * item pick is {@link FilterSelection}'s and the sort is {@link SortSelectionBinder}'s, both under
 * the one scope this class holds, while the column count routes to {@link ColumnSelectionBinder}
 * unscoped - a layout preference over a list means the same thing under every scope. A layer that
 * composed the picker itself would have to name all three, which is exactly the knowledge the
 * binders exist to hold.
 *
 * <p>It is also where the wildcard a layer's picker travels under is captured, once for the mod
 * rather than in each layer: every picker-owning layer would otherwise write the same capture
 * helper, and the helper needs both the selection slot and the sort binder to do its job, so it
 * belongs beside them.
 */
public final class FilterSelectionBinder {

    private FilterSelectionBinder() {
    }

    /**
     * Builds the picker for one scope against this mod's stores: the spotlighted id and the stored
     * sort read live off this scope's slots, the columns caption resolved out of this mod's
     * strings, and every pick wired back to the slot that keeps it.
     *
     * <p>The picker arrives wildcarded because what a layer ranks is the layer's own: it hands over
     * its list bundled with the vocabulary that reads it, and this captures the pair once so no
     * layer writes that capture for itself. That is also why the stored sort is resolved here
     * rather than passed in - resolving it needs the vocabulary, which only arrives inside the
     * bundle.
     *
     * @param scopeId          the scope a pick or clear is read from and written into, so the
     *                         choice is remembered against this scope alone
     * @param picker           the layer's selectable items and the vocabulary that ranks them
     * @param columns          how many columns the item list wraps its rows across
     * @param trailingControls the controls filling the right half of the sort row; empty leaves the
     *                         sort selector alone on the row
     * @param board            the refresh board of the sector this picker was built for, carried
     *                         into the item picks so a spotlight change repaints that sector's map
     * @param hoverSlot        the hover slot of that same sector, told which row the pointer is on
     *                         so a layer can preview what picking it would spotlight
     * @return the picker controls, top to bottom; empty when the picker offers no items
     */
    public static List<ControlSpec> buildPicker(
            String scopeId,
            ListPicker<?> picker,
            ListColumns columns,
            List<ControlSpec> trailingControls,
            MapLayerRefreshBoard board,
            FilterHoverSlot hoverSlot) {

        return buildCapturedPicker(scopeId, picker, columns, trailingControls, board, hoverSlot);
    }

    // The picker built under a captured item type, which is what lets the items and their
    // vocabulary meet again as one type after travelling through the wildcard - the record's own
    // type bound is what makes the capture legal.
    //
    // The empty check comes before the stored sort is resolved, and must stay there: an offers-
    // nothing picker carries no vocabulary to fall back to, so reading the sort first would resolve
    // against nothing. Nothing is lost by the order, since an empty list contributes no controls.
    private static <T extends SelectableListItem> List<ControlSpec> buildCapturedPicker(
            String scopeId,
            ListPicker<T> picker,
            ListColumns columns,
            List<ControlSpec> trailingControls,
            MapLayerRefreshBoard board,
            FilterHoverSlot hoverSlot) {

        if (picker.items().isEmpty()) {
            return List.of();
        }

        // The three live answers read as one, which is what the picker draws the block from: read
        // separately they could be paired across a rebuild, lighting a row in an order that has
        // since changed.
        var activePicks = new ActivePicks<>(
            FilterSelection.getSelectedIdOf(scopeId),
            SortSelectionBinder.resolveStoredSort(scopeId, picker.sortModes()),
            columns);

        return ListPickerControl.buildPicker(
            picker.items(),
            activePicks,
            KmuStrings.get(KmuStrings.MAP_LAYER_CTL_COLUMNS_CAPTION),
            trailingControls,
            new ScopedPickerStore(scopeId, board, hoverSlot));
    }

    // The slots one picker writes into, bound to the scope its item and sort picks belong to, to the
    // board its item picks repaint through, and to the hover slot its pointer reports into. A value
    // rather than loose callbacks so the three are captured once, where they are read, rather than
    // threaded into each write separately.
    private record ScopedPickerStore(
        String scopeId,
        MapLayerRefreshBoard board,
        FilterHoverSlot hoverSlot)
        implements ListPickerStore {

        @Override
        public void clearItemHover() {
            hoverSlot.clearHoveredId(scopeId);
        }

        @Override
        public void clearItemPick() {
            FilterSelection.clearSelection(scopeId, board);
        }

        @Override
        public void reportItemHover(String itemId) {
            // No signal is raised and nothing is persisted: a hover is a preview over paint already
            // on screen, where a pick has the reading layer rebuild everything it draws.
            hoverSlot.recordHoveredId(scopeId, itemId);
        }

        @Override
        public void storeColumnsPick(ListColumns columns) {
            ColumnSelectionBinder.storeColumns(columns);
        }

        @Override
        public void storeItemPick(String itemId) {
            FilterSelection.selectId(scopeId, itemId, board);
        }

        @Override
        public void storeSortPick(ListSort<?> sort) {
            SortSelectionBinder.storeSort(scopeId, sort);
        }
    }
}
