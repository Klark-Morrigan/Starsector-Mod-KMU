package kmu.maplayers.politicalmap.base.refresh;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.refresh.PoliticalMapRefresh;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the per-view filter selection state: the read reports one view's stored bloc or none, a pick
 * persists that view's frozen key and bumps the filter revision so the overlay repaints, a clear drops
 * the key and bumps too, every mutation no-ops cleanly before the sector exists, the self-heal clears
 * only a stored bloc that is no longer selectable, and the legacy migration carries a pre-per-view
 * save's single shared choice into a view slot. The frozen key prefix is pinned as a literal so a
 * rename that would silently reset every save's filter choice fails here rather than shipping.
 */
final class FilterSelectionTest {
    // The view whose slot these tests exercise; its id composes into the per-view key below.
    private static final String VIEW_ID = "factions";

    // The save-serialised per-view key, pinned as a literal: renaming the prefix drops every existing
    // save's filter choice back to none, so a change must break this test first.
    private static final String SELECTED_BLOC_KEY = "$kmu_political_filter_bloc_factions";

    // The pre-per-view single shared key the migration reads and retires; pinned so its retirement
    // path keeps finding it on an un-migrated save.
    private static final String LEGACY_KEY = "$kmu_political_filter_bloc";

    private static final String BLOC_ID = "hegemony";

    @Nested
    class GetSelectedBlocId {

        @Test
        void getSelectedBlocIdReturnsTheStoredBloc() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(SELECTED_BLOC_KEY)).thenReturn(true);
                when(memoryMock.getString(SELECTED_BLOC_KEY)).thenReturn(BLOC_ID);

