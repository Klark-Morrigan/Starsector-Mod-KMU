package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the column selection state: the read reports the stored column-count key or none, a pick
 * persists the key, and the write no-ops cleanly before the sector exists. Like the sort selection it
 * fires no refresh, so there is nothing to verify beyond the memory write. The frozen key is pinned as
 * a literal so a rename that would silently reset every save's column choice fails here rather than
 * shipping.
 */
final class ColumnSelectionTest {

    // The save-serialised key, pinned as a literal: renaming it resets every existing save's column
    // choice, so a change must break this test first.
    private static final String COLUMN_COUNT_KEY = "$kmu_map_list_columns";

    private static final String CHOICE_KEY = "2";

    @Nested
    class GetColumnCountKey {

        @Test
        void getColumnCountKeyReturnsTheStoredKey() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(COLUMN_COUNT_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(COLUMN_COUNT_KEY))
                    .thenReturn(CHOICE_KEY);

                assertThat(ColumnSelection.getColumnCountKey())
                    .isEqualTo(CHOICE_KEY);
            }
        }

        @Test
        void getColumnCountKeyIsNullWhenNoCountIsStored() {
            // A save that never picked a count holds no key, which the column choice resolves to its
            // single-column default.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                assertThat(ColumnSelection.getColumnCountKey())
                    .isNull();
            }
        }

        @Test
        void getColumnCountKeyIsNullBeforeTheSectorExists() {
            // No sector means no save to read, so nothing can have been picked yet.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                assertThat(ColumnSelection.getColumnCountKey())
                    .isNull();
            }
        }
    }

    @Nested
    class SelectColumnCount {

        @Test
        void selectColumnCountPersistsTheKey() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                ColumnSelection.selectColumnCount(CHOICE_KEY);

                verify(memoryMock)
                    .set(COLUMN_COUNT_KEY, CHOICE_KEY);
            }
        }

        @Test
        void selectColumnCountNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into, so the pick is silently dropped rather than
            // dereferencing a null sector.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                ColumnSelection.selectColumnCount(CHOICE_KEY);

                // Nothing to assert beyond it not throwing - there is no memory to have written to.
            }
        }
    }
}
