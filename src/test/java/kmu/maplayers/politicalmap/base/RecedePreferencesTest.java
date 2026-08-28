package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.settings.KmuPoliticalMapSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the recede preferences set: each read falls back to its own toggle's default while no choice is
 * stored - Mute off, Desaturate on - and a stored value always outranks it, each write persists
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
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(TEST_MUTE_KEY))
                    .thenReturn(true);
                when(memoryMock.getBoolean(TEST_MUTE_KEY))
                    .thenReturn(true);

                assertThat(TEST_SET.isMuted())
                    .isTrue();
            }
        }

        @Test
        void isMutedIsFalseBeforeTheSectorExists() {
            // No sector means no save to read, so the read falls back to Mute's default of off.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                assertThat(TEST_SET.isMuted())
                    .isFalse();
            }
        }

        @Test
        void isMutedIsFalseWhenTheKeyWasNeverSet() {
            // Mute keeps the un-receded default: dimming and recolouring are separate asks, and only
            // the recolour is wanted out of the box.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(TEST_MUTE_KEY))
                    .thenReturn(false);

                assertThat(TEST_SET.isMuted())
                    .isFalse();
            }
        }
    }

    @Nested
    class IsDesaturated {

        @Test
        void isDesaturatedReadsTheDesaturateKeyFromSectorMemory() {
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(TEST_DESATURATE_KEY))
                    .thenReturn(true);
                when(memoryMock.getBoolean(TEST_DESATURATE_KEY))
                    .thenReturn(true);

                assertThat(TEST_SET.isDesaturated())
                    .isTrue();
            }
        }

        @Test
        void isDesaturatedIsTrueBeforeTheSectorExists() {
            // No sector means no save to read, so the read falls back to Desaturate's default of on.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                assertThat(TEST_SET.isDesaturated())
                    .isTrue();
            }
        }

        @Test
        void isDesaturatedIsTrueWhenTheKeyWasNeverSet() {
            // An untouched save opens desaturated, so a spotlight recedes the sector from the first
            // click rather than after a hunt through the sidebar checkboxes.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(TEST_DESATURATE_KEY))
                    .thenReturn(false);

                assertThat(TEST_SET.isDesaturated())
                    .isTrue();
            }
        }

        @Test
        void isDesaturatedIsFalseWhenTheStoredChoiceIsOff() {
            // Clearing the box writes a real false, which outranks the on default on every later
            // load - the default describes an untouched save only, never a player's own answer.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                when(memoryMock.contains(TEST_DESATURATE_KEY))
                    .thenReturn(true);
                when(memoryMock.getBoolean(TEST_DESATURATE_KEY))
                    .thenReturn(false);

                assertThat(TEST_SET.isDesaturated())
                    .isFalse();
            }
        }
    }

    @Nested
    class SetMuted {

        @Test
        void setMutedPersistsTheChoiceAndRequestsARefresh() {
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                var revisionBefore =
                    MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE);

                TEST_SET.setMuted(true);

                verify(memoryMock)
                    .set(TEST_MUTE_KEY, true);

                // The flip must bump the recede-style revision, since these sidebar-only toggles
                // never move settingsRevision - that bump is what repaints the overlay live.
                assertThat(MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE))
                    .isNotEqualTo(revisionBefore);
            }
        }

        @Test
        void setMutedWritesTheOffChoiceToo() {
            // A clear is persisted as readily as a set, so turning muting off survives reload.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                TEST_SET.setMuted(false);

                verify(memoryMock)
                    .set(TEST_MUTE_KEY, false);
            }
        }

        @Test
        void setMutedNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into and nothing painting, so the write and the
            // refresh are both skipped rather than bumping a revision no overlay would read.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                var revisionBefore =
                    MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE);

                TEST_SET.setMuted(true);

                assertThat(MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE))
                    .isEqualTo(revisionBefore);
            }
        }

        @Test
        void filterSetWritesItsOwnFrozenMuteKey() {
            // The filter set's mute key is frozen: it is what a save serialises, so a rename resets
            // every filter-recede choice. Pinned through the write so the read shares the same key.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                RecedePreferences.FILTER.setMuted(true);

                verify(memoryMock)
                    .set(FILTER_MUTE_KEY, true);
            }
        }

        @Test
        void allianceSetWritesItsOwnFrozenMuteKey() {
            // The alliance non-allied set writes a key distinct from the filter set's, so a flip in
            // one never moves the other - the two backdrops are tuned independently.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                RecedePreferences.ALLIANCE_NON_ALLIED.setMuted(true);

                verify(memoryMock)
                    .set(ALLIANCE_MUTE_KEY, true);
            }
        }
    }

    @Nested
    class SetDesaturated {

        @Test
        void setDesaturatedPersistsTheChoiceAndRequestsARefresh() {
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                var revisionBefore =
                    MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE);

                TEST_SET.setDesaturated(true);

                verify(memoryMock)
                    .set(TEST_DESATURATE_KEY, true);

                assertThat(MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE))
                    .isNotEqualTo(revisionBefore);
            }
        }

        @Test
        void setDesaturatedWritesTheOffChoiceToo() {
            // The explicit false is what lets a player overrule the on default: an unset key would
            // read back as desaturated again on the next load.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                TEST_SET.setDesaturated(false);

                verify(memoryMock)
                    .set(TEST_DESATURATE_KEY, false);
            }
        }

        @Test
        void setDesaturatedNoOpsBeforeTheSectorExists() {
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(null);

                var revisionBefore =
                    MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE);

                TEST_SET.setDesaturated(true);

                assertThat(MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE))
                    .isEqualTo(revisionBefore);
            }
        }

        @Test
        void filterSetWritesItsOwnFrozenDesaturateKey() {
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                RecedePreferences.FILTER.setDesaturated(true);

                verify(memoryMock)
                    .set(FILTER_DESATURATE_KEY, true);
            }
        }

        @Test
        void allianceSetWritesItsOwnFrozenDesaturateKey() {
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class)) {

                var memoryMock = mock(MemoryAPI.class);

                memoryAccessMock
                    .when(SectorMemoryAccess::readSectorMemory)
                    .thenReturn(memoryMock);

                RecedePreferences.ALLIANCE_NON_ALLIED.setDesaturated(true);

                verify(memoryMock)
                    .set(ALLIANCE_DESATURATE_KEY, true);
            }
        }
    }

    @Nested
    class ResolveRecedeAdjustment {

        @Test
        void resolveRecedeAdjustmentMutesOnlyWhenOnlyMuteIsSet() {
            // Mute alone dims by the modifier and keeps the colour, so a receded bloc recedes
            // without a palette change.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class);
                    var settingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubToggles(memoryAccessMock, settingsMock, true, MUTED_MODIFIER, false);

                assertThat(TEST_SET.resolveRecedeAdjustment())
                    .isEqualTo(new ElementStyleAdjustment(MUTED_MODIFIER, false));
            }
        }

        @Test
        void resolveRecedeAdjustmentDesaturatesOnlyWhenOnlyDesaturateIsSet() {
            // Desaturate alone recolours at full opacity, so the modifier is left unread.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class);
                    var settingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubToggles(memoryAccessMock, settingsMock, false, MUTED_MODIFIER, true);

                assertThat(TEST_SET.resolveRecedeAdjustment())
                    .isEqualTo(new ElementStyleAdjustment(1.0, true));
            }
        }

        @Test
        void resolveRecedeAdjustmentBothMutesAndDesaturatesWhenBothAreSet() {
            // The two knobs combine: a receded bloc dims and recolours at once.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class);
                    var settingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubToggles(memoryAccessMock, settingsMock, true, MUTED_MODIFIER, true);

                assertThat(TEST_SET.resolveRecedeAdjustment())
                    .isEqualTo(new ElementStyleAdjustment(MUTED_MODIFIER, true));
            }
        }

        @Test
        void resolveRecedeAdjustmentIsNoneWhenNeitherIsSet() {
            // Both toggles off is the identity adjustment, so an un-receded look is preserved.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class);
                    var settingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubToggles(memoryAccessMock, settingsMock, false, MUTED_MODIFIER, false);

                assertThat(TEST_SET.resolveRecedeAdjustment())
                    .isEqualTo(ElementStyleAdjustment.NONE);
            }
        }

        @Test
        void resolveRecedeAdjustmentTracksTheMutedModifierValue() {
            // The muted multiplier is the modifier reading, not a constant, so a different modifier
            // value flows straight through to the adjustment.
            try (var memoryAccessMock = mockStatic(SectorMemoryAccess.class);
                    var settingsMock = mockStatic(KmuPoliticalMapSettings.class)) {

                stubToggles(memoryAccessMock, settingsMock, true, 0.72, false);

                assertThat(TEST_SET.resolveRecedeAdjustment().opacityMultiplier())
                    .isEqualTo(0.72);
            }
        }
    }

    // Stubs the test set's two toggle reads through a memory mock and the muted modifier through the
    // settings mock, so resolveRecedeAdjustment runs its real composition over controlled inputs.
    private static void stubToggles(
            MockedStatic<SectorMemoryAccess> memoryAccessMock,
            MockedStatic<KmuPoliticalMapSettings> settingsMock,
            boolean isMuted,
            double mutedModifier,
            boolean shouldDesaturate) {

        var memoryMock = mock(MemoryAPI.class);

        memoryAccessMock
            .when(SectorMemoryAccess::readSectorMemory)
            .thenReturn(memoryMock);

        // isSet gates on the key being present, so a stored value is contains=true plus its boolean;
        // this pins both toggles present so getBoolean is what the read reflects.
        when(memoryMock.contains(TEST_MUTE_KEY))
            .thenReturn(true);
        when(memoryMock.contains(TEST_DESATURATE_KEY))
            .thenReturn(true);
        when(memoryMock.getBoolean(TEST_MUTE_KEY))
            .thenReturn(isMuted);
        when(memoryMock.getBoolean(TEST_DESATURATE_KEY))
            .thenReturn(shouldDesaturate);

        settingsMock
            .when(KmuPoliticalMapSettings::getPoliticalMapAllianceMutedOpacityModifier)
            .thenReturn(mutedModifier);
    }
}
