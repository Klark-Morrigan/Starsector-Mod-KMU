package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

/**
 * Ties KMLib's sort model to the save slot KMU keeps its answer in. The model resolves a stored mode
 * and direction against a caller's vocabulary but reads no save of its own, and {@link SortSelection}
 * holds the two raw keys but knows nothing of what they mean; this is the one place the two meet.
 *
 * <p>It exists so no reading layer carries the join. A caller asks for the stored sort with its own
 * mode set and hands a picked sort back, exactly as it did while the resolution lived on the model
 * itself, so the storage inversion that let the model move out of this mod cost its callers nothing.
 */
public final class SortSelectionBinder {

    private SortSelectionBinder() {
    }

    /**
     * The player's stored sort over the caller's vocabulary in one slot, read live: both of that
     * slot's stored keys off {@link SortSelection}, resolved by the model, so a fresh save reads the
     * caller's default mode and a save with no stored direction reads the stored mode's own default.
     *
     * @param <T>       the list item type the modes rank
     * @param slot      the screen and scope whose stored sort is read
     * @param sortModes the caller's sort vocabulary - the set a stored key resolves against, and the
     *                  mode it falls back to
     * @return the stored sort
     */
    public static <T> ListSort<T> resolveStoredSort(SelectionSlot slot, ListSortModes<T> sortModes) {
        return ListSort.resolveStored(
            SortSelection.getSortModeKeyOf(slot),
            SortSelection.getSortDirectionKeyOf(slot),
            sortModes);
    }

    /**
     * Persists a picked sort as its two keys in one slot. Both are written whichever half a click
     * moved, so the save always holds the whole pair the selector reported rather than one key from
     * this pick and one left over from an earlier one.
     *
     * @param slot the screen and scope the pick belongs to
     * @param sort the sort the picker reported, whose mode and direction keys are stored
     */
    public static void storeSort(SelectionSlot slot, ListSort<?> sort) {
        SortSelection.selectSortMode(slot, sort.mode().persistenceKey());
        SortSelection.selectSortDirection(slot, sort.direction().persistenceKey());
    }
}
