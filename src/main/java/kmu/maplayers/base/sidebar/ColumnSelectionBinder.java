package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.lists.ListColumns;

import kmu.maplayers.base.layer.ScreenMemoryScope;

/**
 * Ties KMLib's column choice to the save slot KMU keeps its answer in - the counterpart of
 * {@link SortSelectionBinder} for the other half of a picker's list layout. The choice resolves a
 * stored key but reads no save of its own, and {@link ColumnSelection} holds the raw key but knows
 * nothing of what it means; this is the one place the two meet.
 */
public final class ColumnSelectionBinder {

    private ColumnSelectionBinder() {
    }

    /**
     * The player's stored column choice on one screen, read live: the stored key off
     * {@link ColumnSelection}, resolved by the choice, so a save that never picked a count reads the
     * single-column default.
     *
     * @param memoryScope the screen whose stored choice is read
     * @return the stored column choice
     */
    public static ListColumns resolveStoredColumns(ScreenMemoryScope memoryScope) {
        return ListColumns.fromKeyOrDefault(ColumnSelection.getColumnCountKey(memoryScope));
    }

    /**
     * Persists a picked column count as its key, against the screen it was picked on.
     *
     * @param memoryScope the screen whose panel made the pick
     * @param columns     the choice the picker reported, whose key is stored
     */
    public static void storeColumns(ScreenMemoryScope memoryScope, ListColumns columns) {
        ColumnSelection.selectColumnCount(memoryScope, columns.persistenceKey());
    }
}
