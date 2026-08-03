package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.lists.ListColumns;

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
     * The player's stored column choice, read live: the stored key off {@link ColumnSelection},
     * resolved by the choice, so a save that never picked a count reads the single-column default.
     *
     * @return the stored column choice
     */
    public static ListColumns resolveStoredColumns() {
        return ListColumns.fromKeyOrDefault(ColumnSelection.getColumnCountKey());
    }

    /**
     * Persists a picked column count as its key.
     *
     * @param columns the choice the picker reported, whose key is stored
     */
    public static void storeColumns(ListColumns columns) {
        ColumnSelection.selectColumnCount(columns.persistenceKey());
    }
}
