package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.ListSortModes;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.maplayers.base.layer.ScreenMemoryScope;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the join between the sort model and this mod's save slots, which is the whole of what the
 * binder does: the read hands one screen's two stored keys in one scope to the model, and the write
 * puts both of a picked sort's keys back under that same pair. What the keys mean is the model's and
 * is pinned there; the store is mocked, so this reads the pass-through alone.
 *
 * <p>Run over the foreign {@link HazardSortMode} vocabulary, since the binder is no more the
 * political map's than the model it binds.
 */
final class SortSelectionBinderTest {

    // The screen whose slots the read and the write land on.
    private static final ScreenMemoryScope MAP_SCOPE = new ScreenMemoryScope("map");

    // The scope whose slots the read and the write land on.
    private static final String SCOPE_ID = "hazards";

    private static final ListSortModes<Hazard> MODES =
        new ListSortModes<>(List.of(HazardSortMode.values()), HazardSortMode.ALPHA);

    @Nested
    class ResolveStoredSort {

        @Test
        void resolveStoredSortPassesTheScreensStoredKeysToTheModel() {

            try (var selectionMock = mockStatic(SortSelection.class)) {

                selectionMock
                    .when(() -> SortSelection.getSortModeKeyOf(MAP_SCOPE, SCOPE_ID))
                    .thenReturn(HazardSortMode.SEVERITY.persistenceKey());
                selectionMock
                    .when(() -> SortSelection.getSortDirectionKeyOf(MAP_SCOPE, SCOPE_ID))
                    .thenReturn(SortDirection.ASCENDING.persistenceKey());

                assertThat(SortSelectionBinder.resolveStoredSort(MAP_SCOPE, SCOPE_ID, MODES))
                    .isEqualTo(new ListSort<>(HazardSortMode.SEVERITY, SortDirection.ASCENDING, MODES));
            }
        }
    }

    @Nested
    class StoreSort {

        @Test
        void storeSortWritesBothOfThePickedSortsKeysUnderTheScreensScope() {
            // Both halves are written whichever one a click moved, so the save never holds this
            // pick's direction beside an earlier pick's mode, and both land under the one screen and
            // scope so a pick made on one panel never reorders the other's list.
            try (var selectionMock = mockStatic(SortSelection.class)) {

                SortSelectionBinder.storeSort(
                    MAP_SCOPE,
                    SCOPE_ID,
                    new ListSort<>(HazardSortMode.RADIUS, SortDirection.ASCENDING, MODES));

                selectionMock.verify(
                    () -> SortSelection.selectSortMode(
                        MAP_SCOPE, SCOPE_ID, HazardSortMode.RADIUS.persistenceKey()));
                selectionMock.verify(
                    () -> SortSelection.selectSortDirection(
                        MAP_SCOPE, SCOPE_ID, SortDirection.ASCENDING.persistenceKey()));
            }
        }
    }
}
