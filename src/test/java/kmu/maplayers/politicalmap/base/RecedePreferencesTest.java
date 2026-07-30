package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.refresh.PoliticalMapRefresh;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the recede preferences set: each read returns false before a save exists, each write persists
 * its instance's frozen key to sector memory and bumps the recede-style revision so the overlay
 * repaints, both no-op cleanly before the sector exists, and the toggles resolve into the adjustment
 * every context this set backs applies. The generic behaviour is exercised through a test-keyed set,
 * while the two live sets' frozen keys are pinned through their writes so a rename that would silently
 * reset every existing save's choice fails here rather than shipping. The migrations that carry an
 * older save's choice up to the current keys are pinned too.
 */
final class RecedePreferencesTest {
    // The two live sets' frozen keys, pinned as literals: renaming one resets every existing save's
    // choice for that set to off, so a change must break this test first. The filter set and the
    // alliance non-allied set must not collide, which distinct literals guarantee.
    private static final String FILTER_MUTE_KEY = "$kmu_political_filter_recede_mute";
    private static final String FILTER_DESATURATE_KEY = "$kmu_political_filter_recede_desaturate";
    private static final String ALLIANCE_MUTE_KEY = "$kmu_political_alliance_recede_mute";
    private static final String ALLIANCE_DESATURATE_KEY = "$kmu_political_alliance_recede_desaturate";

    // The keys the recede shipped under while it was one shared set; migrateSharedKeysIntoFilterSet
    // carries each into the filter set. Pinned so a change to either side of that migration breaks
    // here rather than silently orphaning a pre-split save.
    private static final String SHARED_MUTE_KEY = "$kmu_political_recede_mute";
    private static final String SHARED_DESATURATE_KEY = "$kmu_political_recede_desaturate";

    // The oldest keys, from when the recede lived only in the alliances view; migrateLegacyKeys carries
    // each into the shared keys. Pinned so a change to that migration breaks here rather than silently
    // orphaning the oldest saves.
    private static final String LEGACY_MUTE_KEY = "$kmu_political_alliance_mute_non_allied";
    private static final String LEGACY_DESATURATE_KEY =
            "$kmu_political_alliance_desaturate_non_allied";

