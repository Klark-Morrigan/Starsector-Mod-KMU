package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.lists.ListColumns;

import kmu.maplayers.base.layer.ScreenMemoryScopes;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the join between the column choice and the calling mod's save slot, which is the whole of what
 * the binder does: the read hands the slot's stored key to the choice, and the write puts a picked
 * choice's key back under the slot it was picked on. What the key means is the choice's and is
 * pinned there; the store is mocked, so this reads the pass-through alone.
 */
final class ColumnSelectionBinderTest {

    // The slot the read and the write land on - a stand-in mod on a stand-in screen, since what this
    // binder does is the same whichever mod's panel asked.
    private static final ScreenSelectionSlot SLOT = new ScreenSelectionSlot(
        MapLayerStoreNamespaces.createStandInNamespace(),
        ScreenMemoryScopes.createStandInScreen());

    @Nested
    class ResolveStoredColumns {

        @Test
        void resolveStoredColumnsPassesTheSlotsStoredKeyToTheChoice() {

            try (var selectionMock = mockStatic(ColumnSelection.class)) {

                selectionMock
                    .when(() -> ColumnSelection.getColumnCountKey(SLOT))
                    .thenReturn(ListColumns.TWO.persistenceKey());

                assertThat(ColumnSelectionBinder.resolveStoredColumns(SLOT))
                    .isEqualTo(ListColumns.TWO);
            }
        }
    }

    @Nested
    class StoreColumns {

        @Test
        void storeColumnsWritesThePickedChoicesKeyUnderTheSlot() {

            try (var selectionMock = mockStatic(ColumnSelection.class)) {

                ColumnSelectionBinder.storeColumns(SLOT, ListColumns.TWO);

                selectionMock.verify(
                    () -> ColumnSelection.selectColumnCount(
                        SLOT, ListColumns.TWO.persistenceKey()));
            }
        }
    }
}
