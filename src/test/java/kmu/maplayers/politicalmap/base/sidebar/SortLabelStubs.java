package kmu.maplayers.politicalmap.base.sidebar;

import kmu.util.KmuStrings;

import org.mockito.MockedStatic;

/**
 * Shared test stubbing for the sort selector's row labels. Both the picker test and the sort-selector
 * test build the selector, which reads all five sort-mode labels through {@link KmuStrings}, and an
 * unstubbed lookup returns null - which the selector's label-list copy rejects. Rather than repeat the
 * stubs in each test, both point here, so the mode-to-label wiring the tests assume lives in one place.
 * The direction previews are drawn as triangles rather than strings, so no direction label is stubbed.
 * No test methods, so it adds nothing the nested-tests gate must account for.
 */
final class SortLabelStubs {

    private SortLabelStubs() {
    }

    // Stubs every sort-mode row label, so a test that builds the sort selector gets a full label list
    // rather than a null the copy would reject.
    static void stubSortLabels(MockedStatic<KmuStrings> stringsMock) {
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_SORT_NAME))
                .thenReturn("Name");
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_SORT_DOMINATION))
                .thenReturn("Domination");
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_SORT_PRESENCE))
                .thenReturn("Presence");
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_SORT_SCORE))
                .thenReturn("Score");
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_SORT_MARKET_SIZE))
                .thenReturn("Market size");
    }
}
