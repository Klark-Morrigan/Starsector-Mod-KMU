package kmu.maplayers.base.sidebar;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the column selection state: the read reports one screen's stored column-count key or none, a
 * pick persists that screen's key and leaves another screen's alone, and both no-op cleanly before the
 * sector exists. Like the sort selection it fires no refresh, so there is nothing to observe beyond
 * what landed in the save. The key is pinned as a literal so a rename that would silently reset every
 * save's column choice fails here rather than shipping.
 */
final class ColumnSelectionTest {

    // Two screens of no particular identity: this store's subject is that a count is one screen's, not
    // which screens the mod has - that is MapLayerScreens' answer and is pinned there.
    private static final ScreenMemoryScope SCREEN_SCOPE = ScreenMemoryScopes.createStandInScreen();

    private static final ScreenMemoryScope OTHER_SCREEN_SCOPE =
        ScreenMemoryScopes.createOtherStandInScreen();

    // The slot that screen composes, as a literal: the base key is a save-serialised identity, so a
    // rename must break this test rather than ship and reset every existing save to one column.
    private static final String KEY = "$kmu_map_list_columns_test";

    // The same base key under the second screen, which is what a pick here must never write.
    private static final String OTHER_KEY = "$kmu_map_list_columns_other";

    private static final String CHOICE_KEY = "2";
    private static final String OTHER_CHOICE_KEY = "3";

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
    class GetColumnCountKey {

        @Test
        void getColumnCountKeyReturnsTheStoredKey() {

            sectorMemoryFake.storeValue(KEY, CHOICE_KEY);

            assertThat(ColumnSelection.getColumnCountKey(SCREEN_SCOPE))
                .isEqualTo(CHOICE_KEY);
        }

        @Test
        void getColumnCountKeyReadsEachScreensOwnCount() {
            // Per-screen isolation: the two panels are two widths the player laid the list out inside,
            // so a count picked on one is invisible to the other rather than re-wrapping both.
            sectorMemoryFake.storeValue(KEY, CHOICE_KEY);
            sectorMemoryFake.storeValue(OTHER_KEY, OTHER_CHOICE_KEY);

            assertThat(ColumnSelection.getColumnCountKey(SCREEN_SCOPE))
                .isEqualTo(CHOICE_KEY);
            assertThat(ColumnSelection.getColumnCountKey(OTHER_SCREEN_SCOPE))
                .isEqualTo(OTHER_CHOICE_KEY);
        }

        @Test
        void getColumnCountKeyIsNullWhenNoCountIsStored() {
            // A save that never picked a count holds no key, which the column choice resolves to its
            // single-column default.
            assertThat(ColumnSelection.getColumnCountKey(SCREEN_SCOPE))
                .isNull();
        }

        @Test
        void getColumnCountKeyIsNullBeforeTheSectorExists() {
            // No sector means no save to read, so nothing can have been picked yet.
            sectorMemoryFake.removeSector();

            assertThat(ColumnSelection.getColumnCountKey(SCREEN_SCOPE))
                .isNull();
        }
    }

    @Nested
    class SelectColumnCount {

        @Test
        void selectColumnCountPersistsTheKey() {

            ColumnSelection.selectColumnCount(SCREEN_SCOPE, CHOICE_KEY);

            assertThat(sectorMemoryFake.readStoredValue(KEY))
                .isEqualTo(CHOICE_KEY);
        }

        @Test
        void selectColumnCountLeavesAnotherScreensCountUntouched() {
            // Per-screen isolation on the write side: a pick made on one panel writes that panel's slot
            // alone, so the other keeps the count it was last laid out in.
            ColumnSelection.selectColumnCount(SCREEN_SCOPE, CHOICE_KEY);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_KEY))
                .isFalse();
        }

        @Test
        void selectColumnCountNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into, so the pick is silently dropped rather than
            // dereferencing a null sector.
            sectorMemoryFake.removeSector();

            ColumnSelection.selectColumnCount(SCREEN_SCOPE, CHOICE_KEY);

            // Nothing to assert beyond it not throwing - there is no memory to have written to.
        }
    }
}
