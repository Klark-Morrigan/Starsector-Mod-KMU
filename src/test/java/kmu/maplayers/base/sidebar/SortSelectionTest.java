package kmu.maplayers.base.sidebar;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import kmu.maplayers.base.layer.ScreenMemoryScopes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the sort selection state: the reads report one slot's stored mode and direction keys or none, a
 * pick persists each key into that slot alone, and the writes no-op cleanly before the sector exists.
 * No refresh is fired, so there is nothing to observe beyond what landed in the save. The keys are
 * pinned as literals so a rename that would silently reset every save's sort choice fails here rather
 * than shipping.
 */
final class SortSelectionTest {

    // The slot these cases exercise, and the two that must stay invisible to it: the same scope on
    // another screen, and another scope on the same screen. Stand-in screens, because this store's
    // subject is that a slot is a slot and not which screens the mod has.
    private static final SelectionSlot SLOT =
        new SelectionSlot(ScreenMemoryScopes.createStandInScreen(), "scope_a");

    private static final SelectionSlot OTHER_SCREEN_SLOT =
        new SelectionSlot(ScreenMemoryScopes.createOtherStandInScreen(), "scope_a");

    private static final SelectionSlot OTHER_SCOPE_SLOT =
        new SelectionSlot(ScreenMemoryScopes.createStandInScreen(), "scope_b");

    // The keys those slots compose, as literals: renaming either prefix resets every existing save's
    // sort choice, and dropping either axis puts two pickers back on one shared ranking, so any of
    // those changes must break this test first.
    private static final String MODE_KEY = "$kmu_map_sort_mode_scope_a_test";

    private static final String DIRECTION_KEY = "$kmu_map_sort_direction_scope_a_test";

    private static final String OTHER_SCREEN_MODE_KEY = "$kmu_map_sort_mode_scope_a_other";

    private static final String OTHER_SCREEN_DIRECTION_KEY = "$kmu_map_sort_direction_scope_a_other";

    private static final String OTHER_SCOPE_MODE_KEY = "$kmu_map_sort_mode_scope_b_test";

    private static final String STORED_MODE = "presence";
    private static final String OTHER_STORED_MODE = "standing";
    private static final String STORED_DIRECTION = "asc";

    private SectorMemoryFake sectorMemoryFake;

    @BeforeEach
    void openTheSave() {
        sectorMemoryFake = new SectorMemoryFake();
    }

    @AfterEach
    void closeTheSave() {
        sectorMemoryFake.close();
    }

    @Nested
    class GetSortModeKeyOf {

        @Test
        void getSortModeKeyOfReturnsTheSlotsStoredKey() {

            sectorMemoryFake.storeValue(MODE_KEY, STORED_MODE);

            assertThat(SortSelection.getSortModeKeyOf(SLOT))
                .isEqualTo(STORED_MODE);
        }

        @Test
        void getSortModeKeyOfIsNullForAScopeThatStoredNothing() {
            // Each scope reads its own slot, so a mode picked in one scope must not surface as
            // another's - two vocabularies mean a cross-read key would resolve against nothing.
            sectorMemoryFake.storeValue(MODE_KEY, STORED_MODE);

            assertThat(SortSelection.getSortModeKeyOf(OTHER_SCOPE_SLOT))
                .isNull();
        }

        @Test
        void getSortModeKeyOfReadsEachScreensOwnMode() {
            // Per-screen isolation under one scope id: the same list ranked one way on one panel and
            // another way on the other reads back as each panel left it.
            sectorMemoryFake.storeValue(MODE_KEY, STORED_MODE);
            sectorMemoryFake.storeValue(OTHER_SCREEN_MODE_KEY, OTHER_STORED_MODE);

            assertThat(SortSelection.getSortModeKeyOf(SLOT))
                .isEqualTo(STORED_MODE);
            assertThat(SortSelection.getSortModeKeyOf(OTHER_SCREEN_SLOT))
                .isEqualTo(OTHER_STORED_MODE);
        }

