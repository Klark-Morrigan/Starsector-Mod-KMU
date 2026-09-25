package kmu.maplayers.ownermap.preferences;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.settings.KmuOwnerMapStyleSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the recede preferences set: each read falls back to its own toggle's default while no choice is
 * stored - Mute off, Desaturate on - and a stored value always outranks it, each write persists
 * its instance's key composed under the screen it was flipped on and bumps the recede-style revision on
 * the board it was handed so that sector's overlay repaints, the two screens read and write apart, both
 * no-op cleanly before the sector exists, and the toggles resolve into the adjustment
 * every context this set backs applies. Exercised through a set keyed for this suite: the live sets'
 * frozen keys are their owners', and are pinned where each owner names them.
 */
final class RecedePreferencesTest {

    // Two screens of no particular identity: this store's subject is that a backdrop is tuned per panel,
    // not which screens the mod has - that is MapLayerScreens' answer and is pinned there.
    private static final ScreenMemoryScope SCREEN_SCOPE = ScreenMemoryScopes.createStandInScreen();

    private static final ScreenMemoryScope OTHER_SCREEN_SCOPE =
        ScreenMemoryScopes.createOtherStandInScreen();

    // A set keyed for this suite for the read/write/resolve behaviour, so the cases exercise the
    // instance logic without asserting against any owner's frozen keys.
    private static final RecedePreferences SAMPLE_SET =
        new RecedePreferences("$sample_recede_mute", "$sample_recede_desaturate");

    // That set's slots under the two screens, composed the way the set composes them.
    private static final String SAMPLE_MUTE_KEY = "$sample_recede_mute_test";
    private static final String SAMPLE_DESATURATE_KEY = "$sample_recede_desaturate_test";
    private static final String OTHER_SAMPLE_MUTE_KEY = "$sample_recede_mute_other";
    private static final String OTHER_SAMPLE_DESATURATE_KEY = "$sample_recede_desaturate_other";

    // A distinct, non-default modifier reading so a test that expects it to flow through is not
    // satisfied by the fallback value.
    private static final double MUTED_MODIFIER = 0.3;

    private SectorMemoryFake sectorMemoryFake;

    @BeforeEach
    void openTheSave() {
        sectorMemoryFake = new SectorMemoryFake();
    }

    @AfterEach
    void closeTheSave() {
        sectorMemoryFake.close();
    }

    @Nested
    class IsMuted {

        @Test
        void isMutedReadsTheMuteKeyFromSectorMemory() {

            sectorMemoryFake.storeValue(SAMPLE_MUTE_KEY, true);

            assertThat(SAMPLE_SET.isMuted(SCREEN_SCOPE))
                .isTrue();
        }

        @Test
        void isMutedReadsEachScreensOwnToggle() {
            // Per-screen isolation: the backdrop is tuned on the panel it is being looked at from, so a
            // Mute set on one panel leaves the other's backdrop as it was.
            sectorMemoryFake.storeValue(SAMPLE_MUTE_KEY, true);

            assertThat(SAMPLE_SET.isMuted(SCREEN_SCOPE))
                .isTrue();
            assertThat(SAMPLE_SET.isMuted(OTHER_SCREEN_SCOPE))
                .isFalse();
        }

        @Test
        void isMutedIsFalseBeforeTheSectorExists() {
            // No sector means no save to read, so the read falls back to Mute's default of off.
            sectorMemoryFake.removeSector();

            assertThat(SAMPLE_SET.isMuted(SCREEN_SCOPE))
                .isFalse();
        }

        @Test
        void isMutedIsFalseWhenTheKeyWasNeverSet() {
            // Mute keeps the un-receded default: dimming and recolouring are separate asks, and only
            // the recolour is wanted out of the box.
            assertThat(SAMPLE_SET.isMuted(SCREEN_SCOPE))
                .isFalse();
        }
    }

    @Nested
    class IsDesaturated {

        @Test
        void isDesaturatedReadsTheDesaturateKeyFromSectorMemory() {

            sectorMemoryFake.storeValue(SAMPLE_DESATURATE_KEY, true);

            assertThat(SAMPLE_SET.isDesaturated(SCREEN_SCOPE))
                .isTrue();
        }

