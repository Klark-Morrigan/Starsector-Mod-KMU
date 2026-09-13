package kmu.maplayers.base.sidebar;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import kmu.KmuMod;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the per-slot filter selection state: the read reports one slot's stored ID or none, a pick
 * persists that slot's frozen key and bumps the filter revision on the board it was handed so that
 * sector's reading layer repaints, a clear drops the key and bumps too, every mutation no-ops cleanly
 * before the sector exists, and the self-heal clears only a stored ID that is no longer selectable.
 * The keys are pinned as literals so a rename that would silently reset every save's filter choice
 * fails here rather than shipping.
 *
 * <p>Written and read through the real memory rather than a stubbed one, so a value stored under one
 * key and read under another fails here rather than passing on two stubs that agree - which is the
 * whole of what a slot's three axes are for.
 */
final class FilterSelectionTest {

    // The slot these cases exercise, under this mod's own namespace so the frozen keys below are the
    // ones every existing save holds. Stand-in screens, because this store's subject is that a slot is
    // a slot and not which screens the mod has.
    private static final ScreenSelectionSlot SCREEN_SLOT = new ScreenSelectionSlot(
        KmuMod.MAP_STORE_NAMESPACE,
        ScreenMemoryScopes.createStandInScreen());

    private static final SelectionSlot SLOT = new SelectionSlot(SCREEN_SLOT, "scope_a");

    // The three slots that must stay invisible to it: the same scope on another screen, another scope
    // on the same screen, and the same scope on the same screen under another mod's namespace.
    private static final SelectionSlot OTHER_SCREEN_SLOT = new SelectionSlot(
        new ScreenSelectionSlot(
            KmuMod.MAP_STORE_NAMESPACE,
            ScreenMemoryScopes.createOtherStandInScreen()),
        "scope_a");

    private static final SelectionSlot OTHER_SCOPE_SLOT = new SelectionSlot(SCREEN_SLOT, "scope_b");

    private static final SelectionSlot OTHER_MOD_SLOT = new SelectionSlot(
        new ScreenSelectionSlot(
            MapLayerStoreNamespaces.createStandInNamespace(),
            ScreenMemoryScopes.createStandInScreen()),
        "scope_a");

    // The keys those slots compose, as literals: renaming this mod's own segments drops every existing
    // save's filter choice back to none, and dropping any axis puts two pickers back on one shared
    // spotlight, so any of those changes must break this test first.
    private static final String KEY = "$kmu_map_filter_bloc_scope_a_test";

    private static final String OTHER_SCREEN_KEY = "$kmu_map_filter_bloc_scope_a_other";

    private static final String OTHER_SCOPE_KEY = "$kmu_map_filter_bloc_scope_b_test";

    private static final String OTHER_MOD_KEY = "$test_map_filter_bloc_scope_a_test";

    private static final String SELECTED_ID = "picked_a";
    private static final String OTHER_SELECTED_ID = "picked_b";

    private MapLayerRefreshBoard board;
    private SectorMemoryFake sectorMemoryFake;

    @BeforeEach
    void openTheSave() {
        // The board first, and not for tidiness: its logger is resolved once for the life of the JVM,
        // on first use of the class, and a resolution taken while the game's static entry point is
        // stood in for hands it a null that faults every later suite that logs a line.
        board = new MapLayerRefreshBoard();
        sectorMemoryFake = new SectorMemoryFake();
    }

    @AfterEach
    void closeTheSave() {
        sectorMemoryFake.close();
    }

    @Nested
    class GetSelectedIdOf {

        @Test
        void getSelectedIdOfReturnsTheStoredId() {

            sectorMemoryFake.storeValue(KEY, SELECTED_ID);

            assertThat(FilterSelection.getSelectedIdOf(SLOT))
                .isEqualTo(SELECTED_ID);
        }

