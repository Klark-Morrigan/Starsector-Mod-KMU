package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.math.geometry.BoxEdge;
import kmlib.starsector.memory.SectorMemoryAccess;
import kmlib.starsector.ui.map.presence.CampaignMapView;
import kmlib.starsector.ui.widgets.tabs.style.TabChrome;
import kmlib.starsector.ui.widgets.tabs.style.TabStyle;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.settings.KmuMapLayerSettings;
import kmu.settings.SidebarSettingsMock;
import kmu.starsector.StarsectorUiColoursMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the on-map overlay's gate, its frame edges, and the fold it opens at. The gate is the sector map
 * showing at all, in either of its looks, so the Starscape filter cannot hide a panel whose overlay is
 * painting - and the screen-claim seam this host was handed, so anything raised over the map takes the
 * panel with it. Its frozen fold key and its opening
 * default are pinned as literals: the key because renaming it silently returns every existing save to the
 * default, and the default because opening out is what makes the sidebar the visible way in to the
 * political map on a save that has never folded it. Its look is pinned at the two places the two screens
 * part - the tab chrome with the hotkey convention belonging to it, and the frame's colour - since a host
 * wired to the other screen's factory would draw the wrong panel with every style test still green.
 */
final class MapSidebarHostTest {

    // The live fold key, pinned as a literal: a rename must break this test rather than shipping and
    // quietly reopening every save's panel at the default.
    private static final String DOCKED_KEY = "$kmu_political_map_sidebar_docked";

    // The frozen key this screen's active-layer pick is stored under, pinned here so the shortcut is shown
    // writing the sector map's own pick rather than the intel screen's.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";

    // A stand-in layer binding: which layers exist is the composition root's business, so the shortcut is
    // pinned against a registered fake rather than a concrete view's real key.
    private static final String SHORTCUT_SETTING_KEY = "kmu_testLayerKey";
    private static final int SHORTCUT_KEYCODE = 25;

    private static final float FULLY_DOCKED = 1f;
    private static final float FULLY_EXPANDED = 0f;
    private static final float TOLERANCE = 0.0001f;

    @Nested
    class IsOverlayShowing {

        @Test
        void isOverlayShowingIsTrueWhileTheSectorMapIsUpWhicheverLookItWears() {
            // The Starscape terrain surfaces paint in that mode, so the overlay these
            // controls drive is on screen in either look. Pinned by the filter read never being made:
            // a gate that consulted it would hide the panel over a map that is plainly painting.
            try (var mapViewMock = mockStatic(CampaignMapView.class)) {

                mapViewMock
                    .when(CampaignMapView::isSectorMapShowing)
                    .thenReturn(true);

                assertThat(createHostOnAnUnclaimedScreen().isOverlayShowing())
                    .isTrue();

                mapViewMock.verify(
                    CampaignMapView::isSectorMapWithStarscapeOff,
                    never());
            }
        }

        @Test
        void isOverlayShowingIsFalseWhenTheSectorMapIsNotShowing() {
            try (var mapViewMock = mockStatic(CampaignMapView.class)) {

                mapViewMock
                    .when(CampaignMapView::isSectorMapShowing)
                    .thenReturn(false);

                assertThat(createHostOnAnUnclaimedScreen().isOverlayShowing())
                    .isFalse();
            }
        }

        @Test
        void isOverlayShowingIsFalseWhileTheSectorMapsScreenIsClaimed() {
            // The claim the host was handed has to be the one its gate reads: a host that dropped the seam
            // and answered on the map alone would leave the panel drawn over whatever claimed the screen,
            // taking the input that thing was raised to receive, with every case above still green.
            try (var mapViewMock = mockStatic(CampaignMapView.class)) {

                mapViewMock
                    .when(CampaignMapView::isSectorMapShowing)
                    .thenReturn(true);

                assertThat(new MapSidebarHost(ScreenClaims.createScreenClaimedByAModal())
                    .isOverlayShowing())
                    .isFalse();
            }
        }