        @Test
        void isDesaturatedReadsEachScreensOwnToggle() {
            // The same isolation as Mute, posed against the on default: one screen's explicit clear
            // must not clear the other, which still opens desaturated.
            sectorMemoryFake.storeValue(SAMPLE_DESATURATE_KEY, false);

            assertThat(SAMPLE_SET.isDesaturated(SCREEN_SCOPE))
                .isFalse();
            assertThat(SAMPLE_SET.isDesaturated(OTHER_SCREEN_SCOPE))
                .isTrue();
        }

        @Test
        void isDesaturatedIsTrueBeforeTheSectorExists() {
            // No sector means no save to read, so the read falls back to Desaturate's default of on.
            sectorMemoryFake.removeSector();

            assertThat(SAMPLE_SET.isDesaturated(SCREEN_SCOPE))
                .isTrue();
        }

        @Test
        void isDesaturatedIsTrueWhenTheKeyWasNeverSet() {
            // An untouched save opens desaturated, so a spotlight recedes the sector from the first
            // click rather than after a hunt through the sidebar checkboxes.
            assertThat(SAMPLE_SET.isDesaturated(SCREEN_SCOPE))
                .isTrue();
        }

        @Test
        void isDesaturatedIsFalseWhenTheStoredChoiceIsOff() {
            // Clearing the box writes a real false, which outranks the on default on every later
            // load - the default describes an untouched save only, never a player's own answer.
            sectorMemoryFake.storeValue(SAMPLE_DESATURATE_KEY, false);

            assertThat(SAMPLE_SET.isDesaturated(SCREEN_SCOPE))
                .isFalse();
        }
    }

    @Nested
    class SetMuted {

        @Test
        void setMutedPersistsTheChoiceAndRaisesOnTheBoardItWasHanded() {

            var board = new MapLayerRefreshBoard();

            SAMPLE_SET.setMuted(SCREEN_SCOPE, true, board);

            assertThat(sectorMemoryFake.readStoredValue(SAMPLE_MUTE_KEY))
                .isEqualTo(true);

            // The flip must bump the recede-style revision, since these sidebar-only toggles
            // never move settingsRevision - that bump is what repaints the overlay live.
            assertThat(board.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE))
                .isEqualTo(1);
        }

        @Test
        void setMutedWritesTheOffChoiceToo() {
            // A clear is persisted as readily as a set, so turning muting off survives reload.
            SAMPLE_SET.setMuted(SCREEN_SCOPE, false, new MapLayerRefreshBoard());

            assertThat(sectorMemoryFake.readStoredValue(SAMPLE_MUTE_KEY))
                .isEqualTo(false);
        }

