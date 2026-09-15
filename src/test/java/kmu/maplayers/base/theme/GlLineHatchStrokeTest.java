package kmu.maplayers.base.theme;

import kmlib.opengl.GlLineQuality;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link GlLineHatchStroke#computeWidthPixelsAt} - that the ink covers the same share of the
 * gap at every zoom, and that a fraction below one cannot close the gap at any of them.
 *
 * <p>Pinned because the two are the whole reason the width is carried as a fraction rather than as
 * the pixel count {@code glLineWidth} takes. A width that did not scale with the frame is a screen
 * measure over world-space geometry: it covers an ever larger share of the gap as the map zooms
 * out, filling the hatch in solid past the whole gap and clipping neighbouring strokes into shards
 * just short of it. Neither shows up as a failure anywhere else - the geometry is correct either
 * way, and only the picture is wrong.
 */
final class GlLineHatchStrokeTest {

    private static final float TOLERANCE = 1e-4f;

    // A thousand world units between lines, the shipped spacing, so the numbers below read as the
    // map's own.
    private static final double SPACING = 1000.0;

    @Nested
    class ComputeWidthPixelsAt {

        @Test
        void theInkCoversTheSameShareOfTheGapAtEveryZoom() {
            var stroke = new GlLineHatchStroke(GlLineQuality.ALIASED, 0.25);

            // Zoomed out, the gap is 50 screen pixels; zoomed in an order of magnitude, 500. A
            // quarter of each is what the stroke asks for.
            assertThat(stroke.computeWidthPixelsAt(SPACING, 0.05f))
                .isCloseTo(12.5f, within(TOLERANCE));
            assertThat(stroke.computeWidthPixelsAt(SPACING, 0.5f))
                .isCloseTo(125f, within(TOLERANCE));
        }

        @Test
        void aFractionBelowOneNeverClosesTheGapAtAnyZoom() {
            // The heaviest hatch the settings offer, walked across the zoom range: the stroke that
            // met its gap would read as solid fill, which is the state the hatch exists to be
            // distinguishable from.
            var stroke = new GlLineHatchStroke(GlLineQuality.ALIASED, 0.9);

            for (var factor = 0.01f; factor < 1f; factor += 0.01f) {
                assertThat(stroke.computeWidthPixelsAt(SPACING, factor))
                    .isLessThan((float) SPACING * factor);
            }
        }

        @Test
        void aHeavierFractionInksMoreOfTheSameGap() {
            var light = new GlLineHatchStroke(GlLineQuality.ALIASED, 0.1);
            var heavy = new GlLineHatchStroke(GlLineQuality.ALIASED, 0.8);

            assertThat(heavy.computeWidthPixelsAt(SPACING, 0.1f))
                .isGreaterThan(light.computeWidthPixelsAt(SPACING, 0.1f));
        }
    }
}
