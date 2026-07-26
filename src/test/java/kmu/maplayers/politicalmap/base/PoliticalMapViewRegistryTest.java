package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the political-map view registry's contract with fake views and a fake host tab: the view
 * order it hands back, the pick an untouched save resolves to, the off sentinel that turns the map
 * dark, the id it stores on a toggle, and the active-view read the terrain plugin gates on - which
 * additionally requires the host tab to be the active pick. The concrete view set is the composition
 * root's concern; this names none.
 */
final class PoliticalMapViewRegistryTest {
    // The frozen sector-memory keys the view selection and the active tab serialise under. Pinned as
    // literals so a rename - which would silently reset every existing save to the default - fails
    // this test rather than shipping.
    private static final String ACTIVE_VIEW_KEY = "$kmu_political_active_view";
    private static final String ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";

    private final PoliticalMapView firstViewMock = mock(PoliticalMapView.class);
    private final PoliticalMapView secondViewMock = mock(PoliticalMapView.class);
    private final MapLayer hostTabMock = mock(MapLayer.class);
    private final MapLayer otherTabMock = mock(MapLayer.class);

    @BeforeEach
    void registerFakeViewsAndTabs() {
        when(firstViewMock.getId()).thenReturn("first");
        when(secondViewMock.getId()).thenReturn("second");
        when(hostTabMock.getId()).thenReturn("host");
        when(otherTabMock.getId()).thenReturn("other");
        // First view is the default, so an untouched save resolves to it - the map is up the first
        // time the sector map opens. The host tab is registered default-active in the layer
        // registry, so getActiveView's tab gate is up unless a test switches tabs.
        PoliticalMapViewRegistry.registerViews(
                List.of(firstViewMock, secondViewMock), firstViewMock, hostTabMock);
        MapLayerRegistry.registerLayers(List.of(hostTabMock, otherTabMock), hostTabMock);
    }

    @Nested
    class GetViews {

        @Test
        void getViewsReturnsTheRegisteredViewsInOrder() {
            assertThat(PoliticalMapViewRegistry.getViews())
                    .containsExactly(firstViewMock, secondViewMock);
        }
    }

    @Nested
    class GetSelectedView {

        @Test
        void getSelectedViewDefaultsToTheRegisteredDefaultWithoutASavedPick() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(PoliticalMapViewRegistry.getSelectedView()).isSameAs(firstViewMock);
            }
        }

        @Test
        void getSelectedViewResolvesTheStoredIdToItsView() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY)).thenReturn("second");

                assertThat(PoliticalMapViewRegistry.getSelectedView()).isSameAs(secondViewMock);
            }
        }

        @Test
        void getSelectedViewIsNullForTheOffSentinel() {
            // The empty stored value is the "map off while the tab stays open" state, so no view
            // resolves and the plugin stays dark.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY)).thenReturn("");

                assertThat(PoliticalMapViewRegistry.getSelectedView()).isNull();
            }
        }

        @Test
        void getSelectedViewFallsBackToTheDefaultForAStaleStoredId() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY)).thenReturn("removed_long_ago");

                assertThat(PoliticalMapViewRegistry.getSelectedView()).isSameAs(firstViewMock);
            }
        }
    }

    @Nested
    class GetSelectedViewIndex {

        @Test
        void getSelectedViewIndexIsThePickedViewsPosition() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY)).thenReturn("second");

                assertThat(PoliticalMapViewRegistry.getSelectedViewIndex()).isEqualTo(1);
            }
        }

        @Test
        void getSelectedViewIndexIsNoSelectionWhenOff() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY)).thenReturn("");

                assertThat(PoliticalMapViewRegistry.getSelectedViewIndex())
                        .isEqualTo(ControlSpec.NO_SELECTION);
            }
        }
    }

    @Nested
    class GetActiveView {

        @Test
        void getActiveViewIsTheSelectedViewWhenTheHostTabIsActive() {
            // No sector: the layer registry resolves its default (the host tab) as active, and the
            // view registry its default view, so the map paints the default view.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(PoliticalMapViewRegistry.getActiveView()).isSameAs(firstViewMock);
            }
        }

        @Test
        void getActiveViewIsNullWhenAnotherTabIsActive() {
            // The stored active tab is not the host tab, so the political map does not paint even
            // though a view is selected - switching to No Layer stops the paint without disturbing
            // the stored view.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(ACTIVE_LAYER_KEY)).thenReturn("other");

                assertThat(PoliticalMapViewRegistry.getActiveView()).isNull();
            }
        }
    }

    @Nested
    class ToggleView {

        @Test
        void toggleViewStoresThePickedViewsIdWhenItIsNotTheCurrentSelection() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                // Default selection is the first view, so toggling the second switches to it.
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(false);

                PoliticalMapViewRegistry.toggleView(secondViewMock);

                verify(memoryMock).set(ACTIVE_VIEW_KEY, "second");
            }
        }

        @Test
        void toggleViewStoresTheOffSentinelWhenTheViewIsAlreadySelected() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                // The first view is the default selection, so toggling it turns the map off.
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(false);

                PoliticalMapViewRegistry.toggleView(firstViewMock);

                verify(memoryMock).set(ACTIVE_VIEW_KEY, "");
            }
        }
    }

    @Nested
    class MigrateLegacyOverlaySelection {
        // The frozen key the pre-view faction-overlay boolean lived under, pinned as a literal so a
        // rename that would break the self-heal fails here rather than silently dropping old saves.
        private static final String LEGACY_KEY = "$kmu_political_faction_overlay_on";

        @Test
        void migrateLegacyOverlaySelectionCarriesAnOffOverlayToTheOffSentinel() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(false);
                when(memoryMock.contains(LEGACY_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(LEGACY_KEY)).thenReturn(false);

                PoliticalMapViewRegistry.migrateLegacyOverlaySelection();

                verify(memoryMock).set(ACTIVE_VIEW_KEY, "");
                verify(memoryMock).unset(LEGACY_KEY);
            }
        }

        @Test
        void migrateLegacyOverlaySelectionCarriesAnOnOverlayToTheDefaultView() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(false);
                when(memoryMock.contains(LEGACY_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(LEGACY_KEY)).thenReturn(true);

                PoliticalMapViewRegistry.migrateLegacyOverlaySelection();

                // The only view that existed pre-migration is the default, so an on overlay pins it.
                verify(memoryMock).set(ACTIVE_VIEW_KEY, "first");
                verify(memoryMock).unset(LEGACY_KEY);
            }
        }

        @Test
        void migrateLegacyOverlaySelectionDoesNothingWhenTheNewKeyIsAlreadyPresent() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(true);

                PoliticalMapViewRegistry.migrateLegacyOverlaySelection();

                verify(memoryMock, never()).set(anyString(), any());
                verify(memoryMock, never()).unset(anyString());
            }
        }

        @Test
        void migrateLegacyOverlaySelectionDoesNothingWithoutALegacyKey() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(false);
                when(memoryMock.contains(LEGACY_KEY)).thenReturn(false);

                PoliticalMapViewRegistry.migrateLegacyOverlaySelection();

                verify(memoryMock, never()).set(anyString(), any());
                verify(memoryMock, never()).unset(anyString());
            }
        }
    }

    // Stubs a fresh sector whose memory is {@code memoryMock}, so a test drives the registry's reads
    // and writes through one mocked memory without repeating the two-hop wiring.
    private static void linkSectorMemoryTo(MockedStatic<Global> globalMock, MemoryAPI memoryMock) {
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getMemoryWithoutUpdate()).thenReturn(memoryMock);
        globalMock.when(Global::getSector).thenReturn(sectorMock);
    }
}
