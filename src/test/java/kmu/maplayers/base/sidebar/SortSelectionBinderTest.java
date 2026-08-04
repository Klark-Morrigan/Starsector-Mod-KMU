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
 * Pins the join between the sort model and this mod's save slot, which is the whole of what the
 * binder does: the read hands both stored keys to the model, and the write puts both of a picked
 * sort's keys back. What the keys mean is the model's and is pinned there; the store is mocked, so
 * this reads the pass-through alone.
 *
 * <p>Run over the foreign {@link HazardSortMode} vocabulary, since the binder is no more the
 * political map's than the model it binds.
 */
final class SortSelectionBinderTest {

    private static final ListSortModes<Hazard> MODES =
        new ListSortModes<>(List.of(HazardSortMode.values()), HazardSortMode.ALPHA);

    @Nested
    class ResolveStoredSort {

        @Test
        void resolveStoredSortPassesBothStoredKeysToTheModel() {
            try (MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {

                selectionMock
                    .when(SortSelection::getSortModeKey)
                    .thenReturn(HazardSortMode.SEVERITY.persistenceKey());
                selectionMock
                    .when(SortSelection::getSortDirectionKey)
                    .thenReturn(SortDirection.ASCENDING.persistenceKey());

                assertThat(SortSelectionBinder.resolveStoredSort(MODES))
                    .isEqualTo(new ListSort<>(HazardSortMode.SEVERITY, SortDirection.ASCENDING, MODES));
            }
        }
    }

    @Nested
    class StoreSort {

        @Test
        void storeSortWritesBothOfThePickedSortsKeys() {
            // Both halves are written whichever one a click moved, so the save never holds this
            // pick's direction beside an earlier pick's mode.
            try (MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {

                SortSelectionBinder.storeSort(
                    new ListSort<>(HazardSortMode.RADIUS, SortDirection.ASCENDING, MODES));

                selectionMock.verify(
                    () -> SortSelection.selectSortMode(HazardSortMode.RADIUS.persistenceKey()));
                selectionMock.verify(
                    () -> SortSelection.selectSortDirection(
                        SortDirection.ASCENDING.persistenceKey()));
            }
        }
    }
}
