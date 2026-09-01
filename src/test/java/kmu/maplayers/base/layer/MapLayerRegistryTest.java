package kmu.maplayers.base.layer;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.render.MapLayerRenderer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the framework registry's contract with fake layers: the tab order it hands back, the pick an
 * untouched save resolves to, and the frozen keys each screen's selection reads and writes. The generic
 * persisted-pick logic is {@link PersistedActiveLayerSelectionTest}'s, and the show-or-hide pick's is
 * {@link PersistedMapLayerVisibilityTest}'s; this pins only what the registry adds - the four frozen
 * per-screen keys, and how {@link MapLayerRegistry#isActive} resolves which screen's pick is the live
 * one. The screens themselves are stand-in gates here: which concrete screens exist is the composition
 * root's business, and this pins only that the showing one wins.
 */
final class MapLayerRegistryTest {

    // The frozen sector-memory keys, pinned as literals so a rename - which would silently reset every
    // existing save - fails this test rather than shipping. One per screen, so the two picks stay
    // independent.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";

    // The same for each screen's show-or-hide pick, pinned for the same reason.
    private static final String MAP_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_map";
    private static final String INTEL_LAYERS_SHOWN_KEY = "$kmu_political_layers_shown_intel";

    // The machinery of the sector being drawn, which the registry passes through rather than
    // resolves. One for the class, so a case asserting it reached the layer is comparing against
    // the very object it handed in.
    private final MapLayerInstallation installation = new MapLayerInstallation(null);

    private final MapLayer firstLayerMock = mock(MapLayer.class);
    private final MapLayer secondLayerMock = mock(MapLayer.class);

    private final IntelScreenViewFake intelScreenFake = new IntelScreenViewFake();

    private SectorMemoryFake sectorMemoryFake;

    @BeforeEach
    void registerTwoFakeLayers() {

        when(firstLayerMock.getId())
            .thenReturn("first");
        when(secondLayerMock.getId())
            .thenReturn("second");

        // Second layer is the default, so an untouched save resolves to it - the same shape the real
        // composition root uses (a non-leading default pick).
        MapLayerRegistry.registerLayers(List.of(firstLayerMock, secondLayerMock), secondLayerMock);

        // The registry is static, so a screen left wired would outlive its test. Handing it a fresh
        // fake per test starts each from the intel screen closed rather than wherever a neighbour left
        // it - and keeps the live binding, which reaches into a running game, out of the suite.
        MapLayerRegistry.registerIntelScreen(intelScreenFake);

        sectorMemoryFake = new SectorMemoryFake();
    }

    @AfterEach
    void restoreARegisteredRoster() {
        MapLayerRosters.restoreNonEmptyRoster();
    }

    @AfterEach
    void closeTheSectorMemory() {
        sectorMemoryFake.close();
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

            MapLayerRegistry
                .getMapSelection()
                .selectLayer(firstLayerMock);

            assertThat(sectorMemoryFake.readStoredValue(MAP_ACTIVE_LAYER_KEY))
                .isEqualTo("first");
        }
    }

    @Nested
    class GetIntelSelection {

        @Test
        void getIntelSelectionReadsAndWritesTheFrozenIntelKey() {

            MapLayerRegistry
                .getIntelSelection()
                .selectLayer(firstLayerMock);

            assertThat(sectorMemoryFake.readStoredValue(INTEL_ACTIVE_LAYER_KEY))
                .isEqualTo("first");
        }
    }

    @Nested
    class GetMapVisibility {

        @Test
        void getMapVisibilityReadsAndWritesTheFrozenMapKey() {

            MapLayerRegistry
                .getMapVisibility()
                .showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(MAP_LAYERS_SHOWN_KEY))
                .isEqualTo(false);
        }
    }

    @Nested
    class GetIntelVisibility {

        @Test
        void getIntelVisibilityReadsAndWritesTheFrozenIntelKey() {

            MapLayerRegistry
                .getIntelVisibility()
                .showLayers(false);

            assertThat(sectorMemoryFake.readStoredValue(INTEL_LAYERS_SHOWN_KEY))
                .isEqualTo(false);
        }
    }

    @Nested
    class GetActiveLayer {

        @Test
        void getActiveLayerAnswersFromTheShowingScreensPick() {

            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(MapLayerRegistry.getActiveLayer())
                .isSameAs(firstLayerMock);
        }

        @Test
        void getActiveLayerIsNullBeforeAnyLayerIsRegistered() {
            // The map surface can be asked for a frame before the composition root has run, so the
            // registry has to answer "no pick" rather than leave a caller to find out by throwing.
            MapLayerRegistry.registerLayers(List.of(), null);
            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.getActiveLayer())
                .isNull();
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

            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.resolveActiveMapRenderer(installation))
                .isSameAs(layerRendererMock);
        }

        @Test
        void resolveActiveMapRendererIsNullWhenTheActivePickDrawsNothing() {
            // The switch-only tab, whose whole expression is a null renderer - and the pre-
            // registration frame below it, answered the same way so no pass driven by the active
            // pick needs a case for either. Stated rather than left to the stub's own default, or
            // the case would pass on a layer that was never asked at all.
            when(secondLayerMock.resolveRenderer(installation))
                .thenReturn(null);

            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.resolveActiveMapRenderer(installation))
                .isNull();
        }

        @Test
        void resolveActiveMapRendererIsNullBeforeAnyLayerIsRegistered() {

            MapLayerRegistry.registerLayers(List.of(), null);
            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.resolveActiveMapRenderer(installation))
                .isNull();
        }
    }

    @Nested
    class IsActive {

        @Test
        void isActiveIsTrueOnlyForTheResolvedActivePick() {

            sectorMemoryFake.removeSector();

            assertThat(MapLayerRegistry.isActive(secondLayerMock))
                .isTrue();
            assertThat(MapLayerRegistry.isActive(firstLayerMock))
                .isFalse();
        }

        @Test
        void isActiveAnswersFromTheIntelPickWhileTheIntelScreenIsUp() {
            // The bug this guards: each screen keeps its own tab, so an overlay reading one fixed
            // screen's pick painted the sector map's choice onto the intel screen - No Layer on the
            // intel tab could not turn it off there.
            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(true);

            assertThat(MapLayerRegistry.isActive(firstLayerMock))
                .isTrue();
            assertThat(MapLayerRegistry.isActive(secondLayerMock))
                .isFalse();
        }

        @Test
        void isActiveAnswersFromTheMapPickWhileTheIntelScreenIsNotUp() {

            storeADifferentPickOnEachScreen();
            intelScreenFake.setIntelTabOpen(false);

            assertThat(MapLayerRegistry.isActive(secondLayerMock))
                .isTrue();
            assertThat(MapLayerRegistry.isActive(firstLayerMock))
                .isFalse();
        }
    }

    // Puts the two screens on different tabs, which is what lets a case tell which key an answer came
    // off: with one pick on both keys, a registry reading the wrong one would still answer correctly.
    private void storeADifferentPickOnEachScreen() {

        sectorMemoryFake.storeValue(MAP_ACTIVE_LAYER_KEY, "second");
        sectorMemoryFake.storeValue(INTEL_ACTIVE_LAYER_KEY, "first");
    }
}
