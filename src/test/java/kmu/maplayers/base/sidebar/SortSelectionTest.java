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
 * Pins the sort selection state: the reads report one screen's stored mode and direction keys in one
 * scope or none, a pick persists each key into that pair's slot alone, and the writes no-op cleanly
 * before the sector exists. No refresh is fired, so there is nothing to verify beyond the memory
 * write. The frozen keys are pinned as literals so a rename that would silently reset every save's
 * sort choice fails here rather than shipping.
 */
final class SortSelectionTest {

    // The screen whose slots every test reads and writes, and a second one that must stay untouched.
    private static final ScreenMemoryScope MAP_SCOPE = new ScreenMemoryScope("map");
    private static final ScreenMemoryScope INTEL_SCOPE = new ScreenMemoryScope("intel");

    // The scope whose slots every test reads and writes, and a second one that must stay untouched.
    private static final String SCOPE_ID = "scope_a";
    private static final String OTHER_SCOPE_ID = "scope_b";

    // The save-serialised keys of the screen and scope under test, pinned as literals: renaming
    // either prefix resets every existing save's sort choice, and losing the screen segment would put
    // both panels back on one shared ranking, so either change must break this test first.
    private static final String SORT_MODE_KEY = "$kmu_map_sort_mode_scope_a_map";
    private static final String SORT_DIRECTION_KEY = "$kmu_map_sort_direction_scope_a_map";

    private static final String INTEL_SORT_MODE_KEY = "$kmu_map_sort_mode_scope_a_intel";
    private static final String INTEL_SORT_DIRECTION_KEY = "$kmu_map_sort_direction_scope_a_intel";

    private static final String MODE_KEY = "presence";
    private static final String DIRECTION_KEY = "asc";

    private static final String OTHER_MODE_KEY = "standing";

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

                assertThat(SortSelection.getSortModeKeyOf(MAP_SCOPE, SCOPE_ID))
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

                assertThat(SortSelection.getSortModeKeyOf(MAP_SCOPE, OTHER_SCOPE_ID))
                    .isNull();
            }
        }

        @Test
        void getSortModeKeyOfReadsEachScreensOwnMode() {
            // Per-screen isolation under one scope id: the same list ranked one way on the sector map
            // and another on the intel visor reads back as each panel left it.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(SORT_MODE_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(SORT_MODE_KEY))
                    .thenReturn(MODE_KEY);
                when(memoryMock.contains(INTEL_SORT_MODE_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(INTEL_SORT_MODE_KEY))
                    .thenReturn(OTHER_MODE_KEY);

                assertThat(SortSelection.getSortModeKeyOf(MAP_SCOPE, SCOPE_ID))
                    .isEqualTo(MODE_KEY);
                assertThat(SortSelection.getSortModeKeyOf(INTEL_SCOPE, SCOPE_ID))
                    .isEqualTo(OTHER_MODE_KEY);
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

                assertThat(SortSelection.getSortModeKeyOf(MAP_SCOPE, SCOPE_ID))
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

                assertThat(SortSelection.getSortModeKeyOf(MAP_SCOPE, SCOPE_ID))
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

                SortSelection.selectSortMode(MAP_SCOPE, SCOPE_ID, MODE_KEY);

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

                SortSelection.selectSortMode(MAP_SCOPE, SCOPE_ID, MODE_KEY);

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

                assertThat(SortSelection.getSortDirectionKeyOf(MAP_SCOPE, SCOPE_ID))
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

                assertThat(SortSelection.getSortDirectionKeyOf(MAP_SCOPE, OTHER_SCOPE_ID))
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

                assertThat(SortSelection.getSortDirectionKeyOf(MAP_SCOPE, SCOPE_ID))
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

                SortSelection.selectSortDirection(MAP_SCOPE, SCOPE_ID, DIRECTION_KEY);

                verify(memoryMock)
                    .set(SORT_DIRECTION_KEY, DIRECTION_KEY);
            }
        }

        @Test
        void selectSortDirectionLeavesTheOtherScreensDirectionUntouched() {
            // Per-screen isolation on the write side: a flip made on the intel panel writes that
            // panel's slot alone, so the sector map's list keeps the order it was left in.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                SortSelection.selectSortDirection(INTEL_SCOPE, SCOPE_ID, DIRECTION_KEY);

                verify(memoryMock)
                    .set(INTEL_SORT_DIRECTION_KEY, DIRECTION_KEY);
                verify(memoryMock, never())
                    .set(eq(SORT_DIRECTION_KEY), anyString());
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

                SortSelection.selectSortDirection(MAP_SCOPE, SCOPE_ID, DIRECTION_KEY);

                // Nothing to assert beyond it not throwing - there is no memory to have written to.
            }
        }
    }
}
