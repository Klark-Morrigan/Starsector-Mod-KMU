package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.lists.ListColumns;

import kmu.maplayers.base.layer.ScreenMemoryScope;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the join between the column choice and this mod's save slot, which is the whole of what the
 * binder does: the read hands the screen's stored key to the choice, and the write puts a picked
 * choice's key back under the screen it was picked on. What the key means is the choice's and is
 * pinned there; the store is mocked, so this reads the pass-through alone.
 */
final class ColumnSelectionBinderTest {

    // The screen whose slot the read and the write land on.
    private static final ScreenMemoryScope MAP_SCOPE = new ScreenMemoryScope("map");

    @Nested
    class ResolveStoredColumns {

        @Test
        void resolveStoredColumnsPassesTheScreensStoredKeyToTheChoice() {

            try (var selectionMock = mockStatic(ColumnSelection.class)) {

                selectionMock
                    .when(() -> ColumnSelection.getColumnCountKey(MAP_SCOPE))
                    .thenReturn(ListColumns.TWO.persistenceKey());

                assertThat(ColumnSelectionBinder.resolveStoredColumns(MAP_SCOPE))
                    .isEqualTo(ListColumns.TWO);
            }
        }
    }

    @Nested
    class StoreColumns {

        @Test
        void storeColumnsWritesThePickedChoicesKeyUnderTheScreen() {

            try (var selectionMock = mockStatic(ColumnSelection.class)) {

                ColumnSelectionBinder.storeColumns(MAP_SCOPE, ListColumns.TWO);

                selectionMock.verify(
                    () -> ColumnSelection.selectColumnCount(
                        MAP_SCOPE, ListColumns.TWO.persistenceKey()));
            }
        }
    }
}
