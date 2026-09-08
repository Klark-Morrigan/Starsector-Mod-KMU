package kmu.maplayers.politicalmap.base;

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
 * Pins the name-format preference: the read resolves the stored key back to its choice and falls
 * back to full names when nothing is stored, the write persists the choice's key to the screen's own
 * composed key and bumps the map-style revision on the board it was handed so the labels re-fit, the
 * two screens read and write apart, and both no-op cleanly before the sector exists. The composed key
 * is pinned as a literal, since renaming the base key silently resets every existing save's choice.
 */
final class NameFormatPreferenceTest {

    // Two screens of no particular identity: this preference's subject is that a format is one screen's,
    // not which screens the mod has - that is MapLayerScreens' answer and is pinned there.
    private static final ScreenMemoryScope SCREEN_SCOPE = ScreenMemoryScopes.createStandInScreen();

    private static final ScreenMemoryScope OTHER_SCREEN_SCOPE =
        ScreenMemoryScopes.createOtherStandInScreen();

    // The slot that screen composes, as a literal: the base key is a save-serialised identity, so a
    // rename must break this test rather than ship and quietly return every save to full names.
    private static final String KEY = "$kmu_political_name_format_test";

    // The same base key under the second screen, which is what a pick here must never write.
    private static final String OTHER_KEY = "$kmu_political_name_format_other";

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
    class GetSelectedNameFormat {

        @Test
        void getSelectedNameFormatResolvesTheStoredKeyToItsChoice() {

            sectorMemoryFake.storeValue(KEY, FactionNameFormatChoice.SHORT.persistenceKey());

            assertThat(NameFormatPreference.getSelectedNameFormat(SCREEN_SCOPE))
                .isEqualTo(FactionNameFormatChoice.SHORT);
        }

        @Test
        void getSelectedNameFormatReadsEachScreensOwnChoice() {
            // Per-screen isolation: the two panels frame the sector at different sizes, so the form
            // that fits one is not the form the other was set to.
            sectorMemoryFake.storeValue(KEY, FactionNameFormatChoice.SHORT.persistenceKey());
            sectorMemoryFake.storeValue(OTHER_KEY, FactionNameFormatChoice.NONE.persistenceKey());

            assertThat(NameFormatPreference.getSelectedNameFormat(SCREEN_SCOPE))
                .isEqualTo(FactionNameFormatChoice.SHORT);
            assertThat(NameFormatPreference.getSelectedNameFormat(OTHER_SCREEN_SCOPE))
                .isEqualTo(FactionNameFormatChoice.NONE);
        }

        @Test
        void getSelectedNameFormatIsFullNamesWhenThatScreenNeverPicked() {
            // A screen whose radio was never touched reads the shipped default, even while the other
            // screen holds a choice - a pick made there must not carry over.
            sectorMemoryFake.storeValue(OTHER_KEY, FactionNameFormatChoice.NONE.persistenceKey());

            assertThat(NameFormatPreference.getSelectedNameFormat(SCREEN_SCOPE))
                .isEqualTo(FactionNameFormatChoice.FULL);
        }

        @Test
        void getSelectedNameFormatIsFullNamesBeforeTheSectorExists() {
            // No sector means no save to read, which the shipped default treats as the long-form
            // name every cluster label carried before a format was picked.
            sectorMemoryFake.removeSector();

            assertThat(NameFormatPreference.getSelectedNameFormat(SCREEN_SCOPE))
                .isEqualTo(FactionNameFormatChoice.FULL);
        }
    }

    @Nested
    class SelectNameFormat {

        @Test
        void selectNameFormatPersistsTheChoiceKeyAndRaisesOnTheBoardItWasHanded() {

            var board = new MapLayerRefreshBoard();

            NameFormatPreference.selectNameFormat(
                SCREEN_SCOPE,
                FactionNameFormatChoice.SHORT,
                board);

            assertThat(sectorMemoryFake.readStoredValue(KEY))
                .isEqualTo(FactionNameFormatChoice.SHORT.persistenceKey());
            assertThat(board.getRevision(MapLayerCommonRefreshSignal.MAP_STYLE))
                .isEqualTo(1);
        }

        @Test
        void selectNameFormatLeavesAnotherScreensChoiceUntouched() {
            // Per-screen isolation on the write side: a pick made on one panel writes that panel's slot
            // alone, so the other keeps the form its labels were last fitted to.
            NameFormatPreference.selectNameFormat(
                SCREEN_SCOPE,
                FactionNameFormatChoice.SHORT,
                new MapLayerRefreshBoard());

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_KEY))
                .isFalse();
        }

        @Test
        void selectNameFormatWritesNothingAndRaisesNothingBeforeTheSectorExists() {
            // Before a save there is nothing to write into, so the pick is dropped rather than
            // bumping a revision no overlay would read.
            sectorMemoryFake.removeSector();
            var board = new MapLayerRefreshBoard();

            NameFormatPreference.selectNameFormat(
                SCREEN_SCOPE,
                FactionNameFormatChoice.SHORT,
                board);

            assertThat(board.getRevision(MapLayerCommonRefreshSignal.MAP_STYLE))
                .isEqualTo(0);
        }
    }
}
