package kmu.maplayers.ownermap.preferences;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the uninhabited-outline preference: the read reports off until that screen's save says otherwise,
 * the write persists the screen's own composed key and bumps the map-style revision on the board it was
 * handed so that sector's overlay restyles, the two screens read and write apart, and both no-op cleanly
 * before the sector exists. The composed key is pinned as a literal, so the base key a layer hands over is
 * composed with the screen and nothing else - the layer's own frozen key is pinned where the layer names it.
 */
final class UninhabitedOutlinePreferenceTest {

    // Two screens of no particular identity: this preference's subject is that the outline is one
    // screen's, not which screens the mod has - that is MapLayerScreens' answer and is pinned there.
    private static final ScreenMemoryScope SCREEN_SCOPE = ScreenMemoryScopes.createStandInScreen();

    private static final ScreenMemoryScope OTHER_SCREEN_SCOPE =
        ScreenMemoryScopes.createOtherStandInScreen();

    // The base key the case layer hands over, and the slot each screen composes from it.
    private static final String BASE_KEY = "$test_layer_uninhabited_outline";
    private static final String KEY = "$test_layer_uninhabited_outline_test";

    // The same base key under the second screen, which is what a flip here must never write.
    private static final String OTHER_KEY = "$test_layer_uninhabited_outline_other";

    private final UninhabitedOutlinePreference preference = new UninhabitedOutlinePreference(BASE_KEY);

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
    class IsOutlineDrawn {

        @Test
        void isOutlineDrawnReadsTheOutlineKeyFromSectorMemory() {

            sectorMemoryFake.storeValue(KEY, true);

            assertThat(preference.isOutlineDrawn(SCREEN_SCOPE))
                .isTrue();
        }

        @Test
        void isOutlineDrawnReadsEachScreensOwnToggle() {
            // Per-screen isolation: the two panels are looked at for different things, so asking for
            // the unowned systems on one is not asking for them on the other.
            sectorMemoryFake.storeValue(KEY, true);
            sectorMemoryFake.storeValue(OTHER_KEY, false);

            assertThat(preference.isOutlineDrawn(SCREEN_SCOPE))
                .isTrue();
            assertThat(preference.isOutlineDrawn(OTHER_SCREEN_SCOPE))
                .isFalse();
        }

        @Test
        void isOutlineDrawnIsFalseWhenThatScreensBoxWasNeverTicked() {
            // A screen whose checkbox was never touched keeps the shipped default, even while the
            // other screen draws the outline.
            sectorMemoryFake.storeValue(OTHER_KEY, true);

            assertThat(preference.isOutlineDrawn(SCREEN_SCOPE))
                .isFalse();
        }

        @Test
        void isOutlineDrawnIsFalseBeforeTheSectorExists() {
            // No sector means no save to read, which the shipped default treats as off - only
            // faction-held, independent, and decivilised systems draw.
            sectorMemoryFake.removeSector();

            assertThat(preference.isOutlineDrawn(SCREEN_SCOPE))
                .isFalse();
        }
    }

    @Nested
    class SetOutlineDrawn {

        @Test
        void setOutlineDrawnPersistsTheChoiceAndRaisesOnTheBoardItWasHanded() {

            var board = new MapLayerRefreshBoard();

            preference.setOutlineDrawn(SCREEN_SCOPE, true, board);

            assertThat(sectorMemoryFake.readStoredValue(KEY))
                .isEqualTo(true);
            assertThat(board.getRevision(MapLayerCommonRefreshSignal.MAP_STYLE))
                .isEqualTo(1);
        }

        @Test
        void setOutlineDrawnLeavesAnotherScreensToggleUntouched() {
            // Per-screen isolation on the write side: a flip made on one panel writes that panel's slot
            // alone, so the other keeps the sector it was last showing.
            preference.setOutlineDrawn(
                SCREEN_SCOPE,
                true,
                new MapLayerRefreshBoard());

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_KEY))
                .isFalse();
        }

        @Test
        void setOutlineDrawnWritesNothingAndRaisesNothingBeforeTheSectorExists() {
            // Before a save there is nothing to write into, so the flip is dropped rather than
            // bumping a revision no overlay would read.
            sectorMemoryFake.removeSector();
            var board = new MapLayerRefreshBoard();

            preference.setOutlineDrawn(SCREEN_SCOPE, true, board);

            assertThat(board.getRevision(MapLayerCommonRefreshSignal.MAP_STYLE))
                .isEqualTo(0);
        }
    }
}
