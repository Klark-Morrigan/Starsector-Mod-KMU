package kmu.maplayers.base.layer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.render.MapLayerRenderer;

import org.junit.jupiter.api.AfterEach;
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
 * Pins the framework registry's contract with fake layers: the tab order it hands back, the pick an
 * untouched save resolves to, and the frozen keys each screen's selection reads and writes. The generic
 * persisted-pick logic is {@link PersistedActiveLayerSelectionTest}'s; this pins only what the registry
 * adds - the frozen map and intel keys, and how {@link MapLayerRegistry#isActive} resolves which
 * screen's pick is the live one. The screens themselves are stand-in gates here: which concrete screens
 * exist is the composition root's business, and this pins only that the showing one wins.
 */
final class MapLayerRegistryTest {
    // The frozen sector-memory keys, pinned as literals so a rename - which would silently reset every
    // existing save - fails this test rather than shipping. One per screen, so the two picks stay
    // independent.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";

    // The machinery of the sector being drawn, which the registry passes through rather than
    // resolves. One for the class, so a case asserting it reached the layer is comparing against
    // the very object it handed in.
    private final MapLayerInstallation installation = new MapLayerInstallation(null);

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);
    private final IntelScreenViewFake intelScreenFake = new IntelScreenViewFake();

    @BeforeEach
    void registerTwoFakeLayers() {
        when(firstLayerMock.getId()).thenReturn("first");
        when(secondLayerMock.getId()).thenReturn("second");
        // Second layer is the default, so an untouched save resolves to it - the same shape the real
        // composition root uses (a non-leading default pick).
        MapLayerRegistry.registerLayers(List.of(firstLayerMock, secondLayerMock), secondLayerMock);
        // The registry is static, so a screen left wired would outlive its test. Handing it a fresh
        // fake per test starts each from the intel screen closed rather than wherever a neighbour left
        // it - and keeps the live binding, which reaches into a running game, out of the suite.
        MapLayerRegistry.registerIntelScreen(intelScreenFake);
    }

    @AfterEach
    void restoreARegisteredRoster() {
        MapLayerRosters.restoreNonEmptyRoster();
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
    class GetMapSelection {

        @Test
        void getMapSelectionReadsAndWritesTheFrozenMapKey() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);

                MapLayerRegistry.getMapSelection().selectLayer(firstLayerMock);

                verify(memoryMock).set(MAP_ACTIVE_LAYER_KEY, "first");
            }
        }
    }

    @Nested
    class GetIntelSelection {

        @Test
        void getIntelSelectionReadsAndWritesTheFrozenIntelKey() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);

                MapLayerRegistry.getIntelSelection().selectLayer(firstLayerMock);

                verify(memoryMock).set(INTEL_ACTIVE_LAYER_KEY, "first");
            }
        }
    }

    @Nested
    class GetActiveLayer {

        @Test
        void getActiveLayerAnswersFromTheShowingScreensPick() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(MAP_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(MAP_ACTIVE_LAYER_KEY)).thenReturn("second");
                when(memoryMock.contains(INTEL_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(INTEL_ACTIVE_LAYER_KEY)).thenReturn("first");
                intelScreenFake.setIntelTabOpen(true);

                assertThat(MapLayerRegistry.getActiveLayer()).isSameAs(firstLayerMock);
            }
        }

        @Test
        void getActiveLayerIsNullBeforeAnyLayerIsRegistered() {
            // The map surface can be asked for a frame before the composition root has run, so the
            // registry has to answer "no pick" rather than leave a caller to find out by throwing.
            MapLayerRegistry.registerLayers(List.of(), null);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(MapLayerRegistry.getActiveLayer()).isNull();
            }
        }
    }

    @Nested
    class ResolveActiveMapRenderer {

        @Test
        void resolveActiveMapRendererAsksTheActiveLayerAboutTheInstallationItWasHanded() {
            // The roster is the process's while a renderer is one sector's, so the registry must
            // pass the installation through rather than resolve one of its own - a registry that
            // picked the running sector's would hand every surface the same renderer however many
            // sectors were being drawn. Pinned by stubbing that one installation and no other, so a
            // registry substituting its own would find nothing stubbed for it.
            var layerRendererMock = mock(MapLayerRenderer.class);

            when(secondLayerMock.resolveRenderer(installation))
                .thenReturn(layerRendererMock);

            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(MapLayerRegistry.resolveActiveMapRenderer(installation))
                    .isSameAs(layerRendererMock);
            }
        }

        @Test
        void resolveActiveMapRendererIsNullWhenTheActivePickDrawsNothing() {
            // The switch-only tab, whose whole expression is a null renderer - and the pre-
            // registration frame below it, answered the same way so no pass driven by the active
            // pick needs a case for either. Stated rather than left to the stub's own default, or
            // the case would pass on a layer that was never asked at all.
            when(secondLayerMock.resolveRenderer(installation))
                .thenReturn(null);

            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(MapLayerRegistry.resolveActiveMapRenderer(installation))
                    .isNull();
            }
        }

        @Test
        void resolveActiveMapRendererIsNullBeforeAnyLayerIsRegistered() {
            MapLayerRegistry.registerLayers(List.of(), null);

            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(MapLayerRegistry.resolveActiveMapRenderer(installation))
                    .isNull();
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

        @Test
        void isActiveAnswersFromTheIntelPickWhileTheIntelScreenIsUp() {
            // The bug this guards: each screen keeps its own tab, so an overlay reading one fixed
            // screen's pick painted the sector map's choice onto the intel screen - No Layer on the
            // intel tab could not turn it off there.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                // The two screens sit on different tabs, so only a pick read from the right key can
                // tell them apart.
                when(memoryMock.contains(MAP_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(MAP_ACTIVE_LAYER_KEY)).thenReturn("second");
                when(memoryMock.contains(INTEL_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(INTEL_ACTIVE_LAYER_KEY)).thenReturn("first");
                intelScreenFake.setIntelTabOpen(true);

                assertThat(MapLayerRegistry.isActive(firstLayerMock)).isTrue();
                assertThat(MapLayerRegistry.isActive(secondLayerMock)).isFalse();
            }
        }

        @Test
        void isActiveAnswersFromTheMapPickWhileTheIntelScreenIsNotUp() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(MAP_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(MAP_ACTIVE_LAYER_KEY)).thenReturn("second");
                when(memoryMock.contains(INTEL_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(INTEL_ACTIVE_LAYER_KEY)).thenReturn("first");
                intelScreenFake.setIntelTabOpen(false);

                assertThat(MapLayerRegistry.isActive(secondLayerMock)).isTrue();
                assertThat(MapLayerRegistry.isActive(firstLayerMock)).isFalse();
            }
        }
    }

    // Stubs a fresh sector whose memory is {@code memoryMock}, so a test drives the registry's reads and
    // writes through one mocked memory without repeating the two-hop wiring.
    private static void linkSectorMemoryTo(MockedStatic<Global> globalMock, MemoryAPI memoryMock) {
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getMemoryWithoutUpdate()).thenReturn(memoryMock);
        globalMock.when(Global::getSector).thenReturn(sectorMock);
    }
}
