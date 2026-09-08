package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.base.layer.ScreenMemoryScope;

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
     * The player's stored sort over the caller's vocabulary on one screen in one scope, read live:
     * both of that pair's stored keys off {@link SortSelection}, resolved by the model, so a fresh
     * save reads the caller's default mode and a save with no stored direction reads the stored
     * mode's own default.
     *
     * @param <T>         the list item type the modes rank
     * @param memoryScope the screen whose stored sort is read
     * @param scopeId     the scope whose stored sort is read
     * @param sortModes   the caller's sort vocabulary - the set a stored key resolves against, and
     *                    the mode it falls back to
     * @return the stored sort
     */
    public static <T> ListSort<T> resolveStoredSort(
            ScreenMemoryScope memoryScope,
            String scopeId,
            ListSortModes<T> sortModes) {

        return ListSort.resolveStored(
            SortSelection.getSortModeKeyOf(memoryScope, scopeId),
            SortSelection.getSortDirectionKeyOf(memoryScope, scopeId),
            sortModes);
    }

    /**
     * Persists a picked sort as its two keys in one screen's slots for one scope. Both are written
     * whichever half a click moved, so the save always holds the whole pair the selector reported
     * rather than one key from this pick and one left over from an earlier one.
     *
     * @param memoryScope the screen whose panel made the pick
     * @param scopeId     the scope the pick belongs to
     * @param sort        the sort the picker reported, whose mode and direction keys are stored
     */
    public static void storeSort(
            ScreenMemoryScope memoryScope,
            String scopeId,
            ListSort<?> sort) {

        SortSelection.selectSortMode(memoryScope, scopeId, sort.mode().persistenceKey());
        SortSelection.selectSortDirection(memoryScope, scopeId, sort.direction().persistenceKey());
    }
}
