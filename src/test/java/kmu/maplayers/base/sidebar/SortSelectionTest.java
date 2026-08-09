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
 * Pins the sort selection state: the reads report one scope's stored mode and direction keys or none,
 * a pick persists each key into that scope's slot alone, and the writes no-op cleanly before the
 * sector exists. No refresh is fired, so there is nothing to verify beyond the memory write. The
 * frozen keys are pinned as literals so a rename that would silently reset every save's sort choice
 * fails here rather than shipping.
 */
final class SortSelectionTest {

    // The scope whose slots every test reads and writes, and a second one that must stay untouched.
    private static final String SCOPE_ID = "scope_a";
    private static final String OTHER_SCOPE_ID = "scope_b";

    // The save-serialised keys of the scope under test, pinned as literals: renaming either prefix
    // resets every existing save's sort choice, so a change must break this test first.
    private static final String SORT_MODE_KEY = "$kmu_map_sort_mode_scope_a";
    private static final String SORT_DIRECTION_KEY = "$kmu_map_sort_direction_scope_a";

    private static final String MODE_KEY = "presence";
    private static final String DIRECTION_KEY = "asc";

    @Nested
    class GetSortModeKeyOf {

        @Test
        void getSortModeKeyOfReturnsTheScopesStoredKey() {
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(SORT_MODE_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(SORT_MODE_KEY))
                    .thenReturn(MODE_KEY);

                assertThat(SortSelection.getSortModeKeyOf(SCOPE_ID))
                    .isEqualTo(MODE_KEY);
            }
        }

        @Test
        void getSortModeKeyOfIsNullForAScopeThatStoredNothing() {
            // Each scope reads its own slot, so a mode picked in one scope must not surface as
            // another's - two vocabularies mean a cross-read key would resolve against nothing.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(SORT_MODE_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(SORT_MODE_KEY))
                    .thenReturn(MODE_KEY);

                assertThat(SortSelection.getSortModeKeyOf(OTHER_SCOPE_ID))
                    .isNull();
            }
        }

        @Test
        void getSortModeKeyOfIsNullWhenNoModeIsStored() {
            // A save that never picked a mode holds no key, which the sort mode resolves to its default.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                assertThat(SortSelection.getSortModeKeyOf(SCOPE_ID))
                    .isNull();
            }
        }

        @Test
        void getSortModeKeyOfIsNullBeforeTheSectorExists() {
            // No sector means no save to read, so nothing can have been picked yet.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                assertThat(SortSelection.getSortModeKeyOf(SCOPE_ID))
                    .isNull();
            }
        }
    }

    @Nested
    class SelectSortMode {

        @Test
        void selectSortModePersistsTheKeyUnderTheScope() {
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                SortSelection.selectSortMode(SCOPE_ID, MODE_KEY);

                verify(memoryMock)
                    .set(SORT_MODE_KEY, MODE_KEY);
            }
        }

        @Test
        void selectSortModeNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into, so the pick is silently dropped rather than
            // dereferencing a null sector.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                SortSelection.selectSortMode(SCOPE_ID, MODE_KEY);

                // Nothing to assert beyond it not throwing - there is no memory to have written to.
            }
        }
    }

    @Nested
    class GetSortDirectionKeyOf {

        @Test
        void getSortDirectionKeyOfReturnsTheScopesStoredKey() {
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(SORT_DIRECTION_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(SORT_DIRECTION_KEY))
                    .thenReturn(DIRECTION_KEY);

                assertThat(SortSelection.getSortDirectionKeyOf(SCOPE_ID))
                    .isEqualTo(DIRECTION_KEY);
            }
        }

        @Test
        void getSortDirectionKeyOfIsNullForAScopeThatStoredNothing() {
            // The direction is partitioned by scope for the same reason the mode is: it belongs to
            // the mode it was flipped against, which is one scope's.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(SORT_DIRECTION_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(SORT_DIRECTION_KEY))
                    .thenReturn(DIRECTION_KEY);

                assertThat(SortSelection.getSortDirectionKeyOf(OTHER_SCOPE_ID))
                    .isNull();
            }
        }

        @Test
        void getSortDirectionKeyOfIsNullWhenNoDirectionIsStored() {
            // A scope whose sort was never flipped holds no key, which the caller resolves to the
            // active mode's default direction.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                assertThat(SortSelection.getSortDirectionKeyOf(SCOPE_ID))
                    .isNull();
            }
        }
    }

    @Nested
    class SelectSortDirection {

        @Test
        void selectSortDirectionPersistsTheKeyUnderTheScope() {
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                SortSelection.selectSortDirection(SCOPE_ID, DIRECTION_KEY);

                verify(memoryMock)
                    .set(SORT_DIRECTION_KEY, DIRECTION_KEY);
            }
        }

        @Test
        void selectSortDirectionNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into, so the flip is silently dropped rather than
            // dereferencing a null sector.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                SortSelection.selectSortDirection(SCOPE_ID, DIRECTION_KEY);

                // Nothing to assert beyond it not throwing - there is no memory to have written to.
            }
        }
    }
}
