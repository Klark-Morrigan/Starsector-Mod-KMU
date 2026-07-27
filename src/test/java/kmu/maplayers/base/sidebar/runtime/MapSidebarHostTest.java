package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.input.InputEventAPI;

import kmlib.math.geometry.BoxEdge;
import kmlib.starsector.memory.SectorMemoryAccess;

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
 * Pins the on-map overlay's frame edges and the fold it opens at. Its frozen fold key and its opening
 * default are pinned as literals: the key because renaming it silently returns every existing save to the
 * default, and the default because opening out is what makes the sidebar the visible way in to the
 * political map on a save that has never folded it.
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
    class ResolveBorderEdges {

        @Test
        void resolveBorderEdgesFramesAllFourSides() {
            // The on-map sidebar floats free on the screen, touching no other panel's edge.
            assertThat(MapSidebarHost.INSTANCE.resolveBorderEdges(null)).isEqualTo(BoxEdge.ALL);
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

                MapSidebarHost.INSTANCE.handleKeyPress(eventMock);

                verify(memoryMock).set(MAP_ACTIVE_LAYER_KEY, "political_map");
            }
        }
    }

    @Nested
    class RestoreFoldFromSave {

        @Test
        void restoreFoldFromSaveOpensOutWhenTheSaveHoldsNoFoldYet() {
            // A save that has never folded this panel opens it out: it is the player's primary way in to
            // the political map and has the screen width to sit open.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(DOCKED_KEY)).thenReturn(false);

                MapSidebarHost.INSTANCE.restoreFoldFromSave();

                assertThat(MapSidebarHost.INSTANCE.getController().getCollapseFraction())
                        .isCloseTo(FULLY_EXPANDED, within(TOLERANCE));
                assertThat(MapSidebarHost.INSTANCE.getController().isFullyExpanded()).isTrue();
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

                MapSidebarHost.INSTANCE.restoreFoldFromSave();

                assertThat(MapSidebarHost.INSTANCE.getController().getCollapseFraction())
                        .isCloseTo(FULLY_DOCKED, within(TOLERANCE));
            }
        }

        @Test
        void restoreFoldFromSaveReadsItsOwnKeyRatherThanTheIntelScreensFold() {
            // The two screens' folds are independent, so the on-map panel must not answer to the key the
            // intel panel stores under.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains("$kmu_political_intel_sidebar_docked")).thenReturn(true);
                when(memoryMock.getBoolean("$kmu_political_intel_sidebar_docked")).thenReturn(true);
                when(memoryMock.contains(DOCKED_KEY)).thenReturn(false);

                MapSidebarHost.INSTANCE.restoreFoldFromSave();

                assertThat(MapSidebarHost.INSTANCE.getController().isFullyExpanded()).isTrue();
            }
        }
    }
}