        // The host on a screen nothing has claimed, which is the state every case but the claim's own asks
        // about. A host of its own rather than the live singleton, so the gate is read against a claim this
        // test states rather than against the running game, which no test JVM has.
        private static MapSidebarHost createHostOnAnUnclaimedScreen() {
            return new MapSidebarHost(ScreenClaims.createUnclaimedScreen());
        }
    }

    @Nested
    class HeaderBandHeight {

        @Test
        void headerBandHeightStandsTallEnoughToDrawATabRow() {
            // A band clamped to nothing leaves the panel with no tab row and no way to switch layer, and
            // the style built from this height cannot be reached without a live sector - so the number is
            // pinned here rather than caught on screen.
            assertThat(MapSidebarHost.HEADER_BAND_HEIGHT)
                .isPositive();
        }
    }

    @Nested
    class ResolveWidgetStyle {

        // Held rather than opened per case in a try-with-resources, because every case here needs the
        // same live colours: the look is composed from several of them at once and reads them all
        // whichever field the case then asserts on.
        private StarsectorUiColoursMock uiColoursMock;
        private SidebarSettingsMock sidebarSettingsMock;

        @BeforeEach
        void mockLiveColoursAndSettings() {

            uiColoursMock = StarsectorUiColoursMock.install();
            sidebarSettingsMock = SidebarSettingsMock.install();
        }

        @AfterEach
        void closeLiveColoursAndSettings() {

            sidebarSettingsMock.close();
            uiColoursMock.close();
        }

        @Test
        void resolveWidgetStyleWearsTheSectorMapsStripWithItsKeyUnderlined() {
            // Which look this screen wears is the host's answer, so wiring it to the intel screen's
            // factory would stand the on-map row up as buttons with every style test still green.
            var tabStyle = buildTabStyle();

            assertThat(tabStyle.chrome())
                .isEqualTo(TabChrome.STRIP);
            assertThat(tabStyle.hotkey().isKeyUnderlined())
                .isTrue();
        }

        @Test
        void resolveWidgetStyleFramesThePanelInItsOwnAccent() {
            // This panel floats free with no chrome to match, so its frame takes the same step its
            // controls do - the base accent of whichever scheme the player picked - rather than the
            // recessive dark a panel abutting another screen's frames drops to.
            assertThat(MapSidebarHost.INSTANCE.resolveWidgetStyle().boxColours().border())
                .isEqualTo(StarsectorUiColoursMock.BUTTON_TEXT);
        }

        @Test
        void resolveWidgetStyleStandsTheBandAtThisScreensOwnHeight() {
            // The height is this host's to hold, and the style it hands the paint pass has to be the one
            // its band was laid out against: a host composing its look at the widget default would stand
            // its tabs outside their own band with the case above still green. Pinned against the
            // constant rather than a number, the number itself being dialled against the live screen.
            assertThat(buildTabStyle().headerBandHeight())
                .isEqualTo(MapSidebarHost.HEADER_BAND_HEIGHT);
        }

        // The tab style this screen's look carries, which is the value both its layout and its paint
        // pass read.
        private static TabStyle buildTabStyle() {
            return MapSidebarHost.INSTANCE.resolveWidgetStyle().tabStyle();
        }
    }

    @Nested
    class ResolveBorderEdges {

        @Test
        void resolveBorderEdgesFramesAllFourSides() {
            // The on-map sidebar floats free on the screen, touching no other panel's edge.
            assertThat(MapSidebarHost.INSTANCE.resolveBorderEdges(null))
                .isEqualTo(BoxEdge.ALL);
        }
    }

    @Nested
    class HandleKeyPress {

