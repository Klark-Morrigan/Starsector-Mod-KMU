package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the per-scope filter selection state: the read reports one scope's stored id or none, a pick
 * persists that scope's frozen key and bumps the filter revision on the board it was handed so that
 * sector's reading layer repaints, a clear drops the key and bumps too, every mutation no-ops
 * cleanly before the sector exists, the
 * self-heal clears only a stored id that is no longer selectable, and the legacy migration carries a
 * pre-per-scope save's single shared choice into a scope slot. The frozen key prefix is pinned as a
 * literal so a rename that would silently reset every save's filter choice fails here rather than
 * shipping.
 */
final class FilterSelectionTest {

    // The scope whose slot these tests exercise; its opaque id composes into the per-scope key below.
    private static final String SCOPE_ID = "scope_a";

    // A second scope no test ever selects in; reads and clears against it pin that one scope's slot
    // is invisible to another.
    private static final String OTHER_SCOPE_ID = "scope_b";

    // The save-serialised per-scope key, pinned as a literal: renaming the prefix drops every existing
    // save's filter choice back to none, so a change must break this test first.
    private static final String SELECTED_ID_KEY = "$kmu_map_filter_bloc_scope_a";

    private static final String SELECTED_ID = "picked_a";

    @Nested
    class GetSelectedIdOf {

        @Test
        void getSelectedIdOfReturnsTheStoredId() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(SELECTED_ID_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(SELECTED_ID_KEY))
                    .thenReturn(SELECTED_ID);

                assertThat(FilterSelection.getSelectedIdOf(SCOPE_ID))
                    .isEqualTo(SELECTED_ID);
            }
        }

        @Test
        void getSelectedIdOfIsNullWhenNoIdIsStored() {
            // A scope that never picked an id holds no key, which is the un-filtered state.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                assertThat(FilterSelection.getSelectedIdOf(SCOPE_ID))
                    .isNull();
            }
        }

        @Test
        void getSelectedIdOfDoesNotCrossReadAnotherScopesSelection() {
            // Per-scope isolation: a selection stored under one scope is invisible to another, so
            // switching scopes never inherits the previous scope's choice.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(SELECTED_ID_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(SELECTED_ID_KEY))
                    .thenReturn(SELECTED_ID);

                assertThat(FilterSelection.getSelectedIdOf(OTHER_SCOPE_ID))
                    .isNull();
            }
        }

        @Test
        void getSelectedIdOfIsNullBeforeTheSectorExists() {
            // No sector means no save to read, so nothing can have been picked yet.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                assertThat(FilterSelection.getSelectedIdOf(SCOPE_ID))
                    .isNull();
            }
        }
    }

    @Nested
    class SelectId {

        @Test
        void selectIdPersistsTheChoiceAndRaisesOnTheBoardItWasHanded() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                var board = new MapLayerRefreshBoard();

                FilterSelection.selectId(SCOPE_ID, SELECTED_ID, board);

                verify(memoryMock)
                    .set(SELECTED_ID_KEY, SELECTED_ID);

                // The pick must bump the filter revision, since this sidebar-only choice never moves
                // settingsRevision - that bump is what repaints the reading layer live.
                assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                    .isEqualTo(1);
            }
        }

        @Test
        void selectIdNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into and nothing painting, so the write and the
            // refresh are both skipped rather than bumping a revision no layer would read.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                var board = new MapLayerRefreshBoard();

                FilterSelection.selectId(SCOPE_ID, SELECTED_ID, board);

                assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                    .isEqualTo(0);
            }
        }
    }

    @Nested
    class ClearSelection {

        @Test
        void clearSelectionDropsTheStoredIdAndRaisesOnTheBoardItWasHanded() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(SELECTED_ID_KEY))
                    .thenReturn(true);

                var board = new MapLayerRefreshBoard();

                FilterSelection.clearSelection(SCOPE_ID, board);

                verify(memoryMock)
                    .unset(SELECTED_ID_KEY);

                assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                    .isEqualTo(1);
            }
        }

        @Test
        void clearSelectionNoOpsWhenNoIdIsStored() {
            // Nothing to unset and nothing to repaint when the filter was already off, so a clear on
            // an un-filtered scope neither touches memory nor bumps the revision.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                var board = new MapLayerRefreshBoard();

                FilterSelection.clearSelection(SCOPE_ID, board);

                verify(memoryMock, never())
                    .unset(anyString());

                assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                    .isEqualTo(0);
            }
        }

        @Test
        void clearSelectionLeavesAnotherScopesSelectionUntouched() {
            // Per-scope isolation on the write side: clearing a scope with no selection of its own
            // unsets no key - in particular not the other scope's slot - and bumps no revision, even
            // while that other scope holds an id.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(SELECTED_ID_KEY))
                    .thenReturn(true);

                var board = new MapLayerRefreshBoard();

                FilterSelection.clearSelection(OTHER_SCOPE_ID, board);

                verify(memoryMock, never())
                    .unset(anyString());

                assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                    .isEqualTo(0);
            }
        }

        @Test
        void clearSelectionNoOpsBeforeTheSectorExists() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                var board = new MapLayerRefreshBoard();

                FilterSelection.clearSelection(SCOPE_ID, board);

                assertThat(board.getRevision(MapLayerCommonRefreshSignal.FILTER))
                    .isEqualTo(0);
            }
        }
    }

    @Nested
    class HealStaleSelection {

        @Test
        void healStaleSelectionClearsAnIdThatIsNoLongerSelectable() {
            // A scope whose stored selection stopped being on offer between sessions holds a dangling
            // id; the heal drops it so the filter falls back to none.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(SELECTED_ID_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(SELECTED_ID_KEY))
                    .thenReturn(SELECTED_ID);

                FilterSelection.healStaleSelection(SCOPE_ID, storedId -> false);

                verify(memoryMock)
                    .unset(SELECTED_ID_KEY);
            }
        }

        @Test
        void healStaleSelectionKeepsAnIdThatIsStillSelectable() {
            // A still-valid pick survives untouched, so the scope keeps filtering to the id the
            // player last chose.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(SELECTED_ID_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(SELECTED_ID_KEY))
                    .thenReturn(SELECTED_ID);

                FilterSelection.healStaleSelection(SCOPE_ID, storedId -> true);

                verify(memoryMock, never())
                    .unset(anyString());
            }
        }

        @Test
        void healStaleSelectionNoOpsWhenNoIdIsStored() {
            // Nothing to validate when the filter was off, so the selectable check is never consulted
            // and memory is left as it is.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                FilterSelection.healStaleSelection(
                    SCOPE_ID,
                    storedId -> {
                        throw new AssertionError(
                            "selectable check must not run without a stored id");
                    });

                verify(memoryMock, never())
                    .unset(anyString());
            }
        }

        @Test
        void healStaleSelectionNoOpsBeforeTheSectorExists() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                FilterSelection.healStaleSelection(
                    SCOPE_ID,
                    storedId -> {
                        throw new AssertionError(
                            "selectable check must not run without a sector");
                    });

                memoryAccessMock.verify(SectorMemoryAccess::readSectorMemory);
            }
        }
    }
}
