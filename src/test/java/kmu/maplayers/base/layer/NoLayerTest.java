package kmu.maplayers.base.layer;

import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the one text the empty view resolves for itself. The bar draws what a layer hands back and looks
 * nothing up, so the key a layer reaches for is now the layer's own fact: swapped for another tab's, it
 * is a wrong label on screen and nothing else catches it. What the key is worded as in the shipped
 * bundle is pinned against the strings file elsewhere.
 */
final class NoLayerTest {

    @Nested
    class ResolveTabLabelText {

        @Test
        void resolveTabLabelTextLettersTheTabFromTheEmptyViewsOwnKey() {

            try (var stringsMock = mockStatic(KmuStrings.class)) {

                stringsMock
                    .when(() -> KmuStrings.get(KmuStrings.MAP_LAYER_TAB_NO_LAYER))
                    .thenReturn("No Layer");

                assertThat(NoLayer.INSTANCE.resolveTabLabelText())
                    .isEqualTo("No Layer");
            }
        }
    }
}
