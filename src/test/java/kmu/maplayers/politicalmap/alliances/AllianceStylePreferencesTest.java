package kmu.maplayers.politicalmap.alliances;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefresh;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the two per-save alliance-style toggles: each read returns false before a save exists, each
 * write persists its frozen key to sector memory and bumps the style revision so the overlay
 * repaints, and both no-op cleanly before the sector exists. The frozen keys are pinned as literals
 * so a rename that would silently reset every save's choice fails here rather than shipping.
 */
final class AllianceStylePreferencesTest {
    // The save-serialised keys, pinned as literals: renaming one resets every existing save's choice
    // to off, so a change must break this test first.
    private static final String MUTE_NON_ALLIED_KEY = "$kmu_political_alliance_mute_non_allied";
    private static final String DESATURATE_NON_ALLIED_KEY =
            "$kmu_political_alliance_desaturate_non_allied";

    @Nested
    class IsNonAlliedMuted {

        @Test
        void isNonAlliedMutedReadsTheMuteKeyFromSectorMemory() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.getBoolean(MUTE_NON_ALLIED_KEY)).thenReturn(true);

                assertThat(AllianceStylePreferences.isNonAlliedMuted()).isTrue();
            }
        }

        @Test
        void isNonAlliedMutedIsFalseBeforeTheSectorExists() {
            // No sector means no save to read, which the original un-receded look treats as off.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                assertThat(AllianceStylePreferences.isNonAlliedMuted()).isFalse();
            }
        }
    }

    @Nested
    class IsNonAlliedDesaturated {

        @Test
        void isNonAlliedDesaturatedReadsTheDesaturateKeyFromSectorMemory() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.getBoolean(DESATURATE_NON_ALLIED_KEY)).thenReturn(true);

                assertThat(AllianceStylePreferences.isNonAlliedDesaturated()).isTrue();
            }
        }

        @Test
        void isNonAlliedDesaturatedIsFalseBeforeTheSectorExists() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                assertThat(AllianceStylePreferences.isNonAlliedDesaturated()).isFalse();
            }
        }
    }

    @Nested
    class SetNonAlliedMuted {

        @Test
        void setNonAlliedMutedPersistsTheChoiceAndRequestsARefresh() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                var revisionBefore = PoliticalMapRefresh.getAllianceStyleRevision();

                AllianceStylePreferences.setNonAlliedMuted(true);

                verify(memoryMock).set(MUTE_NON_ALLIED_KEY, true);
                // The flip must bump the style revision, since these sidebar-only toggles never move
                // settingsRevision - that bump is what repaints the overlay live.
                assertThat(PoliticalMapRefresh.getAllianceStyleRevision())
                        .isNotEqualTo(revisionBefore);
            }
        }

        @Test
        void setNonAlliedMutedWritesTheOffChoiceToo() {
            // A clear is persisted as readily as a set, so turning muting off survives reload.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                AllianceStylePreferences.setNonAlliedMuted(false);

                verify(memoryMock).set(MUTE_NON_ALLIED_KEY, false);
            }
        }

        @Test
        void setNonAlliedMutedNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into and nothing painting, so the write and the
            // refresh are both skipped rather than bumping a revision no overlay would read.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);
                var revisionBefore = PoliticalMapRefresh.getAllianceStyleRevision();

                AllianceStylePreferences.setNonAlliedMuted(true);

                assertThat(PoliticalMapRefresh.getAllianceStyleRevision()).isEqualTo(revisionBefore);
            }
        }
    }

    @Nested
    class SetNonAlliedDesaturated {

        @Test
        void setNonAlliedDesaturatedPersistsTheChoiceAndRequestsARefresh() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                var revisionBefore = PoliticalMapRefresh.getAllianceStyleRevision();

                AllianceStylePreferences.setNonAlliedDesaturated(true);

                verify(memoryMock).set(DESATURATE_NON_ALLIED_KEY, true);
                assertThat(PoliticalMapRefresh.getAllianceStyleRevision())
                        .isNotEqualTo(revisionBefore);
            }
        }

        @Test
        void setNonAlliedDesaturatedNoOpsBeforeTheSectorExists() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);
                var revisionBefore = PoliticalMapRefresh.getAllianceStyleRevision();

                AllianceStylePreferences.setNonAlliedDesaturated(true);

                assertThat(PoliticalMapRefresh.getAllianceStyleRevision()).isEqualTo(revisionBefore);
            }
        }
    }
}
