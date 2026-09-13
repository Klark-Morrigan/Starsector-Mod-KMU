package kmu.maplayers.base.layer;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the per-screen persisted pick: the slot it composes for the screen it was built for, that it
 * resolves the stored ID against the registry, and that it falls back to the default for a missing or
 * stale id. The independence case is the property the whole per-screen split rests on - a selection built
 * for one screen never touches another screen's slot, so a switch on one screen leaves the other's pick
 * where it was.
 */
final class PersistedActiveLayerSelectionTest {

    private static final ScreenMemoryScope SCREEN_SCOPE = ScreenMemoryScopes.createStandInScreen();

    // The slot that screen composes, as a literal: the base key is a save-serialised identity, so a rename
    // must break this test rather than ship and reset every existing save to the default.
    private static final String KEY = "$kmu_political_active_layer_test";

    // The same base key under a second screen, which is what this pick must never write.
    private static final String OTHER_KEY = "$kmu_political_active_layer_other";

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);
    private final PersistedActiveLayerSelection selection = new PersistedActiveLayerSelection(SCREEN_SCOPE);

    private SectorMemoryFake sectorMemoryFake;

    @BeforeEach
    void registerTwoFakeLayers() {

        when(firstLayerMock.getId())
            .thenReturn("first");
        when(secondLayerMock.getId())
            .thenReturn("second");

        // Second offers itself as the default, so an untouched save resolves to it - a non-leading
        // default, the shape the real composition root leaves.
        when(secondLayerMock.isOfferedAsDefaultPick())
            .thenReturn(true);

        MapLayerRosters.replaceRosterWith(firstLayerMock, secondLayerMock);

        sectorMemoryFake = new SectorMemoryFake();
    }

    @AfterEach
    void closeTheSectorMemory() {
        sectorMemoryFake.close();
    }

    @Nested
    class GetActiveLayer {

        @Test
        void getActiveLayerDefaultsToTheRegisteredDefaultWithoutASavedPick() {

            sectorMemoryFake.removeSector();

            assertThat(selection.getActiveLayer())
                .isSameAs(secondLayerMock);
        }

        @Test
        void getActiveLayerResolvesTheStoredIdUnderItsOwnKey() {

            sectorMemoryFake.storeValue(KEY, "first");

            assertThat(selection.getActiveLayer())
                .isSameAs(firstLayerMock);
        }

        @Test
        void getActiveLayerFallsBackToTheDefaultForAStaleStoredId() {

            sectorMemoryFake.storeValue(KEY, "removed_long_ago");

            assertThat(selection.getActiveLayer())
                .isSameAs(secondLayerMock);
        }
    }

    @Nested
    class SelectLayer {

        @Test
        void selectLayerWritesThePickedIdUnderItsOwnKey() {

            selection.selectLayer(firstLayerMock);

            assertThat(sectorMemoryFake.readStoredValue(KEY))
                .isEqualTo("first");
        }

        @Test
        void selectLayerOnOneScreenLeavesAnotherScreensPickUntouched() {
            // The independence the per-screen split needs: writing this selection's slot must never touch a
            // second screen's, so a switch on one screen cannot move the other's tab.
            selection.selectLayer(firstLayerMock);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_KEY))
                .isFalse();
        }
    }
}
