package kmu.maplayers.base.layer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private final PersistedActiveLayerSelection otherSelection = new PersistedActiveLayerSelection(OTHER_KEY);

    @BeforeEach
    void registerTwoFakeLayers() {
        when(firstLayerMock.getId()).thenReturn("first");
        when(secondLayerMock.getId()).thenReturn("second");
        // Second is the default, so an untouched save resolves to it - a non-leading default, the shape the
        // real composition root uses.
        MapLayerRegistry.registerLayers(List.of(firstLayerMock, secondLayerMock), secondLayerMock);
    }

    @Nested
    class GetActiveLayer {

        @Test
        void getActiveLayerDefaultsToTheRegisteredDefaultWithoutASavedPick() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(selection.getActiveLayer()).isSameAs(secondLayerMock);
            }
        }

        @Test
        void getActiveLayerResolvesTheStoredIdUnderItsOwnKey() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(KEY)).thenReturn(true);
                when(memoryMock.getString(KEY)).thenReturn("first");

                assertThat(selection.getActiveLayer()).isSameAs(firstLayerMock);
            }
        }

        @Test
        void getActiveLayerFallsBackToTheDefaultForAStaleStoredId() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(KEY)).thenReturn(true);
                when(memoryMock.getString(KEY)).thenReturn("removed_long_ago");

                assertThat(selection.getActiveLayer()).isSameAs(secondLayerMock);
            }
        }
    }

    @Nested
    class SelectLayer {

        @Test
        void selectLayerWritesThePickedIdUnderItsOwnKey() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);

                selection.selectLayer(firstLayerMock);

                verify(memoryMock).set(KEY, "first");
            }
        }

        @Test
        void selectLayerUnderOneKeyLeavesAnotherKeysPickUntouched() {
            // The independence the per-screen split needs: writing this selection's key must never touch a
            // second screen's key, so a switch on one screen cannot move the other's tab.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);

                selection.selectLayer(firstLayerMock);

                verify(memoryMock).set(KEY, "first");
                verify(memoryMock, never()).set(eq(OTHER_KEY), any());
            }
        }
    }

    // Stubs a fresh sector whose memory is {@code memoryMock}, so a test drives the selection's reads and
    // writes through one mocked memory without repeating the two-hop wiring.
    private static void linkSectorMemoryTo(MockedStatic<Global> globalMock, MemoryAPI memoryMock) {
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getMemoryWithoutUpdate()).thenReturn(memoryMock);
        globalMock.when(Global::getSector).thenReturn(sectorMock);
    }
}
