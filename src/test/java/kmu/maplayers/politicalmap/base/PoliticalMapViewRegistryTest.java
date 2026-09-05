package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRosters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the political-map view registry's contract with fake views and a fake host tab: the view
 * order it hands back, the pick an untouched save resolves to, the off sentinel that turns the map
 * dark, the id it stores on a pick, and the active-view read the terrain plugin gates on - which
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

        when(firstViewMock.getId())
            .thenReturn("first");
        when(secondViewMock.getId())
            .thenReturn("second");
        when(hostTabMock.getId())
            .thenReturn("host");
        when(otherTabMock.getId())
            .thenReturn("other");

        // First view is the default, so an untouched save resolves to it - the map is up the first
        // time the sector map opens. The host tab is registered default-active in the layer
        // registry, so getActiveView's tab gate is up unless a test switches tabs.
        PoliticalMapViewRegistry.registerViews(
            List.of(firstViewMock, secondViewMock),
            firstViewMock,
            hostTabMock);

        MapLayerRosters.replaceRosterWith(hostTabMock, otherTabMock);
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
            try (var globalMock = mockStatic(Global.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                assertThat(PoliticalMapViewRegistry.getSelectedView())
                    .isSameAs(firstViewMock);
            }
        }

        @Test
        void getSelectedViewResolvesTheStoredIdToItsView() {
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);

                when(memoryMock.contains(ACTIVE_VIEW_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY))
                    .thenReturn("second");

                assertThat(PoliticalMapViewRegistry.getSelectedView())
                    .isSameAs(secondViewMock);
            }
        }

        @Test
        void getSelectedViewIsNullForTheOffSentinel() {
            // The empty stored value is the "map off while the tab stays open" state, so no view
            // resolves and the plugin stays dark.
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);

                when(memoryMock.contains(ACTIVE_VIEW_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY))
                    .thenReturn("");

                assertThat(PoliticalMapViewRegistry.getSelectedView())
                    .isNull();
            }
        }

        @Test
        void getSelectedViewFallsBackToTheDefaultForAStaleStoredId() {
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);

                when(memoryMock.contains(ACTIVE_VIEW_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY))
                    .thenReturn("removed_long_ago");

                assertThat(PoliticalMapViewRegistry.getSelectedView())
                    .isSameAs(firstViewMock);
            }
        }
    }

    @Nested
    class GetSelectedViewIndex {

        @Test
        void getSelectedViewIndexIsThePickedViewsPosition() {
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);

                when(memoryMock.contains(ACTIVE_VIEW_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY))
                    .thenReturn("second");

                assertThat(PoliticalMapViewRegistry.getSelectedViewIndex())
                    .isEqualTo(1);
            }
        }

        @Test
        void getSelectedViewIndexIsNoSelectionWhenOff() {
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);

                when(memoryMock.contains(ACTIVE_VIEW_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY))
                    .thenReturn("");

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
            try (var globalMock = mockStatic(Global.class)) {

                globalMock.when(Global::getSector)
                    .thenReturn(null);

                assertThat(PoliticalMapViewRegistry.getActiveView())
                    .isSameAs(firstViewMock);
            }
        }

        @Test
        void getActiveViewIsNullWhenAnotherTabIsActive() {
            // The stored active tab is not the host tab, so the political map does not paint even
            // though a view is selected - switching to No Layer stops the paint without disturbing
            // the stored view.
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);

                when(memoryMock.contains(ACTIVE_LAYER_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(ACTIVE_LAYER_KEY))
                    .thenReturn("other");

                assertThat(PoliticalMapViewRegistry.getActiveView())
                    .isNull();
            }
        }
    }

    @Nested
    class SelectView {

        @Test
        void selectViewStoresThePickedViewsIdWhenItIsNotTheCurrentSelection() {
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);

                // Default selection is the first view, so picking the second switches to it.
                when(memoryMock.contains(ACTIVE_VIEW_KEY))
                    .thenReturn(false);

                PoliticalMapViewRegistry.selectView(secondViewMock);

                verify(memoryMock)
                    .set(ACTIVE_VIEW_KEY, "second");
            }
        }

        @Test
        void selectViewKeepsTheViewSelectedWhenItIsAlreadyTheCurrentSelection() {
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);

                // The first view is the default selection, so re-picking it must rewrite the same id
                // rather than fall to the off sentinel - the view axis is never left empty.
                when(memoryMock.contains(ACTIVE_VIEW_KEY))
                    .thenReturn(false);

                PoliticalMapViewRegistry.selectView(firstViewMock);

                verify(memoryMock)
                    .set(ACTIVE_VIEW_KEY, "first");
            }
        }
    }

    // Stubs a fresh sector whose memory is {@code memoryMock}, so a test drives the registry's reads
    // and writes through one mocked memory without repeating the two-hop wiring.
    private static void linkSectorMemoryTo(MockedStatic<Global> globalMock, MemoryAPI memoryMock) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);

        globalMock
            .when(Global::getSector)
            .thenReturn(sectorMock);
    }
}
