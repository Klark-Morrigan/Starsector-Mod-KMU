package kmu.maplayers.base.render.clusters.debug;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one question the overlay answers about itself: whether there is anything to draw. A
 * renderer skips its whole GL pass on the strength of it, so the answer has to be "no" only when
 * every stage really is empty - a stage the smoothing gates left unfilled must not make a
 * populated overlay read as nothing to draw, and an overlay with only one stage populated must
 * not be skipped.
 */
final class ClusterBorderStageOverlayTest {
    private static final List<float[]> ONE_LOOP = List.of(new float[] {0, 0, 1, 0, 1, 1});

    @Nested
    class IsEmpty {

        @Test
        void isEmptyReportsEmptyWhenNoStageHasALoop() {
            var overlay = new ClusterBorderStageOverlay(
                List.of(),
                List.of(),
                List.of());

            assertThat(overlay.isEmpty())
                .isTrue();
        }

        @Test
        void isEmptyReportsNotEmptyForTheBaseStageAlone() {
            // Both gates off is the ordinary reading, not a degenerate one: the base stage is the
            // only one every cluster always has, so an overlay skipped in this state would draw
            // nothing whenever the player turns both smoothing passes off.
            var overlay = new ClusterBorderStageOverlay(
                ONE_LOOP,
                List.of(),
                List.of());

            assertThat(overlay.isEmpty())
                .isFalse();
        }

        @Test
        void isEmptyReportsNotEmptyForTheDespikedStageAlone() {
            var overlay = new ClusterBorderStageOverlay(
                List.of(),
                ONE_LOOP,
                List.of());

            assertThat(overlay.isEmpty())
                .isFalse();
        }

        @Test
        void isEmptyReportsNotEmptyForTheRoundedStageAlone() {
            var overlay = new ClusterBorderStageOverlay(
                List.of(),
                List.of(),
                ONE_LOOP);

            assertThat(overlay.isEmpty())
                .isFalse();
        }
    }
}