        @Test
        void getSortModeKeyOfIsNullWhenNoModeIsStored() {
            // A save that never picked a mode holds no key, which the sort mode resolves to its
            // default.
            assertThat(SortSelection.getSortModeKeyOf(SLOT))
                .isNull();
        }

        @Test
        void getSortModeKeyOfIsNullBeforeTheSectorExists() {
            // No sector means no save to read, so nothing can have been picked yet.
            sectorMemoryFake.removeSector();

            assertThat(SortSelection.getSortModeKeyOf(SLOT))
                .isNull();
        }
    }

    @Nested
    class SelectSortMode {

        @Test
        void selectSortModePersistsTheKeyUnderTheSlot() {

            SortSelection.selectSortMode(SLOT, STORED_MODE);

            assertThat(sectorMemoryFake.readStoredValue(MODE_KEY))
                .isEqualTo(STORED_MODE);
        }

        @Test
        void selectSortModeLeavesTheNeighbouringSlotsUntouched() {
            // Both axes on the write side: a mode picked on one panel's list leaves the same list on
            // the other panel, and every other list on this one, ranked as they were.
            SortSelection.selectSortMode(SLOT, STORED_MODE);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_SCREEN_MODE_KEY))
                .isFalse();
            assertThat(sectorMemoryFake.hasStoredValue(OTHER_SCOPE_MODE_KEY))
                .isFalse();
        }

        @Test
        void selectSortModeNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into, so the pick is silently dropped rather than
            // dereferencing a null sector.
            sectorMemoryFake.removeSector();

            SortSelection.selectSortMode(SLOT, STORED_MODE);

            // Nothing to assert beyond it not throwing - there is no memory to have written to.
        }
    }

    @Nested
    class GetSortDirectionKeyOf {

        @Test
        void getSortDirectionKeyOfReturnsTheSlotsStoredKey() {

            sectorMemoryFake.storeValue(DIRECTION_KEY, STORED_DIRECTION);

            assertThat(SortSelection.getSortDirectionKeyOf(SLOT))
                .isEqualTo(STORED_DIRECTION);
        }

        @Test
        void getSortDirectionKeyOfIsNullForASlotThatStoredNothing() {
            // The direction is partitioned exactly as the mode is: it belongs to the mode it was
            // flipped against, which is one panel's list.
            sectorMemoryFake.storeValue(DIRECTION_KEY, STORED_DIRECTION);

            assertThat(SortSelection.getSortDirectionKeyOf(OTHER_SCREEN_SLOT))
                .isNull();
        }

        @Test
        void getSortDirectionKeyOfIsNullWhenNoDirectionIsStored() {
            // A slot whose sort was never flipped holds no key, which the caller resolves to the
            // active mode's default direction.
            assertThat(SortSelection.getSortDirectionKeyOf(SLOT))
                .isNull();
        }
    }

    @Nested
    class SelectSortDirection {

        @Test
        void selectSortDirectionPersistsTheKeyUnderTheSlot() {

            SortSelection.selectSortDirection(SLOT, STORED_DIRECTION);

            assertThat(sectorMemoryFake.readStoredValue(DIRECTION_KEY))
                .isEqualTo(STORED_DIRECTION);
        }

        @Test
        void selectSortDirectionLeavesAnotherScreensDirectionUntouched() {
            // Per-screen isolation on the write side: a flip made on one panel writes that panel's
            // slot alone, so the other's list keeps the order it was left in.
            SortSelection.selectSortDirection(SLOT, STORED_DIRECTION);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_SCREEN_DIRECTION_KEY))
                .isFalse();
        }

        @Test
        void selectSortDirectionNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into, so the flip is silently dropped rather than
            // dereferencing a null sector.
            sectorMemoryFake.removeSector();

            SortSelection.selectSortDirection(SLOT, STORED_DIRECTION);

            // Nothing to assert beyond it not throwing - there is no memory to have written to.
        }
    }
}
