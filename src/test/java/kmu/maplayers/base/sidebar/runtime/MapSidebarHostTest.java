package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.math.geometry.BoxEdge;
import kmlib.starsector.memory.SectorMemoryAccess;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
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
