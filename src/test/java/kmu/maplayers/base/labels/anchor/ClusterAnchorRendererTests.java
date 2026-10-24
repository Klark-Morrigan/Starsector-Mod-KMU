package kmu.maplayers.base.labels.anchor;

import kmlib.opengl.GlPasses;

import kmu.maplayers.base.render.MapFrame;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * Pins what the overlay pass decides, which is only whether to emit at all: past the guard it is
 * GL emission over runs already packed, and it needs a live GL context to run.
 *
 * <p>The pass is watched at the GL entry point. The empty skip is what keeps a diagnostic off the
 * bill of every normal frame: no anchor is built while the player has the overlay off, so an off
 * toggle has to cost this pass an emptiness check alone.
 */
final class ClusterAnchorRendererTests {

    // A frame that paints, and one at the far end of the map's fade. The scale is never read: what
    // these cases are about is the guard in front of the emission.
    private static final MapFrame PAINTING_FRAME = new MapFrame(1f, 1f);
    private static final MapFrame FADED_OUT_FRAME = new MapFrame(1f, 0f);

    // One dot to draw, so only the fade or the emptiness can be what stops the pass.
    private static final ClusterAnchorRuns ONE_DOT_RUNS = new ClusterAnchorRuns(
        List.of(),
        List.of(new ClusterAnchorRuns.DotRun(Color.WHITE, new float[] {0f, 0f})),
        1f,
        1f);

    @Nested
    class RenderOnMap {

        @Test
        void emitsNothingWhenNoAnchorsWereBuilt() {

            // ARRANGE
            try (var glPassesMock = mockStatic(GlPasses.class)) {

                // ACT
                ClusterAnchorRenderer.renderOnMap(ClusterAnchorRuns.NONE, PAINTING_FRAME);

                // ASSERT
                glPassesMock.verify(
                    () -> GlPasses.runBlendedPass(any(), any(), any()),
                    never());
            }
        }

        // At the ends of the map's zoom fade every run would emit at zero effective alpha.
        @Test
        void emitsNothingWhenTheOverlayIsFullyFadedOut() {

            // ARRANGE
            try (var glPassesMock = mockStatic(GlPasses.class)) {

                // ACT
                ClusterAnchorRenderer.renderOnMap(ONE_DOT_RUNS, FADED_OUT_FRAME);

                // ASSERT
                glPassesMock.verify(
                    () -> GlPasses.runBlendedPass(any(), any(), any()),
                    never());
            }
        }

        @Test
        void emitsTheRunsOnAPaintingFrame() {

            // ARRANGE
            try (var glPassesMock = mockStatic(GlPasses.class)) {

                // ACT
                ClusterAnchorRenderer.renderOnMap(ONE_DOT_RUNS, PAINTING_FRAME);

                // ASSERT
                glPassesMock.verify(
                    () -> GlPasses.runBlendedPass(any(), any(), any()));
            }
        }
    }
}