        @Test
        void setMutedLeavesAnotherScreensToggleUntouched() {
            // Per-screen isolation on the write side: a flip made on one panel writes that panel's slot
            // alone, so the other's backdrop keeps the tuning it was given.
            SAMPLE_SET.setMuted(SCREEN_SCOPE, true, new MapLayerRefreshBoard());

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_SAMPLE_MUTE_KEY))
                .isFalse();
        }

        @Test
        void setMutedNoOpsBeforeTheSectorExists() {
            // No sector means no save to write into and nothing painting, so the write and the
            // refresh are both skipped rather than bumping a revision no overlay would read.
            sectorMemoryFake.removeSector();
            var board = new MapLayerRefreshBoard();

            SAMPLE_SET.setMuted(SCREEN_SCOPE, true, board);

            assertThat(board.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE))
                .isEqualTo(0);
        }
    }

    @Nested
    class SetDesaturated {

        @Test
        void setDesaturatedPersistsTheChoiceAndRaisesOnTheBoardItWasHanded() {

            var board = new MapLayerRefreshBoard();

            SAMPLE_SET.setDesaturated(SCREEN_SCOPE, true, board);

            assertThat(sectorMemoryFake.readStoredValue(SAMPLE_DESATURATE_KEY))
                .isEqualTo(true);
            assertThat(board.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE))
                .isEqualTo(1);
        }

        @Test
        void setDesaturatedWritesTheOffChoiceToo() {
            // The explicit false is what lets a player overrule the on default: an unset key would
            // read back as desaturated again on the next load.
            SAMPLE_SET.setDesaturated(SCREEN_SCOPE, false, new MapLayerRefreshBoard());

            assertThat(sectorMemoryFake.readStoredValue(SAMPLE_DESATURATE_KEY))
                .isEqualTo(false);
        }

        @Test
        void setDesaturatedLeavesAnotherScreensToggleUntouched() {

            SAMPLE_SET.setDesaturated(SCREEN_SCOPE, false, new MapLayerRefreshBoard());

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_SAMPLE_DESATURATE_KEY))
                .isFalse();
        }

        @Test
        void setDesaturatedNoOpsBeforeTheSectorExists() {

            sectorMemoryFake.removeSector();
            var board = new MapLayerRefreshBoard();

            SAMPLE_SET.setDesaturated(SCREEN_SCOPE, true, board);

            assertThat(board.getRevision(MapLayerCommonRefreshSignal.RECEDE_STYLE))
                .isEqualTo(0);
        }
    }

    @Nested
    class ResolveRecedeAdjustment {

        @Test
        void resolveRecedeAdjustmentMutesOnlyWhenOnlyMuteIsSet() {
            // Mute alone dims by the modifier and keeps the colour, so a receded bloc recedes
            // without a palette change.
            try (var settingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                storeToggles(settingsMock, true, MUTED_MODIFIER, false);

                assertThat(SAMPLE_SET.resolveRecedeAdjustment(SCREEN_SCOPE))
                    .isEqualTo(new ElementStyleAdjustment(MUTED_MODIFIER, false));
            }
        }

        @Test
        void resolveRecedeAdjustmentDesaturatesOnlyWhenOnlyDesaturateIsSet() {
            // Desaturate alone recolours at full opacity, so the modifier is left unread.
            try (var settingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                storeToggles(settingsMock, false, MUTED_MODIFIER, true);

                assertThat(SAMPLE_SET.resolveRecedeAdjustment(SCREEN_SCOPE))
                    .isEqualTo(new ElementStyleAdjustment(1.0, true));
            }
        }

        @Test
        void resolveRecedeAdjustmentBothMutesAndDesaturatesWhenBothAreSet() {
            // The two knobs combine: a receded bloc dims and recolours at once.
            try (var settingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                storeToggles(settingsMock, true, MUTED_MODIFIER, true);

                assertThat(SAMPLE_SET.resolveRecedeAdjustment(SCREEN_SCOPE))
                    .isEqualTo(new ElementStyleAdjustment(MUTED_MODIFIER, true));
            }
        }

        @Test
        void resolveRecedeAdjustmentIsNoneWhenNeitherIsSet() {
            // Both toggles off is the identity adjustment, so an un-receded look is preserved.
            try (var settingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                storeToggles(settingsMock, false, MUTED_MODIFIER, false);

                assertThat(SAMPLE_SET.resolveRecedeAdjustment(SCREEN_SCOPE))
                    .isEqualTo(ElementStyleAdjustment.NONE);
            }
        }

        @Test
        void resolveRecedeAdjustmentResolvesEachScreenUnderItsOwnToggles() {
            // The adjustment is composed per screen, so one panel's dimmed backdrop leaves the other's
            // at the untouched reading rather than both painting alike.
            try (var settingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                storeToggles(settingsMock, true, MUTED_MODIFIER, true);

                assertThat(SAMPLE_SET.resolveRecedeAdjustment(OTHER_SCREEN_SCOPE))
                    .isEqualTo(new ElementStyleAdjustment(1.0, true));
            }
        }

        @Test
        void resolveRecedeAdjustmentTracksTheMutedModifierValue() {
            // The muted multiplier is the modifier reading, not a constant, so a different modifier
            // value flows straight through to the adjustment.
            try (var settingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                storeToggles(settingsMock, true, 0.72, false);

                assertThat(SAMPLE_SET.resolveRecedeAdjustment(SCREEN_SCOPE).opacityMultiplier())
                    .isEqualTo(0.72);
            }
        }
    }

    // Stores the sample set's two toggles under the first screen and stubs the muted modifier, so
    // resolveRecedeAdjustment runs its real composition over controlled inputs.
    private void storeToggles(
            MockedStatic<KmuOwnerMapStyleSettings> settingsMock,
            boolean isMuted,
            double mutedModifier,
            boolean shouldDesaturate) {

        sectorMemoryFake.storeValue(SAMPLE_MUTE_KEY, isMuted);
        sectorMemoryFake.storeValue(SAMPLE_DESATURATE_KEY, shouldDesaturate);

        settingsMock
            .when(KmuOwnerMapStyleSettings::getOwnerMapMutedOpacityModifier)
            .thenReturn(mutedModifier);
    }
}