        @Test
        void handleKeyPressWritesTheSectorMapsOwnPick() {
            // The shared jump reads whichever selection its host was built with, so this pins the wiring
            // that keeps an on-map shortcut on the sector map's tab: swapping the two hosts' selections
            // would leave every other test green while the key moved the intel screen's tab.
            var layerMock = mock(MapLayer.class);

            when(layerMock.getId())
                .thenReturn("political_map");
            when(layerMock.getShortcutSettingKey())
                .thenReturn(SHORTCUT_SETTING_KEY);
            when(layerMock.getDefaultShortcutKeycode())
                .thenReturn(SHORTCUT_KEYCODE);

            MapLayerRegistry.registerLayers(List.of(layerMock), layerMock);

            var eventMock = mock(InputEventAPI.class);

            when(eventMock.getEventValue())
                .thenReturn(SHORTCUT_KEYCODE);

            try (var globalMock = mockStatic(Global.class);
                    var settingsMock = mockStatic(KmuMapLayerSettings.class)) {

                var memoryMock = mock(MemoryAPI.class);
                var sectorMock = mock(SectorAPI.class);

                when(sectorMock.getMemoryWithoutUpdate())
                    .thenReturn(memoryMock);

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);
                settingsMock
                    .when(() -> KmuMapLayerSettings.getMapLayerShortcut(
                        SHORTCUT_SETTING_KEY,
                        SHORTCUT_KEYCODE))
                    .thenReturn(SHORTCUT_KEYCODE);

                MapSidebarHost.INSTANCE.handleKeyPress(eventMock);

                verify(memoryMock)
                    .set(MAP_ACTIVE_LAYER_KEY, "political_map");
            }
        }
    }

    @Nested
    class RestoreFoldFromSave {

        // The reseed composes the panel's sound scheme from the player's own levels, which is a real
        // settings read - so it is stood in for here as it is wherever the look is built, whichever fold
        // the case is about.
        private SidebarSettingsMock sidebarSettingsMock;

        @BeforeEach
        void mockLiveSettings() {
            sidebarSettingsMock = SidebarSettingsMock.install();
        }

        @AfterEach
        void closeLiveSettings() {
            sidebarSettingsMock.close();
        }

        @Test
        void restoreFoldFromSaveOpensOutWhenTheSaveHoldsNoFoldYet() {
            // A save that has never folded this panel opens it out: it is the player's primary way in to
            // the political map and has the screen width to sit open.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(DOCKED_KEY))
                    .thenReturn(false);

                MapSidebarHost.INSTANCE.restoreFoldFromSave();

                assertThat(MapSidebarHost.INSTANCE.getController().getCollapseFraction())
                    .isCloseTo(FULLY_EXPANDED, within(TOLERANCE));
                assertThat(MapSidebarHost.INSTANCE.getController().isFullyExpanded())
                    .isTrue();
            }
        }

        @Test
        void restoreFoldFromSaveOpensDockedWhenTheSaveWasLeftDocked() {
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(DOCKED_KEY))
                    .thenReturn(true);
                when(memoryMock.getBoolean(DOCKED_KEY))
                    .thenReturn(true);

                MapSidebarHost.INSTANCE.restoreFoldFromSave();

                assertThat(MapSidebarHost.INSTANCE.getController().getCollapseFraction())
                    .isCloseTo(FULLY_DOCKED, within(TOLERANCE));
            }
        }

        @Test
        void restoreFoldFromSaveReadsItsOwnKeyRatherThanTheIntelScreensFold() {
            // The two screens' folds are independent, so the on-map panel must not answer to the key the
            // intel panel stores under.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains("$kmu_political_intel_sidebar_docked"))
                    .thenReturn(true);
                when(memoryMock.getBoolean("$kmu_political_intel_sidebar_docked"))
                    .thenReturn(true);
                when(memoryMock.contains(DOCKED_KEY))
                    .thenReturn(false);

                MapSidebarHost.INSTANCE.restoreFoldFromSave();

                assertThat(MapSidebarHost.INSTANCE.getController().isFullyExpanded())
                    .isTrue();
            }
        }
    }
}
