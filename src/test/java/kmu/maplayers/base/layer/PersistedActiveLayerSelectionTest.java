package kmu.maplayers.base.layer;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the per-screen persisted pick: it reads and writes one sector-memory key, resolves the stored id
 * against the registry, and falls back to the default for a missing or stale id. The independence case is
 * the property the whole per-screen split rests on - a selection under one key never touches another key,
 * so a switch on one screen leaves the other's pick where it was.
 */
final class PersistedActiveLayerSelectionTest {

    private static final String KEY = "$kmu_test_layer";
    private static final String OTHER_KEY = "$kmu_test_layer_other";

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);
    private final PersistedActiveLayerSelection selection = new PersistedActiveLayerSelection(KEY);

    private SectorMemoryFake sectorMemoryFake;

    @BeforeEach
    void registerTwoFakeLayers() {

        when(firstLayerMock.getId())
            .thenReturn("first");
        when(secondLayerMock.getId())
            .thenReturn("second");

        // Second is the default, so an untouched save resolves to it - a non-leading default, the shape the
        // real composition root uses.
        MapLayerRegistry.registerLayers(
            List.of(firstLayerMock, secondLayerMock),
            secondLayerMock);

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
        void selectLayerUnderOneKeyLeavesAnotherKeysPickUntouched() {
            // The independence the per-screen split needs: writing this selection's key must never touch a
            // second screen's key, so a switch on one screen cannot move the other's tab.
            selection.selectLayer(firstLayerMock);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_KEY))
                .isFalse();
        }
    }
}
