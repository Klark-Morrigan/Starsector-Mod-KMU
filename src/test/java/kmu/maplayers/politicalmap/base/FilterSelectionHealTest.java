package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.sidebar.FilterSelection;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                registryMock.when(PoliticalMapViewRegistry::getSelectedView).thenReturn(null);

                FilterSelectionHeal.healStaleSelectionAgainstActiveView();

                // No active view means no grouping to judge selectability under, so a persisted filter
                // is left untouched rather than cleared against nothing.
                selectionMock.verify(
                        () -> FilterSelection.healStaleSelection(any(), any()), never());
            }
        }

        @Test
        void healStaleSelectionAgainstActiveViewHealsWithTheActiveViewsSelectableBlocs() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class);
                    MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                globalMock.when(Global::getSector).thenReturn(sectorMock);
                registryMock.when(PoliticalMapViewRegistry::getSelectedView).thenReturn(viewMock);
                when(viewMock.getId()).thenReturn("factions");
                when(viewMock.resolveSelectableBlocs(sectorMock))
                        .thenReturn(List.of(new SelectableBloc("hegemony", "Hegemony", "crest_heg")));

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

    @Nested
    class MigrateLegacySharedSelectionToActiveView {

        @Test
        void migrateCarriesTheLegacyChoiceIntoTheActiveViewsSlot() {
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                registryMock.when(PoliticalMapViewRegistry::getSelectedView).thenReturn(viewMock);
                when(viewMock.getId()).thenReturn("factions");

                FilterSelectionHeal.migrateLegacySharedSelectionToActiveView();

                selectionMock.verify(() -> FilterSelection.migrateLegacySharedSelection("factions"));
            }
        }

        @Test
        void migrateFallsBackToTheDefaultViewWhenTheMapWasSavedOff() {
            // A save made with the map off has no active view to attribute the legacy choice to, so the
            // migration carries it into the default view's slot rather than dropping it.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                registryMock.when(PoliticalMapViewRegistry::getSelectedView).thenReturn(null);
                registryMock.when(PoliticalMapViewRegistry::getDefaultView).thenReturn(viewMock);
                when(viewMock.getId()).thenReturn("factions");

                FilterSelectionHeal.migrateLegacySharedSelectionToActiveView();

                selectionMock.verify(() -> FilterSelection.migrateLegacySharedSelection("factions"));
            }
        }

        @Test
        void migrateDoesNothingBeforeTheViewsAreRegistered() {
            // No selected view and no default means the composition root has not registered views yet,
            // so there is no slot to carry the legacy choice into.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                registryMock.when(PoliticalMapViewRegistry::getSelectedView).thenReturn(null);
                registryMock.when(PoliticalMapViewRegistry::getDefaultView).thenReturn(null);

                FilterSelectionHeal.migrateLegacySharedSelectionToActiveView();

                selectionMock.verify(
                        () -> FilterSelection.migrateLegacySharedSelection(anyString()), never());
            }
        }
    }
}