        @Test
        void getSelectedIdOfIsNullWhenNoIdIsStored() {
            // A slot that never picked an ID holds no key, which is the un-filtered state.
            assertThat(FilterSelection.getSelectedIdOf(SLOT))
                .isNull();
        }

        @Test
        void getSelectedIdOfDoesNotCrossReadAnotherScopesSelection() {
            // Per-scope isolation: a selection stored under one scope is invisible to another, so
            // switching scopes never inherits the previous scope's choice.
            sectorMemoryFake.storeValue(KEY, SELECTED_ID);

            assertThat(FilterSelection.getSelectedIdOf(OTHER_SCOPE_SLOT))
                .isNull();
        }

        @Test
        void getSelectedIdOfDoesNotCrossReadAnotherModsSelection() {
            // Per-namespace isolation: two mods whose pickers happen to list under the same scope
            // string on the same panel still read their own picks, where before the namespace both
            // read one.
            sectorMemoryFake.storeValue(KEY, SELECTED_ID);

            assertThat(FilterSelection.getSelectedIdOf(OTHER_MOD_SLOT))
                .isNull();
        }

        @Test
        void getSelectedIdOfReadsEachScreensOwnSelection() {
            // Per-screen isolation under one scope ID: a bloc spotlighted on one panel and a different
            // one on the other read back as each panel left them.
            sectorMemoryFake.storeValue(KEY, SELECTED_ID);
            sectorMemoryFake.storeValue(OTHER_SCREEN_KEY, OTHER_SELECTED_ID);

            assertThat(FilterSelection.getSelectedIdOf(SLOT))
                .isEqualTo(SELECTED_ID);
            assertThat(FilterSelection.getSelectedIdOf(OTHER_SCREEN_SLOT))
                .isEqualTo(OTHER_SELECTED_ID);
        }

        @Test
        void getSelectedIdOfIsNullBeforeTheSectorExists() {
            // No sector means no save to read, so nothing can have been picked yet.
            sectorMemoryFake.removeSector();

            assertThat(FilterSelection.getSelectedIdOf(SLOT))
                .isNull();
        }
    }

    @Nested
    class SelectId {

        @Test
        void selectIdPersistsTheChoiceAndRaisesOnTheBoardItWasHanded() {

            FilterSelection.selectId(SLOT, SELECTED_ID, board);

            assertThat(sectorMemoryFake.readStoredValue(KEY))
                .isEqualTo(SELECTED_ID);

            // The pick must bump the filter revision, since this sidebar-only choice never moves
            // settingsRevision - that bump is what repaints the reading layer live.
            assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                .isEqualTo(1);
        }

