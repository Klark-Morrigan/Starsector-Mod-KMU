package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the uninhabited-outline preference: the read reports off until the save says otherwise, the
 * write persists the frozen key to sector memory and bumps the map-style revision on the board it
 * was handed so that sector's overlay restyles, and both no-op cleanly before the sector exists. The
 * frozen key is pinned as a literal, since renaming it silently resets every existing save's choice.
 */
final class UninhabitedOutlinePreferenceTest {
    // The live key, pinned as a literal: a rename must break this test rather than shipping and
    // quietly turning every save's outline back off.
    private static final String OUTLINE_KEY = "$kmu_political_uninhabited_outline";

    @Nested
    class IsOutlineDrawn {

        @Test
        void isOutlineDrawnReadsTheOutlineKeyFromSectorMemory() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(OUTLINE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(OUTLINE_KEY)).thenReturn(true);

                assertThat(UninhabitedOutlinePreference.isOutlineDrawn()).isTrue();
            }
        }

        @Test
        void isOutlineDrawnIsFalseBeforeTheSectorExists() {
            // No sector means no save to read, which the shipped default treats as off - only
            // faction-held, independent, and decivilised systems draw.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                assertThat(UninhabitedOutlinePreference.isOutlineDrawn()).isFalse();
            }
        }
    }

    @Nested
    class SetOutlineDrawn {

        @Test
        void setOutlineDrawnPersistsTheChoiceAndRaisesOnTheBoardItWasHanded() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                var board = new MapLayerRefreshBoard();

                UninhabitedOutlinePreference.setOutlineDrawn(true, board);

                verify(memoryMock).set(OUTLINE_KEY, true);
                assertThat(board.getRevision(MapLayerCommonRefreshSignal.MAP_STYLE)).isEqualTo(1);
            }
        }

        @Test
        void setOutlineDrawnWritesNothingAndRaisesNothingBeforeTheSectorExists() {
            // Before a save there is nothing to write into, so the flip is dropped rather than
            // bumping a revision no overlay would read.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);
                var board = new MapLayerRefreshBoard();

                UninhabitedOutlinePreference.setOutlineDrawn(true, board);

                verify(memoryMock, never()).set(eq(OUTLINE_KEY), anyBoolean());
                assertThat(board.getRevision(MapLayerCommonRefreshSignal.MAP_STYLE)).isEqualTo(0);
            }
        }
    }
}
