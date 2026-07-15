package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.refresh.FilterSelection;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Pins the view selector's filter-clearing rule: switching from one view to a different one clears the
 * spotlight filter (a stored bloc id means one thing under factions and another under alliances, so it
 * must never carry across), while turning the map off or on leaves any persisted filter intact. The
 * registry, the strings, and the filter selection are stubbed so this drives the selector's click
 * action and pins only what it does to the filter, not how the view or the filter persist.
 */
final class PoliticalMapBodyControlsTest {
    private final PoliticalMapView factionsViewMock = mock(PoliticalMapView.class);
    private final PoliticalMapView alliancesViewMock = mock(PoliticalMapView.class);

    @Nested
    class SelectViewSegment {

        @Test
        void switchingToADifferentViewClearsTheFilter() {
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                stubSelectorViews(registryMock, stringsMock);
                // Factions was selected before the click, alliances after it - a genuine view switch.
                when(PoliticalMapViewRegistry.getSelectedView())
                        .thenReturn(factionsViewMock, alliancesViewMock);

                clickViewSegment(1);

                registryMock.verify(() -> PoliticalMapViewRegistry.toggleView(alliancesViewMock));
                selectionMock.verify(FilterSelection::clearSelection);
            }
        }

        @Test
        void turningTheMapOffLeavesTheFilterIntact() {
            // Re-picking the lit view turns the map off (no view selected after), so the filter
            // persists - it may outlive the map being toggled off, judged later against the next view.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                stubSelectorViews(registryMock, stringsMock);
                when(PoliticalMapViewRegistry.getSelectedView())
                        .thenReturn(factionsViewMock, (PoliticalMapView) null);

                clickViewSegment(0);

                selectionMock.verify(FilterSelection::clearSelection, never());
            }
        }

        @Test
        void turningTheMapOnFromOffLeavesTheFilterIntact() {
            // With the map off nothing was selected before the click; selecting a view leaves any
            // persisted filter for the load heal to judge rather than hard-clearing it here.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                stubSelectorViews(registryMock, stringsMock);
                when(PoliticalMapViewRegistry.getSelectedView())
                        .thenReturn(null, factionsViewMock);

                clickViewSegment(0);

                selectionMock.verify(FilterSelection::clearSelection, never());
            }
        }
    }

    // Fires the selector's click action for the segment at the given index, the path a click on that
    // view's radio row takes - the only way to reach the private selectViewSegment the selector wires.
    private static void clickViewSegment(int segmentIndex) {
        // The selector is a vertical table (an Interactive control), so its click action drives the
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
