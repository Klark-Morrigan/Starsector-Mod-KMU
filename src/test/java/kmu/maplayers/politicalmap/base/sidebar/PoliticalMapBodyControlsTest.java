package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.controls.ReselectBehaviour;

import kmu.maplayers.politicalmap.base.FilterSelectionHeal;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.refresh.FilterSelection;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Pins the view selector's switch rule: each view remembers its own spotlight, so switching toggles the
 * clicked view and heals the switched-in view's slot against its current blocs - never clearing another
 * view's stored selection. The registry, the strings, the filter selection, and the selection heal are
 * stubbed so this drives the selector's click action and pins only what it does, not how the view or the
 * filter persist.
 */
final class PoliticalMapBodyControlsTest {
    private final PoliticalMapView factionsViewMock = mock(PoliticalMapView.class);
    private final PoliticalMapView alliancesViewMock = mock(PoliticalMapView.class);

    @Nested
    class SelectViewSegment {

        @Test
        void switchingToAViewTogglesItThenHealsTheSwitchedInViewsSlot() {
            // The click toggles the clicked view, then heals that view's own slot so a bloc it stored
            // but that has since lapsed does not spotlight an empty footprint. It does not clear - each
            // view keeps its own selection across the switch.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelectionHeal> healMock =
                            mockStatic(FilterSelectionHeal.class)) {
                stubSelectorViews(registryMock, stringsMock);

                clickViewSegment(1);

                registryMock.verify(() -> PoliticalMapViewRegistry.toggleView(alliancesViewMock));
                healMock.verify(FilterSelectionHeal::healStaleSelectionAgainstActiveView);
            }
        }

        @Test
        void switchingViewsNeverClearsAStoredSelection() {
            // The regression this fix targets: a switch must not wipe the filter. selectViewSegment
            // touches no view's stored selection at all - the switched-in view loads its own.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelectionHeal> healMock =
                            mockStatic(FilterSelectionHeal.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                stubSelectorViews(registryMock, stringsMock);

                clickViewSegment(1);

                selectionMock.verifyNoInteractions();
            }
        }

        @Test
        void ignoresASegmentOutsideTheRegisteredViews() {
            // A stray hit past the last view neither toggles a view nor heals, so it changes nothing.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelectionHeal> healMock =
                            mockStatic(FilterSelectionHeal.class)) {
                stubSelectorViews(registryMock, stringsMock);

                clickViewSegment(5);

                registryMock.verify(() -> PoliticalMapViewRegistry.toggleView(any()), never());
                healMock.verifyNoInteractions();
            }
        }
    }

    @Nested
    class BuildViewSelector {

        @Test
        void buildsAHorizontalRadioWithOneSegmentPerView() {
            // The views lay side by side on one row (Factions | Alliances), so the selector is a
            // horizontal radio carrying a segment per registered view in registry order.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubSelectorViews(registryMock, stringsMock);

                var selector = PoliticalMapBodyControls.buildViewSelector();

                assertThat(selector).isInstanceOf(ControlSpec.HorizontalRadio.class);
                assertThat(((ControlSpec.HorizontalRadio) selector).labels())
                        .containsExactly("Factions", "Alliances");
            }
        }

        @Test
        void deselectsOnARepickSoRelightingTheViewTurnsTheMapOff() {
            // Re-picking the lit view must reach the action to turn the overlay off, so the selector
            // carries DESELECT rather than a plain option pair's inert re-pick.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubSelectorViews(registryMock, stringsMock);

                var selector = (ControlSpec.HorizontalRadio) PoliticalMapBodyControls.buildViewSelector();

                assertThat(selector.reselect()).isEqualTo(ReselectBehaviour.DESELECT);
            }
        }
    }

    // Fires the selector's click action for the segment at the given index, the path a click on that
    // view's radio row takes - the only way to reach the private selectViewSegment the selector wires.
    private static void clickViewSegment(int segmentIndex) {
        // The selector is a horizontal radio (an Interactive control), so its click action drives the
        // private selectViewSegment the selector wires.
        var selector = (ControlSpec.Interactive) PoliticalMapBodyControls.buildViewSelector();
        selector.action().activateCell(segmentIndex);
    }

    // Stubs the two registered views and their radio labels, so building the selector and resolving a
    // clicked segment both read the same [Factions, Alliances] order without the live strings table.
    private void stubSelectorViews(MockedStatic<PoliticalMapViewRegistry> registryMock,
            MockedStatic<KmuStrings> stringsMock) {
        when(factionsViewMock.getSegmentLabelKey()).thenReturn("factions_label");
        when(alliancesViewMock.getSegmentLabelKey()).thenReturn("alliances_label");
        registryMock.when(PoliticalMapViewRegistry::getViews)
                .thenReturn(List.of(factionsViewMock, alliancesViewMock));
        registryMock.when(PoliticalMapViewRegistry::getSelectedViewIndex)
                .thenReturn(ControlSpec.NO_SELECTION);
        stringsMock.when(() -> KmuStrings.get("factions_label")).thenReturn("Factions");
        stringsMock.when(() -> KmuStrings.get("alliances_label")).thenReturn("Alliances");
    }
}