    // A test-keyed set for the generic read/write/resolve behaviour, so those tests exercise the
    // instance logic without asserting against any live set's frozen keys - the live keys are pinned
    // separately through the two constants' writes.
    private static final String TEST_MUTE_KEY = "$test_recede_mute";
    private static final String TEST_DESATURATE_KEY = "$test_recede_desaturate";
    private static final RecedePreferences TEST_SET =
            new RecedePreferences(TEST_MUTE_KEY, TEST_DESATURATE_KEY);

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
                when(memoryMock.contains(TEST_MUTE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(TEST_MUTE_KEY)).thenReturn(true);

                assertThat(TEST_SET.isMuted()).isTrue();
            }
        }

        @Test
        void isMutedIsFalseBeforeTheSectorExists() {
            // No sector means no save to read, which the original un-receded look treats as off.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                assertThat(TEST_SET.isMuted()).isFalse();
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
                when(memoryMock.contains(TEST_DESATURATE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(TEST_DESATURATE_KEY)).thenReturn(true);

                assertThat(TEST_SET.isDesaturated()).isTrue();
            }
        }

        @Test
        void isDesaturatedIsFalseBeforeTheSectorExists() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                assertThat(TEST_SET.isDesaturated()).isFalse();
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

                TEST_SET.setMuted(true);

                verify(memoryMock).set(TEST_MUTE_KEY, true);
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

                TEST_SET.setMuted(false);

                verify(memoryMock).set(TEST_MUTE_KEY, false);
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

                TEST_SET.setMuted(true);

                assertThat(PoliticalMapRefresh.getRecedeStyleRevision()).isEqualTo(revisionBefore);
            }
        }

        @Test
        void filterSetWritesItsOwnFrozenMuteKey() {
            // The filter set's mute key is frozen: it is what a save serialises, so a rename resets
            // every filter-recede choice. Pinned through the write so the read shares the same key.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                RecedePreferences.FILTER.setMuted(true);

                verify(memoryMock).set(FILTER_MUTE_KEY, true);
            }
        }

        @Test
        void allianceSetWritesItsOwnFrozenMuteKey() {
            // The alliance non-allied set writes a key distinct from the filter set's, so a flip in
            // one never moves the other - the two grounds are tuned independently.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                RecedePreferences.ALLIANCE_NON_ALLIED.setMuted(true);

                verify(memoryMock).set(ALLIANCE_MUTE_KEY, true);
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

                TEST_SET.setDesaturated(true);

                verify(memoryMock).set(TEST_DESATURATE_KEY, true);
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

                TEST_SET.setDesaturated(true);

                assertThat(PoliticalMapRefresh.getRecedeStyleRevision()).isEqualTo(revisionBefore);
            }
        }

        @Test
        void filterSetWritesItsOwnFrozenDesaturateKey() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                RecedePreferences.FILTER.setDesaturated(true);

                verify(memoryMock).set(FILTER_DESATURATE_KEY, true);
            }
        }

        @Test
        void allianceSetWritesItsOwnFrozenDesaturateKey() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                RecedePreferences.ALLIANCE_NON_ALLIED.setDesaturated(true);

                verify(memoryMock).set(ALLIANCE_DESATURATE_KEY, true);
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

                assertThat(TEST_SET.resolveRecedeAdjustment())
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

                assertThat(TEST_SET.resolveRecedeAdjustment())
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

                assertThat(TEST_SET.resolveRecedeAdjustment())
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

                assertThat(TEST_SET.resolveRecedeAdjustment())
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

                assertThat(TEST_SET.resolveRecedeAdjustment().opacityMultiplier())
                        .isEqualTo(0.72);
            }
        }
    }

    @Nested
    class MigrateLegacyKeys {

        @Test
        void migrateLegacyKeysCarriesTheStoredMuteChoiceToTheSharedKeyAndDropsTheLegacyKey() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(LEGACY_MUTE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(LEGACY_MUTE_KEY)).thenReturn(true);

                RecedePreferences.migrateLegacyKeys();

                verify(memoryMock).set(SHARED_MUTE_KEY, true);
                verify(memoryMock).unset(LEGACY_MUTE_KEY);
            }
        }

        @Test
        void migrateLegacyKeysCarriesTheStoredDesaturateChoiceToTheSharedKeyAndDropsTheLegacyKey() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(LEGACY_DESATURATE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(LEGACY_DESATURATE_KEY)).thenReturn(true);

                RecedePreferences.migrateLegacyKeys();

                verify(memoryMock).set(SHARED_DESATURATE_KEY, true);
                verify(memoryMock).unset(LEGACY_DESATURATE_KEY);
            }
        }

        @Test
        void migrateLegacyKeysCarriesAStoredOffChoiceToo() {
            // The migration gates on the key's presence, not its value, so a stored-off legacy choice
            // carries its false into the shared key rather than being read as an absent key.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(LEGACY_MUTE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(LEGACY_MUTE_KEY)).thenReturn(false);

                RecedePreferences.migrateLegacyKeys();

                verify(memoryMock).set(SHARED_MUTE_KEY, false);
                verify(memoryMock).unset(LEGACY_MUTE_KEY);
            }
        }

        @Test
        void migrateLegacyKeysWritesNothingWhenNoLegacyKeyIsStored() {
            // A save written after the rename holds no legacy key, so there is nothing to carry and
            // the shared keys are left exactly as they are.
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
        void migrateLegacyKeysDoesNotOverwriteASharedChoice() {
            // Once the shared key holds a value the migration steps aside, so a choice already carried
            // there is never clobbered by a stale legacy key that somehow lingers alongside it.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(SHARED_MUTE_KEY)).thenReturn(true);
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

    @Nested
    class MigrateSharedKeysIntoFilterSet {

        @Test
        void migrateSharedKeysCarriesTheStoredMuteChoiceIntoTheFilterKeyAndDropsTheSharedKey() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(SHARED_MUTE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(SHARED_MUTE_KEY)).thenReturn(true);

                RecedePreferences.migrateSharedKeysIntoFilterSet();

                verify(memoryMock).set(FILTER_MUTE_KEY, true);
                verify(memoryMock).unset(SHARED_MUTE_KEY);
            }
        }

        @Test
        void migrateSharedKeysCarriesTheStoredDesaturateChoiceIntoTheFilterKeyAndDropsTheSharedKey() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(SHARED_DESATURATE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(SHARED_DESATURATE_KEY)).thenReturn(true);

                RecedePreferences.migrateSharedKeysIntoFilterSet();

                verify(memoryMock).set(FILTER_DESATURATE_KEY, true);
                verify(memoryMock).unset(SHARED_DESATURATE_KEY);
            }
        }

        @Test
        void migrateSharedKeysCarriesAStoredOffChoiceToo() {
            // The migration gates on presence, so a stored-off shared choice carries its false into
            // the filter key rather than defaulting the filter set back on load.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(SHARED_MUTE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(SHARED_MUTE_KEY)).thenReturn(false);

                RecedePreferences.migrateSharedKeysIntoFilterSet();

                verify(memoryMock).set(FILTER_MUTE_KEY, false);
                verify(memoryMock).unset(SHARED_MUTE_KEY);
            }
        }

        @Test
        void migrateSharedKeysWritesNothingWhenNoSharedKeyIsStored() {
            // A post-split save holds no shared key, so there is nothing to carry and the filter keys
            // are left exactly as they are.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);

                RecedePreferences.migrateSharedKeysIntoFilterSet();

                verify(memoryMock, never()).set(anyString(), anyBoolean());
                verify(memoryMock, never()).unset(anyString());
            }
        }

        @Test
        void migrateSharedKeysDoesNotOverwriteAFilterChoice() {
            // Once the filter key holds a value the migration steps aside, so a post-split filter
            // choice is never clobbered by a stale shared key that somehow lingers alongside it.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(FILTER_MUTE_KEY)).thenReturn(true);
                when(memoryMock.contains(SHARED_MUTE_KEY)).thenReturn(true);

                RecedePreferences.migrateSharedKeysIntoFilterSet();

                verify(memoryMock, never()).set(anyString(), anyBoolean());
                verify(memoryMock, never()).unset(anyString());
            }
        }

        @Test
        void migrateSharedKeysLeavesTheAllianceSetUntouched() {
            // The pre-split choice lands only in the filter set; the alliance non-allied set starts
            // fresh, so its keys are never written even when a shared choice is present.
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                var memoryMock = mock(MemoryAPI.class);
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
                when(memoryMock.contains(SHARED_MUTE_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(SHARED_MUTE_KEY)).thenReturn(true);

                RecedePreferences.migrateSharedKeysIntoFilterSet();

                verify(memoryMock, never()).set(eq(ALLIANCE_MUTE_KEY), anyBoolean());
                verify(memoryMock, never()).set(eq(ALLIANCE_DESATURATE_KEY), anyBoolean());
            }
        }

        @Test
        void migrateSharedKeysIsANoOpBeforeTheSectorExists() {
            try (MockedStatic<SectorMemoryAccess> memoryAccessMock =
                    mockStatic(SectorMemoryAccess.class)) {
                memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(null);

                RecedePreferences.migrateSharedKeysIntoFilterSet();

                memoryAccessMock.verify(SectorMemoryAccess::readSectorMemory);
            }
        }
    }

    // Stubs the test set's two toggle reads through a memory mock and the muted modifier through the
    // settings mock, so resolveRecedeAdjustment runs its real composition over controlled inputs.
    private static void stubToggles(MockedStatic<SectorMemoryAccess> memoryAccessMock,
            MockedStatic<KmuLunaSettings> settingsMock, boolean isMuted, double mutedModifier,
            boolean shouldDesaturate) {
        var memoryMock = mock(MemoryAPI.class);
        memoryAccessMock.when(SectorMemoryAccess::readSectorMemory).thenReturn(memoryMock);
        // isSet gates on the key being present, so a stored value is contains=true plus its boolean;
        // this pins both toggles present so getBoolean is what the read reflects.
        when(memoryMock.contains(TEST_MUTE_KEY)).thenReturn(true);
        when(memoryMock.contains(TEST_DESATURATE_KEY)).thenReturn(true);
        when(memoryMock.getBoolean(TEST_MUTE_KEY)).thenReturn(isMuted);
        when(memoryMock.getBoolean(TEST_DESATURATE_KEY)).thenReturn(shouldDesaturate);
        settingsMock.when(KmuLunaSettings::getPoliticalMapAllianceMutedOpacityModifier)
                .thenReturn(mutedModifier);
    }
}
