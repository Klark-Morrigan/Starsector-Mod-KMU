package kmu.politicalmap.layer;

import com.fs.starfarer.api.Global;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the layer registry's stable contract: the tabs the bar composes, in order, and the
 * pick an untouched save resolves to - the two things every consumer of the registry
 * (bar, input listener, terrain gate) depends on.
 */
final class PoliticalMapLayersTest {

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
                assertThat(PoliticalMapLayers.isFactionTerritoryActive()).isTrue();
            }
        }
    }
}
