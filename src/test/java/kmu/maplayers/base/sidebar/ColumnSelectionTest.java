package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.layer.ScreenMemoryScope;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the column selection state: the read reports one screen's stored column-count key or none, a
 * pick persists that screen's key and leaves the other screen's alone, and the write no-ops cleanly
 * before the sector exists. Like the sort selection it fires no refresh, so there is nothing to verify
 * beyond the memory write. The frozen keys are pinned as literals so a rename that would silently
 * reset every save's column choice fails here rather than shipping.
 */
final class ColumnSelectionTest {

    // The two screens' scopes, and the save-serialised keys they compose, pinned as literals:
    // renaming the base key resets every existing save's column choice, and losing the screen segment
    // would put both panels back on one shared count, so either change must break this test first.
    private static final ScreenMemoryScope MAP_SCOPE = new ScreenMemoryScope("map");

    private static final ScreenMemoryScope INTEL_SCOPE = new ScreenMemoryScope("intel");

    private static final String MAP_COLUMN_COUNT_KEY = "$kmu_map_list_columns_map";

    private static final String INTEL_COLUMN_COUNT_KEY = "$kmu_map_list_columns_intel";

    private static final String CHOICE_KEY = "2";

    private static final String OTHER_CHOICE_KEY = "3";

    @Nested
    class GetColumnCountKey {

        @Test
        void getColumnCountKeyReturnsTheStoredKey() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(MAP_COLUMN_COUNT_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(MAP_COLUMN_COUNT_KEY))
                    .thenReturn(CHOICE_KEY);

                assertThat(ColumnSelection.getColumnCountKey(MAP_SCOPE))
                    .isEqualTo(CHOICE_KEY);
            }
        }

        @Test
        void getColumnCountKeyReadsEachScreensOwnCount() {
            // Per-screen isolation: the two panels are two widths the player laid the list out in,
            // so a count picked on one is invisible to the other rather than re-wrapping both.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(MAP_COLUMN_COUNT_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(MAP_COLUMN_COUNT_KEY))
                    .thenReturn(CHOICE_KEY);
                when(memoryMock.contains(INTEL_COLUMN_COUNT_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(INTEL_COLUMN_COUNT_KEY))
                    .thenReturn(OTHER_CHOICE_KEY);

                assertThat(ColumnSelection.getColumnCountKey(MAP_SCOPE))
                    .isEqualTo(CHOICE_KEY);
                assertThat(ColumnSelection.getColumnCountKey(INTEL_SCOPE))
                    .isEqualTo(OTHER_CHOICE_KEY);
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

                assertThat(ColumnSelection.getColumnCountKey(MAP_SCOPE))
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

                assertThat(ColumnSelection.getColumnCountKey(MAP_SCOPE))
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

                ColumnSelection.selectColumnCount(MAP_SCOPE, CHOICE_KEY);

                verify(memoryMock)
                    .set(MAP_COLUMN_COUNT_KEY, CHOICE_KEY);
            }
        }

        @Test
        void selectColumnCountLeavesTheOtherScreensCountUntouched() {
            // Per-screen isolation on the write side: a pick made on the intel panel writes that
            // panel's slot alone, so the sector map keeps the count it was last laid out in.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                ColumnSelection.selectColumnCount(INTEL_SCOPE, CHOICE_KEY);

                verify(memoryMock)
                    .set(INTEL_COLUMN_COUNT_KEY, CHOICE_KEY);
                verify(memoryMock, never())
                    .set(eq(MAP_COLUMN_COUNT_KEY), anyString());
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

                ColumnSelection.selectColumnCount(MAP_SCOPE, CHOICE_KEY);

                // Nothing to assert beyond it not throwing - there is no memory to have written to.
            }
        }
    }
}
