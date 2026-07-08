package kmu.maplayers.politicalmap.factions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmu.maplayers.MapLayers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the faction overlay's stable contract: the persisted on/off flag it defaults to and
 * stores, and the paint gate the faction terrain plugin reads. The gate depends on the layer
 * registry, so the real layers are registered first (the composition root's own wiring),
 * putting the faction view in the default-active position these tests assume.
 */
final class FactionOverlayStateTest {
    // The frozen sector-memory keys the overlay and the active pick serialise under. Pinned as
    // literals so a rename - which would silently reset every existing save to the default -
    // fails this test rather than shipping.
    private static final String ACTIVE_LAYER_KEY = "$kmu_political_active_layer";
    private static final String FACTION_OVERLAY_KEY = "$kmu_political_faction_overlay_on";

    @BeforeEach
    void registerRealLayers() {
        // The gate reads MapLayerRegistry.isActive(FactionsLayer), so the faction view must be
        // registered and default-active - exactly what the composition root installs at load.
        MapLayers.registerAll();
    }

    @Nested
    class IsFactionTerritoryActive {

        @Test
        void isFactionTerritoryActiveIsTrueForTheDefaultFactionViewWithTheOverlayOn() {
            // The untouched-save state: default layer FACTIONS and default overlay on, so the
            // gate is up the first time the map opens.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(FactionOverlayState.isFactionTerritoryActive()).isTrue();
            }
        }

        @Test
        void isFactionTerritoryActiveIsFalseWhenTheOverlayIsSwitchedOffOnTheActiveFactionView() {
            // The faction view stays the active tab (its control panel open) while the in-body
            // toggle is off, so the gate must be down even though the layer is selected.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(ACTIVE_LAYER_KEY)).thenReturn(false);
                when(memoryMock.contains(FACTION_OVERLAY_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(FACTION_OVERLAY_KEY)).thenReturn(false);

                assertThat(FactionOverlayState.isFactionTerritoryActive()).isFalse();
            }
        }
    }

    @Nested
    class IsFactionOverlayEnabled {

        @Test
        void isFactionOverlayEnabledDefaultsToOnWithoutASavedPick() {
            // No stored flag - either no sector or a save that never toggled it - resolves to
            // on, the same default the untouched sector map opens with.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(FactionOverlayState.isFactionOverlayEnabled()).isTrue();
            }
        }

        @Test
        void isFactionOverlayEnabledReturnsTheStoredFlagWhenPresent() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(FACTION_OVERLAY_KEY)).thenReturn(true);

                when(memoryMock.getBoolean(FACTION_OVERLAY_KEY)).thenReturn(true);
                assertThat(FactionOverlayState.isFactionOverlayEnabled()).isTrue();

                when(memoryMock.getBoolean(FACTION_OVERLAY_KEY)).thenReturn(false);
                assertThat(FactionOverlayState.isFactionOverlayEnabled()).isFalse();
            }
        }
    }

    @Nested
    class ToggleFactionOverlay {

        @Test
        void toggleFactionOverlayWritesOffWhenTheOverlayIsCurrentlyOn() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                // No stored flag -> currently on by default, so the flip must persist off.
                when(memoryMock.contains(FACTION_OVERLAY_KEY)).thenReturn(false);

                FactionOverlayState.toggleFactionOverlay();

                verify(memoryMock).set(FACTION_OVERLAY_KEY, false);
            }
        }

        @Test
        void toggleFactionOverlayWritesOnWhenTheOverlayIsCurrentlyOff() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(FACTION_OVERLAY_KEY)).thenReturn(true);
                when(memoryMock.getBoolean(FACTION_OVERLAY_KEY)).thenReturn(false);

                FactionOverlayState.toggleFactionOverlay();

                verify(memoryMock).set(FACTION_OVERLAY_KEY, true);
            }
        }
    }

    // Stubs a fresh sector whose memory is {@code memoryMock}, so a test drives the overlay's
    // reads and writes through one mocked memory without repeating the two-hop wiring.
    private static void linkSectorMemoryTo(MockedStatic<Global> globalMock, MemoryAPI memoryMock) {
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getMemoryWithoutUpdate()).thenReturn(memoryMock);
        globalMock.when(Global::getSector).thenReturn(sectorMock);
    }
}
