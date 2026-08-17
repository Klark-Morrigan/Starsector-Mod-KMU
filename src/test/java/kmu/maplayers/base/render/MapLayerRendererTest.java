package kmu.maplayers.base.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins what this seam decides on a layer's behalf: a renderer that states only how it paints shows no
 * hover box, prepares nothing per frame and answers the pointer on none of its passes. Those defaults
 * are what let a painting layer say nothing about a single cell of its map, or about keeping anything
 * up to date, without writing a method to say so - so they are pinned against a renderer that
 * implements the interface bare rather than against a mock, which would answer for the interface
 * instead of it.
 */
final class MapLayerRendererTest {
    private static final float FACTOR = 1f;

    // A layer that emits fixed geometry and nothing else: the case every default exists for.
    private final MapLayerRenderer paintOnlyRenderer = (factor, alphaMult, band) -> {
    };

    @Nested
    class ResolveHoverTooltip {

        @Test
        void resolveHoverTooltipIsEmptyForARendererThatOnlyPaints() {
            assertThat(paintOnlyRenderer.resolveHoverTooltip()).isEmpty();
        }
    }

    @Nested
    class PrepareFrame {

        @Test
        void prepareFrameDoesNothingForARendererThatOnlyPaints() {
            // Called once per frame on every renderer, so a layer with nothing to refresh must be
            // able to leave it alone rather than implement an empty method to be skipped.
            assertThatCode(() -> paintOnlyRenderer.prepareFrame(FACTOR))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    class PublishHoverForPass {

        @Test
        void publishHoverForPassDoesNothingForARendererThatOnlyPaints() {
            // Called on every pass of every renderer, so a layer that has nothing to say about the
            // cursor must be able to leave it alone - the read behind it is a matrix readback, and
            // paying for one per pass on a layer with no hover would be the whole cost for nothing.
            assertThatCode(() -> paintOnlyRenderer.publishHoverForPass(FACTOR))
                .doesNotThrowAnyException();
        }
    }
}
