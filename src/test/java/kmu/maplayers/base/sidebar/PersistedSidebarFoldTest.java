package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.layer.ScreenMemoryScope;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the persisted sidebar fold: the key it composes for the screen it was built for, that a save which
 * has never stored one reads the opening default each screen was given, that a stored fold wins over that
 * default, and that a write reaches the save only where the offered fold differs from the one already there
 * - so a panel offering its fold every frame writes once per real change.
 */
final class PersistedSidebarFoldTest {

    // A stand-in screen, since which screens exist is the composition root's business: what is pinned here
    // is that the fold composes its slot from whichever it was handed, and the two live screens' own keys
    // are pinned in their hosts' suites.
    private static final ScreenMemoryScope SCREEN_SCOPE = new ScreenMemoryScope("test");

    // The slot that scope composes, as a literal: the base key is a save-serialised identity, so a rename
    // must break this test rather than ship and quietly reopen every save's panel at its default.
    private static final String FOLD_KEY = "$kmu_political_sidebar_docked_test";

    private static final boolean OPENS_DOCKED = true;
    private static final boolean OPENS_OUT = false;

    @Nested
    class IsRailDocked {

        @Test
        void isRailDockedFallsBackToTheOpeningDefaultWhenTheSaveHoldsNoFold() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(FOLD_KEY))
                    .thenReturn(false);

                assertThat(new PersistedSidebarFold(SCREEN_SCOPE, OPENS_DOCKED).isRailDocked())
                    .isTrue();
                assertThat(new PersistedSidebarFold(SCREEN_SCOPE, OPENS_OUT).isRailDocked())
                    .isFalse();
            }
        }

        @Test
        void isRailDockedReadsTheStoredFoldOverTheOpeningDefault() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(FOLD_KEY))
                    .thenReturn(true);
                when(memoryMock.getBoolean(FOLD_KEY))
                    .thenReturn(false);

                assertThat(new PersistedSidebarFold(SCREEN_SCOPE, OPENS_DOCKED).isRailDocked())
                    .isFalse();
            }
        }

        @Test
        void isRailDockedFallsBackToTheOpeningDefaultBeforeTheSectorExists() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                assertThat(new PersistedSidebarFold(SCREEN_SCOPE, OPENS_DOCKED).isRailDocked())
                    .isTrue();
            }
        }
    }

    @Nested
    class RecordFold {

        @Test
        void recordFoldStoresAFoldThatDiffersFromTheOneInTheSave() {

            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(FOLD_KEY))
                    .thenReturn(true);
                when(memoryMock.getBoolean(FOLD_KEY))
                    .thenReturn(true);

                new PersistedSidebarFold(SCREEN_SCOPE, OPENS_DOCKED).recordFold(false);

                verify(memoryMock)
                    .set(FOLD_KEY, false);
            }
        }

        @Test
        void recordFoldWritesNothingWhenTheSaveAlreadyReadsThatWay() {
            // The panel offers its settled fold every frame it draws; only a real change may reach the save.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(FOLD_KEY))
                    .thenReturn(true);
                when(memoryMock.getBoolean(FOLD_KEY))
                    .thenReturn(true);

                new PersistedSidebarFold(SCREEN_SCOPE, OPENS_DOCKED).recordFold(true);

                verify(memoryMock, never())
                    .set(eq(FOLD_KEY), anyBoolean());
            }
        }

        @Test
        void recordFoldLeavesAnUntouchedSaveUnwrittenWhileThePanelSitsAtItsDefault() {
            // A save that has never stored a fold reads as the default, so a panel resting there writes
            // nothing and the key appears only once the player actually moves the panel.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(FOLD_KEY))
                    .thenReturn(false);

                new PersistedSidebarFold(SCREEN_SCOPE, OPENS_DOCKED).recordFold(true);

                verify(memoryMock, never())
                    .set(eq(FOLD_KEY), anyBoolean());
            }
        }

        @Test
        void recordFoldRetriesAfterAWriteThatCouldNotLand() {
            // Before the sector exists the write is dropped. Nothing is remembered as written, so the next
            // offer stores it rather than believing the choice already reached the save.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                var fold = new PersistedSidebarFold(SCREEN_SCOPE, OPENS_DOCKED);

                fold.recordFold(false);

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(FOLD_KEY))
                    .thenReturn(false);

                fold.recordFold(false);

                verify(memoryMock)
                    .set(FOLD_KEY, false);
            }
        }

        @Test
        void recordFoldFollowsTheSaveRatherThanAFoldRememberedFromAPreviousOne() {
            // One host serves every save loaded in a run. The comparison is against sector memory, so a
            // fold written in one save cannot suppress the same fold being written into the next.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(FOLD_KEY))
                    .thenReturn(true);
                when(memoryMock.getBoolean(FOLD_KEY))
                    .thenReturn(true);

                var fold = new PersistedSidebarFold(SCREEN_SCOPE, OPENS_DOCKED);

                fold.recordFold(false);

                // A second save loaded in the same run still holds the docked fold; folding it out again
                // must write, not be swallowed as "already recorded".
                when(memoryMock.getBoolean(FOLD_KEY))
                    .thenReturn(true);

                fold.recordFold(false);

                verify(memoryMock, times(2))
                    .set(FOLD_KEY, false);
            }
        }
    }
}
