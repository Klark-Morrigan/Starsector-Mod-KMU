package kmu.maplayers.base.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one thing this seam decides on a layer's behalf: a renderer that states only how it paints
 * shows no hover box. That default is what lets a painting layer say nothing about a single cell of its
 * map without writing a method to say so, so it is pinned against a renderer that implements the
 * interface bare rather than against a mock, which would answer for the interface instead of it.
 */
final class MapLayerRendererTest {

    @Nested
    class ResolveHoverTooltip {

        @Test
        void resolveHoverTooltipIsEmptyForARendererThatOnlyPaints() {
            MapLayerRenderer paintOnlyRenderer = (factor, alphaMult) -> {
            };

            assertThat(paintOnlyRenderer.resolveHoverTooltip()).isEmpty();
        }
    }
}
