package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the sort selection state: the read reports the stored mode key or none, a pick persists the
 * key, and the write no-ops cleanly before the sector exists. Unlike the filter selection this fires
 * no refresh, so there is nothing to verify beyond the memory write. The frozen key is pinned as a
 * literal so a rename that would silently reset every save's sort choice fails here rather than
 * shipping.
 */
final class SortSelectionTest {
    // The save-serialised key, pinned as a literal: renaming it resets every existing save's sort
    // choice back to the default, so a change must break this test first.
    private static final String SORT_MODE_KEY = "$kmu_political_sort_mode";

    private static final String MODE_KEY = "presence";

    @Nested
    class GetSortModeKey {

        @Test
        void getSortModeKeyReturnsTheStoredKey() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(SORT_MODE_KEY)).thenReturn(true);
                when(memoryMock.getString(SORT_MODE_KEY)).thenReturn(MODE_KEY);

                assertThat(SortSelection.getSortModeKey()).isEqualTo(MODE_KEY);
            }
        }

        @Test
        void getSortModeKeyIsNullWhenNoModeIsStored() {
            // A save that never picked a mode holds no key, which the sort mode resolves to its default.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                assertThat(SortSelection.getSortModeKey()).isNull();
            }
        }

        @Test
        void getSortModeKeyIsNullBeforeTheSectorExists() {
            // No sector means no save to read, so nothing can have been picked yet.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                assertThat(SortSelection.getSortModeKey()).isNull();
            }
        }
    }

    @Nested
    class SelectSortMode {

        @Test
        void selectSortModePersistsTheKey() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                SortSelection.selectSortMode(MODE_KEY);

                verify(memoryMock).set(SORT_MODE_KEY, MODE_KEY);
            }
        }

        @Test
        void selectSortModeNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into, so the pick is silently dropped rather than
            // dereferencing a null sector.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                SortSelection.selectSortMode(MODE_KEY);

                // Nothing to assert beyond it not throwing - there is no memory to have written to.
            }
        }
    }
}
