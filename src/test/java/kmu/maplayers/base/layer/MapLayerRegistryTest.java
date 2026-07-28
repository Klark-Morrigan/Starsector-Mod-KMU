package kmu.maplayers.base.layer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the framework registry's contract with fake layers: the tab order it hands back, the pick an
 * untouched save resolves to, the frozen keys each screen's selection reads and writes, and how it fans a
 * pre-split save's shared pick into both screens' keys. The generic persisted-pick logic is
 * {@link PersistedActiveLayerSelectionTest}'s; this pins only what the registry adds - the frozen map,
 * intel, and legacy keys, and how {@link MapLayerRegistry#isActive} resolves which screen's pick is the
 * live one. The screens themselves are stand-in gates here: which concrete screens exist is the
 * composition root's business, and this pins only that the showing one wins.
 */
final class MapLayerRegistryTest {
    // The frozen sector-memory keys, pinned as literals so a rename - which would silently reset every
    // existing save - fails this test rather than shipping. The map and intel keys are the current
    // per-screen ones; the legacy key is the pre-split un-suffixed key both are migrated from.
    private static final String MAP_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_map";
    private static final String INTEL_ACTIVE_LAYER_KEY = "$kmu_political_active_layer_intel";
    private static final String LEGACY_ACTIVE_LAYER_KEY = "$kmu_political_active_layer";

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
        // The registry is static, and one test here deliberately empties it; restoring a roster stops
        // that emptied state from outliving this class.
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

    @Nested
    class MigrateStoredLayerId {

        @Test
        void migrateStoredLayerIdRewritesEachScreensStoredPickWhenItIsTheLegacyId() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(MAP_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(MAP_ACTIVE_LAYER_KEY)).thenReturn("old_id");
                when(memoryMock.contains(INTEL_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(INTEL_ACTIVE_LAYER_KEY)).thenReturn("old_id");

                MapLayerRegistry.migrateStoredLayerId("old_id", "new_id");

                verify(memoryMock).set(MAP_ACTIVE_LAYER_KEY, "new_id");
                verify(memoryMock).set(INTEL_ACTIVE_LAYER_KEY, "new_id");
            }
        }

        @Test
        void migrateStoredLayerIdLeavesAPickThatIsNotTheLegacyId() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(MAP_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(MAP_ACTIVE_LAYER_KEY)).thenReturn("some_current_id");
                when(memoryMock.contains(INTEL_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(INTEL_ACTIVE_LAYER_KEY)).thenReturn("some_current_id");

                MapLayerRegistry.migrateStoredLayerId("old_id", "new_id");

                verify(memoryMock, never()).set(anyString(), any());
            }
        }
    }

    @Nested
    class MigrateLegacyActiveLayerKey {

        @Test
        void migrateLegacyActiveLayerKeyFansTheLegacyPickIntoBothKeysAndClearsTheLegacyKey() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(LEGACY_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(LEGACY_ACTIVE_LAYER_KEY)).thenReturn("first");
                when(memoryMock.contains(MAP_ACTIVE_LAYER_KEY)).thenReturn(false);
                when(memoryMock.contains(INTEL_ACTIVE_LAYER_KEY)).thenReturn(false);

                MapLayerRegistry.migrateLegacyActiveLayerKey();

                verify(memoryMock).set(MAP_ACTIVE_LAYER_KEY, "first");
                verify(memoryMock).set(INTEL_ACTIVE_LAYER_KEY, "first");
                verify(memoryMock).unset(LEGACY_ACTIVE_LAYER_KEY);
            }
        }

        @Test
        void migrateLegacyActiveLayerKeySeedsOnlyTheEmptyKeyButStillClearsTheLegacyKey() {
            // A save written after the split already holds a map pick, so the stale legacy value must not
            // overwrite it - but the empty intel key is still seeded, and the legacy key is cleared so no
            // un-suffixed key lingers.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(LEGACY_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.getString(LEGACY_ACTIVE_LAYER_KEY)).thenReturn("first");
                when(memoryMock.contains(MAP_ACTIVE_LAYER_KEY)).thenReturn(true);
                when(memoryMock.contains(INTEL_ACTIVE_LAYER_KEY)).thenReturn(false);

                MapLayerRegistry.migrateLegacyActiveLayerKey();

                verify(memoryMock, never()).set(eq(MAP_ACTIVE_LAYER_KEY), any());
                verify(memoryMock).set(INTEL_ACTIVE_LAYER_KEY, "first");
                verify(memoryMock).unset(LEGACY_ACTIVE_LAYER_KEY);
            }
        }

        @Test
        void migrateLegacyActiveLayerKeyIsANoOpWithoutALegacyKey() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(LEGACY_ACTIVE_LAYER_KEY)).thenReturn(false);

                MapLayerRegistry.migrateLegacyActiveLayerKey();

                verify(memoryMock, never()).set(anyString(), any());
                verify(memoryMock, never()).unset(anyString());
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
