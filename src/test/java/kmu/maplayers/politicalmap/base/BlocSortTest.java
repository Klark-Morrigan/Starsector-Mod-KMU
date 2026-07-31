package kmu.maplayers.politicalmap.base;

import kmu.maplayers.base.sidebar.SortDirection;
import kmu.maplayers.base.sidebar.SortSelection;
import kmu.maplayers.politicalmap.base.politics.BlocStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the sort value's resolution and ranking: the stored mode and direction are read together, with
 * an unstored direction falling back to the mode's own default so a save with no direction reads the
 * mode's natural order, and the comparator ranks blocs exactly as the mode does in that direction. The
 * sort selection store is stubbed so the resolution is pinned free of a live save.
 */
final class BlocSortTest {

    @Nested
    class ResolveStored {

        @Test
        void resolveStoredReadsTheStoredModeAndDirection() {
            try (MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                selectionMock.when(SortSelection::getSortModeKey)
                        .thenReturn(BlocSortMode.PRESENCE.persistenceKey());
                selectionMock.when(SortSelection::getSortDirectionKey)
                        .thenReturn(SortDirection.ASCENDING.persistenceKey());

                assertThat(BlocSort.resolveStored())
                        .isEqualTo(new BlocSort(BlocSortMode.PRESENCE, SortDirection.ASCENDING));
            }
        }

        @Test
        void resolveStoredFallsBackToTheDefaultsWhenNothingIsStored() {
            // A save that never picked a sort holds neither key, so the sort resolves to the default
            // mode in that mode's own natural direction.
            try (MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                assertThat(BlocSort.resolveStored()).isEqualTo(new BlocSort(
                        BlocSortMode.DEFAULT, BlocSortMode.DEFAULT.defaultDirection()));
            }
        }

        @Test
        void resolveStoredResolvesAnUnstoredDirectionAgainstTheStoredModesDefault() {
            // A save with a mode but no direction (a pre-direction save, or one that never flipped)
            // reads that mode's own default direction rather than some global default.
            try (MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                selectionMock.when(SortSelection::getSortModeKey)
                        .thenReturn(BlocSortMode.PRESENCE.persistenceKey());

                assertThat(BlocSort.resolveStored()).isEqualTo(new BlocSort(
                        BlocSortMode.PRESENCE, BlocSortMode.PRESENCE.defaultDirection()));
            }
        }
    }

    @Nested
    class Comparator {

        @Test
        void comparatorRanksBlocsAsTheModeDoesInThatDirection() {
            // The sort's comparator is the mode's comparator run in the sort's direction, so ranking a
            // list through the sort matches ranking it through the mode and direction directly.
            var blocs = List.of(
                    new SelectableBloc("a", "A", null, new BlocStats(5, 8, 40, 12)),
                    new SelectableBloc("b", "B", null, new BlocStats(2, 3, 6, 4)));
            var sort = new BlocSort(BlocSortMode.DOMINATION, SortDirection.DESCENDING);

            var rankedBySort = new ArrayList<>(blocs);
            rankedBySort.sort(sort.comparator());
            var rankedByMode = new ArrayList<>(blocs);
            rankedByMode.sort(BlocSortMode.DOMINATION.comparator(SortDirection.DESCENDING));

            assertThat(rankedBySort).isEqualTo(rankedByMode);
        }
    }
}