                assertThat(FilterSelection.getSelectedBlocId(VIEW_ID)).isEqualTo(BLOC_ID);
            }
        }

        @Test
        void getSelectedBlocIdIsNullWhenNoBlocIsStored() {
            // A view that never picked a bloc holds no key, which is the un-filtered state.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                assertThat(FilterSelection.getSelectedBlocId(VIEW_ID)).isNull();
            }
        }

        @Test
        void getSelectedBlocIdIsNullBeforeTheSectorExists() {
            // No sector means no save to read, so nothing can have been picked yet.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                assertThat(FilterSelection.getSelectedBlocId(VIEW_ID)).isNull();
            }
        }
    }

    @Nested
    class SelectBloc {

        @Test
        void selectBlocPersistsTheChoiceAndRequestsARefresh() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                var revisionBefore = PoliticalMapRefresh.getFilterRevision();

                FilterSelection.selectBloc(VIEW_ID, BLOC_ID);

                verify(memoryMock).set(SELECTED_BLOC_KEY, BLOC_ID);
                // The pick must bump the filter revision, since this sidebar-only choice never moves
                // settingsRevision - that bump is what repaints the overlay live.
                assertThat(PoliticalMapRefresh.getFilterRevision()).isNotEqualTo(revisionBefore);
            }
        }

        @Test
        void selectBlocNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into and nothing painting, so the write and the
            // refresh are both skipped rather than bumping a revision no overlay would read.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);
                var revisionBefore = PoliticalMapRefresh.getFilterRevision();

                FilterSelection.selectBloc(VIEW_ID, BLOC_ID);

                assertThat(PoliticalMapRefresh.getFilterRevision()).isEqualTo(revisionBefore);
            }
        }
    }

    @Nested
    class ClearSelection {

        @Test
        void clearSelectionDropsTheStoredBlocAndRequestsARefresh() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(SELECTED_BLOC_KEY)).thenReturn(true);
                var revisionBefore = PoliticalMapRefresh.getFilterRevision();

                FilterSelection.clearSelection(VIEW_ID);

                verify(memoryMock).unset(SELECTED_BLOC_KEY);
                assertThat(PoliticalMapRefresh.getFilterRevision()).isNotEqualTo(revisionBefore);
            }
        }

        @Test
        void clearSelectionNoOpsWhenNoBlocIsStored() {
            // Nothing to unset and nothing to repaint when the filter was already off, so a clear on
            // an un-filtered view neither touches memory nor bumps the revision.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                var revisionBefore = PoliticalMapRefresh.getFilterRevision();

                FilterSelection.clearSelection(VIEW_ID);

                verify(memoryMock, never()).unset(anyString());
                assertThat(PoliticalMapRefresh.getFilterRevision()).isEqualTo(revisionBefore);
            }
        }

        @Test
        void clearSelectionNoOpsBeforeTheSectorExists() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);
                var revisionBefore = PoliticalMapRefresh.getFilterRevision();

                FilterSelection.clearSelection(VIEW_ID);

                assertThat(PoliticalMapRefresh.getFilterRevision()).isEqualTo(revisionBefore);
            }
        }
    }

    @Nested
    class HealStaleSelection {

        @Test
        void healStaleSelectionClearsABlocThatIsNoLongerSelectable() {
            // A view whose spotlighted faction was removed (or alliance dissolved) between sessions
            // holds a dangling id; the heal drops it so the filter falls back to none.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(SELECTED_BLOC_KEY)).thenReturn(true);
                when(memoryMock.getString(SELECTED_BLOC_KEY)).thenReturn(BLOC_ID);

                FilterSelection.healStaleSelection(VIEW_ID, blocId -> false);

                verify(memoryMock).unset(SELECTED_BLOC_KEY);
            }
        }

        @Test
        void healStaleSelectionKeepsABlocThatIsStillSelectable() {
            // A still-valid pick survives untouched, so the view keeps spotlighting the bloc the
            // player last chose.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(SELECTED_BLOC_KEY)).thenReturn(true);
                when(memoryMock.getString(SELECTED_BLOC_KEY)).thenReturn(BLOC_ID);

                FilterSelection.healStaleSelection(VIEW_ID, blocId -> true);

                verify(memoryMock, never()).unset(anyString());
            }
        }

        @Test
        void healStaleSelectionNoOpsWhenNoBlocIsStored() {
            // Nothing to validate when the filter was off, so the selectable check is never consulted
            // and memory is left as it is.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                FilterSelection.healStaleSelection(VIEW_ID, blocId -> {
                    throw new AssertionError("selectable check must not run without a stored bloc");
                });

                verify(memoryMock, never()).unset(anyString());
            }
        }

        @Test
        void healStaleSelectionNoOpsBeforeTheSectorExists() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                FilterSelection.healStaleSelection(VIEW_ID, blocId -> {
                    throw new AssertionError("selectable check must not run without a sector");
                });

                memoryAccessMock.verify(SectorMemoryAccess::readSectorMemory);
            }
        }
    }

    @Nested
    class MigrateLegacySharedSelection {

        @Test
        void migrateMovesTheLegacyChoiceIntoTheViewSlotAndRetiresTheOldKey() {
            // A pre-per-view save holds its single shared choice under the old key; the migration copies
            // it into the given view's slot and unsets the old key, so the choice survives the split.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(LEGACY_KEY)).thenReturn(true);
                when(memoryMock.getString(LEGACY_KEY)).thenReturn(BLOC_ID);

                FilterSelection.migrateLegacySharedSelection(VIEW_ID);

                verify(memoryMock).set(SELECTED_BLOC_KEY, BLOC_ID);
                verify(memoryMock).unset(LEGACY_KEY);
            }
        }

        @Test
        void migrateNoOpsWhenNoLegacyChoiceIsStored() {
            // An already-migrated save (or one that never filtered) holds no old key, so the migration
            // writes no slot and unsets nothing.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                FilterSelection.migrateLegacySharedSelection(VIEW_ID);

                verify(memoryMock, never()).set(anyString(), anyString());
                verify(memoryMock, never()).unset(anyString());
            }
        }
    }
}
