package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.lists.ListColumns;

/**
 * Ties KMLib's column choice to the save slot a mod keeps its answer in - the counterpart of
 * {@link SortSelectionBinder} for the other half of a picker's list layout. The choice resolves a
 * stored key but reads no save of its own, and {@link ColumnSelection} holds the raw key but knows
 * nothing of what it means; this is the one place the two meet.
 */
public final class ColumnSelectionBinder {

    private ColumnSelectionBinder() {
    }

    /**
     * The player's stored column choice in one slot, read live: the stored key off
     * {@link ColumnSelection}, resolved by the choice, so a save that never picked a count reads the
     * single-column default.
     *
     * @param slot the mod and screen whose stored choice is read
     * @return the stored column choice
     */
    public static ListColumns resolveStoredColumns(ScreenSelectionSlot slot) {
        return ListColumns.fromKeyOrDefault(ColumnSelection.getColumnCountKey(slot));
    }

    /**
     * Persists a picked column count as its key, against the mod and screen it was picked on.
     *
     * @param slot    the mod and screen whose panel made the pick
     * @param columns the choice the picker reported, whose key is stored
     */
    public static void storeColumns(ScreenSelectionSlot slot, ListColumns columns) {
        ColumnSelection.selectColumnCount(slot, columns.persistenceKey());
    }
}
