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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the framework registry's contract with fake layers, so the registry is exercised
 * without naming any concrete view: the tab order it hands back, the pick an untouched save
 * resolves to, the id it stores on a select, and how a stored or stale id resolves. The
 * concrete layer set and the faction overlay state are each other classes' concern.
 */
final class MapLayerRegistryTest {
    // The frozen sector-memory key the registry serialises the active pick under. Pinned as a
    // literal so a rename - which would silently reset every existing save to the default -
    // fails this test rather than shipping.
    private static final String ACTIVE_LAYER_KEY = "$kmu_political_active_layer";

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);

    @BeforeEach
    void registerTwoFakeLayers() {
        when(firstLayerMock.getId()).thenReturn("first");
        when(secondLayerMock.getId()).thenReturn("second");
        // Second layer is the default, so an untouched save resolves to it - the same shape the
        // real composition root uses (a non-leading default pick).
        MapLayerRegistry.registerLayers(List.of(firstLayerMock, secondLayerMock), secondLayerMock);
    }

    @Nested
    class GetLayers {

        @Test
        void getLayersReturnsTheRegisteredLayersInOrder() {
            assertThat(MapLayerRegistry.getLayers())
                    .containsExactly(firstLayerMock, secondLayerMock);
        }
    }

    @Nested
    class GetActiveLayer {

        @Test
        void getActiveLayerDefaultsToTheRegisteredDefaultWithoutASavedPick() {
            // No sector means no save to read a pick from, so the registry falls back to the
            // default it was registered with.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(MapLayerRegistry.getActiveLayer()).isSameAs(secondLayerMock);
            }
        }

        @Test
        void getActiveLayerResolvesTheStoredIdToItsLayer() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(ACTIVE_LAYER_KEY)).thenReturn("first");

                assertThat(MapLayerRegistry.getActiveLayer()).isSameAs(firstLayerMock);
            }
        }

        @Test
        void getActiveLayerFallsBackToTheDefaultForAStaleStoredId() {
            // An id from a build that shipped a layer since removed resolves to nothing, so the
            // registry falls back rather than leaving the bar pointing at no tab.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(ACTIVE_LAYER_KEY)).thenReturn("removed_long_ago");

                assertThat(MapLayerRegistry.getActiveLayer()).isSameAs(secondLayerMock);
            }
        }
    }

    @Nested
    class IsActive {

        @Test
        void isActiveIsTrueOnlyForTheResolvedActivePick() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(MapLayerRegistry.isActive(secondLayerMock)).isTrue();
                assertThat(MapLayerRegistry.isActive(firstLayerMock)).isFalse();
            }
        }
    }

    @Nested
    class SelectLayer {

        @Test
        void selectLayerStoresThePickedLayersId() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);

                MapLayerRegistry.selectLayer(firstLayerMock);

                verify(memoryMock).set(ACTIVE_LAYER_KEY, "first");
            }
        }
    }

    // Stubs a fresh sector whose memory is {@code memoryMock}, so a test drives the registry's
    // reads and writes through one mocked memory without repeating the two-hop wiring.
    private static void linkSectorMemoryTo(MockedStatic<Global> globalMock, MemoryAPI memoryMock) {
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getMemoryWithoutUpdate()).thenReturn(memoryMock);
        globalMock.when(Global::getSector).thenReturn(sectorMock);
    }
}
