package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.widgets.lists.ActivePicks;
import kmlib.starsector.ui.widgets.lists.ListColumns;
import kmlib.starsector.ui.widgets.lists.ListPicker;
import kmlib.starsector.ui.widgets.lists.ListPickerControl;
import kmlib.starsector.ui.widgets.lists.ListPickerStore;
import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.SelectableListItem;

import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.util.KmuStrings;

import java.util.List;

/**
 * Ties KMLib's spotlight picker to the save slots a mod keeps its three answers in - the last of the
 * three binders, and the one that also builds the control. The picker holds no store: it takes the
 * live selection, sort, and column count as values and reports each pick back, so somewhere has to
 * read the calling mod's slots on the way in and write them on the way out. That is this class, and
 * putting it here keeps a calling layer's signature free of every slot the picker touches.
 *
 * <p>It builds rather than only binds because the picker's three ties resolve at one point: the
 * item pick is {@link FilterSelection}'s and the sort is {@link SortSelectionBinder}'s, both under
 * the one {@link SelectionSlot} this class holds, while the column count routes to
 * {@link ColumnSelectionBinder} under that slot's mod and screen alone. A layer that composed the
 * picker itself would have to name all three, which is exactly the knowledge the binders exist to
 * hold.
 *
 * <p>The slot is captured at the build for the reason the board is: a report can land after the
 * player has moved to the other screen, and a pick belongs to the panel it was clicked on rather
 * than to whichever screen happens to be up when the click is handled.
 *
 * <p>The row the pointer rests on routes the same way, into {@link FilterHoverSlot} under the
 * {@link PickerScope} this slot resolves to, and it is the one report that neither persists nor
 * raises: a hover is a preview over paint already on the map, and only one screen is ever up to
 * preview on, so it is also the one tie that takes no screen. Resolved off the slot rather than
 * captured beside it, so the list a hover previews and the list a pick files under are one.
 * It is why the sector arrives as its whole {@link SectorMapMachinery} rather than
 * as the board alone - the slot and the board are both that sector's, and handed over side by side
 * they would be two chances to name two sectors.
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
     * Builds the picker for one slot against this mod's stores: the spotlighted ID and the stored
     * sort read live off that slot, the columns caption resolved out of this mod's strings, and
     * every pick wired back to the store that keeps it.
     *
     * <p>The picker arrives wildcarded because what a layer ranks is the layer's own: it hands over
     * its list bundled with the vocabulary that reads it, and this captures the pair once so no
     * layer writes that capture for itself. That is also why the stored sort is resolved here
     * rather than passed in - resolving it needs the vocabulary, which only arrives inside the
     * bundle.
     *
     * @param slot             the mod, screen and scope a pick, clear or hover is read from and
     *                         written into, so the choice is remembered against that panel's list
     *                         alone
     * @param picker           the layer's selectable items and the vocabulary that ranks them
     * @param columns          how many columns the item list wraps its rows across
     * @param trailingControls the controls filling the right half of the sort row; empty leaves the
     *                         sort selector alone on the row
     * @param machinery        the machinery of the sector this picker was built over, holding both
     *                         the board an item pick repaints through and the slot a hovered row is
     *                         previewed from
     * @return the picker controls, top to bottom; empty when the picker offers no items
     */
    public static List<ControlSpec> buildPicker(
            SelectionSlot slot,
            ListPicker<?> picker,
            ListColumns columns,
            List<ControlSpec> trailingControls,
            SectorMapMachinery machinery) {

        return buildCapturedPicker(slot, picker, columns, trailingControls, machinery);
    }

    // The picker built under a captured item type, which is what lets the items and their
    // vocabulary meet again as one type after travelling through the wildcard - the record's own
    // type bound is what makes the capture legal.
    //
    // The empty check comes before the stored sort is resolved, and must stay there: an offers-
    // nothing picker carries no vocabulary to fall back to, so reading the sort first would resolve
    // against nothing. Nothing is lost by the order, since an empty list contributes no controls.
    private static <T extends SelectableListItem> List<ControlSpec> buildCapturedPicker(
            SelectionSlot slot,
            ListPicker<T> picker,
            ListColumns columns,
            List<ControlSpec> trailingControls,
            SectorMapMachinery machinery) {

        if (picker.items().isEmpty()) {
            return List.of();
        }

        // The three live answers read as one, which is what the picker draws the block from: read
        // separately they could be paired across a rebuild, lighting a row in an order that has
        // since changed.
        var activePicks = new ActivePicks<>(
            FilterSelection.getSelectedIdOf(slot),
            SortSelectionBinder.resolveStoredSort(slot, picker.sortModes()),
            columns);

        return ListPickerControl.buildPicker(
            picker.items(),
            activePicks,
            KmuStrings.get(KmuStrings.MAP_LAYER_CTL_COLUMNS_CAPTION),
            trailingControls,
            // Both writers taken off the one machinery here, rather than resolved when a pick or
            // a hover lands: a build runs while the sector is live, where a report can arrive after
            // a load has disposed it - and asking a disposed machinery for machinery makes a
            // second copy that answers for a sector nothing draws and is never released.
            new ScopedPickerStore(
                slot,
                machinery.resolveRefreshBoard(),
                FilterHoverSlot.resolveHoverSlotIn(machinery)));
    }

    // What one picker writes into, bound to the slot its picks are filed under, to the board its item
    // picks repaint through, and to the hover slot its pointer reports into. A value rather than loose
    // callbacks so the three are captured once, where they are read, rather than threaded into each
    // write separately.
    //
    // The board and the hover slot are derived from one machinery rather than handed over side by
    // side, so no caller can pair one sector's board with another sector's hover.
    private record ScopedPickerStore(
        SelectionSlot slot,
        MapLayerRefreshBoard board,
        FilterHoverSlot hoverSlot)
        implements ListPickerStore {

        @Override
        public void clearItemHover() {
            hoverSlot.clearHoveredId(PickerScope.resolveScopeOf(slot));
        }

        @Override
        public void clearItemPick() {
            FilterSelection.clearSelection(slot, board);
        }

        @Override
        public void reportItemHover(String itemId) {
            // No signal is raised and nothing is persisted: a hover is a preview over paint already
            // on screen, where a pick has the reading layer rebuild everything it draws.
            hoverSlot.recordHoveredId(PickerScope.resolveScopeOf(slot), itemId);
        }

        @Override
        public void storeColumnsPick(ListColumns columns) {
            ColumnSelectionBinder.storeColumns(slot.screenSlot(), columns);
        }

        @Override
        public void storeItemPick(String itemId) {
            FilterSelection.selectId(slot, itemId, board);
        }

        @Override
        public void storeSortPick(ListSort<?> sort) {
            SortSelectionBinder.storeSort(slot, sort);
        }
    }
}
