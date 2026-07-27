package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.math.geometry.BoxEdge;
import kmlib.math.geometry.Rectangle;
import kmlib.starsector.memory.SectorMemoryAccess;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the intel overlay's gate, the frame edges it strokes, and the fold it opens at. The gate is the map
 * visor's rectangle rather than the tab-open read, so the sidebar stays off the sub-tabs that share the
 * intel tab, and it drops again when that visor turns to the starscape, which leaves the controls no
 * visible overlay to drive. The edges drop the borders shared with the visor - the left always (flush against the visor's
 * left edge) and the bottom only when the box reaches the visor's bottom - and keep the top and right,
 * which sit inside the visor. The frozen fold key is pinned as a literal, since renaming it silently
 * re-docks every existing save.
 */
final class IntelSidebarHostTest {
    // A visor with its bottom edge at y = 50, so a box bottom at or within a pixel of 50 is flush with it.
    private static final Rectangle MAP_VISOR = new Rectangle(100f, 50f, 800f, 600f);

    // The live fold key, pinned as a literal: a rename must break this test rather than shipping and
    // quietly re-docking every save that had the rail open.
    private static final String DOCKED_KEY = "$kmu_political_intel_sidebar_docked";

    // The frozen key this screen's active-layer pick is stored under, pinned here so the shortcut is shown
    // writing the intel screen's own pick rather than the sector map's.
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";

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
        void isOverlayShowingIsTrueWhileTheMapVisorIsLitAndOutOfStarscapeMode() {
            var intelScreenFake = new IntelScreenViewFake();
            intelScreenFake.setIntelTabOpen(true);
            intelScreenFake.setMapVisorRect(MAP_VISOR);
            intelScreenFake.setMapStarscapeModeOn(false);

            assertThat(new IntelSidebarHost(intelScreenFake).isOverlayShowing()).isTrue();
        }

        @Test
        void isOverlayShowingIsFalseWhenTheIntelTabIsUpWithNoMapVisor() {
            // The Planets and Factions sub-tabs are the same core tab carrying no visor, so the tab-open
            // read stays true while the rectangle goes away. Gating on the rectangle is what keeps the
            // sidebar off them; gating on the tab-open read would draw it over both.
            var intelScreenFake = new IntelScreenViewFake();
            intelScreenFake.setIntelTabOpen(true);
            intelScreenFake.setMapVisorRect(null);

            assertThat(new IntelSidebarHost(intelScreenFake).isOverlayShowing()).isFalse();
        }

        @Test
        void isOverlayShowingIsFalseWhenTheLitVisorIsInStarscapeMode() {
            // Starscape mode replaces the map with the starfield and suppresses the terrain layers the
            // political overlay rides, so the visor is lit with nothing for the controls to drive.
            var intelScreenFake = new IntelScreenViewFake();
            intelScreenFake.setIntelTabOpen(true);
            intelScreenFake.setMapVisorRect(MAP_VISOR);
            intelScreenFake.setMapStarscapeModeOn(true);

            assertThat(new IntelSidebarHost(intelScreenFake).isOverlayShowing()).isFalse();
        }

