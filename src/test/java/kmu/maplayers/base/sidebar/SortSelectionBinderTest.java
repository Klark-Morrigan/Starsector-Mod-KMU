package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.ListSortModes;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the join between the sort model and this mod's save slots, which is the whole of what the
 * binder does: the read hands the scope's two stored keys to the model, and the write puts both of a
 * picked sort's keys back into that same scope. What the keys mean is the model's and is pinned
 * there; the store is mocked, so this reads the pass-through alone.
 *
 * <p>Run over the foreign {@link HazardSortMode} vocabulary, since the binder is no more the
 * political map's than the model it binds.
 */
final class SortSelectionBinderTest {

    // The scope whose slots the read and the write land on.
    private static final String SCOPE_ID = "hazards";

    private static final ListSortModes<Hazard> MODES =
        new ListSortModes<>(List.of(HazardSortMode.values()), HazardSortMode.ALPHA);

    @Nested
    class ResolveStoredSort {

        @Test
        void resolveStoredSortPassesTheScopesStoredKeysToTheModel() {
            try (MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {

                selectionMock
                    .when(() -> SortSelection.getSortModeKeyOf(SCOPE_ID))
                    .thenReturn(HazardSortMode.SEVERITY.persistenceKey());
                selectionMock
                    .when(() -> SortSelection.getSortDirectionKeyOf(SCOPE_ID))
                    .thenReturn(SortDirection.ASCENDING.persistenceKey());

                assertThat(SortSelectionBinder.resolveStoredSort(SCOPE_ID, MODES))
                    .isEqualTo(new ListSort<>(HazardSortMode.SEVERITY, SortDirection.ASCENDING, MODES));
            }
        }
    }

    @Nested
    class StoreSort {

        @Test
        void storeSortWritesBothOfThePickedSortsKeysUnderTheScope() {
            // Both halves are written whichever one a click moved, so the save never holds this
            // pick's direction beside an earlier pick's mode, and both land in the one scope so a
            // pick made under one picker never reorders another's list.
            try (MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {

                SortSelectionBinder.storeSort(
                    SCOPE_ID,
                    new ListSort<>(HazardSortMode.RADIUS, SortDirection.ASCENDING, MODES));

                selectionMock.verify(
                    () -> SortSelection.selectSortMode(
                        SCOPE_ID, HazardSortMode.RADIUS.persistenceKey()));
                selectionMock.verify(
                    () -> SortSelection.selectSortDirection(
                        SCOPE_ID, SortDirection.ASCENDING.persistenceKey()));
            }
        }
    }
}
