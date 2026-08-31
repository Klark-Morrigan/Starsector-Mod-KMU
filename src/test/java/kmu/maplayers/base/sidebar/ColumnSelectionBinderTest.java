package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.lists.ListColumns;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the join between the column choice and this mod's save slot, which is the whole of what the
 * binder does: the read hands the stored key to the choice, and the write puts a picked choice's key
 * back. What the key means is the choice's and is pinned there; the store is mocked, so this reads
 * the pass-through alone.
 */
final class ColumnSelectionBinderTest {

    @Nested
    class ResolveStoredColumns {

        @Test
        void resolveStoredColumnsPassesTheStoredKeyToTheChoice() {

            try (var selectionMock = mockStatic(ColumnSelection.class)) {

                selectionMock
                    .when(ColumnSelection::getColumnCountKey)
                    .thenReturn(ListColumns.TWO.persistenceKey());

                assertThat(ColumnSelectionBinder.resolveStoredColumns())
                    .isEqualTo(ListColumns.TWO);
            }
        }
    }

    @Nested
    class StoreColumns {

        @Test
        void storeColumnsWritesThePickedChoicesKey() {

            try (var selectionMock = mockStatic(ColumnSelection.class)) {

                ColumnSelectionBinder.storeColumns(ListColumns.TWO);

                selectionMock.verify(
                    () -> ColumnSelection.selectColumnCount(ListColumns.TWO.persistenceKey()));
            }
        }
    }
}
