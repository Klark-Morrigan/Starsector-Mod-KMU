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
 * The one call a mod makes to get a persisted sidebar list. It hands over its rows bundled with the
 * vocabulary that ranks them, the address its answers are kept under, and whatever it pairs beside
 * the sort - and gets back the picker block with every pick already tied to a store. The picker is
 * KMLib's and holds no store; the three stores beside this class hold no widget; this is the one
 * place the two meet, so a calling layer names no store at all.
 *
 * <p><b>Every address it reads or writes at is derived from the one {@link SelectionSlot} handed
 * in</b>, never composed beside it. That is the invariant to keep when editing this class: an answer
 * resolved at an address of its own is how a picker's picks come apart, and the failure is a
 * preference the player sets twice.
 *
 * <p>Which store holds which answer, why the slot and the sector are captured at the build, and why
 * the hover is the one tie that takes no screen are the package's, in
 * <a href="README.md#picker-state">picker state</a>.
 */
public final class ListPickerBinder {

    private ListPickerBinder() {
    }

    /**
     * Builds the picker for one slot against this mod's stores: the spotlighted ID, the stored sort
     * and the stored column count read live off that slot, the columns caption resolved out of this
     * mod's strings, and every pick wired back to the store that keeps it.
     *
     * <p>The picker arrives wildcarded because what a layer ranks is the layer's own, and the
     * stored sort is resolved here rather than passed in because resolving it needs the vocabulary,
     * which only arrives inside that bundle. The caption is this mod's because it is the framework's
     * own chrome rather than anything the caller's list holds.
     *
     * @param slot             the mod, screen and scope every one of this picker's answers is read
     *                         from and written into, so each choice is remembered against that
     *                         panel's list alone
     * @param picker           the layer's selectable items and the vocabulary that ranks them
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
            List<ControlSpec> trailingControls,
            SectorMapMachinery machinery) {

        return buildCapturedPicker(slot, picker, trailingControls, machinery);
    }

    // The picker built under a captured item type, which is what lets the items and their
    // vocabulary meet again as one type after travelling through the wildcard - the record's own
    // type bound is what makes the capture legal.
    //
    // The empty check comes before the stored answers are resolved, and must stay there: an offers-
    // nothing picker carries no vocabulary to fall back to, so reading the sort first would resolve
    // against nothing. Nothing is lost by the order, since an empty list contributes no controls.
    private static <T extends SelectableListItem> List<ControlSpec> buildCapturedPicker(
            SelectionSlot slot,
            ListPicker<T> picker,
            List<ControlSpec> trailingControls,
            SectorMapMachinery machinery) {

        if (picker.items().isEmpty()) {
            return List.of();
        }

        // The three live answers read as one, which is what the picker draws the block from: read
        // separately they could be paired across a rebuild, lighting a row in an order that has
        // since changed. All three come off the one slot, the count off the screen half of it, so
        // the addresses they are read at are the addresses they are written back to.
        var activePicks = new ActivePicks<>(
            FilterSelection.getSelectedIdOf(slot),
            SortSelectionBinder.resolveStoredSort(slot, picker.sortModes()),
            ColumnSelectionBinder.resolveStoredColumns(slot.screenSlot()));

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
