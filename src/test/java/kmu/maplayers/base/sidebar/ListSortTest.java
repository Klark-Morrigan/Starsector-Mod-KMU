package kmu.maplayers.base.sidebar;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the framework's sort resolution and ranking over modes the political map does not declare:
 * the stored mode key is matched against whatever mode set the caller passes, the stored direction
 * resolves against that mode's own default, and the comparator ranks the caller's items exactly as
 * the mode does in that direction. Exercised on the foreign {@link HazardSortMode} set throughout,
 * since a suite that reached for the political enum would re-couple what the seam separates. The
 * sort selection store is stubbed so the resolution is pinned free of a live save.
 */
final class ListSortTest {
    // The foreign vocabulary every resolution here runs against: the mode set bundled with its
    // default, alpha standing in as the fallback the assertions read back.
    private static final HazardSortMode DEFAULT_MODE = HazardSortMode.ALPHA;
    private static final ListSortModes<Hazard> MODES =
        new ListSortModes<>(List.of(HazardSortMode.values()), DEFAULT_MODE);

    @Nested
    class ResolveStored {

        @Test
        void resolveStoredReadsTheStoredModeAndDirection() {
            try (MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {

                selectionMock
                    .when(SortSelection::getSortModeKey)
                    .thenReturn(HazardSortMode.SEVERITY.persistenceKey());
                selectionMock
                    .when(SortSelection::getSortDirectionKey)
                    .thenReturn(SortDirection.ASCENDING.persistenceKey());

                assertThat(ListSort.resolveStored(MODES))
                    .isEqualTo(new ListSort<>(HazardSortMode.SEVERITY, SortDirection.ASCENDING));
            }
        }

        @Test
        void resolveStoredFallsBackToTheCallersDefaultWhenNothingIsStored() {
            // A save that never picked a sort holds neither key, so the sort resolves to the
            // caller's default mode in that mode's own natural direction.
            try (MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {

                assertThat(ListSort.resolveStored(MODES))
                    .isEqualTo(new ListSort<>(DEFAULT_MODE, DEFAULT_MODE.defaultDirection()));
            }
        }

        @Test
        void resolveStoredFallsBackToTheCallersDefaultWhenTheKeyIsUnrecognised() {
            // A key left by an older or a modded build names no mode in the caller's set, so the
            // sort defaults rather than failing on it.
            try (MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {

                selectionMock
                    .when(SortSelection::getSortModeKey)
                    .thenReturn("no_such_mode");

                assertThat(ListSort.resolveStored(MODES))
                    .isEqualTo(new ListSort<>(DEFAULT_MODE, DEFAULT_MODE.defaultDirection()));
            }
        }

        @Test
        void resolveStoredResolvesAnUnstoredDirectionAgainstTheStoredModesDefault() {
            // A save with a mode but no direction (a pre-direction save, or one that never flipped)
            // reads that mode's own default direction rather than some global default.
            try (MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {

                selectionMock
                    .when(SortSelection::getSortModeKey)
                    .thenReturn(HazardSortMode.SEVERITY.persistenceKey());

                assertThat(ListSort.resolveStored(MODES))
                    .isEqualTo(new ListSort<>(
                        HazardSortMode.SEVERITY, HazardSortMode.SEVERITY.defaultDirection()));
            }
        }
    }

    @Nested
    class Comparator {

        @Test
        void comparatorRanksItemsUnderTheSortsModeAndDirection() {
            // The picker sorts under a mode the political map does not declare - the proof the
            // ranking mechanism is the caller's to fill. Severity descending leads with the harsher
            // hazard; the flipped sort reverses the pair.
            var mild = new Hazard("Mild", 1, 5);
            var harsh = new Hazard("Harsh", 9, 5);
            var descending = new ListSort<>(HazardSortMode.SEVERITY, SortDirection.DESCENDING);
            var ascending = new ListSort<>(HazardSortMode.SEVERITY, SortDirection.ASCENDING);

            assertThat(rankedBy(descending, mild, harsh))
                .containsExactly(harsh, mild);
            assertThat(rankedBy(ascending, mild, harsh))
                .containsExactly(mild, harsh);
        }

        @Test
        void comparatorRanksItemsAsTheModeDoesInThatDirection() {
            // The sort's comparator is the mode's comparator run in the sort's direction, so
            // ranking a list through the sort matches ranking it through the mode directly.
            var near = new Hazard("Near", 3, 2);
            var far = new Hazard("Far", 3, 8);
            var sort = new ListSort<>(HazardSortMode.RADIUS, SortDirection.DESCENDING);

            var rankedByMode = new ArrayList<>(List.of(near, far));
            rankedByMode.sort(HazardSortMode.RADIUS.comparator(SortDirection.DESCENDING));

            assertThat(rankedBy(sort, near, far))
                .isEqualTo(rankedByMode);
        }
    }

    // The hazards ranked under the sort's own comparator, so an assertion reads the resulting
    // arrangement without repeating the copy-and-sort plumbing.
    private static List<Hazard> rankedBy(ListSort<Hazard> sort, Hazard... hazards) {
        var ranked = new ArrayList<>(List.of(hazards));
        ranked.sort(sort.comparator());
        return ranked;
    }
}
