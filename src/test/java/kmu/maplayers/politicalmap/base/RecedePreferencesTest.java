package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefresh;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the shared recede preferences: each read returns false before a save exists, each write
 * persists its frozen key to sector memory and bumps the recede-style revision so the overlay
 * repaints, both no-op cleanly before the sector exists, and the toggles resolve into the shared
 * adjustment every receding context applies. The frozen keys are pinned as literals so a rename that
 * would silently reset every save's choice fails here rather than shipping.
 */
final class RecedePreferencesTest {
    // The save-serialised keys, pinned as literals: renaming one resets every existing save's choice
    // to off, so a change must break this test first.
    private static final String MUTE_KEY = "$kmu_political_recede_mute";
    private static final String DESATURATE_KEY = "$kmu_political_recede_desaturate";

    // The keys the toggles shipped under while the recede lived in the alliances view; migrateLegacyKeys
    // carries a pre-rename save's choice from these to the current keys. Pinned so a change to either
    // side of the migration breaks here rather than silently orphaning old saves.
    private static final String LEGACY_MUTE_KEY = "$kmu_political_alliance_mute_non_allied";
    private static final String LEGACY_DESATURATE_KEY =
            "$kmu_political_alliance_desaturate_non_allied";

    // A distinct, non-default modifier reading so a test that expects it to flow through is not
    // satisfied by the fallback value.
    private static final double MUTED_MODIFIER = 0.3;

    @Nested
    class IsMuted {

        @Test
        void isMutedReadsTheMuteKeyFromSectorMemory() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(MUTE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(MUTE_KEY)).thenReturn(true);

