package kmu.maplayers.base.sidebar;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import kmu.KmuMod;
import kmu.maplayers.base.layer.ScreenMemoryScopes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the column selection state: the read reports one slot's stored column-count key or none, a
 * pick persists that slot's key and leaves another screen's and another mod's alone, and both no-op
 * cleanly before the sector exists. Like the sort selection it fires no refresh, so there is nothing to
 * observe beyond what landed in the save. The key is pinned as a literal so a rename that would
 * silently reset every save's column choice fails here rather than shipping.
 */
final class ColumnSelectionTest {

    // The slot these cases exercise, under this mod's own namespace so the frozen key below is the one
    // every existing save holds. A stand-in screen: this store's subject is that a count is one
    // screen's, not which screens the mod has - that is MapLayerScreens' answer and is pinned there.
    private static final ScreenSelectionSlot SLOT = new ScreenSelectionSlot(
        KmuMod.MAP_STORE_NAMESPACE,
        ScreenMemoryScopes.createStandInScreen());

    // The two slots that must stay invisible to it: the other screen under this mod, and this screen
    // under another mod's namespace. The second is what the namespace bought - a count is per screen
    // and deliberately not per scope, so before it a second mod's picker re-wrapped this mod's list.
    private static final ScreenSelectionSlot OTHER_SCREEN_SLOT = new ScreenSelectionSlot(
        KmuMod.MAP_STORE_NAMESPACE,
        ScreenMemoryScopes.createOtherStandInScreen());

    private static final ScreenSelectionSlot OTHER_MOD_SLOT = new ScreenSelectionSlot(
        MapLayerStoreNamespaces.createStandInNamespace(),
        ScreenMemoryScopes.createStandInScreen());

    // The keys those slots compose, as literals: the key is a save-serialised identity, so a rename
    // must break this test rather than ship and reset every existing save to one column.
    private static final String KEY = "$kmu_map_list_columns_test";

    // The same store under the second screen and under the second mod, neither of which a pick here
    // may ever write.
    private static final String OTHER_KEY = "$kmu_map_list_columns_other";

    private static final String OTHER_MOD_KEY = "$test_map_list_columns_test";

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

            assertThat(ColumnSelection.getColumnCountKey(SLOT))
                .isEqualTo(CHOICE_KEY);
        }

        @Test
        void getColumnCountKeyReadsEachScreensOwnCount() {
            // Per-screen isolation: the two panels are two widths the player laid the list out inside,
            // so a count picked on one is invisible to the other rather than re-wrapping both.
            sectorMemoryFake.storeValue(KEY, CHOICE_KEY);
            sectorMemoryFake.storeValue(OTHER_KEY, OTHER_CHOICE_KEY);

            assertThat(ColumnSelection.getColumnCountKey(SLOT))
                .isEqualTo(CHOICE_KEY);
            assertThat(ColumnSelection.getColumnCountKey(OTHER_SCREEN_SLOT))
                .isEqualTo(OTHER_CHOICE_KEY);
        }

        @Test
        void getColumnCountKeyReadsEachModsOwnCount() {
            // Per-namespace isolation, which this store had none of: a count is deliberately one
            // screen's whatever list asked for it, so without the namespace a second mod's picker read
            // and re-wrapped this mod's list.
            sectorMemoryFake.storeValue(KEY, CHOICE_KEY);
            sectorMemoryFake.storeValue(OTHER_MOD_KEY, OTHER_CHOICE_KEY);

            assertThat(ColumnSelection.getColumnCountKey(SLOT))
                .isEqualTo(CHOICE_KEY);
            assertThat(ColumnSelection.getColumnCountKey(OTHER_MOD_SLOT))
                .isEqualTo(OTHER_CHOICE_KEY);
        }

        @Test
        void getColumnCountKeyIsNullWhenNoCountIsStored() {
            // A save that never picked a count holds no key, which the column choice resolves to its
            // single-column default.
            assertThat(ColumnSelection.getColumnCountKey(SLOT))
                .isNull();
        }

        @Test
        void getColumnCountKeyIsNullBeforeTheSectorExists() {
            // No sector means no save to read, so nothing can have been picked yet.
            sectorMemoryFake.removeSector();

            assertThat(ColumnSelection.getColumnCountKey(SLOT))
                .isNull();
        }
    }

    @Nested
    class SelectColumnCount {

        @Test
        void selectColumnCountPersistsTheKey() {

            ColumnSelection.selectColumnCount(SLOT, CHOICE_KEY);

            assertThat(sectorMemoryFake.readStoredValue(KEY))
                .isEqualTo(CHOICE_KEY);
        }

        @Test
        void selectColumnCountLeavesAnotherScreensCountUntouched() {
            // Per-screen isolation on the write side: a pick made on one panel writes that panel's slot
            // alone, so the other keeps the count it was last laid out in.
            ColumnSelection.selectColumnCount(SLOT, CHOICE_KEY);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_KEY))
                .isFalse();
        }

        @Test
        void selectColumnCountLeavesAnotherModsCountUntouched() {
            // Per-namespace isolation on the write side: a count picked in one mod's picker leaves
            // every other mod's list laid out as its own player left it.
            ColumnSelection.selectColumnCount(SLOT, CHOICE_KEY);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_MOD_KEY))
                .isFalse();
        }

        @Test
        void selectColumnCountNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into, so the pick is silently dropped rather than
            // dereferencing a null sector.
            sectorMemoryFake.removeSector();

            ColumnSelection.selectColumnCount(SLOT, CHOICE_KEY);

            // Nothing to assert beyond it not throwing - there is no memory to have written to.
        }
    }
}
