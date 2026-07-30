package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.refresh.PoliticalMapRefresh;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the name-format preference: the read resolves the stored key back to its choice and falls
 * back to full names when nothing is stored, the write persists the choice's key to the frozen
 * memory key and bumps the map-style revision so the labels re-fit, and both no-op cleanly before
 * the sector exists. The frozen key is pinned as a literal, since renaming it silently resets every
 * existing save's choice.
 */
final class NameFormatPreferenceTest {
    // The live key, pinned as a literal: a rename must break this test rather than shipping and
    // quietly returning every save to full names.
    private static final String NAME_FORMAT_KEY = "$kmu_political_name_format";

    @Nested
    class GetSelectedNameFormat {

        @Test
        void getSelectedNameFormatResolvesTheStoredKeyToItsChoice() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(NAME_FORMAT_KEY)).thenReturn(true);
                when(memoryMock.getString(NAME_FORMAT_KEY))
                        .thenReturn(FactionNameFormatChoice.SHORT.persistenceKey());

                assertThat(NameFormatPreference.getSelectedNameFormat())
                        .isEqualTo(FactionNameFormatChoice.SHORT);
            }
        }

        @Test
        void getSelectedNameFormatIsFullNamesBeforeTheSectorExists() {
            // No sector means no save to read, which the shipped default treats as the long-form
            // name every cluster label carried before a format was picked.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                assertThat(NameFormatPreference.getSelectedNameFormat())
                        .isEqualTo(FactionNameFormatChoice.FULL);
            }
        }
    }

    @Nested
    class SelectNameFormat {

        @Test
        void selectNameFormatPersistsTheChoiceKeyAndRequestsAStyleRefresh() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class);
                    MockedStatic<PoliticalMapRefresh> refreshMock =
                            mockStatic(PoliticalMapRefresh.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                NameFormatPreference.selectNameFormat(FactionNameFormatChoice.SHORT);

                verify(memoryMock).set(NAME_FORMAT_KEY,
                        FactionNameFormatChoice.SHORT.persistenceKey());
                refreshMock.verify(PoliticalMapRefresh::requestMapStyleRefresh);
            }
        }

        @Test
        void selectNameFormatWritesNothingAndRequestsNoRefreshBeforeTheSectorExists() {
            // Before a save there is nothing to write into, so the pick is dropped rather than
            // bumping a revision no overlay would read.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class);
                    MockedStatic<PoliticalMapRefresh> refreshMock =
                            mockStatic(PoliticalMapRefresh.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                NameFormatPreference.selectNameFormat(FactionNameFormatChoice.SHORT);

                verify(memoryMock, never()).set(eq(NAME_FORMAT_KEY), anyString());
                refreshMock.verify(PoliticalMapRefresh::requestMapStyleRefresh, never());
            }
        }
    }
}