                assertThat(RecedePreferences.isMuted()).isTrue();
            }
        }

        @Test
        void isMutedIsFalseBeforeTheSectorExists() {
            // No sector means no save to read, which the original un-receded look treats as off.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                assertThat(RecedePreferences.isMuted()).isFalse();
            }
        }
    }

    @Nested
    class IsDesaturated {

        @Test
        void isDesaturatedReadsTheDesaturateKeyFromSectorMemory() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(DESATURATE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(DESATURATE_KEY)).thenReturn(true);

                assertThat(RecedePreferences.isDesaturated()).isTrue();
            }
        }

        @Test
        void isDesaturatedIsFalseBeforeTheSectorExists() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                assertThat(RecedePreferences.isDesaturated()).isFalse();
            }
        }
    }

    @Nested
    class SetMuted {

        @Test
        void setMutedPersistsTheChoiceAndRequestsARefresh() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                var revisionBefore = PoliticalMapRefresh.getRecedeStyleRevision();

                RecedePreferences.setMuted(true);

                verify(memoryMock).set(MUTE_KEY, true);
                // The flip must bump the recede-style revision, since these sidebar-only toggles
                // never move settingsRevision - that bump is what repaints the overlay live.
                assertThat(PoliticalMapRefresh.getRecedeStyleRevision())
                        .isNotEqualTo(revisionBefore);
            }
        }

        @Test
        void setMutedWritesTheOffChoiceToo() {
            // A clear is persisted as readily as a set, so turning muting off survives reload.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                RecedePreferences.setMuted(false);

                verify(memoryMock).set(MUTE_KEY, false);
            }
        }

        @Test
        void setMutedNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into and nothing painting, so the write and the
            // refresh are both skipped rather than bumping a revision no overlay would read.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);
                var revisionBefore = PoliticalMapRefresh.getRecedeStyleRevision();

                RecedePreferences.setMuted(true);

                assertThat(PoliticalMapRefresh.getRecedeStyleRevision()).isEqualTo(revisionBefore);
            }
        }
    }

    @Nested
    class SetDesaturated {

        @Test
        void setDesaturatedPersistsTheChoiceAndRequestsARefresh() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                var revisionBefore = PoliticalMapRefresh.getRecedeStyleRevision();

                RecedePreferences.setDesaturated(true);

                verify(memoryMock).set(DESATURATE_KEY, true);
                assertThat(PoliticalMapRefresh.getRecedeStyleRevision())
                        .isNotEqualTo(revisionBefore);
            }
        }

        @Test
        void setDesaturatedNoOpsBeforeTheSectorExists() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);
                var revisionBefore = PoliticalMapRefresh.getRecedeStyleRevision();

                RecedePreferences.setDesaturated(true);

                assertThat(PoliticalMapRefresh.getRecedeStyleRevision()).isEqualTo(revisionBefore);
            }
        }
    }

    @Nested
    class ResolveRecedeAdjustment {

        @Test
        void resolveRecedeAdjustmentMutesOnlyWhenOnlyMuteIsSet() {
            // Mute alone dims by the modifier and keeps the colour, so a receded bloc recedes
            // without a palette change.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                            mockStatic(SectorMemoryAccess.class);
                    MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubToggles(memoryAccessMock, settingsMock, true, MUTED_MODIFIER, false);

                assertThat(RecedePreferences.resolveRecedeAdjustment())
                        .isEqualTo(new BlocStyleAdjustment(MUTED_MODIFIER, false));
            }
        }

        @Test
        void resolveRecedeAdjustmentDesaturatesOnlyWhenOnlyDesaturateIsSet() {
            // Desaturate alone recolours at full opacity, so the modifier is left unread.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                            mockStatic(SectorMemoryAccess.class);
                    MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubToggles(memoryAccessMock, settingsMock, false, MUTED_MODIFIER, true);

                assertThat(RecedePreferences.resolveRecedeAdjustment())
                        .isEqualTo(new BlocStyleAdjustment(1.0, true));
            }
        }

        @Test
        void resolveRecedeAdjustmentBothMutesAndDesaturatesWhenBothAreSet() {
            // The two knobs combine: a receded bloc dims and recolours at once.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                            mockStatic(SectorMemoryAccess.class);
                    MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubToggles(memoryAccessMock, settingsMock, true, MUTED_MODIFIER, true);

                assertThat(RecedePreferences.resolveRecedeAdjustment())
                        .isEqualTo(new BlocStyleAdjustment(MUTED_MODIFIER, true));
            }
        }

        @Test
        void resolveRecedeAdjustmentIsNoneWhenNeitherIsSet() {
            // Both toggles off is the identity adjustment, so an un-receded look is preserved.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                            mockStatic(SectorMemoryAccess.class);
                    MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubToggles(memoryAccessMock, settingsMock, false, MUTED_MODIFIER, false);

                assertThat(RecedePreferences.resolveRecedeAdjustment())
                        .isEqualTo(BlocStyleAdjustment.NONE);
            }
        }

        @Test
        void resolveRecedeAdjustmentTracksTheMutedModifierValue() {
            // The muted multiplier is the modifier reading, not a constant, so a different modifier
            // value flows straight through to the adjustment.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                            mockStatic(SectorMemoryAccess.class);
                    MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubToggles(memoryAccessMock, settingsMock, true, 0.72, false);

                assertThat(RecedePreferences.resolveRecedeAdjustment().opacityMultiplier())
                        .isEqualTo(0.72);
            }
        }
    }

    @Nested
    class MigrateLegacyKeys {

        @Test
        void migrateLegacyKeysCarriesTheStoredMuteChoiceToTheCurrentKeyAndDropsTheLegacyKey() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(LEGACY_MUTE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(LEGACY_MUTE_KEY)).thenReturn(true);

                RecedePreferences.migrateLegacyKeys();

                verify(memoryMock).set(MUTE_KEY, true);
                verify(memoryMock).unset(LEGACY_MUTE_KEY);
            }
        }

        @Test
        void migrateLegacyKeysCarriesTheStoredDesaturateChoiceToTheCurrentKeyAndDropsTheLegacyKey() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(LEGACY_DESATURATE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(LEGACY_DESATURATE_KEY)).thenReturn(true);

                RecedePreferences.migrateLegacyKeys();

                verify(memoryMock).set(DESATURATE_KEY, true);
                verify(memoryMock).unset(LEGACY_DESATURATE_KEY);
            }
        }

        @Test
        void migrateLegacyKeysCarriesAStoredOffChoiceToo() {
            // The migration gates on the key's presence, not its value, so a stored-off legacy choice
            // carries its false into the current key rather than being read as an absent key.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(LEGACY_MUTE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(LEGACY_MUTE_KEY)).thenReturn(false);

                RecedePreferences.migrateLegacyKeys();

                verify(memoryMock).set(MUTE_KEY, false);
                verify(memoryMock).unset(LEGACY_MUTE_KEY);
            }
        }

        @Test
        void migrateLegacyKeysWritesNothingWhenNoLegacyKeyIsStored() {
            // A save written after the rename holds no legacy key, so there is nothing to carry and
            // the current keys are left exactly as they are.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                RecedePreferences.migrateLegacyKeys();

                verify(memoryMock, never()).set(anyString(), anyBoolean());
                verify(memoryMock, never()).unset(anyString());
            }
        }

        @Test
        void migrateLegacyKeysDoesNotOverwriteACurrentChoice() {
            // Once the current key holds a value the migration steps aside, so a choice made after
            // the rename is never clobbered by a stale legacy key that somehow lingers alongside it.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(MUTE_KEY)).thenReturn(true);
                when(memoryMock.contains(LEGACY_MUTE_KEY)).thenReturn(true);

                RecedePreferences.migrateLegacyKeys();

                verify(memoryMock, never()).set(anyString(), anyBoolean());
                verify(memoryMock, never()).unset(anyString());
            }
        }

        @Test
        void migrateLegacyKeysIsANoOpBeforeTheSectorExists() {
            // No sector means no save to migrate, so the call returns after the read without ever
            // touching a memory that is not there.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                RecedePreferences.migrateLegacyKeys();

                memoryAccessMock.verify(SectorMemoryAccess::readSectorMemory);
            }
        }
    }

    // Stubs the two toggle reads through a memory mock and the muted modifier through the settings
    // mock, so resolveRecedeAdjustment runs its real composition over controlled inputs.
    private static void stubToggles(MockedStatic<SectorMemoryAccess> memoryAccessMock,
            MockedStatic<KmuLunaSettings> settingsMock, boolean isMuted, double mutedModifier,
            boolean shouldDesaturate) {
        var memoryMock = mock(MemoryAPI.class);
        memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
        // isSet gates on the key being present, so a stored value is contains=true plus its boolean;
        // this pins both toggles present so getBoolean is what the read reflects.
        when(memoryMock.contains(MUTE_KEY)).thenReturn(true);
        when(memoryMock.contains(DESATURATE_KEY)).thenReturn(true);
        when(memoryMock.getBoolean(MUTE_KEY)).thenReturn(isMuted);
        when(memoryMock.getBoolean(DESATURATE_KEY)).thenReturn(shouldDesaturate);
        settingsMock.when(KmuLunaSettings::getPoliticalMapAllianceMutedOpacityModifier)
                .thenReturn(mutedModifier);
    }
}
