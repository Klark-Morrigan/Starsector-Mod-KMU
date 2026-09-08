package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.layer.ActiveLayerSelection;
import kmu.maplayers.base.layer.ControlBackedMapLayerVisibility;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRosters;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.MapLayerVisibility;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the political-map view registry's contract with fake views and a fake host tab: the view
 * order it hands back, the pick an untouched save resolves to, the off sentinel that turns the map
 * dark, the id it stores on a pick, and the active-view read the terrain plugin gates on - which
 * additionally requires the host tab to be the active pick. The concrete view set is the composition
 * root's concern; this names none.
 *
 * <p>And that the pick is one per screen. Every read and write names the screen it means, so two
 * panels set to different views hold them apart; the live reads name the screen showing, and the
 * carried read names a screen handed in - which is what lets a frame take the view and the screen's
 * scope off one reading of which panel is up.
 */
final class PoliticalMapViewRegistryTest {

    // The frozen sector-memory keys the view selection and the active tab serialise under, each
    // composed for one screen. Pinned as literals so a rename - which would silently reset every
    // existing save to the default - fails this test rather than shipping.
    private static final String ACTIVE_VIEW_KEY = "$kmu_political_active_view_test";
    private static final String OTHER_SCREEN_ACTIVE_VIEW_KEY = "$kmu_political_active_view_other";
    private static final String MAP_ACTIVE_VIEW_KEY = "$kmu_political_active_view_map";
    private static final String INTEL_ACTIVE_VIEW_KEY = "$kmu_political_active_view_intel";
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";

    // The screen a case is about when its subject is anything but which of the mod's two screens holds
    // the pick, and a second beside it for the cases whose subject is that two screens hold theirs
    // apart.
    private static final ScreenMemoryScope SCREEN = ScreenMemoryScopes.createStandInScreen();
    private static final ScreenMemoryScope OTHER_SCREEN =
        ScreenMemoryScopes.createOtherStandInScreen();

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

