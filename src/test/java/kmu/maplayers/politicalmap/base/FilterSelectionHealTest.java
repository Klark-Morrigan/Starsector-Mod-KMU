package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Pins the glue that heals a loaded save's spotlight selection against its active view: with no view
 * selected the heal is skipped (a persisted filter is left for a later view to judge), and with a view
 * selected the heal runs with a predicate that reports a bloc selectable exactly when the active view
 * still lists it. The view, the sector, and the filter selection are stubbed so this pins the wiring
 * alone, not how the selection actually clears.
 */
final class FilterSelectionHealTest {

    private final PoliticalMapView viewMock = mock(PoliticalMapView.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);

    @Nested
    class HealStaleSelectionAgainstActiveView {

        @Test
        void healStaleSelectionAgainstActiveViewDoesNothingWhenNoViewIsSelected() {
            try (var registryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                registryMock
                    .when(PoliticalMapViewRegistry::getSelectedView)
                    .thenReturn(null);

                FilterSelectionHeal.healStaleSelectionAgainstActiveView();

                // No active view means no grouping to judge selectability under, so a persisted filter
                // is left untouched rather than cleared against nothing.
                selectionMock.verify(
                    () -> FilterSelection.healStaleSelection(any(), any()),
                    never());
            }
        }

        @Test
        void healStaleSelectionAgainstActiveViewHealsWithTheActiveViewsSelectableBlocs() {
            try (var globalMock = mockStatic(Global.class);
                    var registryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                registryMock
                    .when(PoliticalMapViewRegistry::getSelectedView)
                    .thenReturn(viewMock);

                when(viewMock.getId())
                    .thenReturn("factions");

                // The heal matches on the id alone, so the stats half of the option and the
                // vocabulary bundled beside the list both stand in. Stubbed through doReturn
                // because the seam answers a wildcarded picker, whose captured item type a when()
                // stub would have to name.
                doReturn(new ListPicker<>(
                        List.of(new RankedBloc<>(
                            new SelectableBloc("hegemony", "Hegemony", "crest_heg"),
                            DominanceStats.EMPTY)),
                        DominanceSortMode.MODES))
                    .when(viewMock)
                    .resolveBlocPicker(sectorMock);

                FilterSelectionHeal.healStaleSelectionAgainstActiveView();

                // The predicate handed to the heal reports a bloc selectable exactly when the active
                // view still lists it, so a still-listed bloc survives and a vanished one is stale.
                var predicate = capturePredicate(selectionMock);

                assertThat(predicate.test("hegemony")).isTrue();
                assertThat(predicate.test("vanished")).isFalse();
            }
        }
    }

    // Captures the predicate passed to FilterSelection.healStaleSelection, so a test can exercise the
    // selectability rule the glue built from the active view's blocs.
    private static Predicate<String> capturePredicate(MockedStatic<FilterSelection> selectionMock) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Predicate<String>> captor = ArgumentCaptor.forClass(Predicate.class);
        selectionMock.verify(() -> FilterSelection.healStaleSelection(anyString(), captor.capture()));
        return captor.getValue();
    }
}