        @Test
        void selectIdWritesTheSlotItWasPickedOnAlone() {
            // Every axis on the write side: a spotlight picked on one panel's list leaves the same list
            // on the other panel, every other list on this one, and another mod's list of the same
            // name, exactly as they were.
            FilterSelection.selectId(SLOT, SELECTED_ID, board);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_SCREEN_KEY))
                .isFalse();
            assertThat(sectorMemoryFake.hasStoredValue(OTHER_SCOPE_KEY))
                .isFalse();
            assertThat(sectorMemoryFake.hasStoredValue(OTHER_MOD_KEY))
                .isFalse();
        }

        @Test
        void selectIdNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into and nothing painting, so the write and the
            // refresh are both skipped rather than bumping a revision no layer would read.
            sectorMemoryFake.removeSector();

            FilterSelection.selectId(SLOT, SELECTED_ID, board);

            assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                .isEqualTo(0);
        }
    }

    @Nested
    class ClearSelection {

        @Test
        void clearSelectionDropsTheStoredIdAndRaisesOnTheBoardItWasHanded() {

            sectorMemoryFake.storeValue(KEY, SELECTED_ID);

            FilterSelection.clearSelection(SLOT, board);

            assertThat(sectorMemoryFake.hasStoredValue(KEY))
                .isFalse();
            assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                .isEqualTo(1);
        }

        @Test
        void clearSelectionNoOpsWhenNoIdIsStored() {
            // Nothing to unset and nothing to repaint when the filter was already off, so a clear on
            // an un-filtered slot bumps no revision.
            FilterSelection.clearSelection(SLOT, board);

            assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                .isEqualTo(0);
        }

        @Test
        void clearSelectionLeavesAnotherSlotsSelectionUntouched() {
            // Isolation on the clear side: clearing a slot with no selection of its own drops nothing,
            // in particular not the neighbouring slots' IDs, and bumps no revision while they hold one.
            sectorMemoryFake.storeValue(OTHER_SCREEN_KEY, SELECTED_ID);
            sectorMemoryFake.storeValue(OTHER_SCOPE_KEY, SELECTED_ID);

            FilterSelection.clearSelection(SLOT, board);

            assertThat(sectorMemoryFake.readStoredValue(OTHER_SCREEN_KEY))
                .isEqualTo(SELECTED_ID);
            assertThat(sectorMemoryFake.readStoredValue(OTHER_SCOPE_KEY))
                .isEqualTo(SELECTED_ID);
            assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                .isEqualTo(0);
        }

        @Test
        void clearSelectionNoOpsBeforeTheSectorExists() {

            sectorMemoryFake.removeSector();

            FilterSelection.clearSelection(SLOT, board);

            assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                .isEqualTo(0);
        }
    }

    @Nested
    class HealStaleSelection {

        @Test
        void healStaleSelectionClearsAnIdThatIsNoLongerSelectable() {
            // A slot whose stored selection stopped being on offer between sessions holds a dangling
            // ID; the heal drops it so the filter falls back to none.
            sectorMemoryFake.storeValue(KEY, SELECTED_ID);

            FilterSelection.healStaleSelection(SLOT, storedId -> false);

            assertThat(sectorMemoryFake.hasStoredValue(KEY))
                .isFalse();
        }

        @Test
        void healStaleSelectionKeepsAnIdThatIsStillSelectable() {
            // A still-valid pick survives untouched, so the slot keeps filtering to the ID the player
            // last chose.
            sectorMemoryFake.storeValue(KEY, SELECTED_ID);

            FilterSelection.healStaleSelection(SLOT, storedId -> true);

            assertThat(sectorMemoryFake.readStoredValue(KEY))
                .isEqualTo(SELECTED_ID);
        }

        @Test
        void healStaleSelectionClearsOnlyTheSlotItWasNamedFor() {
            // The heal is per slot, so a caller owing every screen runs it once each: healing one
            // leaves the other's stored ID standing until its own call comes.
            sectorMemoryFake.storeValue(KEY, SELECTED_ID);
            sectorMemoryFake.storeValue(OTHER_SCREEN_KEY, OTHER_SELECTED_ID);

            FilterSelection.healStaleSelection(SLOT, storedId -> false);

            assertThat(sectorMemoryFake.hasStoredValue(KEY))
                .isFalse();
            assertThat(sectorMemoryFake.readStoredValue(OTHER_SCREEN_KEY))
                .isEqualTo(OTHER_SELECTED_ID);
        }

        @Test
        void healStaleSelectionNoOpsWhenNoIdIsStored() {
            // Nothing to validate when the filter was off, so the selectable check is never consulted
            // and the save is left as it is.
            FilterSelection.healStaleSelection(
                SLOT,
                storedId -> {
                    throw new AssertionError("selectable check must not run without a stored id");
                });

            assertThat(sectorMemoryFake.hasStoredValue(KEY))
                .isFalse();
        }

        @Test
        void healStaleSelectionNoOpsBeforeTheSectorExists() {

            sectorMemoryFake.removeSector();

            FilterSelection.healStaleSelection(
                SLOT,
                storedId -> {
                    throw new AssertionError("selectable check must not run without a sector");
                });

            // Nothing to assert beyond it neither throwing nor consulting the check - there is no
            // memory to have read a stored ID from.
        }
    }
}