    // The intel-screen binding is static, so a case that posed the visor open would otherwise leave
    // every later read in the JVM answering off the intel screen.
    @AfterEach
    void closeTheIntelScreen() {
        MapLayerScreens.registerIntelScreen(null);
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

                assertThat(PoliticalMapViewRegistry.getSelectedView(SCREEN))
                    .isSameAs(firstViewMock);
            }
        }

        @Test
        void getSelectedViewResolvesTheStoredIdToItsView() {
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);
                storeViewIdAt(memoryMock, ACTIVE_VIEW_KEY, "second");

                assertThat(PoliticalMapViewRegistry.getSelectedView(SCREEN))
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
                storeViewIdAt(memoryMock, ACTIVE_VIEW_KEY, "");

                assertThat(PoliticalMapViewRegistry.getSelectedView(SCREEN))
                    .isNull();
            }
        }

        @Test
        void getSelectedViewFallsBackToTheDefaultForAStaleStoredId() {
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);
                storeViewIdAt(memoryMock, ACTIVE_VIEW_KEY, "removed_long_ago");

                assertThat(PoliticalMapViewRegistry.getSelectedView(SCREEN))
                    .isSameAs(firstViewMock);
            }
        }

        @Test
        void getSelectedViewIsTheGivenScreensOwnPick() {
            // The point of the screen being on the signature: a player who sets one panel to the
            // alliances view and leaves the other on factions gets both, rather than the second panel
            // following the first.
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);
                storeViewIdAt(memoryMock, ACTIVE_VIEW_KEY, "second");
                storeViewIdAt(memoryMock, OTHER_SCREEN_ACTIVE_VIEW_KEY, "first");

                assertThat(PoliticalMapViewRegistry.getSelectedView(SCREEN))
                    .isSameAs(secondViewMock);
                assertThat(PoliticalMapViewRegistry.getSelectedView(OTHER_SCREEN))
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
                storeViewIdAt(memoryMock, ACTIVE_VIEW_KEY, "second");

                assertThat(PoliticalMapViewRegistry.getSelectedViewIndex(SCREEN))
                    .isEqualTo(1);
            }
        }

        @Test
        void getSelectedViewIndexIsNoSelectionWhenOff() {
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);
                storeViewIdAt(memoryMock, ACTIVE_VIEW_KEY, "");

                assertThat(PoliticalMapViewRegistry.getSelectedViewIndex(SCREEN))
                    .isEqualTo(ControlSpec.NO_SELECTION);
            }
        }

        @Test
        void getSelectedViewIndexLightsTheGivenScreensOwnSegment() {
            // The radio on each panel lights that panel's pick, so two panels on different views
            // light different segments at the same time.
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);
                storeViewIdAt(memoryMock, ACTIVE_VIEW_KEY, "second");
                storeViewIdAt(memoryMock, OTHER_SCREEN_ACTIVE_VIEW_KEY, "first");

                assertThat(PoliticalMapViewRegistry.getSelectedViewIndex(SCREEN))
                    .isEqualTo(1);
                assertThat(PoliticalMapViewRegistry.getSelectedViewIndex(OTHER_SCREEN))
                    .isZero();
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
                storeViewIdAt(memoryMock, MAP_ACTIVE_LAYER_KEY, "other");

                assertThat(PoliticalMapViewRegistry.getActiveView())
                    .isNull();
            }
        }

        @Test
        void getActiveViewIsTheShowingScreensOwnPick() {
            // The live read follows the panel the player is looking at, so opening the visor over a
            // sector map set to another view paints what the visor's own panel was set to.
            var intelScreenFake = new IntelScreenViewFake();
            intelScreenFake.setIntelTabOpen(true);
            MapLayerScreens.registerIntelScreen(intelScreenFake);

            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);
                storeViewIdAt(memoryMock, MAP_ACTIVE_VIEW_KEY, "first");
                storeViewIdAt(memoryMock, INTEL_ACTIVE_VIEW_KEY, "second");

                assertThat(PoliticalMapViewRegistry.getActiveView())
                    .isSameAs(secondViewMock);
            }
        }
    }

    @Nested
    class ResolveActiveViewOn {

        @Test
        void resolveActiveViewOnAnswersForTheScreenHandedInRatherThanTheShowingOne() {
            // What a frame carries its screen for: the view it paints and the preferences it bakes
            // under come off one reading of which panel is up. The visor is posed open so a read
            // resolving its own screen would answer the intel pick and fail here.
            var intelScreenFake = new IntelScreenViewFake();
            intelScreenFake.setIntelTabOpen(true);
            MapLayerScreens.registerIntelScreen(intelScreenFake);

            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);
                storeViewIdAt(memoryMock, ACTIVE_VIEW_KEY, "second");
                storeViewIdAt(memoryMock, INTEL_ACTIVE_VIEW_KEY, "first");

                assertThat(PoliticalMapViewRegistry.resolveActiveViewOn(picksOnTheHostTab(SCREEN)))
                    .isSameAs(secondViewMock);
            }
        }

        @Test
        void resolveActiveViewOnIsNullWhileThatScreenIsOnAnotherTab() {
            // The tab gate is that screen's too: a panel switched to No Layer paints nothing, whatever
            // view it has stored and whatever the other panel is on.
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);
                storeViewIdAt(memoryMock, ACTIVE_VIEW_KEY, "second");

                assertThat(PoliticalMapViewRegistry.resolveActiveViewOn(picksOnTab(SCREEN, otherTabMock)))
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

                PoliticalMapViewRegistry.selectView(SCREEN, secondViewMock);

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

                PoliticalMapViewRegistry.selectView(SCREEN, firstViewMock);

                verify(memoryMock)
                    .set(ACTIVE_VIEW_KEY, "first");
            }
        }

        @Test
        void selectViewLeavesEveryOtherScreensPickAlone() {
            // A click lands on one panel's radio, so it writes that panel's slot and no other - the
            // other panel stays on whatever it was set to.
            try (var globalMock = mockStatic(Global.class)) {

                var memoryMock = mock(MemoryAPI.class);

                linkSectorMemoryTo(globalMock, memoryMock);

                PoliticalMapViewRegistry.selectView(SCREEN, secondViewMock);

                verify(memoryMock)
                    .set(ACTIVE_VIEW_KEY, "second");
                verify(memoryMock, never())
                    .set(OTHER_SCREEN_ACTIVE_VIEW_KEY, "second");
            }
        }
    }

    // One screen's picks with the political-map tab as its active pick, which is the arrangement the
    // carried read's tab gate is up under.
    private ScreenLayerPicks picksOnTheHostTab(ScreenMemoryScope memoryScope) {
        return picksOnTab(memoryScope, hostTabMock);
    }

    // One screen's picks set to the given tab, its layers shown - no control has been stood on it, and
    // a screen without one is read as showing whatever it has stored.
    private static ScreenLayerPicks picksOnTab(ScreenMemoryScope memoryScope, MapLayer activeTab) {

        var layerSelectionMock = mock(ActiveLayerSelection.class);

        when(layerSelectionMock.getActiveLayer())
            .thenReturn(activeTab);

        return new ScreenLayerPicks(
            layerSelectionMock,
            new ControlBackedMapLayerVisibility(mock(MapLayerVisibility.class)),
            memoryScope);
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

    // Puts one stored id in one slot, the two stubs a present key needs. Named as the pair they are so
    // a case posing two screens' slots reads as two picks rather than as four stubs.
    private static void storeViewIdAt(MemoryAPI memoryMock, String memoryKey, String storedId) {

        when(memoryMock.contains(memoryKey))
            .thenReturn(true);
        when(memoryMock.getString(memoryKey))
            .thenReturn(storedId);
    }
}
