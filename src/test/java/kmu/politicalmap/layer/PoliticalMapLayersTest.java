package kmu.politicalmap.layer;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the layer registry's stable contract: the tabs the bar composes, the pick an untouched
 * save resolves to, the faction overlay's persisted on/off state, and the paint gate the
 * terrain plugin reads - the things every consumer of the registry (bar, input listener,
 * terrain gate) depends on.
 */
final class PoliticalMapLayersTest {
    // The frozen sector-memory keys the registry serialises the pick and the overlay under.
    // Pinned here as literals so a rename - which would silently reset every existing save to
    // the default - fails this test rather than shipping.
    private static final String ACTIVE_LAYER_KEY = "$kmu_political_active_layer";
    private static final String FACTION_OVERLAY_KEY = "$kmu_political_faction_overlay_on";

    @Nested
    class GetLayers {

        @Test
        void getLayersListsNoLayerThenFactionsInTabOrder() {
            var ids = PoliticalMapLayers.getLayers().stream()
                    .map(PoliticalMapLayer::getId)
                    .toList();

            assertThat(ids).containsExactly("no_layer", "factions");
        }
    }

    @Nested
    class GetActiveLayer {

        @Test
        void getActiveLayerDefaultsToFactionsWithoutASavedPick() {
            // No sector means no save to read a pick from, so the registry falls back to its
            // default - the faction view, keeping the overlay up the first time the map opens.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(PoliticalMapLayers.getActiveLayer().getId()).isEqualTo("factions");
            }
        }
    }

    @Nested
    class IsFactionTerritoryActive {

        @Test
        void isFactionTerritoryActiveIsTrueForTheDefaultFactionViewWithTheOverlayOn() {
            // The untouched-save state: default layer FACTIONS and default overlay on, so the
            // gate is up the first time the map opens.
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                globalMock.when(Global::getSector).thenReturn(null);

                assertThat(PoliticalMapLayers.isFactionTerritoryActive()).isTrue();
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

                assertThat(PoliticalMapLayers.isFactionTerritoryActive()).isFalse();
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

                assertThat(PoliticalMapLayers.isFactionOverlayEnabled()).isTrue();
            }
        }

        @Test
        void isFactionOverlayEnabledReturnsTheStoredFlagWhenPresent() {
            try (MockedStatic<Global> globalMock = mockStatic(Global.class)) {
                var memoryMock = mock(MemoryAPI.class);
                linkSectorMemoryTo(globalMock, memoryMock);
                when(memoryMock.contains(FACTION_OVERLAY_KEY)).thenReturn(true);

                when(memoryMock.getBoolean(FACTION_OVERLAY_KEY)).thenReturn(true);
                assertThat(PoliticalMapLayers.isFactionOverlayEnabled()).isTrue();

                when(memoryMock.getBoolean(FACTION_OVERLAY_KEY)).thenReturn(false);
                assertThat(PoliticalMapLayers.isFactionOverlayEnabled()).isFalse();
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

                PoliticalMapLayers.toggleFactionOverlay();

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

                PoliticalMapLayers.toggleFactionOverlay();

                verify(memoryMock).set(FACTION_OVERLAY_KEY, true);
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
