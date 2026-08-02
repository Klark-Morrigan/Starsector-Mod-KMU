package kmu.maplayers.base.render.clusters.debug;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that a captured stage lands in the stage it was captured as, and that an uncaptured one
 * stays empty. Those two together are what let the overlay be read as a record of which passes
 * ran: a producer gates the passes itself and captures only what happened, so a collector that
 * cross-filled or defaulted a stage would report a pass that never ran, with the geometry to
 * make it look convincing.
 */
final class ClusterBorderStageCollectorTest {
    private static final List<List<double[]>> ONE_LOOP =
        List.of(List.of(
            new double[] {1, 2},
            new double[] {3, 4}));

    @Nested
    class BuildOverlay {

        @Test
        void buildOverlayLeavesEveryStageEmptyWhenNothingWasCaptured() {
            assertThat(new ClusterBorderStageCollector().buildOverlay().isEmpty())
                .isTrue();
        }

        @Test
        void buildOverlayFlattensCapturedLoopsIntoGlRuns() {
            var collector = new ClusterBorderStageCollector();

            collector.captureBaseStage(ONE_LOOP);

            // The vertices arrive as points and leave as one flat [x, y, x, y] run per loop -
            // the conversion the collector exists to do once instead of at each capture point.
            assertThat(collector.buildOverlay().baseLoops())
                .containsExactly(new float[] {1, 2, 3, 4});
        }

        @Test
        void buildOverlayAccumulatesEachClustersCaptureIntoTheSameStage() {
            var collector = new ClusterBorderStageCollector();

            collector.captureBaseStage(ONE_LOOP);
            collector.captureBaseStage(ONE_LOOP);

            // A producer captures per cluster, so a stage collects across the whole sector
            // rather than holding only the last cluster's loops.
            assertThat(collector.buildOverlay().baseLoops())
                .hasSize(2);
        }
    }

    @Nested
    class CaptureBaseStage {

        @Test
        void captureBaseStageFillsTheBaseStageAndLeavesTheSmoothedStagesEmpty() {
            var collector = new ClusterBorderStageCollector();

            collector.captureBaseStage(ONE_LOOP);
            var overlay = collector.buildOverlay();

            assertThat(overlay.baseLoops())
                .hasSize(1);
            assertThat(overlay.despikedLoops())
                .isEmpty();
            assertThat(overlay.roundedLoops())
                .isEmpty();
        }
    }

    @Nested
    class CaptureDespikedStage {

        @Test
        void captureDespikedStageFillsOnlyTheDespikedStage() {
            var collector = new ClusterBorderStageCollector();

            collector.captureDespikedStage(ONE_LOOP);
            var overlay = collector.buildOverlay();

            assertThat(overlay.despikedLoops())
                .hasSize(1);
            assertThat(overlay.baseLoops())
                .isEmpty();
            assertThat(overlay.roundedLoops())
                .isEmpty();
        }
    }

    @Nested
    class CaptureRoundedStage {

        @Test
        void captureRoundedStageFillsOnlyTheRoundedStage() {
            var collector = new ClusterBorderStageCollector();

            collector.captureRoundedStage(ONE_LOOP);
            var overlay = collector.buildOverlay();

            assertThat(overlay.roundedLoops())
                .hasSize(1);
            assertThat(overlay.baseLoops())
                .isEmpty();
            assertThat(overlay.despikedLoops())
                .isEmpty();
        }
    }
}
