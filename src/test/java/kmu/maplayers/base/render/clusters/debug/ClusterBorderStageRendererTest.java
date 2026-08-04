package kmu.maplayers.base.render.clusters.debug;

import kmu.maplayers.base.labels.anchor.DiagnosticPalette;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how the three stages are told apart, which is the whole of what the overlay claims: an
 * earlier pass is under a later one, graded by the shared diagnostic ramp, and stroked wider so
 * it haloes out from beneath rather than being hidden by it. Get any of the three backwards and
 * the picture still draws - three rings in three colours - while saying the opposite of what
 * happened in the pipeline, which no crash and no empty-overlay check would catch.
 *
 * <p>The emission itself is not reachable here (it needs a live GL context, which is why the
 * coverage report exempts this class), but the emission decides nothing: it walks the list this
 * builds, in order. So the list is the behaviour, and it is pure.
 */
final class ClusterBorderStageRendererTest {
    private static final List<float[]> BASE_LOOPS = List.of(new float[] {0, 0});
    private static final List<float[]> DESPIKED_LOOPS = List.of(new float[] {1, 1});
    private static final List<float[]> ROUNDED_LOOPS = List.of(new float[] {2, 2});

    @Nested
    class ListStageStrokesBottomToTop {

        @Test
        void listStageStrokesBottomToTopOrdersTheStagesBaseDespikedRounded() {
            var strokes = ClusterBorderStageRenderer.listStageStrokesBottomToTop(buildOverlay());

            // Drawn in list order, so this is the stacking: the raw trace at the bottom and what
            // ships on top. Reversed, the overlay would show the finished border being buried by
            // the geometry it superseded.
            assertThat(strokes)
                .extracting(stroke -> stroke.loopRuns())
                .containsExactly(BASE_LOOPS, DESPIKED_LOOPS, ROUNDED_LOOPS);
        }

        @Test
        void listStageStrokesBottomToTopGradesTheStagesDiscardedIntermediateAccepted() {
            var strokes = ClusterBorderStageRenderer.listStageStrokesBottomToTop(buildOverlay());

            // The shared ramp, not colours of this overlay's own choosing: red always means
            // superseded and green always means what ships, across every diagnostic.
            assertThat(strokes)
                .extracting(stroke -> stroke.strokeColour())
                .containsExactly(
                    DiagnosticPalette.DISCARDED_COLOUR,
                    DiagnosticPalette.INTERMEDIATE_COLOUR,
                    DiagnosticPalette.ACCEPTED_COLOUR);
        }

        @Test
        void listStageStrokesBottomToTopTapersTheWidthsSoEachStageRingsOutFromUnderTheNext() {
            var strokes = ClusterBorderStageRenderer.listStageStrokesBottomToTop(buildOverlay());

            // Strictly decreasing, checked as a relation rather than against three literals: what
            // matters is that a lower stage is never covered by the one drawn over it, and the
            // exact pixel widths are free to be retuned.
            assertThat(strokes.get(0).lineWidth())
                .isGreaterThan(strokes.get(1).lineWidth());
            assertThat(strokes.get(1).lineWidth())
                .isGreaterThan(strokes.get(2).lineWidth());
        }

        @Test
        void listStageStrokesBottomToTopKeepsAGatedOffStageInPlaceAsAnEmptyStroke() {
            // Sanding off: the despiked stage is empty, and it still occupies its own slot. Were
            // it dropped, the rounded stage would inherit the despiked stage's colour and width
            // and the overlay would claim the sanding pass ran.
            var strokes = ClusterBorderStageRenderer.listStageStrokesBottomToTop(
                new ClusterBorderStageOverlay(BASE_LOOPS, List.of(), ROUNDED_LOOPS));

            assertThat(strokes)
                .hasSize(3);
            assertThat(strokes.get(1).loopRuns())
                .isEmpty();
            assertThat(strokes.get(2).strokeColour())
                .isEqualTo(DiagnosticPalette.ACCEPTED_COLOUR);
        }
    }

    private static ClusterBorderStageOverlay buildOverlay() {
        return new ClusterBorderStageOverlay(BASE_LOOPS, DESPIKED_LOOPS, ROUNDED_LOOPS);
    }
}