        @Test
        void isOverlayShowingIsFalseWhenTheIntelTabIsNotShowing() {
            var intelScreenFake = new IntelScreenViewFake();
            intelScreenFake.setIntelTabOpen(false);

            assertThat(new IntelSidebarHost(intelScreenFake).isOverlayShowing()).isFalse();
        }
    }

    @Nested
    class DecideBorderEdges {

        @Test
        void decideBorderEdgesAlwaysDropsTheLeftEdge() {
            var floatingBox = IntelSidebarHost.decideBorderEdges(400f, MAP_VISOR);
            var flushBox = IntelSidebarHost.decideBorderEdges(MAP_VISOR.y(), MAP_VISOR);

            assertThat(floatingBox).doesNotContain(BoxEdge.LEFT);
            assertThat(flushBox).doesNotContain(BoxEdge.LEFT);
        }

        @Test
        void decideBorderEdgesAlwaysKeepsTheTopAndRightEdges() {
            var edges = IntelSidebarHost.decideBorderEdges(400f, MAP_VISOR);

            assertThat(edges).contains(BoxEdge.TOP, BoxEdge.RIGHT);
        }

        @Test
        void decideBorderEdgesDropsTheBottomEdgeWhenTheBoxSitsOnTheVisorBottom() {
            var edges = IntelSidebarHost.decideBorderEdges(MAP_VISOR.y(), MAP_VISOR);

            assertThat(edges).doesNotContain(BoxEdge.BOTTOM);
        }

        @Test
        void decideBorderEdgesDropsTheBottomEdgeWhenTheBoxIsWithinTheFlushTolerance() {
            // One pixel above the visor bottom still counts as flush, absorbing the padding's rounding.
            var edges = IntelSidebarHost.decideBorderEdges(MAP_VISOR.y() + 1f, MAP_VISOR);

            assertThat(edges).doesNotContain(BoxEdge.BOTTOM);
        }

        @Test
        void decideBorderEdgesKeepsTheBottomEdgeWhenTheBoxFloatsClearOfTheVisorBottom() {
            // Ten pixels above the visor bottom: the box does not reach it, so the bottom border shows.
            var edges = IntelSidebarHost.decideBorderEdges(MAP_VISOR.y() + 10f, MAP_VISOR);

            assertThat(edges).contains(BoxEdge.BOTTOM);
        }

        @Test
        void decideBorderEdgesKeepsTheBottomEdgeWhenThereIsNoVisor() {
            var edges = IntelSidebarHost.decideBorderEdges(400f, null);

            assertThat(edges).contains(BoxEdge.BOTTOM);
        }
    }

    @Nested
    class LayoutBorderEdges {

        @Test
        void layoutBorderEdgesDropsTheLeftEdgeSoTheReservedStripCollapses() {
            // The box sits flush against the visor's left edge, so it reserves no left inset and the content
            // meets the visor rather than leaving a bare strip where the border would have been.
            assertThat(IntelSidebarHost.layoutBorderEdges()).doesNotContain(BoxEdge.LEFT);
        }

        @Test
        void layoutBorderEdgesKeepsTheTopRightAndBottomEdges() {
            // The top and right frame the sidebar inside the visor; the bottom keeps its reserved inset for
            // now (its stroke drops separately on flush, but collapsing the bottom strip is deferred).
            assertThat(IntelSidebarHost.layoutBorderEdges())
                    .contains(BoxEdge.TOP, BoxEdge.RIGHT, BoxEdge.BOTTOM);
        }

        @Test
        void layoutBorderEdgesDropsTheLeftEdgeInStepWithTheStroke() {
            // The reserved edges and the stroked edges must agree on the left, or the box would collapse the
            // left strip while still stroking the border there (or the reverse). Both drop it.
            assertThat(IntelSidebarHost.decideBorderEdges(400f, null))
                    .doesNotContain(BoxEdge.LEFT);
            assertThat(IntelSidebarHost.layoutBorderEdges()).doesNotContain(BoxEdge.LEFT);
        }
    }

    @Nested
    class HandleKeyPress {

        @Test
        void handleKeyPressWritesTheIntelScreensOwnPick() {
            // The shared jump reads whichever selection its host was built with, so this pins the wiring
            // that makes a shortcut pressed on the intel screen move the intel tab: swapping the two hosts'
            // selections would leave every other test green while the key moved the sector map's tab.
            var layerMock = mock(MapLayer.class);
            when(layerMock.getId()).thenReturn("political_map");
            when(layerMock.getShortcutSettingKey()).thenReturn(SHORTCUT_SETTING_KEY);
            when(layerMock.getDefaultShortcutKeycode()).thenReturn(SHORTCUT_KEYCODE);
            MapLayerRegistry.registerLayers(List.of(layerMock), layerMock);
            var eventMock = mock(InputEventAPI.class);
            when(eventMock.getEventValue()).thenReturn(SHORTCUT_KEYCODE);

            try (MockedStatic<Global> globalMock = mockStatic(Global.class);
                    MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                var memoryMock = mock(MemoryAPI.class);
                var sectorMock = mock(SectorAPI.class);
                when(sectorMock.getMemoryWithoutUpdate()).thenReturn(memoryMock);
                globalMock.when(Global::getSector).thenReturn(sectorMock);
                settingsMock.when(() -> KmuLunaSettings.getPoliticalMapLayerShortcut(
                        SHORTCUT_SETTING_KEY, SHORTCUT_KEYCODE)).thenReturn(SHORTCUT_KEYCODE);

                new IntelSidebarHost(new IntelScreenViewFake()).handleKeyPress(eventMock);

                verify(memoryMock).set(INTEL_ACTIVE_LAYER_KEY, "political_map");
            }
        }
    }

    @Nested
    class RestoreFoldFromSave {

        @Test
        void restoreFoldFromSaveOpensDockedWhenTheSaveHoldsNoChoiceYet() {
            // A fresh save has never written the key, so the default applies and the rail stays clear of
            // the visor until the player expands it.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(DOCKED_KEY)).thenReturn(false);

                var host = new IntelSidebarHost(new IntelScreenViewFake());
                host.restoreFoldFromSave();

                assertThat(host.getController().getCollapseFraction())
                        .isCloseTo(FULLY_DOCKED, within(TOLERANCE));
            }
        }

        @Test
        void restoreFoldFromSaveOpensExpandedWhenTheSaveWasLeftWithTheRailOpen() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(DOCKED_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(DOCKED_KEY)).thenReturn(false);

                var host = new IntelSidebarHost(new IntelScreenViewFake());
                host.restoreFoldFromSave();

                assertThat(host.getController().getCollapseFraction())
                        .isCloseTo(FULLY_EXPANDED, within(TOLERANCE));
                assertThat(host.getController().isFullyExpanded()).isTrue();
            }
        }

        @Test
        void restoreFoldFromSaveOpensDockedWhenTheSaveWasLeftDocked() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(DOCKED_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(DOCKED_KEY)).thenReturn(true);

                var host = new IntelSidebarHost(new IntelScreenViewFake());
                host.restoreFoldFromSave();

                assertThat(host.getController().getCollapseFraction())
                        .isCloseTo(FULLY_DOCKED, within(TOLERANCE));
            }
        }

        @Test
        void restoreFoldFromSaveDropsTheFoldTheHostCarriedFromAPreviousSave() {
            // Loading a second save in one run must not inherit the first save's rail: the host is a
            // process-lifetime singleton, so the reseed is the only thing that clears the old fold.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(DOCKED_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(DOCKED_KEY)).thenReturn(false);

                var host = new IntelSidebarHost(new IntelScreenViewFake());
                host.restoreFoldFromSave();
                assertThat(host.getController().isFullyExpanded()).isTrue();

                when(memoryMock.getBoolean(DOCKED_KEY)).thenReturn(true);
                host.restoreFoldFromSave();

                assertThat(host.getController().getCollapseFraction())
                        .isCloseTo(FULLY_DOCKED, within(TOLERANCE));
            }
        }
    }

}
